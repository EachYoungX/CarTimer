package com.EachYoungX.timer.database;

/**
 * Small process-local boundary for database writes and raw database snapshots.
 */
public final class DatabaseIoLock {
    public static final Object WRITE_LOCK = new Object();

    private DatabaseIoLock() {
    }
}
