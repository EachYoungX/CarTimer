package com.EachYoungX.timer.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.EachYoungX.timer.utils.DateUtils;
import com.EachYoungX.timer.adapters.LogAdapter;
import com.EachYoungX.timer.database.LogDatabaseHelper;
import com.EachYoungX.timer.models.LogEntry;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.ui.ThemeManager;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日志详情页面
 * 专门用于接收来自统计页的跳转参数，展示特定时间段的日志列表
 * 支持 MONTH、WEEK、DAY 三种筛选类型
 */
public class LogDetailActivity extends AppCompatActivity {

    public static final String EXTRA_FILTER_TYPE = "filter_type";
    public static final String EXTRA_YEAR = "year";
    public static final String EXTRA_MONTH = "month";
    public static final String EXTRA_WEEK_INDEX = "week_index";
    public static final String EXTRA_DATE_KEY = "date_key";

    private MaterialToolbar toolbar;
    private TextView tvHeaderTitle;
    private TextView tvTotalDuration;
    private TextView tvTotalDays;
    private TextView tvTotalCount;
    private RecyclerView recyclerLogs;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View emptyView;
    private TextView tvEmptyMessage;
    private ImageButton btnCalendar; // 日历按钮

    private LogDatabaseHelper dbHelper;
    private List<LogEntry> logList;
    private LogAdapter adapter;

    private String filterType;
    private int year;
    private int month;
    private int weekIndex;
    private String dateKey;

    // 主题状态跟踪
    private ThemeManager.ThemeColor lastThemeColor;
    private boolean lastFollowSystem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.getInstance().applyTheme(this);
        saveThemeState();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_detail);

        // 接收参数
        if (!receiveArguments()) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        initToolbar();
        initRecyclerView();
        loadLogs();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 屏幕方向改变时，更新 Adapter 的显示数量
        if (adapter != null) {
            adapter.updateVisibleCount();
            adapter.refreshData(logList);
        }
    }

    /**
     * 接收 Intent 参数
     */
    private boolean receiveArguments() {
        filterType = getIntent().getStringExtra(EXTRA_FILTER_TYPE);
        year = getIntent().getIntExtra(EXTRA_YEAR, -1);
        month = getIntent().getIntExtra(EXTRA_MONTH, -1);
        weekIndex = getIntent().getIntExtra(EXTRA_WEEK_INDEX, -1);
        dateKey = getIntent().getStringExtra(EXTRA_DATE_KEY);

        // 验证参数
        if (filterType == null) {
            return false;
        }

        if ("MONTH".equals(filterType) && (year <= 0 || month <= 0)) {
            return false;
        }

        if ("WEEK".equals(filterType) && (year <= 0 || month <= 0 || weekIndex < 0)) {
            return false;
        }

        if ("DAY".equals(filterType) && dateKey == null) {
            return false;
        }

        return true;
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvHeaderTitle = findViewById(R.id.tv_header_title);
        tvTotalDuration = findViewById(R.id.tv_total_duration);
        tvTotalDays = findViewById(R.id.tv_total_days);
        tvTotalCount = findViewById(R.id.tv_total_count);
        recyclerLogs = findViewById(R.id.recycler_logs);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        emptyView = findViewById(R.id.empty_view);
        tvEmptyMessage = findViewById(R.id.tv_empty_message);
        btnCalendar = findViewById(R.id.btn_calendar);
        android.util.Log.d("LogDetailActivity",
                "initViews: btnCalendar=" + (btnCalendar != null ? "not null" : "null"));

        dbHelper = new LogDatabaseHelper(this);
        logList = new ArrayList<>();

        // 只有月页面才显示日历按钮
        android.util.Log.d("LogDetailActivity", "initViews: filterType=" + filterType);
        if ("MONTH".equals(filterType)) {
            btnCalendar.setVisibility(View.VISIBLE);
            android.util.Log.d("LogDetailActivity", "initViews: MONTH filter, showing calendar button");
            btnCalendar.setOnClickListener(v -> {
                android.util.Log.d("LogDetailActivity", "Calendar button clicked!");
                showDatePicker();
            });
        } else {
            android.util.Log.d("LogDetailActivity", "initViews: Not MONTH filter, calendar button hidden");
        }
    }

    private void initToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        // 设置标题
        updateHeaderTitle();
    }

    /**
     * 更新 Header 标题
     */
    private void updateHeaderTitle() {
        if ("MONTH".equals(filterType)) {
            tvHeaderTitle.setText(year + "年" + month + "月 行车记录");
        } else if ("WEEK".equals(filterType)) {
            tvHeaderTitle.setText(year + "年第" + (weekIndex + 1) + "周 行车记录");
        } else if ("DAY".equals(filterType)) {
            tvHeaderTitle.setText(dateKey + " 行车记录");
        }
    }

    private void initRecyclerView() {
        adapter = new LogAdapter(this, logList);
        recyclerLogs.setLayoutManager(new LinearLayoutManager(this));
        recyclerLogs.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadLogs);
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
     * 加载日志数据
     */
    private void loadLogs() {
        logList.clear();

        // 从数据库获取的原始 List<LogEntry> 应该是只包含数据的
        List<LogEntry> logs = queryLogsByFilter();

        if (logs.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();

            // 二次检查：确保 logs 中不包含任何 Header 类型的对象
            List<LogEntry> cleanLogs = new ArrayList<>();
            for (LogEntry e : logs) {
                if (e != null && !e.isHeader() && !e.isFooter()) {
                    cleanLogs.add(e);
                }
            }

            // 更新 logList 字段（用于 scrollToDate 查找）
            logList.addAll(buildLogsWithHeaders(cleanLogs));
            android.util.Log.d("LogDetailActivity", "loadLogs: logList size=" + logList.size());

            // 传递纯净数据给 Adapter
            adapter.refreshData(cleanLogs);
        }

        updateStats(logs);
        swipeRefreshLayout.setRefreshing(false);
    }

    /**
     * 根据筛选条件查询日志
     */
    private List<LogEntry> queryLogsByFilter() {
        if ("MONTH".equals(filterType)) {
            String monthKey = String.format("%04d-%02d", year, month);
            return dbHelper.getLogsByMonth(monthKey);
        } else if ("WEEK".equals(filterType)) {
            return getLogsForWeek(year, month, weekIndex);
        } else if ("DAY".equals(filterType)) {
            return dbHelper.getLogsByDate(dateKey);
        }
        return new ArrayList<>();
    }

    /**
     * 获取某一周的日志（使用统一的周计算逻辑）
     */
    private List<LogEntry> getLogsForWeek(int year, int month, int weekIndex) {
        // 计算该周的日期范围
        DateUtils.WeekDateRange weekRange = DateUtils.getWeekDateRange(year, month, weekIndex);

        // 获取该月所有数据
        String monthKey = String.format("%04d-%02d", year, month);
        List<LogEntry> monthLogs = dbHelper.getLogsByMonth(monthKey);

        // 过滤出该周的日志
        List<LogEntry> weekLogs = new ArrayList<>();
        for (LogEntry log : monthLogs) {
            long logTime = log.getStartTime();
            if (logTime >= weekRange.getStartTime() && logTime <= weekRange.getEndTime()) {
                weekLogs.add(log);
            }
        }

        return weekLogs;
    }

    /**
     * 显示日期选择器弹窗
     */
    private void showDatePicker() {
        android.util.Log.d("LogDetailActivity", "showDatePicker: START - year=" + year + ", month=" + month);

        // 使用 Calendar 获取当前日期
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1); // 设置为该月 1 日

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        // 创建 DatePickerDialog
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    // 用户选择日期后，滚动到对应位置
                    String selectedDateKey = String.format("%04d-%02d-%02d",
                            selectedYear, selectedMonth + 1, selectedDay);
                    android.util.Log.d("LogDetailActivity", "Date selected: " + selectedDateKey);
                    scrollToDate(selectedDateKey);
                },
                year, month, day);

        // 设置日期选择器的范围（限制在当前月内）
        datePickerDialog.getDatePicker().setMinDate(calendar.getTimeInMillis());

        // 设置最大日期为该月最后一天
        Calendar maxCalendar = Calendar.getInstance();
        maxCalendar.set(year, month, 1);
        maxCalendar.add(Calendar.MONTH, 1);
        maxCalendar.add(Calendar.DAY_OF_MONTH, -1);
        datePickerDialog.getDatePicker().setMaxDate(maxCalendar.getTimeInMillis());

        datePickerDialog.show();
        android.util.Log.d("LogDetailActivity", "showDatePicker: DatePickerDialog shown");
    }

    /**
     * 滚动到指定日期的位置
     */
    private void scrollToDate(String dateKey) {
        android.util.Log.d("LogDetailActivity",
                "scrollToDate: START - dateKey=" + dateKey + ", logList size=" + logList.size());

        // 使用 DateUtils 格式化日期，确保与 Header 中的格式一致
        String targetHeader = com.EachYoungX.timer.utils.DateUtils.formatDateHeader(dateKey);
        android.util.Log.d("LogDetailActivity", "scrollToDate: targetHeader=" + targetHeader);

        // 在 logList 中查找该日期的 Header 位置
        for (int i = 0; i < logList.size(); i++) {
            LogEntry entry = logList.get(i);
            if (entry.isHeader()) {
                String headerDate = entry.getHeaderDate();
                android.util.Log.d("LogDetailActivity",
                        "scrollToDate: Checking header at " + i + ": headerDate=" + headerDate);

                if (targetHeader.equals(headerDate)) {
                    // 找到对应的 Header，滚动到该位置（确保滚动到顶部）
                    ((androidx.recyclerview.widget.LinearLayoutManager) recyclerLogs.getLayoutManager())
                            .scrollToPositionWithOffset(i, 0);
                    android.util.Log.d("LogDetailActivity", "scrollToDate: Found header at position " + i);

                    // 可选：高亮显示一下
                    Toast.makeText(this, "已跳转到 " + dateKey, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        // 如果没有找到该日期
        android.util.Log.d("LogDetailActivity", "scrollToDate: Date not found in logList");
        Toast.makeText(this, "该日期没有记录", Toast.LENGTH_SHORT).show();
    }

    private List<LogEntry> buildLogsWithHeaders(List<LogEntry> logs) {
        List<LogEntry> result = new ArrayList<>();

        if (logs.isEmpty()) {
            return result;
        }

        // 按日期分组
        Map<String, List<LogEntry>> groupedLogs = new java.util.HashMap<>();
        for (LogEntry log : logs) {
            String dateKey = log.getDateKey();
            if (dateKey == null)
                continue; // 跳过脏数据

            if (!groupedLogs.containsKey(dateKey)) {
                groupedLogs.put(dateKey, new ArrayList<>());
            }
            groupedLogs.get(dateKey).add(log);
        }

        // 遍历分组，添加 Header 和日志
        for (Map.Entry<String, List<LogEntry>> entry : groupedLogs.entrySet()) {
            String dateKey = entry.getKey();
            List<LogEntry> dayLogs = entry.getValue();

            // 计算当日总计
            long dayTotal = 0;
            for (LogEntry log : dayLogs) {
                dayTotal += log.getDuration();
            }

            // 添加 Header
            result.add(LogEntry.createHeader(
                    DateUtils.formatDateHeader(dateKey),
                    "当日总计：" + DateUtils.formatDuration(dayTotal)));

            // 添加该日的所有日志
            result.addAll(dayLogs);
        }

        return result;
    }

    /**
     * 更新统计信息
     */
    private void updateStats(List<LogEntry> logs) {
        if (logs.isEmpty()) {
            tvTotalDuration.setText("--");
            tvTotalDays.setText("--");
            tvTotalCount.setText("--");
            return;
        }

        long totalDuration = 0;
        Set<String> distinctDates = new HashSet<>();

        for (LogEntry log : logs) {
            totalDuration += log.getDuration();
            distinctDates.add(log.getDateKey());
        }

        tvTotalDuration.setText(DateUtils.formatDuration(totalDuration));
        tvTotalDays.setText(String.valueOf(distinctDates.size()));
        tvTotalCount.setText(String.valueOf(logs.size()));
    }

    private void showEmptyState() {
        recyclerLogs.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);

        if ("MONTH".equals(filterType)) {
            tvEmptyMessage.setText(year + "年" + month + "月暂无记录");
        } else if ("WEEK".equals(filterType)) {
            tvEmptyMessage.setText("第" + (weekIndex + 1) + "周暂无记录");
        } else if ("DAY".equals(filterType)) {
            tvEmptyMessage.setText(dateKey + "暂无记录");
        }
    }

    private void hideEmptyState() {
        recyclerLogs.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检查主题是否变化
        if (isThemeChanged()) {
            recreate(); // 主题变化，重新创建 Activity
        } else {
            loadLogs(); // 主题未变化，正常加载日志
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
