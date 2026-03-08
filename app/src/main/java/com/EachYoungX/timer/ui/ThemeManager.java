package com.EachYoungX.timer.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.EachYoungX.timer.R;

/**
 * 主题管理器单例类
 * 
 * 功能：
 * 1. 管理 4 种主色调（经典蓝、活力橙、极简灰、深邃紫）
 * 2. 支持深色模式适配
 * 3. 支持跟随系统昼夜变化
 * 4. 使用 SharedPreferences 持久化用户选择
 * 
 * 使用方式：
 * 1. 在 Application.onCreate() 中调用 ThemeManager.getInstance().init(this)
 * 2. 在 Activity.onCreate() 最早期调用
 * ThemeManager.getInstance().applyTheme(activity)
 * 3. 用户切换主题后调用 setThemeColor() 和 setFollowSystem()，然后 recreate() Activity
 */
public class ThemeManager {

    // 主题颜色枚举
    public enum ThemeColor {
        BLUE("blue"), // 经典蓝
        ORANGE("orange"), // 活力橙
        WHITE("white"), // 极简灰
        BLACK("black"); // 深邃紫

        private final String value;

        ThemeColor(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static ThemeColor fromValue(String value) {
            for (ThemeColor color : values()) {
                if (color.value.equals(value)) {
                    return color;
                }
            }
            return BLUE; // 默认返回经典蓝
        }
    }

    // SharedPreferences 键名
    private static final String PREF_NAME = "theme_prefs";
    private static final String KEY_THEME_COLOR = "theme_color";
    private static final String KEY_FOLLOW_SYSTEM = "follow_system";
    private static final String KEY_DARK_MODE = "dark_mode"; // 新增：暗色模式开关

    // 单例实例
    private static ThemeManager instance;

    // SharedPreferences
    private SharedPreferences prefs;

    // 当前主题颜色
    private ThemeColor currentThemeColor;

    // 是否跟随系统
    private boolean followSystem;

    // 是否强制暗色模式（新增）
    private boolean darkMode;

    /**
     * 私有构造函数
     */
    private ThemeManager() {
    }

    /**
     * 获取单例实例
     */
    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * 初始化（在 Application.onCreate() 中调用）
     */
    public void init(@NonNull Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadSettings();
    }

    /**
     * 加载用户设置
     */
    private void loadSettings() {
        String themeColorStr = prefs.getString(KEY_THEME_COLOR, ThemeColor.BLUE.getValue());
        currentThemeColor = ThemeColor.fromValue(themeColorStr);
        followSystem = prefs.getBoolean(KEY_FOLLOW_SYSTEM, false);
        darkMode = prefs.getBoolean(KEY_DARK_MODE, false); // 新增：加载暗色模式设置
    }

    /**
     * 保存用户设置
     */
    private void saveSettings() {
        prefs.edit()
                .putString(KEY_THEME_COLOR, currentThemeColor.getValue())
                .putBoolean(KEY_FOLLOW_SYSTEM, followSystem)
                .putBoolean(KEY_DARK_MODE, darkMode) // 新增：保存暗色模式设置
                .apply();
    }

    /**
     * 应用主题（在 Activity.onCreate() 最早期调用）
     */
    public void applyTheme(@NonNull Context context) {
        // 优先级：强制暗色模式 > 跟随系统 > 默认亮色
        if (darkMode) {
            // 强制暗色模式
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (followSystem) {
            // 跟随系统
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            // 默认亮色模式
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // 设置主题样式
        int themeResId = getThemeResId();
        context.setTheme(themeResId);
    }

    /**
     * 根据当前主题颜色获取对应的主题资源 ID
     */
    private int getThemeResId() {
        switch (currentThemeColor) {
            case ORANGE:
                return R.style.Theme_CarTimer_Orange;
            case WHITE:
                return R.style.Theme_CarTimer_White;
            case BLACK:
                return R.style.Theme_CarTimer_Black;
            case BLUE:
            default:
                return R.style.Theme_CarTimer_Blue;
        }
    }

    /**
     * 设置主题颜色
     * 
     * @param themeColor 主题颜色
     */
    public void setThemeColor(@NonNull ThemeColor themeColor) {
        if (this.currentThemeColor != themeColor) {
            this.currentThemeColor = themeColor;
            saveSettings();
        }
    }

    /**
     * 获取当前主题颜色
     */
    public ThemeColor getThemeColor() {
        return currentThemeColor;
    }

    /**
     * 设置是否跟随系统昼夜变化
     * 注意：与暗色模式互斥，开启跟随系统会自动关闭暗色模式
     * 
     * @param follow true-跟随系统，false-不跟随
     */
    public void setFollowSystem(boolean follow) {
        if (this.followSystem != follow) {
            this.followSystem = follow;
            // 互斥逻辑：开启跟随系统时自动关闭暗色模式
            if (follow && this.darkMode) {
                this.darkMode = false;
            }
            saveSettings();
        }
    }

    /**
     * 设置强制暗色模式
     * 注意：与跟随系统互斥，开启暗色模式会自动关闭跟随系统
     * 
     * @param dark true-强制暗色，false-不强制
     */
    public void setDarkMode(boolean dark) {
        if (this.darkMode != dark) {
            this.darkMode = dark;
            // 互斥逻辑：开启暗色模式时自动关闭跟随系统
            if (dark && this.followSystem) {
                this.followSystem = false;
            }
            saveSettings();
        }
    }

    /**
     * 获取是否跟随系统
     */
    public boolean isFollowSystem() {
        return followSystem;
    }

    /**
     * 获取是否强制暗色模式（新增）
     */
    public boolean isDarkMode() {
        return darkMode;
    }

    /**
     * 获取所有可用的主题颜色
     */
    public ThemeColor[] getAvailableThemes() {
        return ThemeColor.values();
    }

    /**
     * 获取主题颜色的显示名称
     */
    public String getThemeDisplayName(ThemeColor themeColor) {
        switch (themeColor) {
            case BLUE:
                return "经典蓝";
            case ORANGE:
                return "活力橙";
            case WHITE:
                return "极简灰";
            case BLACK:
                return "深邃紫";
            default:
                return "未知";
        }
    }

    /**
     * 获取主题颜色的描述
     */
    public String getThemeDescription(ThemeColor themeColor) {
        switch (themeColor) {
            case BLUE:
                return "经典商务风格，清爽专业";
            case ORANGE:
                return "活力动感，充满能量";
            case WHITE:
                return "简约素雅，干净利落";
            case BLACK:
                return "深邃神秘，夜间友好";
            default:
                return "";
        }
    }
}
