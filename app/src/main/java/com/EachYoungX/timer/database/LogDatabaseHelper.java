package com.EachYoungX.timer.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.EachYoungX.timer.utils.DateUtils;
import com.EachYoungX.timer.models.LogEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "car_timer_logs.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_LOGS = "logs";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";
    public static final String COLUMN_DURATION = "duration";
    public static final String COLUMN_DATE_KEY = "date_key";
    public static final String COLUMN_WEEK_KEY = "week_key";
    public static final String COLUMN_MONTH_KEY = "month_key";

    private static final String CREATE_TABLE_LOGS = "CREATE TABLE " + TABLE_LOGS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_START_TIME + " INTEGER, " +
            COLUMN_END_TIME + " INTEGER, " +
            COLUMN_DURATION + " INTEGER, " +
            COLUMN_DATE_KEY + " TEXT, " +
            COLUMN_WEEK_KEY + " TEXT, " +
            COLUMN_MONTH_KEY + " TEXT" +
            ");";

    public LogDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_LOGS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_LOGS);
        onCreate(db);
    }

    public void insertLog(long startTime, long endTime, long duration) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_START_TIME, startTime);
        values.put(COLUMN_END_TIME, endTime);
        values.put(COLUMN_DURATION, duration);

        // 计算日期标识
        String dateKey = getDateKey(startTime);
        values.put(COLUMN_DATE_KEY, dateKey);

        // 计算周标识
        String weekKey = getWeekKey(startTime);
        values.put(COLUMN_WEEK_KEY, weekKey);

        // 计算月份标识
        String monthKey = getMonthKey(startTime);
        values.put(COLUMN_MONTH_KEY, monthKey);

        db.insert(TABLE_LOGS, null, values);
        db.close();
    }

    // 获取日期标识 (YYYY-MM-DD)
    private String getDateKey(long timestamp) {
        return DateUtils.getDateKey(timestamp);
    }

    // 获取周标识 (YYYY-Www)
    private String getWeekKey(long timestamp) {
        return DateUtils.getWeekKey(timestamp);
    }

    // 获取月份标识 (YYYY-MM)
    private String getMonthKey(long timestamp) {
        return DateUtils.getMonthKey(timestamp);
    }

    public List<LogEntry> getAllLogs() {
        List<LogEntry> logList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // 先检查表中有多少数据
        Cursor countCursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_LOGS, null);
        int count = 0;
        if (countCursor.moveToFirst()) {
            count = countCursor.getInt(0);
        }
        countCursor.close();
        android.util.Log.d("LogDatabaseHelper", "Total records in database: " + count);

        // 查询所有日志
        Cursor cursor = db.query(TABLE_LOGS, null, null, null, null, null, COLUMN_START_TIME + " DESC");
        android.util.Log.d("LogDatabaseHelper", "Query returned cursor with count: " + cursor.getCount());

        if (cursor.moveToFirst()) {
            do {
                LogEntry log = new LogEntry();
                log.setId(cursor.getLong(0));
                log.setStartTime(cursor.getLong(1));
                log.setEndTime(cursor.getLong(2));
                log.setDuration(cursor.getLong(3));
                log.setDateKey(cursor.getString(4));
                log.setWeekKey(cursor.getString(5));
                log.setMonthKey(cursor.getString(6));
                logList.add(log);
                android.util.Log.d("LogDatabaseHelper", "Loaded log: id=" + log.getId() + ", dateKey="
                        + log.getDateKey() + ", duration=" + log.getDuration());
            } while (cursor.moveToNext());
        } else {
            android.util.Log.d("LogDatabaseHelper", "Cursor is empty, no logs found");
        }
        cursor.close();
        db.close();
        android.util.Log.d("LogDatabaseHelper", "Returning " + logList.size() + " logs");
        return logList;
    }

    public List<LogEntry> getLogsByDate(String dateKey) {
        List<LogEntry> logList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOGS, null, COLUMN_DATE_KEY + "=?", new String[] { dateKey }, null, null,
                COLUMN_START_TIME + " DESC");

        if (cursor.moveToFirst()) {
            do {
                LogEntry log = new LogEntry();
                log.setId(cursor.getLong(0));
                log.setStartTime(cursor.getLong(1));
                log.setEndTime(cursor.getLong(2));
                log.setDuration(cursor.getLong(3));
                log.setDateKey(cursor.getString(4));
                log.setWeekKey(cursor.getString(5));
                log.setMonthKey(cursor.getString(6));
                logList.add(log);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return logList;
    }

    /**
     * 检查指定日期是否有日志记录
     *
     * @param dateKey 日期键（格式：YYYY-MM-DD）
     * @return true 如果有记录，false 如果没有记录
     */
    public boolean hasLogsOnDate(String dateKey) {
        android.util.Log.d("LogDatabaseHelper", "hasLogsOnDate: START - dateKey=" + dateKey);

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            // 只查询第一条记录，提高效率
            cursor = db.query(TABLE_LOGS, new String[] { COLUMN_ID }, COLUMN_DATE_KEY + "=?",
                    new String[] { dateKey }, null, null, null);

            boolean hasLogs = cursor != null && cursor.moveToFirst();
            android.util.Log.d("LogDatabaseHelper",
                    "hasLogsOnDate: cursor count=" + (cursor != null ? cursor.getCount() : 0) + ", hasLogs=" + hasLogs);

            return hasLogs;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
            android.util.Log.d("LogDatabaseHelper", "hasLogsOnDate: cursor and db closed");
        }
    }

    // 按月份查询日志
    public List<LogEntry> getLogsByMonth(String monthKey) {
        List<LogEntry> logList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOGS, null, COLUMN_MONTH_KEY + "=?", new String[] { monthKey }, null, null,
                COLUMN_START_TIME + " DESC");

        if (cursor.moveToFirst()) {
            do {
                LogEntry log = new LogEntry();
                log.setId(cursor.getLong(0));
                log.setStartTime(cursor.getLong(1));
                log.setEndTime(cursor.getLong(2));
                log.setDuration(cursor.getLong(3));
                log.setDateKey(cursor.getString(4));
                log.setWeekKey(cursor.getString(5));
                log.setMonthKey(cursor.getString(6));
                logList.add(log);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return logList;
    }

    public List<LogEntry> getLogsByYearMonth(String monthKey) {
        List<LogEntry> logList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOGS, null, COLUMN_MONTH_KEY + "=?", new String[] { monthKey }, null, null,
                COLUMN_START_TIME + " DESC");

        if (cursor.moveToFirst()) {
            do {
                LogEntry log = new LogEntry();
                log.setId(cursor.getLong(0));
                log.setStartTime(cursor.getLong(1));
                log.setEndTime(cursor.getLong(2));
                log.setDuration(cursor.getLong(3));
                log.setDateKey(cursor.getString(4));
                log.setWeekKey(cursor.getString(5));
                log.setMonthKey(cursor.getString(6));
                logList.add(log);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return logList;
    }

    public List<LogEntry> getLogsByYear(String year) {
        List<LogEntry> logList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOGS, null, COLUMN_MONTH_KEY + " LIKE ?", new String[] { year + "-%" }, null,
                null, COLUMN_START_TIME + " DESC");

        if (cursor.moveToFirst()) {
            do {
                LogEntry log = new LogEntry();
                log.setId(cursor.getLong(0));
                log.setStartTime(cursor.getLong(1));
                log.setEndTime(cursor.getLong(2));
                log.setDuration(cursor.getLong(3));
                log.setDateKey(cursor.getString(4));
                log.setWeekKey(cursor.getString(5));
                log.setMonthKey(cursor.getString(6));
                logList.add(log);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return logList;
    }

    public long getTotalRunningTimeByDate(String dateKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT SUM(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS + " WHERE " + COLUMN_DATE_KEY + "=?",
                new String[] { dateKey });
        long totalTime = 0;
        if (cursor.moveToFirst()) {
            totalTime = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        return totalTime;
    }

    public long getTotalRunningTimeByYearMonth(String monthKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT SUM(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS + " WHERE " + COLUMN_MONTH_KEY + "=?",
                new String[] { monthKey });
        long totalTime = 0;
        if (cursor.moveToFirst()) {
            totalTime = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        return totalTime;
    }

    public long getTotalRunningTimeByYear(String year) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT SUM(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS + " WHERE " + COLUMN_MONTH_KEY + " LIKE ?",
                new String[] { year + "-%" });
        long totalTime = 0;
        if (cursor.moveToFirst()) {
            totalTime = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        return totalTime;
    }

    public long getTotalRunningTimeByDateRange(String startDateKey, String endDateKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS + " WHERE "
                + COLUMN_DATE_KEY + " >= ? AND " + COLUMN_DATE_KEY + " <= ?",
                new String[] { startDateKey, endDateKey });
        long totalTime = 0;
        if (cursor.moveToFirst()) {
            totalTime = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        return totalTime;
    }

    public long getAverageRunningTimeByDateRange(String startDateKey, String endDateKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT AVG(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS + " WHERE "
                + COLUMN_DATE_KEY + " >= ? AND " + COLUMN_DATE_KEY + " <= ?",
                new String[] { startDateKey, endDateKey });
        long avgTime = 0;
        if (cursor.moveToFirst()) {
            avgTime = (long) cursor.getDouble(0);
        }
        cursor.close();
        db.close();
        return avgTime;
    }

    public long getMaxRunningTimeByDateRange(String startDateKey, String endDateKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS + " WHERE "
                + COLUMN_DATE_KEY + " >= ? AND " + COLUMN_DATE_KEY + " <= ?",
                new String[] { startDateKey, endDateKey });
        long maxTime = 0;
        if (cursor.moveToFirst()) {
            maxTime = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        return maxTime;
    }

    public List<String> getDistinctDateKeys() {
        List<String> dateKeys = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT " + COLUMN_DATE_KEY + " FROM " + TABLE_LOGS + " ORDER BY " + COLUMN_DATE_KEY + " DESC",
                null);
        if (cursor.moveToFirst()) {
            do {
                dateKeys.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return dateKeys;
    }

    public List<String> getDistinctMonthKeys() {
        List<String> monthKeys = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + COLUMN_MONTH_KEY + " FROM " + TABLE_LOGS + " ORDER BY "
                + COLUMN_MONTH_KEY + " DESC", null);
        if (cursor.moveToFirst()) {
            do {
                monthKeys.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return monthKeys;
    }

    public List<String> getDistinctYearKeys() {
        List<String> yearKeys = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT DISTINCT SUBSTR(" + COLUMN_MONTH_KEY + ", 1, 4) AS year FROM " + TABLE_LOGS
                + " ORDER BY year DESC", null);
        if (cursor.moveToFirst()) {
            do {
                yearKeys.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return yearKeys;
    }

    public long getTotalRunningTimeByWeek(String weekKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT SUM(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS + " WHERE " + COLUMN_WEEK_KEY + "=?",
                new String[] { weekKey });
        long totalTime = 0;
        if (cursor.moveToFirst()) {
            totalTime = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        return totalTime;
    }

    // 获取指定年月的所有周标识
    public List<String> getWeekKeysByMonth(String monthKey) {
        List<String> weekKeys = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + COLUMN_WEEK_KEY + " FROM " + TABLE_LOGS + " WHERE "
                + COLUMN_MONTH_KEY + "=? ORDER BY " + COLUMN_WEEK_KEY, new String[] { monthKey });
        if (cursor.moveToFirst()) {
            do {
                weekKeys.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return weekKeys;
    }

    // ========== 统计查询方法 ==========

    // 获取指定年份各月的总时长（用于月度趋势图）
    public Map<String, Long> getMonthlyStats(String year) {
        Map<String, Long> stats = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_MONTH_KEY + ", SUM(" + COLUMN_DURATION + ") as total " +
                        "FROM " + TABLE_LOGS +
                        " WHERE " + COLUMN_MONTH_KEY + " LIKE ?" +
                        " GROUP BY " + COLUMN_MONTH_KEY +
                        " ORDER BY " + COLUMN_MONTH_KEY,
                new String[] { year + "-%" });
        if (cursor.moveToFirst()) {
            do {
                String monthKey = cursor.getString(0);
                long total = cursor.getLong(1);
                stats.put(monthKey, total);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return stats;
    }

    // 获取指定月份每周的总时长（用于周度分布图）
    public Map<String, Long> getWeeklyStats(String monthKey) {
        Map<String, Long> stats = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_WEEK_KEY + ", SUM(" + COLUMN_DURATION + ") as total " +
                        "FROM " + TABLE_LOGS +
                        " WHERE " + COLUMN_MONTH_KEY + " = ?" +
                        " GROUP BY " + COLUMN_WEEK_KEY +
                        " ORDER BY " + COLUMN_WEEK_KEY,
                new String[] { monthKey });
        if (cursor.moveToFirst()) {
            do {
                String weekKey = cursor.getString(0);
                long total = cursor.getLong(1);
                stats.put(weekKey, total);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return stats;
    }

    // 获取指定月份每天的总时长（用于日度分布图）
    public Map<String, Long> getDailyStats(String monthKey) {
        Map<String, Long> stats = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_DATE_KEY + ", SUM(" + COLUMN_DURATION + ") as total " +
                        "FROM " + TABLE_LOGS +
                        " WHERE " + COLUMN_MONTH_KEY + " = ?" +
                        " GROUP BY " + COLUMN_DATE_KEY +
                        " ORDER BY " + COLUMN_DATE_KEY,
                new String[] { monthKey });
        if (cursor.moveToFirst()) {
            do {
                String dateKey = cursor.getString(0);
                long total = cursor.getLong(1);
                stats.put(dateKey, total);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return stats;
    }

    // 获取某一天的总时长
    public long getDayTotalDuration(String dateKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT SUM(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS +
                        " WHERE " + COLUMN_DATE_KEY + " = ?",
                new String[] { dateKey });
        long total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getLong(0);
        }
        cursor.close();
        db.close();
        return total;
    }

    // 获取自定义区间的详细统计
    public Map<String, Object> getCustomRangeStats(String startDateKey, String endDateKey) {
        Map<String, Object> stats = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // 总时长
        Cursor cursor1 = db.rawQuery(
                "SELECT SUM(" + COLUMN_DURATION + ") FROM " + TABLE_LOGS +
                        " WHERE " + COLUMN_DATE_KEY + " >= ? AND " + COLUMN_DATE_KEY + " <= ?",
                new String[] { startDateKey, endDateKey });
        long totalDuration = 0;
        if (cursor1.moveToFirst()) {
            totalDuration = cursor1.getLong(0);
        }
        cursor1.close();

        // 总记录数
        Cursor cursor2 = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_LOGS +
                        " WHERE " + COLUMN_DATE_KEY + " >= ? AND " + COLUMN_DATE_KEY + " <= ?",
                new String[] { startDateKey, endDateKey });
        int count = 0;
        if (cursor2.moveToFirst()) {
            count = cursor2.getInt(0);
        }
        cursor2.close();

        // 平均时长
        long avgDuration = count > 0 ? totalDuration / count : 0;

        // 最长单次记录
        Cursor cursor3 = db.rawQuery(
                "SELECT MAX(" + COLUMN_DURATION + "), " + COLUMN_DATE_KEY + ", " + COLUMN_START_TIME +
                        " FROM " + TABLE_LOGS +
                        " WHERE " + COLUMN_DATE_KEY + " >= ? AND " + COLUMN_DATE_KEY + " <= ?",
                new String[] { startDateKey, endDateKey });
        long maxDuration = 0;
        String maxDate = "";
        long maxStartTime = 0;
        if (cursor3.moveToFirst()) {
            maxDuration = cursor3.getLong(0);
            maxDate = cursor3.getString(1);
            maxStartTime = cursor3.getLong(2);
        }
        cursor3.close();

        stats.put("totalDuration", totalDuration);
        stats.put("count", count);
        stats.put("avgDuration", avgDuration);
        stats.put("maxDuration", maxDuration);
        stats.put("maxDate", maxDate);
        stats.put("maxStartTime", maxStartTime);

        db.close();
        return stats;
    }

    // 获取最近 N 条记录
    public List<LogEntry> getRecentLogs(int limit) {
        List<LogEntry> logList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_LOGS, null, null, null, null, null,
                COLUMN_START_TIME + " DESC", String.valueOf(limit));

        if (cursor.moveToFirst()) {
            do {
                LogEntry log = new LogEntry();
                log.setId(cursor.getLong(0));
                log.setStartTime(cursor.getLong(1));
                log.setEndTime(cursor.getLong(2));
                log.setDuration(cursor.getLong(3));
                log.setDateKey(cursor.getString(4));
                log.setWeekKey(cursor.getString(5));
                log.setMonthKey(cursor.getString(6));
                logList.add(log);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return logList;
    }

    // 检查是否还有更多数据
    public boolean hasMoreLogs(int offset, int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_LOGS + " LIMIT ? OFFSET ?",
                new String[] { String.valueOf(limit), String.valueOf(offset) });
        boolean hasMore = false;
        if (cursor.moveToFirst()) {
            hasMore = cursor.getInt(0) > 0;
        }
        cursor.close();
        db.close();
        return hasMore;
    }
}
