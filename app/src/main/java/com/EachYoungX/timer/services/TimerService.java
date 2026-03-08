package com.EachYoungX.timer.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.EachYoungX.timer.CarTimerApplication;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.activities.MainActivity;
import com.EachYoungX.timer.database.LogDatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimerService extends Service {
    // --- 日志 TAG ---
    private static final String TAG = "TIMER_SERVICE_LIFECYCLE";
    // --- 客户端计数与自毁逻辑 ---
    private static long lastClientDisconnectTime = 0;
    private static final String KEY_LAST_DISCONNECT_TIME = "last_disconnect_time";

    private static int activeClients = 0;
    private static final Handler selfDestructHandler = new Handler(Looper.getMainLooper());
    private static Runnable selfDestructRunnable;
    private static final long SELF_DESTRUCT_DELAY = 2000L; // 2秒延迟

    public static final String ACTION_START = "com.EachYoungX.timer.ACTION_START";
    public static final String ACTION_PAUSE = "com.EachYoungX.timer.ACTION_PAUSE";
    public static final String ACTION_STOP = "com.EachYoungX.timer.ACTION_STOP";
    public static final String ACTION_TIME_UPDATE = "com.EachYoungX.timer.ACTION_TIME_UPDATE";
    public static final String ACTION_RESUME = "com.EachYoungX.timer.ACTION_RESUME";
    public static final String ACTION_GET_STATUS = "com.EachYoungX.timer.ACTION_GET_STATUS";
    public static final String EXTRA_IS_RUNNING = "is_running";

    // 状态持久化常量
    private static final String PREFS_NAME = "TimerState";
    private static final String KEY_IS_RUNNING = "is_running";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_ELAPSED_TIME = "elapsed_time";
    private static final String KEY_START_DATE_TIME = "start_date_time";
    private static final String KEY_LAST_HEARTBEAT = "last_heartbeat"; // 新增：最后心跳时间

    // 退出状态管理 - 区分崩溃恢复和正常关闭
    private static final String KEY_EXIT_STATUS = "exit_status";
    private static final int STATUS_STOPPED = 0; // 已手动停止
    private static final int STATUS_RUNNING = 1; // 正在运行（未结算）
    private static final int STATUS_FINALIZED = 2; // 已由于关闭/关机成功结算

    private final Handler handler;
    private long startTime;
    private long elapsedTime;
    private boolean isRunning;
    private Runnable timerRunnable;
    private static final String CHANNEL_ID = "timer_channel";
    private static final int NOTIFICATION_ID = 1;
    private String startDateTime;

    // 关机广播接收器
    private BroadcastReceiver shutdownReceiver;

    public TimerService() {
        handler = new Handler(Looper.getMainLooper());
    }

    public static void registerClient() {
        activeClients++;
        Log.d(TAG, "registerClient: Active clients: " + activeClients);
        selfDestructHandler.removeCallbacks(getSelfDestructRunnable());

        // 清除持久化的断开时间，表示用户回来了
        Context context = CarTimerApplication.getAppContext();
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_LAST_DISCONNECT_TIME)
                    .apply();
        }
    }

    /**
     * 供客户端注销
     */
    public static void unregisterClient() {
        activeClients--;
        Log.d(TAG, "unregisterClient: Active clients: " + activeClients);
        if (activeClients <= 0) {
            long now = System.currentTimeMillis();
            lastClientDisconnectTime = now;

            // 立即持久化最后的断开时间
            Context context = CarTimerApplication.getAppContext();
            if (context != null) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_LAST_DISCONNECT_TIME, now)
                        .apply();
            }

            Log.d(TAG, "unregisterClient: Persisted last client disconnect time: " + now);
            selfDestructHandler.postDelayed(getSelfDestructRunnable(), SELF_DESTRUCT_DELAY);
        }
    }

    /**
     * 获取当前活跃客户端数量（用于调试）
     */
    public static int getActiveClients() {
        return activeClients;
    }

    /**
     * 获取自毁任务的单例
     * 需要一个全局的 Context 来发送停止意图
     */
    private static Runnable getSelfDestructRunnable() {
        if (selfDestructRunnable == null) {
            selfDestructRunnable = () -> {
                Log.e(TAG, "SelfDestruct EXECUTION: Finalizing log.");
                Context context = CarTimerApplication.getAppContext();
                if (context != null) {
                    // 从磁盘读取真正断开的那一刻
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    long realDisconnectTime = prefs.getLong(KEY_LAST_DISCONNECT_TIME, System.currentTimeMillis());

                    Intent stopIntent = new Intent(context, TimerService.class);
                    stopIntent.setAction(ACTION_STOP);
                    stopIntent.putExtra("OVERRIDE_END_TIME", realDisconnectTime);
                    context.startService(stopIntent);
                }
            };
        }
        return selfDestructRunnable;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Service is being created.");

        // 先显示安全通知，防止 ANR（5 秒内必须调用 startForeground）
        showSafeNotification();

        // 关键逻辑：启动时检查是否有未完成的"僵尸记录"
        restoreAndCheckAnomaly();

        // 注册关机广播接收器
        registerShutdownReceiver();

        createNotificationChannel();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    elapsedTime = System.currentTimeMillis() - startTime;
                    broadcastTime();
                    updateNotification();

                    // 每秒更新"心跳时间"，证明应用还活着
                    updateHeartbeat();

                    handler.postDelayed(this, 1000);
                }
            }
        };

        // 根据退出状态判断是否需要恢复
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int lastStatus = prefs.getInt(KEY_EXIT_STATUS, STATUS_STOPPED);

        if (lastStatus == STATUS_RUNNING) {
            // 崩溃场景：发现上次没结算就死了（比如 ANR、内存被系统回收）
            Log.d(TAG, "onCreate: Detected crash recovery scenario (lastStatus=RUNNING). Restoring state.");
            restoreState();
            if (isRunning && startTime > 0) {
                Log.d(TAG, "onCreate: Restored running state. startTime=" + startTime);
                handler.post(timerRunnable);
                // 启动自毁检查：如果在重启后 2 秒内没有 UI 报到，则判定为孤儿进程，自动结束并保存
                selfDestructHandler.postDelayed(getSelfDestructRunnable(), SELF_DESTRUCT_DELAY);
            }
        } else {
            // 正常启动场景：上次是关机结算了，或者手动停止了
            Log.d(TAG, "onCreate: Normal startup (lastStatus=" + lastStatus + "). Clearing memory state.");
            // 等待 ACTION_START 指令
            clearMemoryState();
        }
    }

    /**
     * 关键逻辑：启动时检查是否有未完成的"僵尸记录"
     */
    private void restoreAndCheckAnomaly() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean wasRunning = prefs.getBoolean(KEY_IS_RUNNING, false);

        if (wasRunning) {
            // 发现异常：上次服务被强杀了，没有走 Stop 流程
            long lastStartTime = prefs.getLong(KEY_START_TIME, 0);
            long lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0);
            String lastStartDateTime = prefs.getString(KEY_START_DATE_TIME, null);

            if (lastStartTime > 0 && lastHeartbeat > lastStartTime) {
                // 将这段"残留"的时间段强制保存到数据库
                long finalDuration = lastHeartbeat - lastStartTime;

                // 极端检查：如果时长小于0（比如系统时间被手动调过），强制设为0
                if (finalDuration < 0)
                    finalDuration = 0;

                Log.w(TAG, "restoreAndCheckAnomaly: Found zombie session! Saving anomaly log. Duration: "
                        + (finalDuration / 1000) + "s");

                saveAnomalyLog(lastStartTime, lastHeartbeat, finalDuration);
            } else if (lastStartTime > 0) {
                // 没有心跳记录，使用最后一次断开时间或当前时间作为结束时间
                long lastDisconnectTime = prefs.getLong(KEY_LAST_DISCONNECT_TIME, System.currentTimeMillis());
                long finalDuration = Math.max(0, lastDisconnectTime - lastStartTime);

                Log.w(TAG, "restoreAndCheckAnomaly: Found incomplete session without heartbeat. Saving. Duration: "
                        + (finalDuration / 1000) + "s");

                saveAnomalyLog(lastStartTime, lastDisconnectTime, finalDuration);
            }

            // 处理完异常后，彻底重置持久化状态，防止重复录入
            clearState();
        }

        // 重新初始化状态
        this.isRunning = false;
        this.elapsedTime = 0;
        this.startTime = 0;
        this.startDateTime = null;
    }

    /**
     * 更新心跳：每一秒写一次轻量级的持久化
     * 使用异步线程写入，避免阻塞主线程
     */
    private void updateHeartbeat() {
        // 使用独立的后台线程写入心跳，避免阻塞主线程
        new Thread(() -> {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis())
                    .apply();
        }).start();
    }

    /**
     * 补录异常日志
     */
    private void saveAnomalyLog(long sTime, long eTime, long duration) {
        try {
            LogDatabaseHelper dbHelper = new LogDatabaseHelper(this);
            dbHelper.insertLog(sTime, eTime, duration);
            Log.i(TAG, "saveAnomalyLog: Anomaly log saved successfully. Start: " + sTime + ", End: " + eTime
                    + ", Duration: " + duration);
        } catch (Exception e) {
            Log.e(TAG, "saveAnomalyLog: Error saving anomaly log", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 每次收到 Intent，首先移除自毁回调
        selfDestructHandler.removeCallbacks(getSelfDestructRunnable());

        if (intent == null) {
            // 这是系统在服务被杀后自动重启的情况 (START_STICKY)
            Log.e(TAG, "onStartCommand: Service RESTARTED by System (Sticky).");

            // 系统重启后，立即从 SharedPreferences 恢复状态
            restoreStateFromPrefs();

            // 系统重启时，确保 foreground 状态正常
            ensureForeground();

            if (activeClients <= 0) {
                Log.d(TAG, "onStartCommand: No clients after restart, starting self-destruct timer.");
                selfDestructHandler.postDelayed(getSelfDestructRunnable(), SELF_DESTRUCT_DELAY);
            }
            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "onStartCommand: Action = " + action);

        // 对于 START/RESUME 动作，立即确保前台服务状态
        if (ACTION_START.equals(action) || ACTION_RESUME.equals(action)) {
            SharedPreferences prefs = getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);
            boolean autoMinimize = prefs.getBoolean("auto_minimize", false);

            if (autoMinimize) {
                Log.d(TAG, "onStartCommand: Auto-minimize is ON. Starting foreground service.");
                startForegroundService();
            } else {
                Log.d(TAG, "onStartCommand: Auto-minimize is OFF. Starting as background service.");
                // 不需要前台通知，确保不在 foreground 状态
                ensureNotForeground();
            }
        }

        switch (action) {
            case ACTION_START:
            case ACTION_RESUME:
                startTimer();
                break;
            case ACTION_PAUSE:
                pauseTimer();
                break;
            case ACTION_STOP:
                stopTimer(System.currentTimeMillis());
                stopForeground(true);
                stopSelf();
                break;
            case ACTION_GET_STATUS:
                Intent statusIntent = new Intent(ACTION_TIME_UPDATE);
                statusIntent.putExtra(EXTRA_IS_RUNNING, isRunning);
                statusIntent.putExtra("time", elapsedTime);
                sendBroadcast(statusIntent);
                break;
        }

        return START_STICKY;
    }

    private void startTimer() {
        if (isRunning) {
            return;
        }

        isRunning = true;

        // 只有当 elapsedTime 为 0 时，才认为是全新开始
        if (elapsedTime <= 0) {
            startTime = System.currentTimeMillis();
            startDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        } else {
            // 如果有 elapsedTime，说明是从暂停中恢复，或者是从崩溃/重启中恢复
            startTime = System.currentTimeMillis() - elapsedTime;
            // 保留它最初的值
        }

        handler.post(timerRunnable);
        // 标记为运行中状态
        saveState(STATUS_RUNNING);
    }

    private void pauseTimer() {
        Log.d(TAG, "pauseTimer: Attempting to pause timer. Current isRunning state: " + isRunning);
        if (isRunning) {
            isRunning = false;
            handler.removeCallbacks(timerRunnable);
            elapsedTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, "pauseTimer: Timer handler removed. isRunning set to false.");
            saveState();
            broadcastTime();
        } else {
            Log.d(TAG, "pauseTimer: Timer was not running. No action taken.");
        }
    }

    private void stopTimer(long overrideEndTime) {
        Log.d(TAG, "stopTimer: --- STOP PROCESS START ---");
        isRunning = false;
        handler.removeCallbacks(timerRunnable);

        // 从磁盘重新读一次 startTime (防止内存变量丢失)
        restoreState();

        if (startDateTime != null && startTime > 0) {
            // 如果 overrideEndTime > 0，说明是自毁触发，使用那个持久化的"断开时刻"
            // 否则（用户手动点停止），使用当前系统时刻
            long finalEndTime = (overrideEndTime > 0) ? overrideEndTime : System.currentTimeMillis();

            // 最终时长 = 逻辑上的结束时间 - 逻辑上的开始时间
            long finalDuration = finalEndTime - startTime;

            // 极端检查：如果时长小于 0（比如系统时间被手动调过），强制设为 0
            if (finalDuration < 0)
                finalDuration = 0;

            Log.d(TAG, "stopTimer: Final Duration Calculated: " + (finalDuration / 1000) + "s (Used EndTime: "
                    + finalEndTime + ")");

            try {
                LogDatabaseHelper dbHelper = new LogDatabaseHelper(this);
                dbHelper.insertLog(startTime, finalEndTime, finalDuration);
            } catch (Exception e) {
                Log.e(TAG, "stopTimer: DB Error", e);
            }
        }

        // 标记为已手动停止状态
        saveState(STATUS_STOPPED);

        // 清理内存状态
        clearMemoryState();
        broadcastTime();
        Log.d(TAG, "stopTimer: --- STOP PROCESS COMPLETE ---");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e(TAG, "onTaskRemoved: !!!!!!! TASK REMOVED BY USER !!!!!!!");

        // 用户划掉后台，属于"软件层面的意图结束"
        // 执行影子结算，把最后一次心跳存入数据库
        finalizeAndSaveLog();

        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    /**
     * 注册关机广播接收器
     */
    private void registerShutdownReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(Intent.ACTION_REBOOT);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        shutdownReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.e(TAG, "onReceive: Shutdown/Reboot broadcast received. Action=" + action);

                // 关机/重启前，执行影子结算
                if (Intent.ACTION_SHUTDOWN.equals(action) || Intent.ACTION_REBOOT.equals(action)) {
                    Log.d(TAG, "onReceive: System shutting down. Finalizing and saving log.");
                    finalizeAndSaveLog();
                }
            }
        };

        try {
            registerReceiver(shutdownReceiver, filter);
            Log.d(TAG, "registerShutdownReceiver: Shutdown receiver registered.");
        } catch (Exception e) {
            Log.e(TAG, "registerShutdownReceiver: Failed to register receiver", e);
        }
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy: Service is being destroyed.");
        handler.removeCallbacks(timerRunnable);

        // 注销关机广播接收器
        if (shutdownReceiver != null) {
            try {
                unregisterReceiver(shutdownReceiver);
                Log.d(TAG, "onDestroy: Shutdown receiver unregistered.");
            } catch (Exception e) {
                Log.d(TAG, "onDestroy: Failed to unregister receiver", e);
            }
        }

        super.onDestroy();
    }

    private void saveState() {
        saveState(STATUS_RUNNING);
    }

    /**
     * 带状态标记的保存方法
     * 
     * @param status 退出状态（STATUS_RUNNING/STATUS_STOPPED/STATUS_FINALIZED）
     */
    private void saveState(int status) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_RUNNING, isRunning);
        editor.putLong(KEY_START_TIME, startTime);
        editor.putLong(KEY_ELAPSED_TIME, elapsedTime);
        editor.putString(KEY_START_DATE_TIME, startDateTime);
        editor.putInt(KEY_EXIT_STATUS, status);
        editor.apply();
        Log.d(TAG, "saveState: State saved. isRunning=" + isRunning + ", elapsedTime=" + elapsedTime + ", status="
                + status);
    }

    private void restoreState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isRunning = prefs.getBoolean(KEY_IS_RUNNING, false);
        startTime = prefs.getLong(KEY_START_TIME, 0);
        elapsedTime = prefs.getLong(KEY_ELAPSED_TIME, 0);
        startDateTime = prefs.getString(KEY_START_DATE_TIME, null);
        Log.d(TAG, "restoreState: State restored. isRunning=" + isRunning + ", elapsedTime=" + elapsedTime);
    }

    /**
     * 从 SharedPreferences 恢复状态到内存变量
     * 用于系统重启后恢复 startTime，避免计时归零
     */
    private void restoreStateFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean wasRunning = prefs.getBoolean(KEY_IS_RUNNING, false);
        long savedStartTime = prefs.getLong(KEY_START_TIME, 0);
        long savedElapsedTime = prefs.getLong(KEY_ELAPSED_TIME, 0);
        String savedStartDateTime = prefs.getString(KEY_START_DATE_TIME, null);

        if (wasRunning && savedStartTime > 0) {
            // 恢复状态
            this.isRunning = true;
            this.startTime = savedStartTime;
            this.elapsedTime = savedElapsedTime;
            this.startDateTime = savedStartDateTime;

            Log.d(TAG, "restoreStateFromPrefs: Restored running state. startTime=" + startTime +
                    ", elapsedTime=" + elapsedTime + ", startDateTime=" + startDateTime);

            // 重启计时器
            handler.post(timerRunnable);
        } else {
            Log.d(TAG, "restoreStateFromPrefs: No running state to restore.");
        }
    }

    private void clearState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Log.d(TAG, "clearState: SharedPreferences state has been cleared.");
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("计时器正在运行")
                .setContentText(formatTime(elapsedTime))
                .setSmallIcon(R.mipmap.timer)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void startForegroundService() {
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    /**
     * 显示安全通知，防止 ANR
     * 在 onCreate 中第一时间调用，确保 5 秒内完成 startForeground
     */
    private void showSafeNotification() {
        // 无条件先启动前台服务，防止系统 ANR
        // 后续会根据 autoMinimize 设置决定是否移除
        createNotificationChannel();
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        Log.d(TAG, "showSafeNotification: Foreground service started to prevent ANR.");
    }

    /**
     * 核心结算方法：根据最后一次心跳存入数据库，并标记为已结算
     * 用于处理人为关闭（杀后台）、关机等"软件层面的意图结束"场景
     */
    private void finalizeAndSaveLog() {
        if (isRunning) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0);
            long sTime = prefs.getLong(KEY_START_TIME, 0);
            String startDateTimeStr = prefs.getString(KEY_START_DATE_TIME, null);

            if (sTime > 0 && lastHeartbeat > sTime) {
                long duration = lastHeartbeat - sTime;

                // 极端检查：如果时长小于 0，强制设为 0
                if (duration < 0)
                    duration = 0;

                Log.d(TAG, "finalizeAndSaveLog: Saving final log. Start: " + sTime +
                        ", LastHeartbeat: " + lastHeartbeat + ", Duration: " + (duration / 1000) + "s");

                // 写入数据库
                try {
                    LogDatabaseHelper dbHelper = new LogDatabaseHelper(this);
                    dbHelper.insertLog(sTime, lastHeartbeat, duration);
                    Log.i(TAG, "finalizeAndSaveLog: Final log saved successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "finalizeAndSaveLog: Error saving to database", e);
                }
            } else {
                Log.d(TAG, "finalizeAndSaveLog: No valid data to save. sTime=" + sTime +
                        ", lastHeartbeat=" + lastHeartbeat);
            }

            // ：标记为已结算
            isRunning = false;
            saveState(STATUS_FINALIZED);

            // 移除前台通知
            try {
                stopForeground(true);
            } catch (Exception e) {
                Log.d(TAG, "finalizeAndSaveLog: stopForeground failed.", e);
            }

            Log.d(TAG, "finalizeAndSaveLog: State marked as FINALIZED.");
        } else {
            Log.d(TAG, "finalizeAndSaveLog: Timer was not running. No action needed.");
        }
    }

    /**
     * 清空内存状态，但不删除 SharedPreferences 中的 exit_status
     */
    private void clearMemoryState() {
        this.isRunning = false;
        this.elapsedTime = 0;
        this.startTime = 0;
        this.startDateTime = null;
        Log.d(TAG, "clearMemoryState: Memory state cleared.");
    }

    /**
     * 确保服务处于前台状态
     * 用于系统重启后恢复 foreground 状态
     */
    private void ensureForeground() {
        // 如果当前不在前台，且需要显示通知（根据偏好设置）
        SharedPreferences prefs = getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);
        boolean autoMinimize = prefs.getBoolean("auto_minimize", false);

        if (autoMinimize && isRunning) {
            Log.d(TAG, "ensureForeground: Restoring foreground state after system restart.");
            startForegroundService();
        }
    }

    /**
     * 确保服务不处于前台状态
     * 用于不需要通知栏的情况
     */
    private void ensureNotForeground() {
        // 如果当前在前台，移除它
        try {
            stopForeground(true);
            Log.d(TAG, "ensureNotForeground: Removed foreground notification.");
        } catch (Exception e) {
            // 可能本来就不在前台，忽略异常
            Log.d(TAG, "ensureNotForeground: Service was not in foreground.");
        }
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "计时器服务",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private String formatTime(long timeInMillis) {
        int hours = (int) (timeInMillis / 3600000);
        int minutes = (int) ((timeInMillis % 3600000) / 60000);
        int seconds = (int) ((timeInMillis % 60000) / 1000);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void broadcastTime() {
        Intent intent = new Intent(ACTION_TIME_UPDATE);
        intent.putExtra("time", elapsedTime);
        intent.putExtra(EXTRA_IS_RUNNING, isRunning); // 每次都发送运行状态
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
