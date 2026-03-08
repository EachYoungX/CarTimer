package com.EachYoungX.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

import com.EachYoungX.timer.services.FloatingWindowService;
import com.EachYoungX.timer.services.TimerService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            // 1. 获取统一的配置
            SharedPreferences prefs = context.getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);
            boolean autoStartApp = prefs.getBoolean("auto_start", false); // 是否允许开机响应
            boolean autoTimer = prefs.getBoolean("auto_timer", false);    // 是否立即开始计时

            if (!autoStartApp) return; // 如果总开关没开，直接退出

            // 2. 使用 WakeLock 确保 CPU 不在启动过程中休眠
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Timer:BootRun");
            wakeLock.acquire(10000); // 锁定 10 秒足够启动服务

            // 3. 启动逻辑
            try {
                // 启动悬浮窗服务
                Intent floatingIntent = new Intent(context, FloatingWindowService.class);
                startServiceCompat(context, floatingIntent);

                if (autoTimer) {
                    // 延迟启动计时，确保系统资源初始化完成
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        Intent timerIntent = new Intent(context, TimerService.class)
                                .setAction(TimerService.ACTION_START);
                        startServiceCompat(context, timerIntent);
                    }, 2000); // 增加到 2 秒更稳健
                }
            } finally {
                if (wakeLock.isHeld()) wakeLock.release();
            }
        }
    }

    // 适配 Android 8.0+ 的启动工具类
    private void startServiceCompat(Context context, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}