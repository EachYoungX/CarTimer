package com.EachYoungX.timer.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.EachYoungX.timer.utils.ChartColorHelper;
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

import java.util.ArrayList;
import java.util.Map;

public class MonthlyTrendFragment extends Fragment {

    private BarChart barChart;
    private TextView tvEmpty;
    private int year = java.time.Year.now().getValue();
    private LogDatabaseHelper dbHelper;
    private String[] monthLabels;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chart, container, false);

        barChart = view.findViewById(R.id.bar_chart);
        tvEmpty = view.findViewById(R.id.tv_empty);

        // 隐藏月份选择器
        View monthSelector = view.findViewById(R.id.month_selector);
        if (monthSelector != null) {
            monthSelector.setVisibility(View.GONE);
        }

        loadChartData();

        return view;
    }

    public void setYear(int year) {
        this.year = year;
        loadChartData();
    }

    private void loadChartData() {
        // 确保 getActivity() 不为 null
        if (getActivity() == null) {
            Log.d("MonthlyTrendFragment", "loadChartData: Activity is null, skipping.");
            return;
        }

        // 延迟初始化 dbHelper，确保 Activity 已附加
        if (dbHelper == null) {
            dbHelper = new LogDatabaseHelper(getActivity());
        }

        Map<String, Long> stats = dbHelper.getMonthlyStats(String.valueOf(year));

        if (stats.isEmpty()) {
            barChart.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        barChart.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        // 准备数据
        ArrayList<BarEntry> entries = new ArrayList<>();
        monthLabels = new String[12];

        for (int i = 0; i < 12; i++) {
            String monthKey = String.format("%04d-%02d", year, i + 1);
            Long duration = stats.get(monthKey);
            // 将毫秒转换为小时
            float hours = (duration != null) ? duration / (60f * 60f * 1000f) : 0f;
            entries.add(new BarEntry(i, hours));
            monthLabels[i] = (i + 1) + "月";
        }

        // 创建数据集（使用主题颜色）
        BarDataSet dataSet = new BarDataSet(entries, "月度趋势");
        dataSet.setColor(ChartColorHelper.getPrimaryColor(getContext()));
        dataSet.setValueTextColor(ChartColorHelper.getTextColor(getContext()));
        dataSet.setValueTextSize(11f);
        dataSet.setHighLightColor(ChartColorHelper.getHighlightColor(getContext()));

        // 自定义值格式化器：智能显示小时或分钟
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value <= 0)
                    return "";
                if (value < 1.0f) {
                    // 小于 1 小时，显示分钟
                    int minutes = Math.round(value * 60);
                    return minutes + "m";
                } else {
                    // 大于等于 1 小时，显示小时（保留一位小数或取整）
                    if (value == (int) value) {
                        return (int) value + "h";
                    } else {
                        return String.format("%.1fh", value);
                    }
                }
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f); // 增加柱宽，减少间隙

        // 配置图表
        barChart.setData(barData);
        barChart.setFitBars(true);

        // 1. 强制 Y 轴从 0 开始
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(Math.max(2f, getMaxYValue(entries) * 1.2f)); // 动态设置最大值
        leftAxis.setGranularity(1f);

        // 2. 自定义 Y 轴标签格式化
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

        // 3. 清理图表装饰
        // 隐藏描述文字
        Description desc = new Description();
        desc.setEnabled(false);
        barChart.setDescription(desc);

        // 隐藏图例
        Legend legend = barChart.getLegend();
        legend.setEnabled(false);

        // 4. X 轴配置
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(12);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
        xAxis.setTextSize(10f);
        xAxis.setTextColor(ChartColorHelper.getTextColor(getContext()));
        xAxis.setDrawGridLines(false); // 隐藏垂直网格线
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(ChartColorHelper.getAxisColor(getContext()));

        // 5. Y 轴配置
        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setEnabled(false); // 隐藏右侧 Y 轴

        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ChartColorHelper.getGridColor(getContext()));
        leftAxis.setGridLineWidth(1f);
        // 设置虚线网格
        leftAxis.enableGridDashedLine(10f, 10f, 0f);

        // 6. 优化柱状图样式
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);

        // 7. 添加点击交互
        barChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int monthIndex = (int) e.getX();
                if (monthIndex >= 0 && monthIndex < 12) {
                    // 检查该月是否有数据
                    String monthKey = String.format("%04d-%02d", year, monthIndex + 1);
                    Long duration = stats.get(monthKey);

                    if (duration == null || duration == 0) {
                        Toast.makeText(getActivity(), year + "年" + (monthIndex + 1) + "月暂无记录", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }

                    openMonthDetail(monthIndex + 1);
                }
            }

            @Override
            public void onNothingSelected() {
                // 取消高亮
            }
        });

        // 8. 动画
        barChart.animateY(500);
        barChart.invalidate();
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

    // 打开月份详情 - 跳转到日志详情页
    private void openMonthDetail(int month) {
        if (getActivity() == null)
            return;

        Intent intent = new Intent(getActivity(), LogDetailActivity.class);
        intent.putExtra(LogDetailActivity.EXTRA_FILTER_TYPE, "MONTH");
        intent.putExtra(LogDetailActivity.EXTRA_YEAR, year);
        intent.putExtra(LogDetailActivity.EXTRA_MONTH, month);

        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}