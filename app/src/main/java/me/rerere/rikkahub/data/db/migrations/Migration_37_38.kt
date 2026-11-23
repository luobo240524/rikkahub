package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_37_38"

/**
 * 纯增量(「我的世界」三轮):
 * world_state 加 last_notified_at 列——用户在世界页做过的事(碎碎念/留灯/花园/宠物)
 * 下一轮对话注入告知助手时,用它记"已通知到哪",避免每轮重复播报同一批动作。
 */
val Migration_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 37 to 38 (world_state.last_notified_at)")
        DatabaseMigrationTracker.onMigrationStart(37, 38)
        db.beginTransaction()
        try {
            db.execSQL(
                "ALTER TABLE `world_state` ADD COLUMN `last_notified_at` INTEGER NOT NULL DEFAULT 0"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 37 to 38 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
