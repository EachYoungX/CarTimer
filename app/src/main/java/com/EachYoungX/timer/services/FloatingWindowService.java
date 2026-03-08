package com.EachYoungX.timer.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.EachYoungX.timer.R;
import com.EachYoungX.timer.activities.MainActivity;

public class FloatingWindowService extends Service {
    private static final String PREFS_NAME = "TimerPrefs";
    private static final String PREF_X = "window_x";
    private static final String PREF_Y = "window_y";
    private static final String CHANNEL_ID = "floating_window_channel";
    private static final int NOTIFICATION_ID = 2;

    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

    private WindowManager windowManager;
    private View floatingView;
    private TextView floatingTimerText;
    private BroadcastReceiver timerReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        TimerService.registerClient();

        // 创建通知渠道并启动前台服务（必须在 5 秒内调用）
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        initFloatingWindow();
        registerTimerReceiver();
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "悬浮窗服务",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("保持悬浮窗显示");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("计时器运行中")
                .setContentText("悬浮窗已启动")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
    }

    private void initFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_timer, null);
        floatingTimerText = floatingView.findViewById(R.id.floating_timer_text);

        // 读取保存的位置
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int x = prefs.getInt(PREF_X, 0);
        int y = prefs.getInt(PREF_Y, 100);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;

        // 设置触摸监听
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // 保存位置
                        android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME,
                                Context.MODE_PRIVATE).edit();
                        editor.putInt(PREF_X, params.x);
                        editor.putInt(PREF_Y, params.y);
                        editor.apply();

                        // 如果移动距离很小，认为是点击事件
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 &&
                                Math.abs(event.getRawY() - initialTouchY) < 10) {
                            Intent intent = new Intent(FloatingWindowService.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
    }

    private void registerTimerReceiver() {
        timerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TimerService.ACTION_TIME_UPDATE.equals(intent.getAction())) {
                    long timeInMillis = intent.getLongExtra("time", 0);
                    updateTimerDisplay(timeInMillis);
                }
            }
        };
        IntentFilter filter = new IntentFilter(TimerService.ACTION_TIME_UPDATE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timerReceiver, filter);
        }
    }

    private void updateTimerDisplay(long timeInMillis) {
        int hours = (int) (timeInMillis / 3600000);
        int minutes = (int) ((timeInMillis % 3600000) / 60000);
        int seconds = (int) ((timeInMillis % 60000) / 1000);
        String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        floatingTimerText.setText(timeString);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TimerService.unregisterClient();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        if (timerReceiver != null) {
            unregisterReceiver(timerReceiver);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}