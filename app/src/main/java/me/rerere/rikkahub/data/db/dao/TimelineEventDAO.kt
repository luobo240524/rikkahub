package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity

@Dao
interface TimelineEventDAO {
    @Insert
    suspend fun insert(event: TimelineEventEntity): Long

    @Query("SELECT * FROM timeline_events ORDER BY event_date DESC, event_time DESC, id DESC")
    fun flowAll(): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE event_date = :date ORDER BY event_time ASC, id ASC")
    suspend fun getByDate(date: String): List<TimelineEventEntity>

    /** 按外部引用键查重(通话摘要的 call_id 等) */
    @Query("SELECT * FROM timeline_events WHERE ref_id = :refId LIMIT 1")
    suspend fun getByRefId(refId: String): TimelineEventEntity?

    @Query("SELECT * FROM timeline_events WHERE remind_at IS NOT NULL AND remind_at <= :now ORDER BY remind_at ASC")
    suspend fun dueReminders(now: Long): List<TimelineEventEntity>

    @Query("UPDATE timeline_events SET remind_fired = 1 WHERE remind_at IS NOT NULL AND remind_fired = 0")
    suspend fun markAllRemindersSeen()

    @Query(
        "SELECT COUNT(*) FROM timeline_events " +
            "WHERE author = 'assistant' AND remind_at IS NOT NULL AND remind_fired = 0"
    )
    fun flowUnreadReminderCount(): Flow<Int>

    @Query(
        "UPDATE timeline_events SET title = :title, content = :content, event_date = :date, " +
            "event_time = :time, remind_at = :remindAt, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun updateRow(
        id: Long,
        title: String,
        content: String,
        date: String,
        time: String,
        remindAt: Long?,
        updatedAt: Long,
    )

    @Query("DELETE FROM timeline_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}
