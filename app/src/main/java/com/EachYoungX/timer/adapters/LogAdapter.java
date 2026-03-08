package com.EachYoungX.timer.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.EachYoungX.timer.utils.DateUtils;
import com.EachYoungX.timer.models.LogEntry;
import com.EachYoungX.timer.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<LogEntry> originalLogList; // 原始完整数据
    private List<LogEntry> displayList; // 动态生成的显示列表
    private OnLoadMoreListener loadMoreListener;

    // 展开/折叠状态管理：key=dateKey, value=是否展开
    private Set<String> expandedDates = new HashSet<>();

    // 每个 Header 下的记录数量限制（根据屏幕方向动态调整）
    // 横屏：3 条，竖屏：5 条
    private int visibleCount = 5; // 默认竖屏

    // 防抖：上次点击时间戳
    private long lastClickTime = 0;
    // 防抖时间间隔（毫秒）
    private static final long DEBOUNCE_DELAY = 300; // 300ms 防抖
    
    // 防止并发更新标志
    private volatile boolean isUpdating = false;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int TYPE_FOOTER = 2;
    private static final int TYPE_EXPAND_BUTTON = 3; // 展开/收起按钮

    public LogAdapter(Context context, List<LogEntry> logList) {
        this.context = context;
        this.originalLogList = new ArrayList<>(logList);
        this.displayList = new ArrayList<>();

        // 根据屏幕方向设置默认显示数量
        updateVisibleCount();

        // 生成初始显示列表
        rebuildDisplayList();
    }

    /**
     * 根据屏幕方向更新显示数量
     */
    public void updateVisibleCount() {
        if (context != null && context.getResources() != null) {
            int orientation = context.getResources().getConfiguration().orientation;
            if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                visibleCount = 3; // 横屏显示 3 条
            } else {
                visibleCount = 5; // 竖屏显示 5 条
            }
        }
    }

    /**
     * 获取当前显示数量限制
     */
    private int getVisibleCount() {
        return visibleCount;
    }

    /**
     * 重建显示列表 - 动态数据映射
     * 根据展开/折叠状态，动态生成要显示的数据集合
     */
    private void rebuildDisplayList() {
        displayList.clear();

        if (originalLogList.isEmpty()) {
            return;
        }

        // 1. 获取纯净的分组数据
        Map<String, List<LogEntry>> groupedLogs = groupLogsByDate(originalLogList);

        // 2. 动态构建
        for (Map.Entry<String, List<LogEntry>> entry : groupedLogs.entrySet()) {
            String dateKey = entry.getKey();
            List<LogEntry> dayLogs = entry.getValue();

            // 只有当该天确实有数据时才添加 Header
            if (dayLogs.isEmpty()) continue;

            // 添加 Header
            String headerDate = DateUtils.formatDateHeader(dateKey);
            long dayTotal = calculateDayTotal(dayLogs);
            displayList.add(LogEntry.createHeader(headerDate, "当日总计：" + DateUtils.formatDuration(dayTotal)));

            // 展开/收起逻辑
            boolean isExpanded = expandedDates.contains(dateKey);
            int limit = getVisibleCount();

            if (isExpanded || dayLogs.size() <= limit) {
                displayList.addAll(dayLogs);
                if (isExpanded && dayLogs.size() > limit) {
                    LogEntry btn = LogEntry.createExpandButton();
                    btn.setDateKey(dateKey);
                    displayList.add(btn);
                }
            } else {
                for (int i = 0; i < limit; i++) {
                    displayList.add(dayLogs.get(i));
                }
                LogEntry btn = LogEntry.createExpandButton();
                btn.setDateKey(dateKey);
                displayList.add(btn);
            }
        }

        // 3. 最后添加 Footer
        displayList.add(LogEntry.createFooter("—— 已经到底了 ——"));
    }

    /**
     * 按日期分组日志
     */
    private Map<String, List<LogEntry>> groupLogsByDate(List<LogEntry> logs) {
        // 使用 LinkedHashMap 保证日期显示顺序（按数据插入顺序）
        Map<String, List<LogEntry>> grouped = new java.util.LinkedHashMap<>();

        for (LogEntry log : logs) {
            // 核心过滤：只对真正的“记录”条目进行分组，忽略 Header/Footer/Button
            if (log == null || log.isHeader() || log.isFooter() || log.isExpandButton()) {
                continue;
            }

            String dateKey = log.getDateKey();
            // 过滤掉没有日期的脏数据
            if (dateKey == null || dateKey.isEmpty()) {
                continue;
            }

            if (!grouped.containsKey(dateKey)) {
                grouped.put(dateKey, new ArrayList<>());
            }
            grouped.get(dateKey).add(log);
        }
        return grouped;
    }

    /**
     * 计算某天的总时长
     */
    private long calculateDayTotal(List<LogEntry> dayLogs) {
        long total = 0;
        for (LogEntry log : dayLogs) {
            total += log.getDuration();
        }
        return total;
    }

    @Override
    public int getItemViewType(int position) {
        if (displayList.isEmpty()) {
            return TYPE_ITEM;
        }

        LogEntry entry = displayList.get(position);
        if (entry.isHeader()) {
            return TYPE_HEADER;
        } else if (entry.isFooter()) {
            return TYPE_FOOTER;
        } else if (entry.isExpandButton()) {
            return TYPE_EXPAND_BUTTON;
        } else {
            return TYPE_ITEM;
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == TYPE_FOOTER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_footer, parent, false);
            return new FooterViewHolder(view);
        } else if (viewType == TYPE_EXPAND_BUTTON) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_expand_button, parent, false);
            return new ExpandButtonViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_item_compact, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        LogEntry entry = displayList.get(position);

        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(entry, position);
        } else if (holder instanceof FooterViewHolder) {
            ((FooterViewHolder) holder).bind(entry);
        } else if (holder instanceof ExpandButtonViewHolder) {
            ((ExpandButtonViewHolder) holder).bind(entry, 0, position);
        } else if (holder instanceof ItemViewHolder) {
            ((ItemViewHolder) holder).bind(entry);
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    public void refreshData(List<LogEntry> newLogList) {
        this.originalLogList.clear();
        this.originalLogList.addAll(new ArrayList<>(newLogList));

        // 重建显示列表
        rebuildDisplayList();

        notifyDataSetChanged();
    }

    public void setOnLoadMoreListener(OnLoadMoreListener listener) {
        this.loadMoreListener = listener;
    }

    public interface OnLoadMoreListener {
        void onLoadMore();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeaderDate;
        TextView tvHeaderTotal;
        ImageButton btnExpand; // 展开/收起按钮

        public HeaderViewHolder(View itemView) {
            super(itemView);
            tvHeaderDate = itemView.findViewById(R.id.tv_header_date);
            tvHeaderTotal = itemView.findViewById(R.id.tv_header_total);
            btnExpand = itemView.findViewById(R.id.btn_expand);

            // Header 点击事件：切换展开/收起状态
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position >= 0 && position < displayList.size()) {
                    LogEntry entry = displayList.get(position);
                    if (entry.isHeader()) {
                        // 从 displayList 中找到对应的 dateKey
                        String dateKey = findDateKeyFromHeader(entry.getHeaderDate());
                        if (dateKey != null) {
                            toggleExpand(dateKey);
                        }
                    }
                }
            });

            // 展开/收起箭头按钮单独绑定点击事件
            btnExpand.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position >= 0 && position < displayList.size()) {
                    LogEntry entry = displayList.get(position);
                    if (entry.isHeader()) {
                        String dateKey = findDateKeyFromHeader(entry.getHeaderDate());
                        if (dateKey != null) {
                            toggleExpand(dateKey);
                        }
                    }
                }
            });
        }

        public void bind(LogEntry entry, int position) {
            tvHeaderDate.setText(entry.getHeaderDate());
            tvHeaderTotal.setText(entry.getHeaderTotal());

            // 更新展开/收起图标
            // 从 displayList 中找到对应的 dateKey
            String dateKey = findDateKeyFromHeader(entry.getHeaderDate());
            boolean isExpanded = dateKey != null && expandedDates.contains(dateKey);
            btnExpand.setImageResource(isExpanded ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);
        }
    }

    /**
     * 切换展开/收起状态（带防抖和异步更新保护）
     */
    private void toggleExpand(String dateKey) {
        // 防抖检查：如果距离上次点击时间太短，忽略此次点击
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < DEBOUNCE_DELAY) {
            // 点击过于频繁，忽略
            return;
        }
        
        // 检查是否正在更新中
        if (isUpdating) {
            // 正在更新，忽略此次点击
            android.util.Log.d("LogAdapter", "toggleExpand: Update in progress, ignoring click.");
            return;
        }
        
        lastClickTime = currentTime;
        isUpdating = true; // 标记为更新中

        try {
            // 切换状态
            if (expandedDates.contains(dateKey)) {
                expandedDates.remove(dateKey);
            } else {
                expandedDates.add(dateKey);
            }

            // 在后台线程中重建显示列表，避免阻塞主线程
            new Thread(() -> {
                try {
                    rebuildDisplayList();
                    
                    // 在主线程中更新 UI
                    android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            notifyDataSetChanged();
                        } catch (Exception e) {
                            // 捕获可能的并发修改异常，防止崩溃
                            android.util.Log.e("LogAdapter", "Error during notifyDataSetChanged", e);
                            // 不执行任何重置操作，保持当前状态
                        } finally {
                            isUpdating = false; // 释放更新锁
                        }
                    });
                } catch (Exception e) {
                    // 后台线程中的异常处理
                    android.util.Log.e("LogAdapter", "Error in rebuildDisplayList", e);
                    isUpdating = false; // 释放更新锁
                }
            }).start();
        } catch (Exception e) {
            // 捕获异常，防止崩溃传播
            android.util.Log.e("LogAdapter", "Error in toggleExpand", e);
            isUpdating = false; // 释放更新锁
        }
    }

    class FooterViewHolder extends RecyclerView.ViewHolder {
        TextView tvFooterMessage;

        public FooterViewHolder(View itemView) {
            super(itemView);
            tvFooterMessage = itemView.findViewById(R.id.tv_footer_message);
        }

        public void bind(LogEntry entry) {
            tvFooterMessage.setText(entry.getFooterText());
        }
    }

    class ExpandButtonViewHolder extends RecyclerView.ViewHolder {
        Button btnExpandCollapse;

        public ExpandButtonViewHolder(View itemView) {
            super(itemView);
            btnExpandCollapse = itemView.findViewById(R.id.btn_expand_collapse);
        }

        public void bind(LogEntry entry, int headerPosition, int buttonPosition) {
            // 从 displayList 中找到对应的 dateKey
            String dateKey = findDateKeyFromButtonPosition(buttonPosition);
            int itemCount = getDayLogCount(dateKey);
            boolean isExpanded = dateKey != null && expandedDates.contains(dateKey);

            if (isExpanded) {
                // 展开状态，显示"收起"
                btnExpandCollapse.setText("收起 ▲");
            } else {
                // 折叠状态，显示"展开更多"
                int hiddenCount = itemCount - getVisibleCount();
                btnExpandCollapse.setText("展开更多 ▼ (还有 " + hiddenCount + " 条)");
            }

            btnExpandCollapse.setOnClickListener(v -> {
                if (dateKey != null) {
                    toggleExpand(dateKey);
                }
            });
        }
    }

    /**
     * 从 Header 标题中查找 dateKey
     */
    private String findDateKeyFromHeader(String headerDate) {
        // 直接从 originalLogList 查找原始 dateKey
        for (LogEntry entry : originalLogList) {
            if (entry == null || entry.isHeader()) continue;

            String dKey = entry.getDateKey();
            if (dKey != null && DateUtils.formatDateHeader(dKey).equals(headerDate)) {
                return dKey;
            }
        }
        return null;
    }

    /**
     * 从展开按钮位置查找 dateKey
     */
    private String findDateKeyFromButtonPosition(int buttonPosition) {
        if (buttonPosition < 0 || buttonPosition >= displayList.size()) {
            return null;
        }
        LogEntry buttonEntry = displayList.get(buttonPosition);
        if (buttonEntry.isExpandButton()) {
            return buttonEntry.getDateKey();
        }
        return null;
    }

    /**
     * 获取某天的记录数量
     */
    private int getDayLogCount(String dateKey) {
        if (dateKey == null)
            return 0;
        int count = 0;
        for (LogEntry entry : originalLogList) {
            String entryDateKey = entry.getDateKey();
            // 先判空，避免 NPE
            if (entryDateKey != null && entryDateKey.equals(dateKey)) {
                count++;
            }
        }
        return count;
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvStartTime;
        TextView tvEndTime;
        TextView tvDuration;

        public ItemViewHolder(View itemView) {
            super(itemView);
            tvStartTime = itemView.findViewById(R.id.tv_start_time);
            tvEndTime = itemView.findViewById(R.id.tv_end_time);
            tvDuration = itemView.findViewById(R.id.tv_duration);
        }

        public void bind(LogEntry entry) {
            tvStartTime.setText(DateUtils.formatTime(entry.getStartTime()));
            tvEndTime.setText(DateUtils.formatTime(entry.getEndTime()));
            tvDuration.setText(DateUtils.formatDuration(entry.getDuration()));
        }
    }
}
