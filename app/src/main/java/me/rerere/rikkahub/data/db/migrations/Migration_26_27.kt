package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker
import me.rerere.rikkahub.data.model.MemoryFile

private const val TAG = "Migration_26_27"

/**
 * 记忆便签 → 记忆文件库（移植自上游 27→28，适配本 fork 的版本序列 26→27）。
 *
 * 旧 MemoryEntity 每条是一句话便签，每次对话全量注入；新 memory_file 是
 * Markdown 文件库（profile/preferences 全文注入，其余只给清单按需读）。
 * 旧便签合并进 /profile.md：它同样全文注入，升级后模型看到的事实不变。
 */
val Migration_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 26 to 27 (memory records -> memory files)")
        DatabaseMigrationTracker.onMigrationStart(26, 27)
        db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `memory_file` (`assistant_id` TEXT NOT NULL, `path` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`assistant_id`, `path`))"
            )

            val memories = linkedMapOf<String, MutableList<String>>()
            db.query("SELECT assistant_id, content FROM MemoryEntity ORDER BY id").use { cursor ->
                while (cursor.moveToNext()) {
                    memories.getOrPut(cursor.getString(0)) { mutableListOf() }.add(cursor.getString(1))
                }
            }

            val now = System.currentTimeMillis()
            var migratedCount = 0
            memories.forEach { (assistantId, contents) ->
                val profile = legacyMemoriesToProfile(contents) ?: return@forEach
                db.execSQL(
                    "INSERT INTO memory_file (assistant_id, path, content, updated_at) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(assistantId, MemoryFile.PROFILE_PATH, profile, now)
                )
                migratedCount++
            }

            db.execSQL("DROP TABLE IF EXISTS MemoryEntity")
            db.setTransactionSuccessful()
            Log.i(TAG, "migrate: migrate from 26 to 27 success ($migratedCount memory stores migrated)")
        } finally {
            db.endTransaction()
            DatabaseMigrationTracker.onMigrationEnd()
        }
    }
}

/** 把旧的逐条记忆拼成一份 profile 文件，没有有效内容时返回 null */
internal fun legacyMemoriesToProfile(contents: List<String>): String? {
    val lines = contents
        .map { it.lines().joinToString(" ") { line -> line.trim() }.trim() }
        .filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null
    return buildString {
        appendLine("---")
        appendLine("name: profile")
        appendLine("description: Who the user is")
        appendLine("---")
        appendLine()
        lines.forEach { appendLine("- [stated] $it") }
    }
}
