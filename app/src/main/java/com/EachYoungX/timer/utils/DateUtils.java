package com.EachYoungX.timer.utils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    // 格式化时间戳为日期字符串 (YYYY-MM-DD)
    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // 格式化时间戳为时间字符串 (HH:mm:ss)
    public static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // 格式化时间戳为完整日期时间字符串
    public static String formatDateTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // 格式化时长为可读字符串
    public static String formatDuration(long duration) {
        long hours = duration / (1000 * 60 * 60);
        long minutes = (duration % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (duration % (1000 * 60)) / 1000;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    // 获取日期标识 (YYYY-MM-DD)
    public static String getDateKey(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // 获取月份标识 (YYYY-MM)
    public static String getMonthKey(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // 获取周标识 (YYYY-Www)，遵循ISO 8601标准
    public static String getWeekKey(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        // 设置周一为一周的开始
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        int year = calendar.get(Calendar.YEAR);
        int week = calendar.get(Calendar.WEEK_OF_YEAR);
        return String.format(Locale.getDefault(), "%04d-W%02d", year, week);
    }

    // 获取指定日期的周一日期
    public static Date getMondayOfWeek(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return calendar.getTime();
    }

    // 获取指定年月的第一天
    public static String getFirstDayOfMonth(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    // 获取指定年月的最后一天
    public static String getLastDayOfMonth(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month - 1, 1);
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(calendar.getTime());
    }

    // 解析日期字符串为时间戳
    public static long parseDate(String dateString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateString);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 解析年月字符串为时间戳
    public static long parseMonth(String monthString) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
            Date date = sdf.parse(monthString);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    // 格式化日期为 Header 显示格式
    public static String formatDateHeader(String dateKey) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateKey);
            SimpleDateFormat outputSdf = new SimpleDateFormat("yyyy 年 M 月 d 日", Locale.getDefault());
            return outputSdf.format(date);
        } catch (Exception e) {
            return dateKey;
        }
    }
    
    /**
     * 周日期范围计算结果
     */
    public static class WeekDateRange {
        private final long startTime;
        private final long endTime;
        private final LocalDate startDate;
        private final LocalDate endDate;
        
        public WeekDateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.startTime = startDate.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            this.endTime = endDate.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public long getEndTime() {
            return endTime;
        }
        
        public LocalDate getStartDate() {
            return startDate;
        }
        
        public LocalDate getEndDate() {
            return endDate;
        }
    }
    
    /**
     * 计算某年某月第 N 周的日期范围（ISO 8601 标准，0-based 索引）
     * @param year 年份
     * @param month 月份（1-12）
     * @param weekIndex 周索引（0-based，0 表示第 1 周）
     * @return 周日期范围
     */
    public static WeekDateRange getWeekDateRange(int year, int month, int weekIndex) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        
        // 使用 ISO 8601 标准：周一为一周的开始
        WeekFields weekFields = WeekFields.of(Locale.CHINA);
        
        // 找到该月第一个周一
        LocalDate firstMonday = firstDayOfMonth.with(
            java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        
        // 计算目标周的周一（0-based 索引）
        LocalDate targetMonday = firstMonday.plusWeeks(weekIndex);
        
        // 目标周的周日
        LocalDate targetSunday = targetMonday.plusDays(6);
        
        return new WeekDateRange(targetMonday, targetSunday);
    }
    
    /**
     * 计算某一天属于该月的第几周（0-based 索引）
     * @param year 年份
     * @param month 月份（1-12）
     * @param day 日期
     * @return 周索引（0-based）
     */
    public static int getWeekIndexOfDate(int year, int month, int day) {
        LocalDate date = LocalDate.of(year, month, day);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        
        // 找到该月第一个周一
        LocalDate firstMonday = firstDayOfMonth.with(
            java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        
        // 计算日期差
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, date);
        
        // 如果日期在第一个周一之前，属于第 0 周
        if (daysDiff < 0) {
            return 0;
        }
        
        // 计算周索引（0-based）
        return (int) (daysDiff / 7);
    }
}