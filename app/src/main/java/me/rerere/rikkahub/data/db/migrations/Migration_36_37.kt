package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_36_37"

/**
 * 纯增量(「你的世界」一期):
 * 7 张新表全部带 assistant_id 隔离——碎碎念(world_thoughts)/留灯(world_lights)/
 * 花盆(world_plants)/宠物(world_pet_state,含蛋 hatch 列)/宠物日记(world_pet_events)/
 * 小园日记(garden_log,拍板=独立表)/每助手全局计数(world_state,粮仓)。
 * 只 CREATE,不碰旧数据。
 */
val Migration_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 36 to 37 (your-world 7 tables)")
        DatabaseMigrationTracker.onMigrationStart(36, 37)
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `world_thoughts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `author` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `mood` TEXT NOT NULL,
                    `drawn_at` INTEGER,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_thoughts_assistant_id` ON `world_thoughts` (`assistant_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `world_lights` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `author` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `lit_at` INTEGER NOT NULL,
                    `out_at` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_lights_assistant_id` ON `world_lights` (`assistant_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `world_plants` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `slot` INTEGER NOT NULL,
                    `species` TEXT NOT NULL,
                    `growth` INTEGER NOT NULL,
                    `waters` INTEGER NOT NULL,
                    `thirst` INTEGER NOT NULL,
                    `has_pest` INTEGER NOT NULL,
                    `has_weed` INTEGER NOT NULL,
                    `last_care_at` INTEGER NOT NULL,
                    `planted_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_plants_assistant_id` ON `world_plants` (`assistant_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `world_pet_state` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `species` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `gender` TEXT NOT NULL,
                    `is_golden` INTEGER NOT NULL,
                    `hatch_signals` INTEGER NOT NULL,
                    `born_at` INTEGER,
                    `last_fed_at` INTEGER NOT NULL,
                    `last_play_at` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_pet_state_assistant_id` ON `world_pet_state` (`assistant_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `world_pet_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `pet_id` INTEGER,
                    `actor` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `detail` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_pet_events_assistant_id` ON `world_pet_events` (`assistant_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_world_pet_events_pet_id` ON `world_pet_events` (`pet_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `garden_log` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `assistant_id` TEXT NOT NULL,
                    `actor` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `plant_id` INTEGER,
                    `detail` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_garden_log_assistant_id` ON `garden_log` (`assistant_id`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `world_state` (
                    `subject` TEXT NOT NULL,
                    `granary` INTEGER NOT NULL DEFAULT 0,
                    `wall_key` TEXT NOT NULL DEFAULT '',
                    `deco_key` TEXT NOT NULL DEFAULT '',
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`subject`)
                )
                """.trimIndent()
            )
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 36 to 37 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
