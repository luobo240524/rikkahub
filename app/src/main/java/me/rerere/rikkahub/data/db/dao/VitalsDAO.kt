package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.VitalPulseEntity
import me.rerere.rikkahub.data.db.entity.VitalsStateEntity

@Dao
interface VitalsDAO {
    // ---- 状态快照(每助手一行) ----

    @Upsert
    suspend fun upsertState(state: VitalsStateEntity)

    @Query("SELECT * FROM vitals_state WHERE subject = :subject")
    suspend fun getState(subject: String): VitalsStateEntity?

    @Query("SELECT * FROM vitals_state WHERE subject = :subject")
    fun flowState(subject: String): Flow<VitalsStateEntity?>

    @Query("DELETE FROM vitals_state WHERE subject = :subject")
    suspend fun deleteState(subject: String)

    // ---- 脉冲事件日志 ----

    @Insert
    suspend fun insertPulse(pulse: VitalPulseEntity): Long

    /** 某助手 since 之后的事件(潮汐带/曲线用) */
    @Query("SELECT * FROM vital_pulses WHERE subject = :subject AND created_at >= :since ORDER BY created_at ASC")
    suspend fun pulsesSince(subject: String, since: Long): List<VitalPulseEntity>

    @Query("SELECT * FROM vital_pulses WHERE subject = :subject AND created_at >= :since ORDER BY created_at ASC")
    fun flowPulsesSince(subject: String, since: Long): Flow<List<VitalPulseEntity>>

    /** 保留策略:清理 7 天前旧事件 */
    @Query("DELETE FROM vital_pulses WHERE created_at < :before")
    suspend fun purgePulsesBefore(before: Long): Int

    @Query("DELETE FROM vital_pulses WHERE subject = :subject")
    suspend fun deletePulses(subject: String)
}
