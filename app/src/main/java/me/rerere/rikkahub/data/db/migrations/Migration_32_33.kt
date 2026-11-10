package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_32_33"

/**
 * 纯增量:secrets/moments_posts/mood_checkins 三张表加 scheduled_at 定时生效列
 * (朋友圈/打卡/心里话可设定时间到点生效,NULL=立即生效)。
 * 不碰任何旧表数据;UI 层与读工具按 scheduled_at 过滤,到点自动出现。
 */
val Migration_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 32 to 33 (scheduled_at columns)")
        DatabaseMigrationTracker.onMigrationStart(32, 33)
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `secrets` ADD COLUMN `scheduled_at` INTEGER")
            db.execSQL("ALTER TABLE `moments_posts` ADD COLUMN `scheduled_at` INTEGER")
            db.execSQL("ALTER TABLE `mood_checkins` ADD COLUMN `scheduled_at` INTEGER")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 32 to 33 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
