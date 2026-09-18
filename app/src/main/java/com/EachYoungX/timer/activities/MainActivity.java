package com.EachYoungX.timer.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.EachYoungX.timer.fragments.HomeFragment;
import com.EachYoungX.timer.fragments.LogFragment;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.fragments.StatisticsFragment;
import com.EachYoungX.timer.ui.ThemeManager;
import com.EachYoungX.timer.services.TimerService;
import com.EachYoungX.timer.utils.BackupManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AlertDialog;

/**
 * 主页面 Activity
 * 底部导航栏：主页、日志、统计、设置
 */
public class MainActivity extends AppCompatActivity {

    // 记录进入页面时的主题状态，用于检测主题是否变化
    private ThemeManager.ThemeColor lastThemeColor;
    private boolean lastFollowSystem;

    // 记录是否已经注册了客户端，避免重复注册
    private boolean clientRegistered = false;
    private boolean recoveryCheckStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 在 setContentView 之前应用主题
        ThemeManager.getInstance().applyTheme(this);

        // 记录当前主题状态
        saveThemeState();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(navListener);

        // 默认显示主页
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }

        // 首次启动先检查卸载重装后留下的公共备份，再决定是否自动启动计时器
        if (savedInstanceState == null) {
            ensureStoragePermissionThenCheckRecovery();
        }
        
        // 首次启动时注册客户端
        registerClientIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 1. 核心修复：检查进入设置页面后，主题是否被修改过
        if (isThemeChanged()) {
            Log.d("MainActivity", "Theme change detected, recreating...");
            // 更新记录的状态，防止无限重绘
            saveThemeState(); 
            // 重新创建 Activity 以应用新主题
            recreate(); 
            return; // 结束当前生命周期，等待 recreate
        }

        // 2. 原有的逻辑
        registerClientIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 不立即注销，允许 Fragment 切换
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 不注销客户端：Fragment 切换可能触发 onStop，但应用仍在前台
        // 只有 Activity 被销毁时才注销
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Activity 销毁时，注销客户端
        unregisterClientIfNeeded();
    }

    /**
     * 如果需要，注册客户端
     */
    private void registerClientIfNeeded() {
        if (!clientRegistered) {
            TimerService.registerClient();
            clientRegistered = true;
            Log.d("MainActivity", "Client registered. Active clients: " + TimerService.getActiveClients());
        }
    }

    /**
     * 如果需要，注销客户端
     */
    private void unregisterClientIfNeeded() {
        if (clientRegistered) {
            TimerService.unregisterClient();
            clientRegistered = false;
            Log.d("MainActivity", "Client unregistered. Active clients: " + TimerService.getActiveClients());
        }
    }

    /**
     * 保存当前主题状态
     */
    private void saveThemeState() {
        ThemeManager themeManager = ThemeManager.getInstance();
        lastThemeColor = themeManager.getThemeColor();
        lastFollowSystem = themeManager.isFollowSystem();
    }

    /**
     * 检查主题是否发生变化
     */
    private boolean isThemeChanged() {
        ThemeManager themeManager = ThemeManager.getInstance();
        ThemeManager.ThemeColor currentThemeColor = themeManager.getThemeColor();
        boolean currentFollowSystem = themeManager.isFollowSystem();

        return lastThemeColor != currentThemeColor || lastFollowSystem != currentFollowSystem;
    }

    /**
     * 检查设置并自动启动计时器
     */
    private void checkAndAutoStartTimer() {
        SharedPreferences prefs = getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);

        // 1. 检查是否启用自动计时
        boolean autoStartOnLaunch = prefs.getBoolean("auto_timer", false);

        if (autoStartOnLaunch) {
            // 2. 检查是否需要最小化（显示通知栏）
            boolean autoMinimize = prefs.getBoolean("auto_minimize", false);

            // 3. 根据 autoMinimize 选择启动方式，避免 ANR
            Intent serviceIntent = new Intent(this, TimerService.class);
            serviceIntent.setAction(TimerService.ACTION_START);

            if (autoMinimize) {
                // 需要通知栏：使用 startForegroundService
                Log.d("MainActivity", "checkAndAutoStartTimer: Starting with foreground service (auto-minimize ON)");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } else {
                // 不需要通知栏：使用普通 startService
                Log.d("MainActivity", "checkAndAutoStartTimer: Starting with background service (auto-minimize OFF)");
                startService(serviceIntent);
            }
        }
    }

    private void checkForRecoveryBackup() {
        if (recoveryCheckStarted) {
            return;
        }
        recoveryCheckStarted = true;
        new Thread(() -> {
            BackupManager.LatestBackup backup = null;
            try {
                int localCount = BackupManager.countLocalLogs(getApplicationContext());
                backup = localCount == 0 ? BackupManager.findLatestCsv(getApplicationContext()) : null;
            } catch (Exception e) {
                Log.w("MainActivity", "Recovery check skipped", e);
            }
            BackupManager.LatestBackup discoveredBackup = backup;
            runOnUiThread(() -> {
                if (discoveredBackup == null) {
                    checkAndAutoStartTimer();
                    return;
                }
                showRecoveryPrompt(discoveredBackup);
            });
        }, "CarTimer-RecoveryCheck").start();
    }

    private void ensureStoragePermissionThenCheckRecovery() {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2101);
            return;
        }
        checkForRecoveryBackup();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2101) {
            checkForRecoveryBackup();
        }
    }

    private void showRecoveryPrompt(BackupManager.LatestBackup backup) {
        String message = "发现已有 CarTimer 数据备份\n\n"
                + "当前数据：0 条\n"
                + "发现备份：" + backup.recordCount + " 条\n"
                + "恢复后：预计 " + backup.recordCount + " 条\n\n"
                + "备份时间：" + BackupManager.formatModifiedTime(backup.modifiedAt) + "\n"
                + "来源：Download/CarTimer/backup/\n\n"
                + "是否恢复？";
        new AlertDialog.Builder(this)
                .setTitle("发现已有数据备份")
                .setMessage(message)
                .setPositiveButton("恢复数据", (dialog, which) -> restoreBackup(backup))
                .setNegativeButton("稍后处理", (dialog, which) -> checkAndAutoStartTimer())
                .setOnCancelListener(dialog -> checkAndAutoStartTimer())
                .show();
    }

    private void restoreBackup(BackupManager.LatestBackup backup) {
        new Thread(() -> {
            try {
                BackupManager.RestoreResult result = BackupManager.restoreCsv(getApplicationContext(), backup);
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("恢复成功")
                            .setMessage("已恢复 " + result.importedCount + " 条记录。\n"
                                    + (result.skippedCount > 0 ? "跳过重复记录 " + result.skippedCount + " 条。" : ""))
                            .setPositiveButton("知道了", (dialog, which) -> checkAndAutoStartTimer())
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("恢复失败")
                            .setMessage("备份未写入数据库。\n原因：" + String.valueOf(e.getMessage()))
                            .setPositiveButton("知道了", (dialog, which) -> checkAndAutoStartTimer())
                            .show();
                });
            }
        }, "CarTimer-Restore").start();
    }

    private BottomNavigationView.OnNavigationItemSelectedListener navListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(MenuItem item) {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                loadFragment(new HomeFragment());
            } else if (itemId == R.id.nav_logs) {
                loadFragment(new LogFragment());
            } else if (itemId == R.id.nav_statistics) {
                loadFragment(new StatisticsFragment());
            } else if (itemId == R.id.nav_settings) {
                // 打开设置页面
                openSettings();
                return true;
            }

            return true;
        }
    };

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}
