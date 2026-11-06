package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.MoodCheckinEntity

@Dao
interface MoodCheckinDAO {
    /** (date, subject) 唯一索引,重复打卡覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkin: MoodCheckinEntity)

    @Query("SELECT * FROM mood_checkins ORDER BY date DESC, created_at DESC")
    fun flowAll(): Flow<List<MoodCheckinEntity>>

    /** 某主体最近 N 天的打卡(mood_read 工具用) */
    @Query("SELECT * FROM mood_checkins WHERE subject = :subject AND date >= :sinceDate ORDER BY date DESC")
    suspend fun recentBySubject(subject: String, sinceDate: String): List<MoodCheckinEntity>

    /** 某月有打卡的日期集合(月历视图用) */
    @Query("SELECT * FROM mood_checkins WHERE date BETWEEN :fromDate AND :toDate")
    suspend fun betweenDates(fromDate: String, toDate: String): List<MoodCheckinEntity>

    /** 某天某主体的打卡记录 */
    @Query("SELECT * FROM mood_checkins WHERE date = :date AND subject = :subject LIMIT 1")
    suspend fun getByDateAndSubject(date: String, subject: String): MoodCheckinEntity?

    @Query("DELETE FROM mood_checkins WHERE id = :id")
    suspend fun deleteById(id: Long)
}
