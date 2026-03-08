package com.EachYoungX.timer.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.EachYoungX.timer.R;
import com.EachYoungX.timer.database.LogDatabaseHelper;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 自定义月历视图
 * 显示 6 行 7 列的日期网格，支持点击选择日期
 * 优化：固定单元格尺寸，避免拉伸变形
 * 主题适配：使用语义化颜色资源，支持动态换肤
 */
public class MonthCalendarView extends GridLayout {

    private TextView[] dateCells = new TextView[42]; // 6 行 x 7 列
    private LocalDate selectedDate;
    private LocalDate today;
    private YearMonth currentMonth;
    private OnDateSelectedListener dateSelectedListener;
    private int cellSize;

    public MonthCalendarView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public MonthCalendarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MonthCalendarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setColumnCount(7);
        setRowCount(6);
        today = LocalDate.now();
        selectedDate = today;
        currentMonth = YearMonth.now();

        // 获取单元格尺寸
        cellSize = getResources().getDimensionPixelSize(R.dimen.calendar_cell_size);
    }

    /**
     * 渲染指定月份的日历
     */
    public void render(YearMonth yearMonth) {
        currentMonth = yearMonth;
        removeAllViews();

        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        // 获取 1 号是周几（1=周一，7=周日）
        int startDayOfWeek = firstDayOfMonth.getDayOfWeek().getValue();
        int daysInMonth = yearMonth.lengthOfMonth();

        // 计算起始偏移：转换为以周日为第一列的偏移量
        // 周日 (7) -> 0, 周一 (1) -> 1, 周六 (6) -> 6
        int startOffset = (startDayOfWeek % 7);

        // 创建 42 个日期格子
        for (int i = 0; i < 42; i++) {
            TextView cell = createCell(i, startOffset, daysInMonth, firstDayOfMonth);
            addView(cell);
            dateCells[i] = cell;
        }

        updateSelection();
    }

    /**
     * 创建单个日期格子
     */
    private TextView createCell(int position, int startOffset, int daysInMonth, LocalDate firstDayOfMonth) {
        TextView cell = new TextView(getContext());

        // 使用自适应权重，与星期标题对齐
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = cellSize;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1.0f);
        cell.setLayoutParams(params);

        cell.setGravity(Gravity.CENTER);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.calendar_text_size));
        cell.setPadding(0, 0, 0, 0);
        cell.setBackgroundResource(R.drawable.bg_date_cell);
        cell.setOnClickListener(v -> onDateClick(position, startOffset, daysInMonth, firstDayOfMonth));

        // 计算日期
        int day = position - startOffset + 1;
        if (day >= 1 && day <= daysInMonth) {
            // 当前月的日期
            LocalDate date = firstDayOfMonth.plusDays(day - 1);
            cell.setText(String.valueOf(day));

            // 使用主题属性获取颜色（自动适配深色模式）
            int[] attrs = { R.attr.colorOnSurface };
            android.content.res.TypedArray ta = getContext().getTheme().obtainStyledAttributes(attrs);
            int textColor = ta.getColor(0, ContextCompat.getColor(getContext(), R.color.on_surface));
            ta.recycle();
            cell.setTextColor(textColor);

            cell.setTag(date);

            // 检查是否有记录（可以添加标记）
            android.util.Log.d("MonthCalendarView",
                    "createCell: position=" + position + ", day=" + day + ", date=" + date);
            boolean hasLogs = checkHasLogs(date);
            android.util.Log.d("MonthCalendarView", "createCell: hasLogs=" + hasLogs);
            if (hasLogs) {
                // 有记录使用主题色
                android.util.Log.d("MonthCalendarView", "createCell: Setting primary color for date with logs");
                int[] primaryAttrs = { R.attr.colorPrimary };
                android.content.res.TypedArray primaryTa = getContext().getTheme().obtainStyledAttributes(primaryAttrs);
                cell.setTextColor(primaryTa.getColor(0, ContextCompat.getColor(getContext(), R.color.primary)));
                primaryTa.recycle();
            }
        } else {
            // 非当前月的日期：隐藏文字，不显示
            cell.setText("");
            cell.setTextColor(Color.TRANSPARENT);
            cell.setAlpha(1.0f);
            cell.setTag(null);
            cell.setClickable(false);
        }

        return cell;
    }

    /**
     * 检查指定日期是否有日志记录
     */
    private boolean checkHasLogs(LocalDate date) {
        android.util.Log.d("MonthCalendarView", "checkHasLogs: START - date=" + date);

        if (date == null) {
            android.util.Log.d("MonthCalendarView", "checkHasLogs: date is null, returning false");
            return false;
        }

        // 查询数据库，检查该日期是否有记录
        try {
            // 创建日期键（格式：YYYY-MM-DD）
            String dateKey = String.format("%04d-%02d-%02d",
                    date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            android.util.Log.d("MonthCalendarView", "checkHasLogs: dateKey=" + dateKey);

            // 使用 dbHelper 查询
            LogDatabaseHelper dbHelper = new LogDatabaseHelper(getContext());
            android.util.Log.d("MonthCalendarView", "checkHasLogs: Created dbHelper, calling hasLogsOnDate");

            boolean hasLogs = dbHelper.hasLogsOnDate(dateKey);
            android.util.Log.d("MonthCalendarView", "checkHasLogs: hasLogsOnDate returned=" + hasLogs);

            dbHelper.close();
            android.util.Log.d("MonthCalendarView", "checkHasLogs: dbHelper closed, returning=" + hasLogs);

            return hasLogs;
        } catch (Exception e) {
            // 如果查询失败，默认返回 false
            android.util.Log.e("MonthCalendarView", "checkHasLogs: Exception occurred", e);
            return false;
        }
    }

    /**
     * 日期点击事件
     */
    private void onDateClick(int position, int startOffset, int daysInMonth, LocalDate firstDayOfMonth) {
        int day = position - startOffset + 1;
        if (day >= 1 && day <= daysInMonth) {
            LocalDate clickedDate = firstDayOfMonth.plusDays(day - 1);
            setSelectedDate(clickedDate);
        }
    }

    /**
     * 设置选中的日期
     */
    public void setSelectedDate(LocalDate date) {
        if (date == null)
            return;

        this.selectedDate = date;

        // 如果选中的日期不在当前显示的月份，切换月份
        if (!YearMonth.from(date).equals(currentMonth)) {
            render(YearMonth.from(date));
        } else {
            updateSelection();
        }

        if (dateSelectedListener != null) {
            dateSelectedListener.onDateSelected(date);
        }
    }

    /**
     * 更新选中状态（使用 LayerDrawable 保持圆形）
     */
    private void updateSelection() {
        for (TextView cell : dateCells) {
            if (cell == null)
                continue;

            Object tag = cell.getTag();
            if (tag instanceof LocalDate) {
                LocalDate cellDate = (LocalDate) tag;

                // 重置样式
                cell.setSelected(false);

                // 使用主题属性获取颜色（自动适配深色模式）
                int[] attrs = { R.attr.colorOnSurface };
                android.content.res.TypedArray ta = getContext().getTheme().obtainStyledAttributes(attrs);
                int onSurfaceColor = ta.getColor(0, ContextCompat.getColor(getContext(), R.color.on_surface));
                ta.recycle();
                cell.setTextColor(onSurfaceColor);

                cell.setBackground(null);

                // 今天的样式
                if (cellDate.equals(today)) {
                    // 使用 LayerDrawable 保持圆形
                    cell.setBackground(createLayerCircleBackground(android.R.attr.colorPrimary));

                    // 获取 onPrimary 颜色
                    int[] onPrimaryAttrs = { R.attr.colorOnPrimary };
                    android.content.res.TypedArray onPrimaryTa = getContext().getTheme()
                            .obtainStyledAttributes(onPrimaryAttrs);
                    int onPrimaryColor = onPrimaryTa.getColor(0,
                            ContextCompat.getColor(getContext(), R.color.on_primary));
                    onPrimaryTa.recycle();
                    cell.setTextColor(onPrimaryColor);
                }
                // 选中日的样式
                else if (cellDate.equals(selectedDate)) {
                    // 使用 LayerDrawable 保持圆形
                    cell.setBackground(createLayerCircleBackground(android.R.attr.colorSecondary));

                    // 获取 onSecondary 颜色
                    int[] onSecondaryAttrs = { R.attr.colorOnSecondary };
                    android.content.res.TypedArray onSecondaryTa = getContext().getTheme()
                            .obtainStyledAttributes(onSecondaryAttrs);
                    int onSecondaryColor = onSecondaryTa.getColor(0,
                            ContextCompat.getColor(getContext(), R.color.on_secondary));
                    onSecondaryTa.recycle();
                    cell.setTextColor(onSecondaryColor);
                }
                // 普通样式
                else {
                    cell.setBackgroundResource(R.drawable.bg_date_cell);
                }
            }
        }
    }

    /**
     * 创建 LayerDrawable 圆形背景（动态计算 inset，保持正圆形）
     */
    private android.graphics.drawable.Drawable createLayerCircleBackground(int colorAttr) {
        // 1. 创建基础的圆形 Drawable
        android.graphics.drawable.GradientDrawable circleDrawable = new android.graphics.drawable.GradientDrawable();
        circleDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);

        // 获取主题属性颜色
        int[] attrs = { colorAttr };
        android.content.res.TypedArray ta = getContext().getTheme().obtainStyledAttributes(attrs);
        int color = ta.getColor(0, ContextCompat.getColor(getContext(), android.R.color.holo_blue_light));
        ta.recycle();
        circleDrawable.setColor(color);

        // 2. 将圆形 Drawable 放入 LayerDrawable 中
        android.graphics.drawable.Drawable[] layers = new android.graphics.drawable.Drawable[] { circleDrawable };
        android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(layers) {
            @Override
            public void setBounds(int left, int top, int right, int bottom) {
                super.setBounds(left, top, right, bottom); // 首先让 LayerDrawable 自己确定边界

                int width = right - left;
                int height = bottom - top;

                // 3. 关键步骤：计算出一个正方形区域，并直接设置给内部的 circleDrawable
                int diameter = (int) (Math.min(width, height) * 0.73); // 以较短边为基准计算直径
                int horizontalMargin = (width - diameter) / 2;
                int verticalMargin = (height - diameter) / 2;

                // 获取内部的 Drawable (我们只有一个，所以索引是 0)
                Drawable innerCircle = getDrawable(0);
                // 为内部 Drawable 设置一个位于中央的正方形 Bounds
                innerCircle.setBounds(
                        left + horizontalMargin,
                        top + verticalMargin,
                        right - horizontalMargin,
                        bottom - verticalMargin);
            }
        };

        return layerDrawable;
    }

    /**
     * 获取当前选中的日期
     */
    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    /**
     * 获取当前显示的月份
     */
    public YearMonth getCurrentMonth() {
        return currentMonth;
    }

    /**
     * 设置日期选择监听器
     */
    public void setOnDateSelectedListener(OnDateSelectedListener listener) {
        this.dateSelectedListener = listener;
    }

    /**
     * 日期选择监听器接口
     */
    public interface OnDateSelectedListener {
        void onDateSelected(LocalDate date);
    }
}
