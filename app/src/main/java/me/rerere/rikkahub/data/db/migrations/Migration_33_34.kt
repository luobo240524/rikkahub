package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_33_34"

/**
 * 纯增量:
 * 1. timeline_events 加 ref_id 列(外部引用键,通话摘要按 call_id 去重,每通电话只摘要一次)
 * 2. 新建 anniversaries 表(纪念日:公历月日+是否每年重复+备注,时间轴/唤醒/提示词共用)
 * 不碰任何旧表数据。
 */
val Migration_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 33 to 34 (ref_id + anniversaries)")
        DatabaseMigrationTracker.onMigrationStart(33, 34)
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `timeline_events` ADD COLUMN `ref_id` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `anniversaries` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`month` INTEGER NOT NULL, " +
                    "`day` INTEGER NOT NULL, " +
                    "`target_year` INTEGER, " +
                    "`repeat_yearly` INTEGER NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_anniversaries_month_day` ON `anniversaries` (`month`, `day`)"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 33 to 34 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
