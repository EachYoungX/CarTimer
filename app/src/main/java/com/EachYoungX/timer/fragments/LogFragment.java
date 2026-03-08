package com.EachYoungX.timer.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.EachYoungX.timer.utils.DateUtils;
import com.EachYoungX.timer.models.LogEntry;
import com.EachYoungX.timer.ui.MonthCalendarView;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.activities.LogDetailActivity;
import com.EachYoungX.timer.adapters.LogAdapter;
import com.EachYoungX.timer.database.LogDatabaseHelper;
import com.google.android.material.card.MaterialCardView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 日志主页 Fragment（左右分栏版本）
 * 左侧：日志列表 + 统计卡片
 * 右侧：月度日历面板
 * 入口：底部导航栏"日志"
 */
public class LogFragment extends Fragment {

    // 左侧视图
    private RecyclerView recyclerLogs;
    private MaterialCardView statsSummary;
    private TextView tvTodayDuration;
    private TextView tvTodayCount;
    private TextView tvAvgDuration;
    private View emptyView;
    private TextView tvEmptyMessage;

    // 右侧视图
    private MonthCalendarView calendarView;
    private TextView tvCurrentMonth;
    private ImageButton btnPrevMonth;
    private ImageButton btnNextMonth;

    private LogDatabaseHelper dbHelper;
    private List<LogEntry> logList;
    private LogAdapter adapter;

    private LocalDate selectedDate;
    private YearMonth currentMonth;
    private DateTimeFormatter monthFormatter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_log, container, false);
        initViews(view);
        initCalendar();
        initRecyclerView();

        // 默认选择今天
        selectedDate = LocalDate.now();
        currentMonth = YearMonth.now();
        monthFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA);

        // 渲染日历并加载今天的数据
        calendarView.render(currentMonth);
        calendarView.setSelectedDate(selectedDate);
        updateMonthDisplay();
        loadLogs();

        return view;
    }

    private void initViews(View view) {
        // 左侧视图
        recyclerLogs = view.findViewById(R.id.recycler_logs);
        statsSummary = view.findViewById(R.id.stats_summary);
        tvTodayDuration = view.findViewById(R.id.tv_today_duration);
        tvTodayCount = view.findViewById(R.id.tv_today_count);
        tvAvgDuration = view.findViewById(R.id.tv_avg_duration);
        emptyView = view.findViewById(R.id.empty_view);
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message);

        // 右侧视图
        calendarView = view.findViewById(R.id.calendar_grid);
        tvCurrentMonth = view.findViewById(R.id.tv_current_month);
        btnPrevMonth = view.findViewById(R.id.btn_prev_month);
        btnNextMonth = view.findViewById(R.id.btn_next_month);

        dbHelper = new LogDatabaseHelper(getActivity());
        logList = new ArrayList<>();
    }

    private void initCalendar() {
        // 设置日期选择监听器
        calendarView.setOnDateSelectedListener(this::onDateSelected);

        // 上月按钮
        btnPrevMonth.setOnClickListener(v -> {
            currentMonth = currentMonth.minusMonths(1);
            calendarView.render(currentMonth);
            updateMonthDisplay();
        });

        // 下月按钮
        btnNextMonth.setOnClickListener(v -> {
            currentMonth = currentMonth.plusMonths(1);
            calendarView.render(currentMonth);
            updateMonthDisplay();
        });
    }

    /**
     * 日期选择回调
     */
    private void onDateSelected(LocalDate date) {
        selectedDate = date;
        updateMonthDisplay();
        loadLogs();
    }

    /**
     * 更新月份显示
     */
    private void updateMonthDisplay() {
        tvCurrentMonth.setText(currentMonth.format(monthFormatter));
    }

    /**
     * 更新标题显示
     */
    private void updateTitle() {
        String title;
        if (selectedDate.equals(LocalDate.now())) {
            title = "今日行车记录";
        } else {
            title = selectedDate.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)) + " 行车记录";
        }

        if (getActivity() != null && getActivity() instanceof LogDetailActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(title);
            }
        }
    }

    private void initRecyclerView() {
        adapter = new LogAdapter(getActivity(), logList);
        recyclerLogs.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerLogs.setAdapter(adapter);
    }

    private void loadLogs() {
        logList.clear();

        String dateKey = String.format("%04d-%02d-%02d",
                selectedDate.getYear(),
                selectedDate.getMonthValue(),
                selectedDate.getDayOfMonth());

        List<LogEntry> logs = dbHelper.getLogsByDate(dateKey);

        if (logs.isEmpty()) {
            // 显示空状态视图
            showEmptyState(dateKey);
        } else {
            // 隐藏空状态视图，显示日志列表
            hideEmptyState();
            logList.addAll(logs);

            // 添加 Footer（明确底部边界）
            logList.add(LogEntry.createFooter("暂无更多记录"));
        }

        adapter.refreshData(logList);
        updateStats(logs);
        updateTitle();
    }

    private void updateStats(List<LogEntry> logs) {
        if (logs.isEmpty()) {
            tvTodayDuration.setText("今日：--");
            tvTodayCount.setText("次数：--");
            tvAvgDuration.setText("平均：--");
            return;
        }

        long totalDuration = 0;
        for (LogEntry log : logs) {
            totalDuration += log.getDuration();
        }

        long avgDuration = totalDuration / logs.size();

        tvTodayDuration.setText("今日：" + DateUtils.formatDuration(totalDuration));
        tvTodayCount.setText("次数：" + logs.size());
        tvAvgDuration.setText("平均：" + DateUtils.formatDuration(avgDuration));
    }

    /**
     * 显示空状态视图
     */
    private void showEmptyState(String dateKey) {
        recyclerLogs.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);

        // 格式化日期显示
        String message;
        if (selectedDate.equals(LocalDate.now())) {
            message = "今天暂无记录";
        } else {
            message = selectedDate.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)) + " 暂无记录";
        }
        tvEmptyMessage.setText(message);
    }

    /**
     * 隐藏空状态视图
     */
    private void hideEmptyState() {
        recyclerLogs.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 如果是今天，重新加载数据
        if (selectedDate.equals(LocalDate.now())) {
            selectedDate = LocalDate.now();
            currentMonth = YearMonth.now();
            calendarView.render(currentMonth);
            calendarView.setSelectedDate(selectedDate);
            updateMonthDisplay();
            loadLogs();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
