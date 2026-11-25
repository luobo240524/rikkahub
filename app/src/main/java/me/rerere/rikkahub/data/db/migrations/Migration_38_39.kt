package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_38_39"

/**
 * 宠物成长体系(批次B):world_pet_state 加 5 列。
 * growth=照顾收益累计;有效成长= growth + 陪伴天数(纯派生,老宠物迁移后立即有成长,无需回填);
 * stage/stage_at=阶段与进阶时刻(仓库层惰性结算);origin=破壳由来;personality=性格5维 JSON。
 */
val Migration_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 38 to 39 (world_pet_state growth/stage/origin/personality)")
        DatabaseMigrationTracker.onMigrationStart(38, 39)
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE `world_pet_state` ADD COLUMN `growth` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `world_pet_state` ADD COLUMN `stage` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `world_pet_state` ADD COLUMN `stage_at` INTEGER")
            db.execSQL("ALTER TABLE `world_pet_state` ADD COLUMN `origin` TEXT")
            db.execSQL("ALTER TABLE `world_pet_state` ADD COLUMN `personality` TEXT")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 38 to 39 success")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}
