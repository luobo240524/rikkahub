package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_29_30"

/**
 * secrets 表加 author 列:让助手也能写心里话(user/assistant 区分作者)。
 * ALTER TABLE ADD COLUMN 带默认值=纯增量,不碰旧数据(同 Migration_25_26 先例)。
 */
val Migration_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 29 to 30 (secrets.author)")
        DatabaseMigrationTracker.onMigrationStart(29, 30)
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `secrets` ADD COLUMN `author` TEXT NOT NULL DEFAULT 'user'")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 29 to 30 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
