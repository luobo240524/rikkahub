package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_39_40"

val Migration_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 39 to 40 (world_letters)")
        DatabaseMigrationTracker.onMigrationStart(39, 40)
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `world_letters` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `author` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `scheduled_at` INTEGER,
                    `notified_at` INTEGER,
                    `opened_at` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_letters_assistant_id_scheduled_at` ON `world_letters` (`assistant_id`, `scheduled_at`)")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 39 to 40 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
