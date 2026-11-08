package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_30_31"

/**
 * 纯增量两件事:
 * 1. social_interactions 加 reply_to 列(评论回复目标,顶层评论不显示「xx 回复 xx」)
 * 2. mood_checkins 去掉 (date,subject) 唯一索引 → 一天内可多次打卡,不再覆盖
 */
val Migration_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 30 to 31 (reply_to + mood multi-checkin)")
        DatabaseMigrationTracker.onMigrationStart(30, 31)
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `social_interactions` ADD COLUMN `reply_to` TEXT")
            db.execSQL("DROP INDEX IF EXISTS `index_mood_checkins_date_subject`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_mood_checkins_date_subject` " +
                    "ON `mood_checkins` (`date`, `subject`)"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 30 to 31 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
