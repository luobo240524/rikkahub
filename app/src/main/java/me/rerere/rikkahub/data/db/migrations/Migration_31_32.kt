package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_31_32"

/**
 * 纯增量:新建 timeline_events 表(四期时间轴:自动提炼/手动记录/节点提醒)。
 * 不碰任何旧表数据;串联动态由页面层聚合 moments/checkins/secrets,不需要外键。
 */
val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 31 to 32 (timeline_events)")
        DatabaseMigrationTracker.onMigrationStart(31, 32)
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `timeline_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`event_date` TEXT NOT NULL, " +
                    "`event_time` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`author` TEXT NOT NULL, " +
                    "`remind_at` INTEGER, " +
                    "`remind_fired` INTEGER NOT NULL, " +
                    "`conversation_id` TEXT, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_timeline_events_event_date` " +
                    "ON `timeline_events` (`event_date`)"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 31 to 32 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
