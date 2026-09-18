package com.EachYoungX.timer.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.EachYoungX.timer.database.DatabaseIoLock;
import com.EachYoungX.timer.database.LogDatabaseHelper;
import com.EachYoungX.timer.models.LogEntry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v2.1.1 data safety boundary. The database remains the source of truth;
 * public Download files are recovery snapshots that survive uninstall.
 */
public final class BackupManager {
    public static final String CSV_FILE_NAME = "CarTimer_latest.csv";
    public static final String DB_FILE_NAME = "CarTimer_latest.db";
    public static final String BACKUP_RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS
            + "/CarTimer/backup/";
    private static final String CSV_HEADER = "date_key,start_time,end_time,duration,week_key,month_key";

    private BackupManager() {
    }

    public interface BackupCallback {
        void onComplete(BackupResult result);
    }

    public static final class BackupResult {
        public final boolean csvPublished;
        public final boolean dbPublished;
        public final int recordCount;
        public final String error;

        BackupResult(boolean csvPublished, boolean dbPublished, int recordCount, String error) {
            this.csvPublished = csvPublished;
            this.dbPublished = dbPublished;
            this.recordCount = recordCount;
            this.error = error;
        }

        public boolean isSuccess() {
            return csvPublished && dbPublished;
        }
    }

    public static final class LatestBackup {
        public final Uri uri;
        public final File file;
        public final int recordCount;
        public final long modifiedAt;

        LatestBackup(Uri uri, File file, int recordCount, long modifiedAt) {
            this.uri = uri;
            this.file = file;
            this.recordCount = recordCount;
            this.modifiedAt = modifiedAt;
        }
    }

    public static final class RestoreResult {
        public final int importedCount;
        public final int skippedCount;
        public final int totalCount;

        RestoreResult(int importedCount, int skippedCount, int totalCount) {
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
            this.totalCount = totalCount;
        }
    }

    private interface PublishedFileValidator {
        void validate(InputStream input) throws IOException;
    }

    public static void backupLatestAsync(Context context, BackupCallback callback) {
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            BackupResult result = backupLatest(appContext);
            if (callback != null && context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> callback.onComplete(result));
            }
        }, "CarTimer-Backup").start();
    }

    public static BackupResult backupLatest(Context context) {
        boolean csvPublished = false;
        boolean dbPublished = false;
        int recordCount = 0;
        String error = null;
        File csvTemp = null;
        File dbTemp = null;
        LogDatabaseHelper helper = new LogDatabaseHelper(context.getApplicationContext());

        synchronized (DatabaseIoLock.WRITE_LOCK) {
            try {
                List<LogEntry> logs = helper.getAllLogs();
                recordCount = logs.size();
                File tempDir = new File(context.getCacheDir(), "car_timer_backup");
                if (!tempDir.exists() && !tempDir.mkdirs()) {
                    throw new IOException("无法创建备份临时目录");
                }

                csvTemp = new File(tempDir, CSV_FILE_NAME + ".tmp");
                writeText(csvTemp, buildCsv(logs));
                final int expectedCount = recordCount;
                validateFile(csvTemp, input -> {
                    List<List<String>> rows = parseCsv(input);
                    validateRows(rows);
                    if (rows.size() - 1 != expectedCount) {
                        throw new IOException("CSV 记录数不一致");
                    }
                });
                publishFile(context, csvTemp, CSV_FILE_NAME, "text/csv", input -> {
                    List<List<String>> rows = parseCsv(input);
                    validateRows(rows);
                    if (rows.size() - 1 != expectedCount) {
                        throw new IOException("CSV 记录数不一致");
                    }
                });
                csvPublished = true;

                final int finalRecordCount = recordCount;
                dbTemp = createDatabaseSnapshot(context, helper, finalRecordCount);
                publishFile(context, dbTemp, DB_FILE_NAME, "application/octet-stream",
                        input -> validateDatabaseFile(context, input, finalRecordCount));
                dbPublished = true;
            } catch (Exception e) {
                error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            } finally {
                helper.close();
            }
        }

        deleteQuietly(csvTemp);
        deleteQuietly(dbTemp);
        return new BackupResult(csvPublished, dbPublished, recordCount, error);
    }

    public static int countLocalLogs(Context context) {
        LogDatabaseHelper helper = new LogDatabaseHelper(context.getApplicationContext());
        synchronized (DatabaseIoLock.WRITE_LOCK) {
            try (Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM logs", null)) {
                return cursor.moveToFirst() ? cursor.getInt(0) : 0;
            } finally {
                helper.close();
            }
        }
    }

    public static LatestBackup findLatestCsv(Context context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();
                String[] projection = {MediaStore.Downloads._ID, MediaStore.Downloads.DATE_MODIFIED};
                try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                        MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                                + MediaStore.Downloads.RELATIVE_PATH + "=?",
                        new String[]{CSV_FILE_NAME, BACKUP_RELATIVE_PATH},
                        MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
                    if (cursor != null && cursor.moveToFirst()) {
                        Uri uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)));
                        int count = countCsvRecords(context, uri, null);
                        if (count > 0) {
                            long modified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)) * 1000L;
                            return new LatestBackup(uri, null, count, modified);
                        }
                    }
                }
            } else {
                File file = new File(new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "CarTimer/backup"), CSV_FILE_NAME);
                if (file.isFile() && file.length() > 0) {
                    int count = countCsvRecords(context, null, file);
                    if (count > 0) {
                        return new LatestBackup(null, file, count, file.lastModified());
                    }
                }
            }
        } catch (Exception ignored) {
            // Backup discovery must never prevent normal application startup.
        }
        return null;
    }

    public static RestoreResult restoreCsv(Context context, LatestBackup backup) throws Exception {
        List<List<String>> rows = openAndParse(context, backup);
        validateRows(rows);
        List<List<String>> records = rows.subList(1, rows.size());
        int imported = 0;
        int skipped = 0;
        LogDatabaseHelper helper = new LogDatabaseHelper(context.getApplicationContext());
        synchronized (DatabaseIoLock.WRITE_LOCK) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                for (List<String> row : records) {
                    long startTime = Long.parseLong(row.get(1));
                    try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM logs WHERE start_time=?",
                            new String[]{String.valueOf(startTime)})) {
                        if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                            skipped++;
                            continue;
                        }
                    }
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(LogDatabaseHelper.COLUMN_DATE_KEY, row.get(0));
                    values.put(LogDatabaseHelper.COLUMN_START_TIME, startTime);
                    values.put(LogDatabaseHelper.COLUMN_END_TIME, Long.parseLong(row.get(2)));
                    values.put(LogDatabaseHelper.COLUMN_DURATION, Long.parseLong(row.get(3)));
                    values.put(LogDatabaseHelper.COLUMN_WEEK_KEY, row.get(4));
                    values.put(LogDatabaseHelper.COLUMN_MONTH_KEY, row.get(5));
                    if (db.insertOrThrow(LogDatabaseHelper.TABLE_LOGS, null, values) != -1) {
                        imported++;
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
                helper.close();
            }
        }
        return new RestoreResult(imported, skipped, countLocalLogs(context));
    }

    /**
     * Restore a user-selected CSV using the same strict parser and transaction
     * as the automatic startup recovery flow.
     */
    public static RestoreResult restoreCsv(Context context, Uri uri) throws Exception {
        return restoreCsv(context, new LatestBackup(uri, null, 0, 0));
    }

    public static String formatModifiedTime(long modifiedAt) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(modifiedAt));
    }

    private static File createDatabaseSnapshot(Context context, LogDatabaseHelper helper, int expectedCount)
            throws Exception {
        SQLiteDatabase db = helper.getWritableDatabase();
        String integrity = scalar(db, "PRAGMA integrity_check");
        if (!"ok".equalsIgnoreCase(integrity)) {
            throw new IOException("SQLite integrity_check 失败: " + integrity);
        }
        if ("wal".equalsIgnoreCase(scalar(db, "PRAGMA journal_mode"))) {
            try (Cursor cursor = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
                if (!cursor.moveToFirst() || cursor.getInt(0) != 0) {
                    throw new IOException("SQLite WAL checkpoint 失败");
                }
            }
        }
        File source = context.getDatabasePath("car_timer_logs.db");
        if (!source.isFile() || source.length() <= 0) {
            throw new IOException("数据库文件不存在");
        }

        helper.close();
        File target = new File(new File(context.getCacheDir(), "car_timer_backup"), DB_FILE_NAME + ".tmp");
        copyFile(source, target);
        if (target.length() != source.length()) {
            throw new IOException("数据库快照大小校验失败");
        }
        SQLiteDatabase snapshot = SQLiteDatabase.openDatabase(target.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            String snapshotIntegrity = scalar(snapshot, "PRAGMA integrity_check");
            if (!"ok".equalsIgnoreCase(snapshotIntegrity)) {
                throw new IOException("数据库快照完整性校验失败");
            }
            int snapshotCount = countLogs(snapshot);
            if (snapshotCount != expectedCount) {
                throw new IOException("数据库快照记录数校验失败");
            }
        } finally {
            snapshot.close();
        }
        return target;
    }

    private static void publishFile(Context context, File source, String fileName, String mimeType,
            PublishedFileValidator validator) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            String stagedName = fileName + ".tmp";
            deletePublicFile(context, stagedName);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, stagedName);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, BACKUP_RELATIVE_PATH);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri staged = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (staged == null) {
                throw new IOException("无法创建公共备份文件");
            }
            try {
                try (OutputStream output = resolver.openOutputStream(staged, "w");
                        InputStream input = new FileInputStream(source)) {
                    if (output == null) {
                        throw new IOException("无法写入公共备份文件");
                    }
                    copyStream(input, output);
                }
                try (InputStream input = resolver.openInputStream(staged)) {
                    if (input == null) {
                        throw new IOException("无法重新读取公共备份文件");
                    }
                    validator.validate(input);
                }
                deletePublicFile(context, fileName);
                ContentValues publishValues = new ContentValues();
                publishValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                publishValues.put(MediaStore.Downloads.IS_PENDING, 0);
                if (resolver.update(staged, publishValues, null, null) != 1) {
                    throw new IOException("无法发布公共备份文件");
                }
            } catch (Exception e) {
                resolver.delete(staged, null, null);
                throw e;
            }
        } else {
            File dir = new File(new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "CarTimer"), "backup");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建公共备份目录");
            }
            File staged = new File(dir, fileName + ".tmp");
            File target = new File(dir, fileName);
            copyFile(source, staged);
            try (InputStream input = new FileInputStream(staged)) {
                validator.validate(input);
            }
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deletePublicFile(Context context, String fileName) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
                    new String[]{fileName, BACKUP_RELATIVE_PATH}, null)) {
                if (cursor != null) {
                    int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
                    while (cursor.moveToNext()) {
                        Uri uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                cursor.getString(idIndex));
                        resolver.delete(uri, null, null);
                    }
                }
            } catch (Exception ignored) {
                // A missing old backup is safe; the new snapshot will still be attempted.
            }
        }
    }

    private static int countCsvRecords(Context context, Uri uri, File file) throws Exception {
        List<List<String>> rows;
        if (file != null) {
            try (InputStream input = new FileInputStream(file)) {
                rows = parseCsv(input);
            }
        } else {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("无法打开 CSV 备份");
                }
                rows = parseCsv(input);
            }
        }
        validateRows(rows);
        return rows.size() - 1;
    }

    private static List<List<String>> openAndParse(Context context, LatestBackup backup) throws Exception {
        if (backup.file != null) {
            try (InputStream input = new FileInputStream(backup.file)) {
                return parseCsv(input);
            }
        }
        try (InputStream input = context.getContentResolver().openInputStream(backup.uri)) {
            if (input == null) {
                throw new IOException("无法打开 CSV 备份");
            }
            return parseCsv(input);
        }
    }

    private static void validateFile(File file, PublishedFileValidator validator) throws Exception {
        if (!file.isFile() || file.length() <= 0) {
            throw new IOException("备份文件为空");
        }
        try (InputStream input = new FileInputStream(file)) {
            validator.validate(input);
        }
    }

    private static void validateRows(List<List<String>> rows) throws IOException {
        if (rows.isEmpty() || rows.get(0).size() != 6
                || !CSV_HEADER.equals(String.join(",", rows.get(0)))) {
            throw new IOException("CSV 表头不匹配");
        }
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() != 6) {
                throw new IOException("CSV 第 " + (i + 1) + " 行格式错误");
            }
            try {
                Long.parseLong(row.get(1));
                Long.parseLong(row.get(2));
                Long.parseLong(row.get(3));
            } catch (NumberFormatException e) {
                throw new IOException("CSV 第 " + (i + 1) + " 行时间字段错误");
            }
        }
    }

    private static List<List<String>> parseCsv(InputStream input) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            boolean sawAny = false;
            int value;
            while ((value = reader.read()) != -1) {
                char c = (char) value;
                sawAny = true;
                if (quoted) {
                    if (c == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append(c);
                    }
                } else if (c == '"' && field.length() == 0) {
                    quoted = true;
                } else if (c == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n') {
                    row.add(field.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    field.setLength(0);
                    sawAny = false;
                } else if (c != '\r') {
                    field.append(c);
                }
            }
            if (quoted) {
                throw new IOException("CSV 引号未闭合");
            }
            if (sawAny || !row.isEmpty() || field.length() > 0) {
                row.add(field.toString());
                rows.add(row);
            }
        }
        if (!rows.isEmpty() && !rows.get(0).isEmpty() && rows.get(0).get(0).startsWith("\ufeff")) {
            rows.get(0).set(0, rows.get(0).get(0).substring(1));
        }
        return rows;
    }

    private static String buildCsv(List<LogEntry> logs) {
        StringBuilder csv = new StringBuilder("\ufeff").append(CSV_HEADER).append('\n');
        for (LogEntry log : logs) {
            csv.append(csvValue(log.getDateKey())).append(',')
                    .append(log.getStartTime()).append(',')
                    .append(log.getEndTime()).append(',')
                    .append(log.getDuration()).append(',')
                    .append(csvValue(log.getWeekKey())).append(',')
                    .append(csvValue(log.getMonthKey())).append('\n');
        }
        return csv.toString();
    }

    private static String csvValue(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
                ? "\"" + escaped + "\"" : escaped;
    }

    private static void writeText(File file, String content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(target)) {
            copyStream(input, output);
        }
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static void validateDatabaseFile(Context context, InputStream input, int expectedCount)
            throws IOException {
        File temp = null;
        try {
            File dir = new File(context.getCacheDir(), "cartimer-db-verify");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建数据库校验目录");
            }
            temp = File.createTempFile("backup-", ".db", dir);
            try (OutputStream output = new FileOutputStream(temp)) {
                copyStream(input, output);
            }
            SQLiteDatabase db = SQLiteDatabase.openDatabase(temp.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try {
                if (!"ok".equalsIgnoreCase(scalar(db, "PRAGMA integrity_check"))
                        || countLogs(db) != expectedCount) {
                    throw new IOException("数据库公共备份校验失败");
                }
            } finally {
                db.close();
            }
        } finally {
            deleteQuietly(temp);
        }
    }

    private static int countLogs(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM logs", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static String scalar(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
