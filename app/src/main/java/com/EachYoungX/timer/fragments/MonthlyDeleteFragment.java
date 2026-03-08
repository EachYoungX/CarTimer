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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MonthlyDeleteFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private int year = java.time.Year.now().getValue();
    private LogDatabaseHelper dbHelper;
    private Set<Long> selectedRecords;
    private MonthlyDeleteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_delete_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        tvEmpty = view.findViewById(R.id.tv_empty);

        // 不在这里初始化 dbHelper，延迟到 loadChartData 中初始化
        // 确保 getActivity() 不为 null

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        loadChartData();

        return view;
    }

    public void setYear(int year) {
        this.year = year;
        loadChartData();
    }

    public void setSelectedRecords(Set<Long> selectedRecords) {
        this.selectedRecords = selectedRecords;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadChartData() {
        // 确保 getActivity() 不为 null
        if (getActivity() == null) {
            Log.d("MonthlyDeleteFragment", "loadChartData: Activity is null, skipping.");
            return;
        }

        // 延迟初始化 dbHelper，确保 Activity 已附加
        if (dbHelper == null) {
            try {
                dbHelper = new LogDatabaseHelper(getActivity());
                Log.d("MonthlyDeleteFragment", "dbHelper initialized successfully.");
            } catch (Exception e) {
                Log.e("MonthlyDeleteFragment", "Failed to initialize dbHelper", e);
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

        Map<String, Long> stats = dbHelper.getMonthlyStats(String.valueOf(year));

        if (stats.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        recyclerView.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        List<MonthItem> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String monthKey = String.format("%04d-%02d", year, i + 1);
            Long duration = stats.get(monthKey);
            if (duration != null && duration > 0) {
                items.add(new MonthItem(i + 1, monthKey, duration));
            }
        }

        adapter = new MonthlyDeleteAdapter(items, selectedRecords, (ManualDeleteActivity) getActivity());
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    private static class MonthItem {
        int month;
        String monthKey;
        long duration;

        MonthItem(int month, String monthKey, long duration) {
            this.month = month;
            this.monthKey = monthKey;
            this.duration = duration;
        }
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
        private final ManualDeleteActivity activity;
        private final Set<String> expandedDays = new HashSet<>();
        
        // 防抖：上次点击时间戳
        private long lastClickTime = 0;
        private static final long DEBOUNCE_DELAY = 300; // 300ms 防抖
        
        // 防止并发更新标志
        private volatile boolean isUpdating = false;

        DayAdapter(List<DayItem> dayItems, ManualDeleteActivity activity) {
            this.dayItems = dayItems;
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

    private class MonthlyDeleteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<MonthItem> items;
        private final Set<Long> selectedRecords;
        private final ManualDeleteActivity activity;
        private final Set<String> expandedDays = new HashSet<>(); // 展开的日期

        MonthlyDeleteAdapter(List<MonthItem> items, Set<Long> selectedRecords, ManualDeleteActivity activity) {
            this.items = items;
            this.selectedRecords = selectedRecords;
            this.activity = activity;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_delete_month, parent, false);
            return new MonthViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthViewHolder monthHolder = (MonthViewHolder) holder;
            bindMonthViewHolder(monthHolder, position);
        }

        private void bindMonthViewHolder(MonthViewHolder holder, int position) {
            MonthItem item = items.get(position);
            holder.monthText.setText(year + "年" + item.month + "月");

            long hours = item.duration / (60 * 60 * 1000);
            long minutes = (item.duration % (60 * 60 * 1000)) / (60 * 1000);
            holder.durationText.setText(String.format("总计：%dh %dm", hours, minutes));

            int recordCount = getRecordCount(item.monthKey);
            holder.recordCountText.setText("共 " + recordCount + " 条记录");

            boolean allSelected = areAllRecordsSelected(item.monthKey);
            holder.checkBox.setChecked(allSelected);

            // 只有点击勾选框才触发勾选
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectAllRecordsInMonth(item.monthKey);
                } else {
                    deselectAllRecordsInMonth(item.monthKey);
                }
            });

            // 点击卡片区域展开/收起，而不是勾选
            holder.cardView.setOnClickListener(v -> {
                String monthKey = item.monthKey;
                if (expandedDays.contains(monthKey)) {
                    expandedDays.remove(monthKey);
                    holder.recyclerDays.setVisibility(View.GONE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_down);
                } else {
                    expandedDays.add(monthKey);
                    holder.recyclerDays.setVisibility(View.VISIBLE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_up);
                    loadDayRecords(holder.recyclerDays, monthKey);
                }
            });

            // 展开/收起按钮点击事件
            holder.btnExpand.setOnClickListener(v -> {
                String monthKey = item.monthKey;
                if (expandedDays.contains(monthKey)) {
                    expandedDays.remove(monthKey);
                    holder.recyclerDays.setVisibility(View.GONE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_down);
                } else {
                    expandedDays.add(monthKey);
                    holder.recyclerDays.setVisibility(View.VISIBLE);
                    holder.btnExpand.setImageResource(R.drawable.ic_arrow_up);
                    loadDayRecords(holder.recyclerDays, monthKey);
                }
            });
        }

        /**
         * 加载某月的日记录列表
         */
        private void loadDayRecords(RecyclerView recyclerView, String monthKey) {
            List<LogEntry> logs = dbHelper.getLogsByMonth(monthKey);

            // 按日期分组
            Map<String, List<LogEntry>> dayLogsMap = new HashMap<>();
            for (LogEntry log : logs) {
                String dateKey = log.getDateKey();
                if (dateKey == null)
                    continue;

                if (!dayLogsMap.containsKey(dateKey)) {
                    dayLogsMap.put(dateKey, new ArrayList<>());
                }
                dayLogsMap.get(dateKey).add(log);
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
            DayAdapter dayAdapter = new DayAdapter(dayItems, activity);
            recyclerView.setAdapter(dayAdapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        }

        private int getRecordCount(String monthKey) {
            return dbHelper.getLogsByMonth(monthKey).size();
        }

        private boolean areAllRecordsSelected(String monthKey) {
            List<LogEntry> logs = dbHelper.getLogsByMonth(monthKey);
            if (logs.isEmpty())
                return false;
            for (LogEntry log : logs) {
                if (!selectedRecords.contains(log.getStartTime())) {
                    return false;
                }
            }
            return true;
        }

        private void selectAllRecordsInMonth(String monthKey) {
            List<LogEntry> logs = dbHelper.getLogsByMonth(monthKey);
            for (LogEntry log : logs) {
                selectedRecords.add(log.getStartTime());
            }
            notifyDataSetChanged();
            if (activity != null) {
                activity.updateConfirmButtonFromFragment();
            }
        }

        private void deselectAllRecordsInMonth(String monthKey) {
            List<LogEntry> logs = dbHelper.getLogsByMonth(monthKey);
            for (LogEntry log : logs) {
                selectedRecords.remove(log.getStartTime());
            }
            notifyDataSetChanged();
            if (activity != null) {
                activity.updateConfirmButtonFromFragment();
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class MonthViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardView;
            TextView monthText;
            TextView durationText;
            TextView recordCountText;
            CheckBox checkBox;
            ImageButton btnExpand;
            RecyclerView recyclerDays;

            MonthViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.card_view);
                monthText = itemView.findViewById(R.id.tv_month);
                durationText = itemView.findViewById(R.id.tv_duration);
                recordCountText = itemView.findViewById(R.id.tv_record_count);
                checkBox = itemView.findViewById(R.id.checkbox);
                btnExpand = itemView.findViewById(R.id.btn_expand);
                recyclerDays = itemView.findViewById(R.id.recycler_days);
            }
        }
    }
}
