package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.fts.SimpleDictManager
import me.rerere.rikkahub.data.db.migrations.Migration_6_7
import me.rerere.rikkahub.data.db.migrations.Migration_11_12
import me.rerere.rikkahub.data.db.migrations.Migration_13_14
import me.rerere.rikkahub.data.db.migrations.Migration_14_15
import me.rerere.rikkahub.data.db.migrations.Migration_15_16
import me.rerere.rikkahub.data.db.migrations.Migration_25_26

/** Shared schema, migrations and extensions for the app and staged backup validation. */
internal object AppDatabaseFactory {
    fun create(context: Context, name: String = SQLiteConfiguration.DATABASE_NAME): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(Migration_6_7, Migration_11_12, Migration_13_14, Migration_14_15, Migration_15_16, Migration_25_26)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    val dictDir = SimpleDictManager.extractDict(context)
                    val cursor = db.query("SELECT jieba_dict(?)", arrayOf(dictDir.absolutePath))
                    cursor.use {
                        if (it.moveToFirst()) {
                            val result = it.getString(0)
                            val success = result?.trimEnd('/') == dictDir.absolutePath.trimEnd('/')
                            if (!success) {
                                android.util.Log.e(
                                    "DataSourceModule",
                                    "jieba_dict failed: $result, path=${dictDir.absolutePath}"
                                )
                            }
                        }
                    }
                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                            text,
                            node_id UNINDEXED,
                            message_id UNINDEXED,
                            conversation_id UNINDEXED,
                            title UNINDEXED,
                            update_at UNINDEXED,
                            tokenize = 'simple'
                        )
                        """.trimIndent()
                    )
                    // 索引表完整性自愈：表可能因历史崩溃而损坏（损坏后任何写入都会触发 native 崩溃）。
                    // 只针对 message_fts 这一张虚拟表，绝不触碰对话数据表。
                    ensureFtsHealthy(db, context)
                }
            })
            .openHelperFactory(SQLiteConfiguration.openHelperFactory(context))
            .build()

    private const val PREFS_NAME = "fts_health"
    private const val KEY_NEEDS_REBUILD = "needs_rebuild"

    /**
     * 检测 message_fts 完整性；损坏则重建表结构并置位「需要全量重灌」标记。
     * 下次索引管理实例启动时会把所有对话重新灌入。
     * 注意：只操作 message_fts，对话数据表不参与任何写操作。
     */
    private fun ensureFtsHealthy(db: SupportSQLiteDatabase, context: Context) {
        val healthy = runCatching {
            db.query("INSERT INTO message_fts(message_fts) VALUES('integrity-check')").use { true }
        }.getOrElse { e ->
            android.util.Log.e("FtsHealth", "integrity-check failed, table may be corrupted", e)
            false
        }
        if (healthy) {
            android.util.Log.i("FtsHealth", "message_fts integrity OK")
            return
        }
        android.util.Log.w("FtsHealth", "message_fts corrupted -> rebuilding table structure")
        runCatching {
            db.execSQL("DROP TABLE IF EXISTS message_fts")
            db.execSQL(
                """
                CREATE VIRTUAL TABLE message_fts USING fts5(
                    text,
                    node_id UNINDEXED,
                    message_id UNINDEXED,
                    conversation_id UNINDEXED,
                    title UNINDEXED,
                    update_at UNINDEXED,
                    tokenize = 'simple'
                )
                """.trimIndent()
            )
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_NEEDS_REBUILD, true).apply()
            android.util.Log.i("FtsHealth", "message_fts rebuilt, flagged for full reindex")
        }.onFailure { e ->
            android.util.Log.e("FtsHealth", "rebuild message_fts failed", e)
        }
    }

    /** 是否需要全量重灌索引（由索引管理器在后台消费） */
    fun needsFullReindex(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NEEDS_REBUILD, false)

    /** 全量重灌完成后清除标记 */
    fun clearFullReindexFlag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NEEDS_REBUILD, false).apply()
    }
}
