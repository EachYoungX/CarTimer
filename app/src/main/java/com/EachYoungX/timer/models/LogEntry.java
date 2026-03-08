package com.EachYoungX.timer.models;

public class LogEntry {
    private long id;
    private long startTime; // 开始时间戳
    private long endTime; // 结束时间戳
    private long duration; // 持续时长（毫秒）
    private String dateKey; // 日期标识 (YYYY-MM-DD)
    private String weekKey; // 周标识 (YYYY-Www)
    private String monthKey; // 月份标识 (YYYY-MM)

    // Header 和 Footer 支持
    private boolean isHeader = false;
    private boolean isFooter = false;
    private boolean isExpandButton = false; // 展开/收起按钮
    private String headerDate;
    private String headerTotal;
    private String footerText;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getDateKey() {
        return dateKey;
    }

    public void setDateKey(String dateKey) {
        this.dateKey = dateKey;
    }

    public String getWeekKey() {
        return weekKey;
    }

    public void setWeekKey(String weekKey) {
        this.weekKey = weekKey;
    }

    public String getMonthKey() {
        return monthKey;
    }

    public void setMonthKey(String monthKey) {
        this.monthKey = monthKey;
    }

    // Header 和 Footer 方法
    public boolean isHeader() {
        return isHeader;
    }

    public void setHeader(boolean header) {
        isHeader = header;
    }

    public boolean isFooter() {
        return isFooter;
    }

    public void setFooter(boolean footer) {
        isFooter = footer;
    }

    public String getHeaderDate() {
        return headerDate;
    }

    public void setHeaderDate(String headerDate) {
        this.headerDate = headerDate;
    }

    public String getHeaderTotal() {
        return headerTotal;
    }

    public void setHeaderTotal(String headerTotal) {
        this.headerTotal = headerTotal;
    }

    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String footerText) {
        this.footerText = footerText;
    }

    // 展开/收起按钮方法
    public boolean isExpandButton() {
        return isExpandButton;
    }

    public void setExpandButton(boolean expandButton) {
        isExpandButton = expandButton;
    }

    // 创建 Header 条目的静态方法
    public static LogEntry createHeader(String date, String total) {
        LogEntry entry = new LogEntry();
        entry.setHeader(true);
        entry.setHeaderDate(date);
        entry.setHeaderTotal(total);
        return entry;
    }

    // 创建 Footer 条目的静态方法
    public static LogEntry createFooter(String text) {
        LogEntry entry = new LogEntry();
        entry.setFooter(true);
        entry.setFooterText(text);
        return entry;
    }

    // 创建展开/收起按钮条目的静态方法
    public static LogEntry createExpandButton() {
        LogEntry entry = new LogEntry();
        entry.setExpandButton(true);
        return entry;
    }
}
