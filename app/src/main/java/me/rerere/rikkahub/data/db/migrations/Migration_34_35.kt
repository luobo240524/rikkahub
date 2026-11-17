package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_34_35"

/**
 * 纯增量(助手生命体征):
 * 1. 新建 vitals_state 表(每助手一行状态快照: valence/arousal/底色三件套/心跳快照)
 * 2. 新建 vital_pulses 表(情绪脉冲事件日志, 潮汐带/曲线用, 7 天保留)
 * 不碰任何旧表数据。默认值与 Room 实体定义严格一致(否则 schema 校验失败)。
 */
val Migration_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 34 to 35 (vitals_state + vital_pulses)")
        DatabaseMigrationTracker.onMigrationStart(34, 35)
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vitals_state` (" +
                    "`subject` TEXT NOT NULL, " +
                    "`valence` REAL NOT NULL DEFAULT 0, " +
                    "`arousal` REAL NOT NULL DEFAULT 0, " +
                    "`safety` REAL NOT NULL DEFAULT 0.7, " +
                    "`confidence` REAL NOT NULL DEFAULT 0.5, " +
                    "`mood_base` REAL NOT NULL DEFAULT 0, " +
                    "`pulse` INTEGER NOT NULL DEFAULT 62, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`subject`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vital_pulses` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`subject` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`valence_delta` REAL NOT NULL, " +
                    "`arousal_delta` REAL NOT NULL, " +
                    "`label` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_vital_pulses_subject_created_at` " +
                    "ON `vital_pulses` (`subject`, `created_at`)"
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 34 to 35 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
