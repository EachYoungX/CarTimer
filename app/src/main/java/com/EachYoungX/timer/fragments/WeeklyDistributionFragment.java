package com.EachYoungX.timer.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.EachYoungX.timer.utils.ChartColorHelper;
import com.EachYoungX.timer.utils.DateUtils;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.activities.LogDetailActivity;
import com.EachYoungX.timer.database.LogDatabaseHelper;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeeklyDistributionFragment extends Fragment {

    private BarChart barChart;
    private TextView tvEmpty, tvMonth;
    private ImageButton btnPrevMonth, btnNextMonth;
    private int year = java.time.Year.now().getValue();
    private int month = java.time.LocalDate.now().getMonthValue() - 1; // 0-based
    private LogDatabaseHelper dbHelper;
    private String[] weekLabels;
    private List<String> weekKeys; // 存储所有周的 key

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart, container, false);

        barChart = view.findViewById(R.id.bar_chart);
        tvEmpty = view.findViewById(R.id.tv_empty);
        tvMonth = view.findViewById(R.id.tv_month);
        btnPrevMonth = view.findViewById(R.id.btn_prev_month);
        btnNextMonth = view.findViewById(R.id.btn_next_month);

        // 显示月份选择器
        View monthSelector = view.findViewById(R.id.month_selector);
        if (monthSelector != null) {
            monthSelector.setVisibility(View.VISIBLE);
        }

        updateMonthDisplay();
        loadChartData();

        btnPrevMonth.setOnClickListener(v -> {
            month--;
            if (month < 0) {
                month = 11;
                year--;
            }
            updateMonthDisplay();
            loadChartData();
        });

        btnNextMonth.setOnClickListener(v -> {
            month++;
            if (month > 11) {
                month = 0;
                year++;
            }
            updateMonthDisplay();
            loadChartData();
        });

        return view;
    }

    public void setYear(int year) {
        this.year = year;
        updateMonthDisplay();
        loadChartData();
    }

    private void updateMonthDisplay() {
        tvMonth.setText(year + "年 " + (month + 1) + "月");
    }

    private void loadChartData() {
        // 确保 getActivity() 不为 null
        if (getActivity() == null) {
            Log.d("WeeklyDistributionFragment", "loadChartData: Activity is null, skipping.");
            return;
        }

        // 延迟初始化 dbHelper，确保 Activity 已附加
        if (dbHelper == null) {
            dbHelper = new LogDatabaseHelper(getActivity());
        }

        // 1. 计算该月包含的所有周（基于日历算法）
        List<WeekInfo> allWeeks = calculateAllWeeksOfMonth(year, month + 1);

        // 2. 从数据库获取有数据的周
        Map<String, Long> weeklyStats = getWeeklyStatsFromDatabase(year, month + 1);

        if (allWeeks.isEmpty()) {
            barChart.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        barChart.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        // 3. 准备图表数据 - 补全所有周
        ArrayList<BarEntry> entries = new ArrayList<>();
        weekLabels = new String[allWeeks.size()];
        weekKeys = new ArrayList<>();

        for (int i = 0; i < allWeeks.size(); i++) {
            WeekInfo weekInfo = allWeeks.get(i);
            Long duration = weeklyStats.getOrDefault(weekInfo.weekKey, 0L);

            // 将毫秒转换为小时
            float hours = duration / (60f * 60f * 1000f);
            entries.add(new BarEntry(i, hours));

            weekLabels[i] = weekInfo.label;
            weekKeys.add(weekInfo.weekKey);
        }

        // 4. 创建数据集（使用主题颜色）
        BarDataSet dataSet = new BarDataSet(entries, "周度分布");
        dataSet.setColor(ChartColorHelper.getPrimaryColor(getContext()));
        dataSet.setValueTextColor(ChartColorHelper.getTextColor(getContext()));
        dataSet.setValueTextSize(11f);
        dataSet.setHighLightColor(ChartColorHelper.getHighlightColor(getContext()));

        // 自定义值格式化器
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value <= 0)
                    return "";
                if (value < 1.0f) {
                    int minutes = Math.round(value * 60);
                    return minutes + "m";
                } else {
                    if (value == (int) value) {
                        return (int) value + "h";
                    } else {
                        return String.format("%.1fh", value);
                    }
                }
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);

        // 5. 配置图表
        barChart.setData(barData);
        barChart.setFitBars(true);

        // Y 轴从 0 开始
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(Math.max(2f, getMaxYValue(entries) * 1.2f));
        leftAxis.setGranularity(1f);

        // 自定义 Y 轴标签格式化
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value <= 0)
                    return "0";
                if (value < 1.0f) {
                    int minutes = Math.round(value * 60);
                    return minutes + "m";
                } else {
                    if (value == (int) value) {
                        return (int) value + "h";
                    } else {
                        return String.format("%.1f", value) + "h";
                    }
                }
            }
        });
        leftAxis.setTextSize(10f);
        leftAxis.setTextColor(ChartColorHelper.getTextColor(getContext()));

        // 清理图表装饰
        Description desc = new Description();
        desc.setEnabled(false);
        barChart.setDescription(desc);

        Legend legend = barChart.getLegend();
        legend.setEnabled(false);

        // X 轴配置
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(allWeeks.size());
        xAxis.setValueFormatter(new IndexAxisValueFormatter(weekLabels));
        xAxis.setTextSize(10f);
        xAxis.setTextColor(ChartColorHelper.getTextColor(getContext()));
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(ChartColorHelper.getAxisColor(getContext()));
        xAxis.setLabelRotationAngle(0f);

        // Y 轴配置
        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setEnabled(false);

        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ChartColorHelper.getGridColor(getContext()));
        leftAxis.setGridLineWidth(1f);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);

        // 优化柱状图样式
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);

        // 添加点击交互
        barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int weekIndex = (int) e.getX();
                if (weekIndex >= 0 && weekIndex < weekKeys.size()) {
                    // 检查该周是否有数据
                    String weekKey = weekKeys.get(weekIndex);
                    Long duration = weeklyStats.getOrDefault(weekKey, 0L);

                    if (duration == 0) {
                        Toast.makeText(getActivity(), "第" + (weekIndex + 1) + "周暂无记录", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    openWeekDetail(weekIndex);
                }
            }

            @Override
            public void onNothingSelected() {
                // 取消高亮
            }
        });

        // 动画
        barChart.animateY(500);
        barChart.invalidate();
    }

    // 计算某个月包含的所有周（基于日历算法，使用 0-based 索引）
    private List<WeekInfo> calculateAllWeeksOfMonth(int year, int month) {
        List<WeekInfo> weeks = new ArrayList<>();
        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        // 找到第一个周一
        LocalDate firstMonday = firstDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        // 如果该月没有周一（极端情况），返回空列表
        if (firstMonday.getMonthValue() != month) {
            // 第一个周一已经跑到下个月了
            return weeks;
        }

        int weekIndex = 0;
        LocalDate currentMonday = firstMonday;
        while (!currentMonday.isAfter(lastDay)) {
            String weekKey = String.format("%04d-W%02d", year, weekIndex);
            String label = "第" + (weekIndex + 1) + "周";

            weeks.add(new WeekInfo(weekKey, label, weekIndex));

            currentMonday = currentMonday.plusWeeks(1);
            weekIndex++;
        }

        return weeks;
    }

    // 从数据库获取有数据的周统计（使用 0-based 索引）
    private Map<String, Long> getWeeklyStatsFromDatabase(int year, int month) {
        Map<String, Long> weeklyStats = new HashMap<>();
        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        // 遍历该月所有日期，按周聚合
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            String dateKey = String.format("%04d-%02d-%02d", year, month, date.getDayOfMonth());
            long dayDuration = dbHelper.getDayTotalDuration(dateKey);

            if (dayDuration > 0) {
                // 使用统一的周索引计算
                int weekIndex = DateUtils.getWeekIndexOfDate(year, month, date.getDayOfMonth());
                String weekKey = String.format("%04d-W%02d", year, weekIndex);

                weeklyStats.put(weekKey, weeklyStats.getOrDefault(weekKey, 0L) + dayDuration);
            }
        }

        return weeklyStats;
    }

    // 获取某一天的总时长
    private long getDayDuration(String dateKey) {
        return dbHelper.getDayTotalDuration(dateKey);
    }

    // 计算 Y 轴最大值
    private float getMaxYValue(ArrayList<BarEntry> entries) {
        float max = 0f;
        for (BarEntry entry : entries) {
            if (entry.getY() > max) {
                max = entry.getY();
            }
        }
        return max;
    }

    // 打开周详情 - 跳转到日志详情页
    private void openWeekDetail(int weekIndex) {
        if (getActivity() == null)
            return;

        Intent intent = new Intent(getActivity(), LogDetailActivity.class);
        intent.putExtra(LogDetailActivity.EXTRA_FILTER_TYPE, "WEEK");
        intent.putExtra(LogDetailActivity.EXTRA_YEAR, year);
        intent.putExtra(LogDetailActivity.EXTRA_MONTH, month + 1);
        intent.putExtra(LogDetailActivity.EXTRA_WEEK_INDEX, weekIndex);

        startActivity(intent);
    }

    // 内部类：周信息
    private static class WeekInfo {
        String weekKey;
        String label;
        int weekOfMonth;

        WeekInfo(String weekKey, String label, int weekOfMonth) {
            this.weekKey = weekKey;
            this.label = label;
            this.weekOfMonth = weekOfMonth;
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