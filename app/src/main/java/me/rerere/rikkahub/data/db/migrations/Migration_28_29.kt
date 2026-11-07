package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_28_29"

/**
 * 三期新增「社交互动 + 心里话」两表:点赞/评论 + 心里话。
 * 手写迁移免 schema json;列名/索引名必须与 Room 从实体推断的完全一致。
 */
val Migration_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 28 to 29 (social_interactions + secrets)")
        DatabaseMigrationTracker.onMigrationStart(28, 29)
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `social_interactions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`target_type` TEXT NOT NULL, " +
                    "`target_id` INTEGER NOT NULL, " +
                    "`actor` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`content` TEXT, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`is_read` INTEGER NOT NULL DEFAULT 1)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_social_interactions_target_type_target_id` " +
                    "ON `social_interactions` (`target_type`, `target_id`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `secrets` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`content` TEXT NOT NULL, " +
                    "`visible` INTEGER NOT NULL DEFAULT 1, " +
                    "`conversation_id` TEXT, " +
                    "`created_at` INTEGER NOT NULL)"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 28 to 29 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
