package me.rerere.rikkahub.data.db.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.DatabaseMigrationTracker

private const val TAG = "Migration_25_26"

val Migration_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: add conversation override columns (chat_model_id_override / web_search_override)")
        DatabaseMigrationTracker.onMigrationStart(25, 26)
        db.execSQL("ALTER TABLE ConversationEntity ADD COLUMN chat_model_id_override TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE ConversationEntity ADD COLUMN web_search_override TEXT NOT NULL DEFAULT ''")
        DatabaseMigrationTracker.onMigrationEnd()
    }
}
