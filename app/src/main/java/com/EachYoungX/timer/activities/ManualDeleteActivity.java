package com.EachYoungX.timer.activities;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.EachYoungX.timer.database.LogDatabaseHelper;
import com.EachYoungX.timer.adapters.ManualDeletePagerAdapter;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.ui.ThemeManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.HashSet;
import java.util.Set;

public class ManualDeleteActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private TextView tvYear;
    private ImageButton btnPrevYear, btnNextYear;
    private MaterialButton btnConfirmDelete;
    private com.google.android.material.appbar.MaterialToolbar toolbar;

    private int currentYear;
    private ManualDeletePagerAdapter pagerAdapter;

    // 全局勾选记录集合（存储 startTime）
    private Set<Long> selectedRecords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.getInstance().applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_delete);

        selectedRecords = new HashSet<>();

        // 在进入任何 Fragment 之前，强制初始化数据库
        // 确保即使服务刚被停止，这里也能重新建立连接
        try {
            LogDatabaseHelper dbHelper = new LogDatabaseHelper(this);
            Log.d("ManualDeleteActivity", "Database helper initialized successfully.");
        } catch (Exception e) {
            Log.e("ManualDeleteActivity", "Failed to initialize database helper", e);
            Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_SHORT).show();
        }

        initViews();
        setupViewPager();
        setupListeners();
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        tvYear = findViewById(R.id.tv_year);
        btnPrevYear = findViewById(R.id.btn_prev_year);
        btnNextYear = findViewById(R.id.btn_next_year);
        btnConfirmDelete = findViewById(R.id.btn_confirm_delete);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // 返回按钮定向回数据隐私设置页面
        toolbar.setNavigationOnClickListener(v -> {
            Intent intent = new Intent(ManualDeleteActivity.this, DataPrivacyActivity.class);
            startActivity(intent);
            finish();
        });

        currentYear = java.time.Year.now().getValue();
        updateYearDisplay();
    }

    private void setupViewPager() {
        pagerAdapter = new ManualDeletePagerAdapter(this, selectedRecords);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText("按月删除");
                            break;
                        case 1:
                            tab.setText("按周删除");
                            break;
                    }
                }).attach();
    }

    private void setupListeners() {
        btnPrevYear.setOnClickListener(v -> {
            currentYear--;
            updateYearDisplay();
            refreshFragments();
        });

        btnNextYear.setOnClickListener(v -> {
            currentYear++;
            updateYearDisplay();
            refreshFragments();
        });

        btnConfirmDelete.setOnClickListener(v -> showConfirmDialog());
    }

    private void updateYearDisplay() {
        tvYear.setText(String.valueOf(currentYear));
        btnPrevYear.setEnabled(currentYear > 2000);
        btnNextYear.setEnabled(currentYear < 2100);
    }

    private void refreshFragments() {
        if (pagerAdapter != null) {
            pagerAdapter.setYear(currentYear);
        }
        updateConfirmButton();
    }

    private void updateConfirmButton() {
        int count = selectedRecords.size();
        if (count > 0) {
            btnConfirmDelete.setText("确认删除 (" + count + "条)");
            btnConfirmDelete.setEnabled(true);
        } else {
            btnConfirmDelete.setText("确认删除");
            btnConfirmDelete.setEnabled(false);
        }
    }

    private void showConfirmDialog() {
        if (selectedRecords.isEmpty()) {
            Toast.makeText(this, "请先勾选要删除的记录", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除选中的 " + selectedRecords.size() + " 条记录吗？\n\n此操作不可恢复！")
                .setPositiveButton("取消", null)
                .setNegativeButton("确认清空", (dialog, which) -> showFinalConfirmDialog())
                .show();
    }

    private void showFinalConfirmDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirm_delete, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("最终确认")
                .setView(dialogView)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button btnConfirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnConfirm.setEnabled(false);

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
                            deleteSelectedRecords();
                            dialog.dismiss();
                        });
                    }
                }
            }, 1000);
        });

        dialog.show();
    }

    private void deleteSelectedRecords() {
        new Thread(() -> {
            LogDatabaseHelper dbHelper = new LogDatabaseHelper(this);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.beginTransaction();

            try {
                for (Long startTime : selectedRecords) {
                    db.delete("logs", "start_time = ?", new String[] { String.valueOf(startTime) });
                }
                db.setTransactionSuccessful();
                db.endTransaction();
                db.close();

                runOnUiThread(() -> {
                    Toast.makeText(this, "成功删除 " + selectedRecords.size() + " 条记录", Toast.LENGTH_SHORT).show();
                    selectedRecords.clear();
                    updateConfirmButton();
                    refreshFragments();
                    // 不 finish()，停留在删除页面，方便继续操作
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "删除失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    public int getCurrentYear() {
        return currentYear;
    }

    public void updateConfirmButtonFromFragment() {
        runOnUiThread(this::updateConfirmButton);
    }
}
