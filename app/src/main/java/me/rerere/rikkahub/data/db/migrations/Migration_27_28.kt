package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_27_28"

/**
 * 二期新增「发现」两表：每日心情打卡 + 朋友圈帖子。
 * 手写迁移免 schema json;列名/索引名必须与 Room 从实体推断的完全一致。
 */
val Migration_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 27 to 28 (mood_checkins + moments_posts)")
        DatabaseMigrationTracker.onMigrationStart(27, 28)
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `mood_checkins` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`date` TEXT NOT NULL, " +
                    "`subject` TEXT NOT NULL, " +
                    "`mood` TEXT NOT NULL, " +
                    "`phrase` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_checkins_date_subject` " +
                    "ON `mood_checkins` (`date`, `subject`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `moments_posts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`author` TEXT NOT NULL, " +
                    "`conversation_id` TEXT, " +
                    "`content` TEXT NOT NULL, " +
                    "`mood` TEXT, " +
                    "`created_at` INTEGER NOT NULL)"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 27 to 28 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
