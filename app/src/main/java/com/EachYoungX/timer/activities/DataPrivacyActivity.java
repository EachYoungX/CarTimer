package com.EachYoungX.timer.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.EachYoungX.timer.database.LogDatabaseHelper;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.ui.ThemeManager;
import com.EachYoungX.timer.services.TimerService;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataPrivacyActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private MaterialButton btnExport, btnImport, btnDelete;
    private Spinner spinnerCleanupPeriod;
    private LogDatabaseHelper dbHelper;
    private SharedPreferences prefs;

    private static final int EXPORT_REQUEST_CODE = 1001;
    private static final int IMPORT_REQUEST_CODE = 1002;

    // 文件选择器
    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    exportToCSV(uri);
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    importFromCSV(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.getInstance().applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_privacy);

        dbHelper = new LogDatabaseHelper(this);
        prefs = getSharedPreferences("TimerPrefs", MODE_PRIVATE);

        initViews();
        setupListeners();
        loadCleanupPeriod();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        btnExport = findViewById(R.id.btn_export);
        btnImport = findViewById(R.id.btn_import);
        btnDelete = findViewById(R.id.btn_delete);
        spinnerCleanupPeriod = findViewById(R.id.spinner_cleanup_period);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        // 导出按钮
        btnExport.setOnClickListener(v -> showExportDialog());

        // 导入按钮
        btnImport.setOnClickListener(v -> showImportDialog());

        // 删除按钮
        btnDelete.setOnClickListener(v -> showDeleteDialog());

        // 自动清理周期选择
        spinnerCleanupPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveCleanupPeriod(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /**
     * 显示导出对话框
     */
    private void showExportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("导出记录")
                .setMessage("确定要导出所有行驶记录为 CSV 文件吗？\n\n文件格式：UTF-8 with BOM\n包含字段：日期、开始时间、结束时间、时长")
                .setPositiveButton("导出", (dialog, which) -> openExportPicker())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 打开文件选择器（导出）
     */
    private void openExportPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");

        // 生成文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "timer_export_" + timestamp + ".csv");

        exportLauncher.launch(intent);
    }

    /**
     * 导出到 CSV
     */
    private void exportToCSV(Uri uri) {
        new Thread(() -> {
            try {
                PrintWriter writer = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(getContentResolver().openOutputStream(uri), "UTF-8")));

                // 写入 BOM
                writer.write('\ufeff');

                // 写入表头
                writer.println("date_key,start_time,end_time,duration,week_key,month_key");

                // 查询所有记录
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Cursor cursor = db.rawQuery(
                        "SELECT date_key, start_time, end_time, duration, week_key, month_key FROM logs ORDER BY start_time",
                        null);

                int count = 0;
                if (cursor.moveToFirst()) {
                    do {
                        String dateKey = cursor.getString(0);
                        long startTime = cursor.getLong(1);
                        long endTime = cursor.getLong(2);
                        long duration = cursor.getLong(3);
                        String weekKey = cursor.getString(4);
                        String monthKey = cursor.getString(5);

                        writer.println(dateKey + "," + startTime + "," + endTime + "," +
                                duration + "," + weekKey + "," + monthKey);
                        count++;
                    } while (cursor.moveToNext());
                }

                cursor.close();
                db.close();
                writer.close();

                int finalCount = count;
                runOnUiThread(() -> Toast.makeText(this, "成功导出 " + finalCount + " 条记录", Toast.LENGTH_LONG).show());

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /**
     * 显示导入对话框
     */
    private void showImportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("导入记录")
                .setMessage("从 CSV 文件导入行驶记录\n\n注意事项：\n1. 仅支持标准 CSV 格式（UTF-8）\n2. 自动跳过重复记录（相同开始时间）\n3. 导入过程不可中断")
                .setPositiveButton("选择文件", (dialog, which) -> openImportPicker())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 打开文件选择器（导入）
     */
    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] { "text/csv", "text/comma-separated-values" });

        importLauncher.launch(intent);
    }

    /**
     * 从 CSV 导入
     */
    private void importFromCSV(Uri uri) {
        new Thread(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.beginTransaction();

            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));

                String line;
                int lineNumber = 0;
                int importedCount = 0;
                int skippedCount = 0;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;

                    // 跳过表头
                    if (lineNumber == 1 && line.contains("date_key")) {
                        continue;
                    }

                    // 解析 CSV 行
                    String[] parts = line.split(",");
                    if (parts.length < 6) {
                        continue;
                    }

                    try {
                        String dateKey = parts[0].trim();
                        long startTime = Long.parseLong(parts[1].trim());
                        long endTime = Long.parseLong(parts[2].trim());
                        long duration = Long.parseLong(parts[3].trim());
                        String weekKey = parts[4].trim();
                        String monthKey = parts[5].trim();

                        // 检查是否已存在（根据 start_time 去重）
                        Cursor cursor = db.rawQuery(
                                "SELECT COUNT(*) FROM logs WHERE start_time = ?",
                                new String[] { String.valueOf(startTime) });

                        if (cursor.moveToFirst() && cursor.getInt(0) == 0) {
                            // 不存在，插入新记录
                            db.execSQL(
                                    "INSERT INTO logs (date_key, start_time, end_time, duration, week_key, month_key) VALUES (?, ?, ?, ?, ?, ?)",
                                    new Object[] { dateKey, startTime, endTime, duration, weekKey, monthKey });
                            importedCount++;
                        } else {
                            // 已存在，跳过
                            skippedCount++;
                        }
                        cursor.close();

                    } catch (Exception e) {
                        e.printStackTrace();
                        // 跳过无效行
                    }
                }

                reader.close();
                db.setTransactionSuccessful();
                db.endTransaction();
                db.close();

                int finalImported = importedCount;
                int finalSkipped = skippedCount;
                runOnUiThread(() -> Toast.makeText(this,
                        "导入完成\n成功：" + finalImported + " 条\n跳过（重复）: " + finalSkipped + " 条",
                        Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                e.printStackTrace();
                db.endTransaction();
                runOnUiThread(() -> Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /**
     * 显示删除对话框
     */
    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清空记录")
                .setMessage("⚠️ 危险操作\n\n请选择删除方式：\n\n• 按年月删除：选择特定年月/周进行删除\n• 清空所有：删除所有行驶记录\n\n此操作不可恢复！")
                .setPositiveButton("取消", null)
                .setNeutralButton("按年月删除", (dialog, which) -> {
                    // 删除前确认是否停止计时
                    showStopTimerConfirmDialog(ManualDeleteActivity.class);
                })
                .setNegativeButton("清空所有", (dialog, which) -> {
                    // 删除前确认是否停止计时
                    showStopTimerConfirmDialog(null);
                })
                .show();
    }

    /**
     * 显示停止计时确认对话框
     * 
     * @param targetActivity 要跳转的目标 Activity（null 表示直接清空所有）
     */
    private void showStopTimerConfirmDialog(final Class<?> targetActivity) {
        // 检查当前是否正在计时
        if (!isTimerRunning()) {
            // 没有在计时，直接继续
            if (targetActivity != null) {
                Intent intent = new Intent(DataPrivacyActivity.this, targetActivity);
                startActivity(intent);
            } else {
                showConfirmDeleteDialog();
            }
            return;
        }

        // 正在计时，弹出确认对话框
        new AlertDialog.Builder(this)
                .setTitle("计时器运行中")
                .setMessage("检测到计时器正在运行。\n\n执行删除操作时需要停止计时并保存当前记录。\n\n是否继续？")
                .setPositiveButton("停止并继续", (dialog, which) -> {
                    // 停止计时并保存
                    stopTimerAndSave();

                    // 延迟一下，确保保存完成
                    new android.os.Handler().postDelayed(() -> {
                        if (targetActivity != null) {
                            Intent intent = new Intent(DataPrivacyActivity.this, targetActivity);
                            startActivity(intent);
                        } else {
                            showConfirmDeleteDialog();
                        }
                    }, 500);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 检查计时器是否正在运行
     */
    private boolean isTimerRunning() {
        try {
            SharedPreferences prefs = getSharedPreferences("TimerPrefs", MODE_PRIVATE);
            // 检查 TimerService 的状态
            SharedPreferences timerPrefs = getSharedPreferences("TimerState", MODE_PRIVATE);
            return timerPrefs.getBoolean("is_running", false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 停止计时器并保存记录
     */
    private void stopTimerAndSave() {
        Intent stopIntent = new Intent(this, TimerService.class);
        stopIntent.setAction(TimerService.ACTION_STOP);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(stopIntent);
        } else {
            startService(stopIntent);
        }
    }

    /**
     * 显示二次确认对话框（带倒计时）
     */
    private void showConfirmDeleteDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("最终确认")
                .setView(dialogView)
                .setPositiveButton("确定", null) // 先设为 null，后面会覆盖
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button btnConfirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnConfirm.setEnabled(false);

            // 3 秒倒计时
            new android.os.Handler().postDelayed(new Runnable() {
                int seconds = 3;

                @Override
                public void run() {
                    if (seconds > 0) {
                        btnConfirm.setText("请等待 (" + seconds + "秒)");
                        seconds--;
                        new android.os.Handler().postDelayed(this, 1000);
                    } else {
                        btnConfirm.setText("确定清空");
                        btnConfirm.setEnabled(true);
                        btnConfirm.setOnClickListener(v -> {
                            deleteAllLogs();
                            dialog.dismiss();
                        });
                    }
                }
            }, 1000);
        });

        dialog.show();
    }

    /**
     * 删除所有记录
     */
    private void deleteAllLogs() {
        new Thread(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("DELETE FROM logs");
            db.close();

            runOnUiThread(() -> {
                Toast.makeText(this, "已清空所有记录", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    /**
     * 加载自动清理周期
     */
    private void loadCleanupPeriod() {
        int period = prefs.getInt("auto_cleanup_period", 0);
        spinnerCleanupPeriod.setSelection(period);
    }

    /**
     * 保存自动清理周期
     */
    private void saveCleanupPeriod(int position) {
        prefs.edit().putInt("auto_cleanup_period", position).apply();

        // 如果周期改变，立即检查并执行清理
        if (position > 0) {
            checkAndCleanupData();
        }
    }

    /**
     * 检查并执行自动清理
     */
    public void checkAndCleanupData() {
        int period = prefs.getInt("auto_cleanup_period", 0);
        if (period == 0) {
            return; // 从不清理
        }

        long thresholdMillis = System.currentTimeMillis() - ((long) period * 365 * 24 * 60 * 60 * 1000);

        new Thread(() -> {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int deletedCount = db.delete("logs", "start_time < ?",
                    new String[] { String.valueOf(thresholdMillis) });
            db.close();

            if (deletedCount > 0) {
                runOnUiThread(
                        () -> Toast.makeText(this, "自动清理完成，删除 " + deletedCount + " 条过期记录", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
