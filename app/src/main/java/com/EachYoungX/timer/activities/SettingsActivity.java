package com.EachYoungX.timer.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.EachYoungX.timer.R;
import com.EachYoungX.timer.ui.ThemeManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * 设置页面 Activity
 * 
 * 功能：
 * 1. 主题切换（4 种颜色）
 * 2. 跟随系统深色模式
 * 3. 行为设置（开机自启、自动计时、自动最小化）
 * 4. 关于信息
 */
public class SettingsActivity extends AppCompatActivity {

    // UI 组件
    private MaterialToolbar toolbar;
    private SwitchMaterial switchFollowSystem;
    private SwitchMaterial switchDarkMode; // 新增：暗色模式开关
    private FrameLayout themeBlue, themeOrange, themeWhite, themeBlack;
    private SwitchMaterial switchAutoStart, switchAutoTimer, switchAutoMinimize;
    private TextView tvVersion;

    // SharedPreferences
    private SharedPreferences prefs;

    // ThemeManager
    private ThemeManager themeManager;

    // 当前选中的主题视图
    private View selectedThemeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 在 super.onCreate() 之前应用主题
        themeManager = ThemeManager.getInstance();
        themeManager.applyTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        initToolbar();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        switchFollowSystem = findViewById(R.id.switch_follow_system);
        switchDarkMode = findViewById(R.id.switch_dark_mode); // 新增
        themeBlue = findViewById(R.id.theme_blue);
        themeOrange = findViewById(R.id.theme_orange);
        themeWhite = findViewById(R.id.theme_white);
        themeBlack = findViewById(R.id.theme_black);
        switchAutoStart = findViewById(R.id.switch_auto_start);
        switchAutoTimer = findViewById(R.id.switch_auto_timer);
        switchAutoMinimize = findViewById(R.id.switch_auto_minimize);
        tvVersion = findViewById(R.id.tv_version);

        prefs = getSharedPreferences("TimerPrefs", MODE_PRIVATE);

        // 设置版本号
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText(versionName);
        } catch (Exception e) {
            tvVersion.setText("1.0.0");
        }
    }

    private void initToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> finish());
    }

    /**
     * 加载用户设置
     */
    private void loadSettings() {
        // 加载主题设置
        ThemeManager.ThemeColor currentTheme = themeManager.getThemeColor();
        boolean followSystem = themeManager.isFollowSystem();
        boolean darkMode = themeManager.isDarkMode(); // 新增

        switchFollowSystem.setChecked(followSystem);
        switchDarkMode.setChecked(darkMode); // 新增
        updateFollowSystemUI(followSystem);

        // 设置当前选中的主题
        selectThemeView(currentTheme);

        // 加载行为设置
        switchAutoStart.setChecked(prefs.getBoolean("auto_start", false));
        switchAutoTimer.setChecked(prefs.getBoolean("auto_timer", false));
        switchAutoMinimize.setChecked(prefs.getBoolean("auto_minimize", false));
    }

    /**
     * 选择主题视图
     */
    private void selectThemeView(ThemeManager.ThemeColor theme) {
        // 清除之前的选中状态
        if (selectedThemeView != null) {
            selectedThemeView.setSelected(false);
        }

        // 设置新的选中状态
        switch (theme) {
            case ORANGE:
                selectedThemeView = themeOrange;
                break;
            case WHITE:
                selectedThemeView = themeWhite;
                break;
            case BLACK:
                selectedThemeView = themeBlack;
                break;
            case BLUE:
            default:
                selectedThemeView = themeBlue;
                break;
        }

        if (selectedThemeView != null) {
            selectedThemeView.setSelected(true);
        }
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // 主题颜色选择 - 经典蓝
        themeBlue.setOnClickListener(v -> {
            if (themeManager.getThemeColor() != ThemeManager.ThemeColor.BLUE) {
                themeManager.setThemeColor(ThemeManager.ThemeColor.BLUE);
                selectThemeView(ThemeManager.ThemeColor.BLUE);
                recreate();
            }
        });

        // 主题颜色选择 - 活力橙
        themeOrange.setOnClickListener(v -> {
            if (themeManager.getThemeColor() != ThemeManager.ThemeColor.ORANGE) {
                themeManager.setThemeColor(ThemeManager.ThemeColor.ORANGE);
                selectThemeView(ThemeManager.ThemeColor.ORANGE);
                recreate();
            }
        });

        // 主题颜色选择 - 极简灰
        themeWhite.setOnClickListener(v -> {
            if (themeManager.getThemeColor() != ThemeManager.ThemeColor.WHITE) {
                themeManager.setThemeColor(ThemeManager.ThemeColor.WHITE);
                selectThemeView(ThemeManager.ThemeColor.WHITE);
                recreate();
            }
        });

        // 主题颜色选择 - 深邃紫
        themeBlack.setOnClickListener(v -> {
            if (themeManager.getThemeColor() != ThemeManager.ThemeColor.BLACK) {
                themeManager.setThemeColor(ThemeManager.ThemeColor.BLACK);
                selectThemeView(ThemeManager.ThemeColor.BLACK);
                recreate();
            }
        });

        // 跟随系统深色模式
        switchFollowSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 1. 更新 ThemeManager 内部状态并保存到 SP（互斥逻辑在 ThemeManager 中处理）
            themeManager.setFollowSystem(isChecked);

            // 2. 调用 ThemeManager 应用全局夜间模式
            themeManager.applyTheme(this);

            // 3. 更新当前页面的 UI 样式
            updateFollowSystemUI(isChecked);

            // 4. 同步暗色模式开关状态（互斥）
            if (isChecked) {
                switchDarkMode.setChecked(false);
            }

            // 5. 立即重绘当前页面显示效果
            recreate();
        });

        // 暗色模式（新增）
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 1. 更新 ThemeManager 内部状态并保存到 SP（互斥逻辑在 ThemeManager 中处理）
            themeManager.setDarkMode(isChecked);

            // 2. 调用 ThemeManager 应用全局夜间模式
            themeManager.applyTheme(this);

            // 3. 同步跟随系统开关状态（互斥）
            if (isChecked) {
                switchFollowSystem.setChecked(false);
            }

            // 4. 立即重绘当前页面显示效果
            recreate();
        });

        // 行为设置
        switchAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_start", isChecked).apply();
        });

        switchAutoTimer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_timer", isChecked).apply();
        });

        switchAutoMinimize.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_minimize", isChecked).apply();
        });

        // 数据与隐私入口
        View cardDataPrivacy = findViewById(R.id.card_data_privacy);
        if (cardDataPrivacy != null) {
            cardDataPrivacy.setOnClickListener(v -> {
                Intent intent = new Intent(SettingsActivity.this, DataPrivacyActivity.class);
                startActivity(intent);
            });
        }
    }

    /**
     * 更新跟随系统开关的 UI 状态
     */
    /**
     * 更新跟随系统的 UI 显示
     * 优化：跟随系统开启时仍然允许更换主题色
     */
    private void updateFollowSystemUI(boolean followSystem) {
        // 优化：不禁用主题选择器，允许用户在跟随系统时仍然可以选择主题色
        // 主题选择器始终保持可用状态
        themeBlue.setEnabled(true);
        themeOrange.setEnabled(true);
        themeWhite.setEnabled(true);
        themeBlack.setEnabled(true);

        // 主题选择器始终保持正常透明度
        themeBlue.setAlpha(1.0f);
        themeOrange.setAlpha(1.0f);
        themeWhite.setAlpha(1.0f);
        themeBlack.setAlpha(1.0f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新设置状态（可能从其他页面返回后发生变化）
        loadSettings();
    }
}
