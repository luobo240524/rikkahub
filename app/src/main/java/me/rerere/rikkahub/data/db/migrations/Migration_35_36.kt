package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_35_36"

/**
 * 纯增量(生命体征 Dominance 支配感轴):
 * vitals_state 加 dominance 列(-1 被压着 ~ +1 掌控局面),
 * 由自评词典 d 坐标驱动,投影到「安全感/自信」两条进度条。
 * safety/confidence 旧列保留不删(兼容旧 schema,新 UI 改由 dominance 实时投影)。
 */
val Migration_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 35 to 36 (vitals_state.dominance)")
        DatabaseMigrationTracker.onMigrationStart(35, 36)
        db.beginTransaction()
        try {
            db.execSQL(
                "ALTER TABLE `vitals_state` ADD COLUMN `dominance` REAL NOT NULL DEFAULT 0"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 35 to 36 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
