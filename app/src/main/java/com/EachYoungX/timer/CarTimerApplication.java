package com.EachYoungX.timer;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.EachYoungX.timer.ui.ThemeManager;

/**
 * 应用程序入口类
 * 负责：
 * 1. 初始化 ThemeManager
 * 2. 应用全局主题设置
 */
public class CarTimerApplication extends Application {
    private static Context appContext;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // 在这里初始化 ThemeManager（在 Application 创建时）
        ThemeManager.getInstance().init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化应用主题
        // 这里不需要调用 applyTheme，因为会在每个 Activity 的 onCreate 中调用
        appContext = getApplicationContext();
        ThemeManager.getInstance().init(this);
        if (ThemeManager.getInstance().isFollowSystem()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
    public static Context getAppContext() {
        return appContext;
    }
}
