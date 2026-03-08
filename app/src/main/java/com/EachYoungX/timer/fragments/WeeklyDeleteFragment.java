package com.EachYoungX.timer.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.EachYoungX.timer.utils.DateUtils;
import com.EachYoungX.timer.models.LogEntry;
import com.EachYoungX.timer.R;
import com.EachYoungX.timer.activities.ManualDeleteActivity;
import com.EachYoungX.timer.database.LogDatabaseHelper;
import com.google.android.material.card.MaterialCardView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WeeklyDeleteFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private TextView tvMonth;
    private ImageButton btnPrevMonth, btnNextMonth;
    private int year = java.time.Year.now().getValue();
    private int month = java.time.LocalDate.now().getMonthValue();
    private LogDatabaseHelper dbHelper;
    private Set<Long> selectedRecords;
    private WeeklyDeleteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_delete_weekly, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        tvEmpty = view.findViewById(R.id.tv_empty);
        tvMonth = view.findViewById(R.id.tv_month);
        btnPrevMonth = view.findViewById(R.id.btn_prev_month);
        btnNextMonth = view.findViewById(R.id.btn_next_month);

        // 不在这里初始化 dbHelper，延迟到 loadChartData 中初始化
        // 确保 getActivity() 不为 null

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        btnPrevMonth.setOnClickListener(v -> {
            goToPreviousMonth();
        });

        btnNextMonth.setOnClickListener(v -> {
            goToNextMonth();
        });

        updateMonthDisplay();
        loadChartData();

        return view;
    }

    public void setYear(int year) {
        this.year = year;
        // 检查 view 是否已创建，避免 NPE
        if (tvMonth != null) {
            updateMonthDisplay();
        }
        loadChartData();
    }

    public void setSelectedRecords(Set<Long> selectedRecords) {
        this.selectedRecords = selectedRecords;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void goToPreviousMonth() {
        month--;
        if (month < 1) {
            month = 12;
            year--;
        }
        updateMonthDisplay();
        loadChartData();
    }

    private void goToNextMonth() {
        month++;
        if (month > 12) {
            month = 1;
            year++;
        }
        updateMonthDisplay();
        loadChartData();
    }

    private void updateMonthDisplay() {
        // 检查 tvMonth 是否为 null，避免 NPE
        if (tvMonth != null) {
            tvMonth.setText(String.format(Locale.CHINA, "%04d年%02d月", year, month));
        }
    }

    private void loadChartData() {
        // 确保 getActivity() 不为 null
        if (getActivity() == null) {
            Log.d("WeeklyDeleteFragment", "loadChartData: Activity is null, skipping.");
            return;
        }

        // 延迟初始化 dbHelper，确保 Activity 已附加
        if (dbHelper == null) {
            try {
                dbHelper = new LogDatabaseHelper(getActivity());
                Log.d("WeeklyDeleteFragment", "dbHelper initialized successfully.");
            } catch (Exception e) {
                Log.e("WeeklyDeleteFragment", "Failed to initialize dbHelper", e);
                // 如果初始化失败，显示错误提示
                if (getContext() != null) {
                    Toast.makeText(getContext(), "数据未就绪，请稍后重试", Toast.LENGTH_SHORT).show();
                }
                recyclerView.setVisibility(View.GONE);
                tvEmpty.setText("数据加载失败");
                tvEmpty.setVisibility(View.VISIBLE);
                return;
            }
        }

        List<WeekItem> items = calculateAllWeeksOfMonth(year, month);

        if (items.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        recyclerView.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        adapter = new WeeklyDeleteAdapter(items, selectedRecords, (ManualDeleteActivity) getActivity());
        recyclerView.setAdapter(adapter);
    }

    private List<WeekItem> calculateAllWeeksOfMonth(int year, int month) {
        List<WeekItem> weeks = new ArrayList<>();
        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        LocalDate firstMonday = firstDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        int weekIndex = 0;
        LocalDate currentMonday = firstMonday;
        while (!currentMonday.isAfter(lastDay)) {
            LocalDate sunday = currentMonday.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            String weekLabel = String.format(Locale.CHINA, "第%d周 (%s - %s)",
                    weekIndex + 1,
                    currentMonday.format(DateTimeFormatter.ofPattern("MM/dd")),
                    sunday.format(DateTimeFormatter.ofPattern("MM/dd")));

            weeks.add(new WeekItem(weekIndex, weekLabel, currentMonday, sunday));
            currentMonday = currentMonday.plusWeeks(1);
            weekIndex++;
        }
        return weeks;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    private static class WeekItem {
        int weekIndex;
        String label;
        LocalDate monday;
        LocalDate sunday;

        WeekItem(int weekIndex, String label, LocalDate monday, LocalDate sunday) {
            this.weekIndex = weekIndex;
            this.label = label;
            this.monday = monday;
            this.sunday = sunday;
        }
    }

    private class WeeklyDeleteAdapter extends RecyclerView.Adapter<WeeklyDeleteAdapter.ViewHolder> {
        private final List<WeekItem> items;
        private final Set<Long> selectedRecords;
        private final ManualDeleteActivity activity;
        private final Set<String> expandedWeeks = new HashSet<>(); // 展开的周

        WeeklyDeleteAdapter(List<WeekItem> items, Set<Long> selectedRecords, ManualDeleteActivity activity) {
            this.items = items;
            this.selectedRecords = selectedRecords;
            this.activity = activity;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_delete_week, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WeekItem item = items.get(position);
            holder.weekText.setText(item.label);

            long weekDuration = getWeekDuration(item.monday, item.sunday);
            long hours = weekDuration / (60 * 60 * 1000);
            long minutes = (weekDuration % (60 * 60 * 1000)) / (60 * 1000);
            holder.durationText.setText(String.format("总计：%dh %dm", hours, minutes));

            int recordCount = getRecordCount(item.monday, item.sunday);
            holder.recordCountText.setText("共 " + recordCount + " 条记录");

            boolean allSelected = areAllRecordsSelected(item.monday, item.sunday);
            holder.checkBox.setChecked(allSelected);

            // 只有点击勾选框才触发勾选
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectAllRecordsInWeek(item.monday, item.sunday);
                } else {
                    deselectAllRecordsInWeek(item.monday, item.sunday);
                }
                if (activity != null) {
                    activity.updateConfirmButtonFromFragment();
                }
            });

            // 点击卡片区域展开/收起，而不是勾选
            holder.cardView.setOnClickListener(v -> {
                String weekKey = item.monday.toString() + "_" + item.sunday.toString();
                if (expandedWeeks.contains(weekKey)) {
                    expandedWeeks.remove(weekKey);
                    holder.recyclerDays.setVisibility(View.GONE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_down);
                } else {
                    expandedWeeks.add(weekKey);
                    holder.recyclerDays.setVisibility(View.VISIBLE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_up);
                    loadDayRecords(holder.recyclerDays, item.monday, item.sunday, activity);
                }
            });

            // 展开/收起按钮点击事件
            holder.btnExpand.setOnClickListener(v -> {
                String weekKey = item.monday.toString() + "_" + item.sunday.toString();
                if (expandedWeeks.contains(weekKey)) {
                    expandedWeeks.remove(weekKey);
                    holder.recyclerDays.setVisibility(View.GONE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_down);
                } else {
                    expandedWeeks.add(weekKey);
                    holder.recyclerDays.setVisibility(View.VISIBLE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_up);
                    loadDayRecords(holder.recyclerDays, item.monday, item.sunday, activity);
                }
            });
        }

        private long getWeekDuration(LocalDate monday, LocalDate sunday) {
            long total = 0;
            LocalDate current = monday;
            while (!current.isAfter(sunday)) {
                String dateKey = current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                List<LogEntry> logs = dbHelper.getLogsByDate(dateKey);
                for (LogEntry log : logs) {
                    total += log.getDuration();
                }
                current = current.plusDays(1);
            }
            return total;
        }

        private int getRecordCount(LocalDate monday, LocalDate sunday) {
            int count = 0;
            LocalDate current = monday;
            while (!current.isAfter(sunday)) {
                String dateKey = current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                count += dbHelper.getLogsByDate(dateKey).size();
                current = current.plusDays(1);
            }
            return count;
        }

        private boolean areAllRecordsSelected(LocalDate monday, LocalDate sunday) {
            List<Long> weekStartTimes = getWeekStartTimes(monday, sunday);
            if (weekStartTimes.isEmpty())
                return false;
            for (Long startTime : weekStartTimes) {
                if (!selectedRecords.contains(startTime)) {
                    return false;
                }
            }
            return true;
        }

        private void selectAllRecordsInWeek(LocalDate monday, LocalDate sunday) {
            List<Long> startTimes = getWeekStartTimes(monday, sunday);
            selectedRecords.addAll(startTimes);
            notifyDataSetChanged();
            if (activity != null) {
                activity.updateConfirmButtonFromFragment();
            }
        }

        private void deselectAllRecordsInWeek(LocalDate monday, LocalDate sunday) {
            List<Long> startTimes = getWeekStartTimes(monday, sunday);
            selectedRecords.removeAll(startTimes);
            notifyDataSetChanged();
            if (activity != null) {
                activity.updateConfirmButtonFromFragment();
            }
        }

        private List<Long> getWeekStartTimes(LocalDate monday, LocalDate sunday) {
            List<Long> startTimes = new ArrayList<>();
            LocalDate current = monday;
            while (!current.isAfter(sunday)) {
                String dateKey = current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                List<LogEntry> logs = dbHelper.getLogsByDate(dateKey);
                for (LogEntry log : logs) {
                    startTimes.add(log.getStartTime());
                }
                current = current.plusDays(1);
            }
            return startTimes;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardView;
            TextView weekText;
            TextView durationText;
            TextView recordCountText;
            CheckBox checkBox;
            ImageButton btnExpand;
            RecyclerView recyclerDays;

            ViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.card_view);
                weekText = itemView.findViewById(R.id.tv_week);
                durationText = itemView.findViewById(R.id.tv_duration);
                recordCountText = itemView.findViewById(R.id.tv_record_count);
                checkBox = itemView.findViewById(R.id.checkbox);
                btnExpand = itemView.findViewById(R.id.btn_expand);
                recyclerDays = itemView.findViewById(R.id.recycler_days);
            }
        }
    }

    /**
     * 加载某周的日记录列表
     */
    private void loadDayRecords(RecyclerView recyclerView, LocalDate monday, LocalDate sunday,
            ManualDeleteActivity activity) {
        // 按日期分组
        Map<String, List<LogEntry>> dayLogsMap = new HashMap<>();
        LocalDate current = monday;
        while (!current.isAfter(sunday)) {
            String dateKey = current.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            List<LogEntry> logs = dbHelper.getLogsByDate(dateKey);
            if (!logs.isEmpty()) {
                dayLogsMap.put(dateKey, logs);
            }
            current = current.plusDays(1);
        }

        // 创建日列表数据
        List<DayItem> dayItems = new ArrayList<>();
        for (Map.Entry<String, List<LogEntry>> entry : dayLogsMap.entrySet()) {
            String dateKey = entry.getKey();
            List<LogEntry> dayLogs = entry.getValue();

            long dayTotal = 0;
            for (LogEntry log : dayLogs) {
                dayTotal += log.getDuration();
            }

            dayItems.add(new DayItem(dateKey, dayLogs.size(), dayTotal, dayLogs));
        }

        // 设置日列表适配器
        DayAdapter dayAdapter = new DayAdapter(dayItems, selectedRecords, activity);
        recyclerView.setAdapter(dayAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
    }

    /**
     * 日记录数据项
     */
    private static class DayItem {
        String dateKey;
        int recordCount;
        long duration;
        List<LogEntry> records;

        DayItem(String dateKey, int recordCount, long duration, List<LogEntry> records) {
            this.dateKey = dateKey;
            this.recordCount = recordCount;
            this.duration = duration;
            this.records = records;
        }
    }

    /**
     * 日列表适配器
     */
    private class DayAdapter extends RecyclerView.Adapter<DayAdapter.DayViewHolder> {
        private final List<DayItem> dayItems;
        private final Set<Long> selectedRecords;
        private final ManualDeleteActivity activity;
        private final Set<String> expandedDays = new HashSet<>();
        
        // 防抖：上次点击时间戳
        private long lastClickTime = 0;
        private static final long DEBOUNCE_DELAY = 300; // 300ms 防抖
        
        // 防止并发更新标志
        private volatile boolean isUpdating = false;

        DayAdapter(List<DayItem> dayItems, Set<Long> selectedRecords, ManualDeleteActivity activity) {
            this.dayItems = dayItems;
            this.selectedRecords = selectedRecords;
            this.activity = activity;
        }

        @NonNull
        @Override
        public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_delete_day, parent, false);
            return new DayViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
            DayItem item = dayItems.get(position);

            // 格式化日期显示
            holder.dayDateText.setText(DateUtils.formatDateHeader(item.dateKey));

            long hours = item.duration / (60 * 60 * 1000);
            long minutes = (item.duration % (60 * 60 * 1000)) / (60 * 1000);
            holder.dayDurationText.setText(String.format("总计：%dh %dm", hours, minutes));
            holder.dayRecordCountText.setText("共 " + item.recordCount + " 条");

            // 检查整日是否全选
            boolean allSelected = areAllRecordsSelectedInDay(item.records);
            holder.dayCheckBox.setChecked(allSelected);

            // 只有点击勾选框才触发勾选
            holder.dayCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    for (LogEntry log : item.records) {
                        selectedRecords.add(log.getStartTime());
                    }
                } else {
                    for (LogEntry log : item.records) {
                        selectedRecords.remove(log.getStartTime());
                    }
                }
                if (activity != null) {
                    activity.updateConfirmButtonFromFragment();
                }
            });

            // 点击卡片区域展开/收起，而不是勾选
            holder.dayCardView.setOnClickListener(v -> {
                // 防抖检查
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < DEBOUNCE_DELAY) {
                    return; // 忽略快速点击
                }
                
                // 检查是否正在更新中
                if (isUpdating) {
                    return; // 忽略并发点击
                }
                
                lastClickTime = currentTime;
                isUpdating = true;
                
                try {
                    if (expandedDays.contains(item.dateKey)) {
                        expandedDays.remove(item.dateKey);
                        holder.recyclerRecords.setVisibility(View.GONE);
                        holder.btnDayExpand.setImageResource(R.drawable.ic_arrow_down);
                    } else {
                        expandedDays.add(item.dateKey);
                        holder.recyclerRecords.setVisibility(View.VISIBLE);
                        holder.btnDayExpand.setImageResource(R.drawable.ic_arrow_up);
                        loadRecordRecords(holder.recyclerRecords, item.records, activity);
                    }
                } finally {
                    isUpdating = false;
                }
            });

            // 展开/收起按钮点击事件
            holder.btnDayExpand.setOnClickListener(v -> {
                // 防抖检查
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < DEBOUNCE_DELAY) {
                    return; // 忽略快速点击
                }
                
                // 检查是否正在更新中
                if (isUpdating) {
                    return; // 忽略并发点击
                }
                
                lastClickTime = currentTime;
                isUpdating = true;
                
                try {
                    if (expandedDays.contains(item.dateKey)) {
                        expandedDays.remove(item.dateKey);
                        holder.recyclerRecords.setVisibility(View.GONE);
                        holder.btnDayExpand.setImageResource(R.drawable.ic_arrow_down);
                    } else {
                        expandedDays.add(item.dateKey);
                        holder.recyclerRecords.setVisibility(View.VISIBLE);
                        holder.btnDayExpand.setImageResource(R.drawable.ic_arrow_up);
                        loadRecordRecords(holder.recyclerRecords, item.records, activity);
                    }
                } finally {
                    isUpdating = false;
                }
            });
        }

        private boolean areAllRecordsSelectedInDay(List<LogEntry> records) {
            if (records.isEmpty())
                return false;
            for (LogEntry log : records) {
                if (!selectedRecords.contains(log.getStartTime())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getItemCount() {
            return dayItems.size();
        }

        class DayViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView dayCardView;
            TextView dayDateText;
            TextView dayDurationText;
            TextView dayRecordCountText;
            CheckBox dayCheckBox;
            ImageButton btnDayExpand;
            RecyclerView recyclerRecords;

            DayViewHolder(View itemView) {
                super(itemView);
                dayCardView = itemView.findViewById(R.id.day_card_view);
                dayDateText = itemView.findViewById(R.id.tv_day_date);
                dayDurationText = itemView.findViewById(R.id.tv_day_duration);
                dayRecordCountText = itemView.findViewById(R.id.tv_day_record_count);
                dayCheckBox = itemView.findViewById(R.id.day_checkbox);
                btnDayExpand = itemView.findViewById(R.id.btn_day_expand);
                recyclerRecords = itemView.findViewById(R.id.recycler_day_records);
            }
        }
    }

    /**
     * 加载记录列表
     */
    private void loadRecordRecords(RecyclerView recyclerView, List<LogEntry> records, ManualDeleteActivity activity) {
        RecordAdapter recordAdapter = new RecordAdapter(records, selectedRecords, activity);
        recyclerView.setAdapter(recordAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
    }

    /**
     * 记录级适配器
     */
    private class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.RecordViewHolder> {
        private final List<LogEntry> records;
        private final Set<Long> selectedRecords;
        private final ManualDeleteActivity activity;

        RecordAdapter(List<LogEntry> records, Set<Long> selectedRecords, ManualDeleteActivity activity) {
            this.records = records;
            this.selectedRecords = selectedRecords;
            this.activity = activity;
        }

        @NonNull
        @Override
        public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_delete_record, parent, false);
            return new RecordViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
            LogEntry entry = records.get(position);

            // 格式化时间显示
            String startTime = DateUtils.formatTime(entry.getStartTime());
            String endTime = DateUtils.formatTime(entry.getEndTime());
            holder.recordTimeText.setText(startTime + " - " + endTime);

            long hours = entry.getDuration() / (60 * 60 * 1000);
            long minutes = (entry.getDuration() % (60 * 60 * 1000)) / (60 * 1000);
            holder.recordDurationText.setText(String.format("时长：%dh %dm", hours, minutes));

            // 勾选状态
            holder.recordCheckBox.setChecked(selectedRecords.contains(entry.getStartTime()));

            // 勾选事件
            holder.recordCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedRecords.add(entry.getStartTime());
                } else {
                    selectedRecords.remove(entry.getStartTime());
                }
                if (activity != null) {
                    activity.updateConfirmButtonFromFragment();
                }
            });

            // 单条记录点击整个区域都能勾选
            holder.itemView.setOnClickListener(v -> {
                holder.recordCheckBox.toggle();
            });
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        class RecordViewHolder extends RecyclerView.ViewHolder {
            TextView recordTimeText;
            TextView recordDurationText;
            CheckBox recordCheckBox;

            RecordViewHolder(View itemView) {
                super(itemView);
                recordTimeText = itemView.findViewById(R.id.tv_record_time);
                recordDurationText = itemView.findViewById(R.id.tv_record_duration);
                recordCheckBox = itemView.findViewById(R.id.record_checkbox);
            }
        }
    }
}
