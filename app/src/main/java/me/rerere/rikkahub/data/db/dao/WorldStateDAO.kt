package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldStateEntity

@Dao
interface WorldStateDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: WorldStateEntity)

    @Query("SELECT * FROM world_state WHERE subject = :assistantId")
    suspend fun get(assistantId: String): WorldStateEntity?

    @Query("SELECT * FROM world_state WHERE subject = :assistantId")
    fun observe(assistantId: String): Flow<WorldStateEntity?>

    /** 粮仓原子 +1(无行时由仓库层先建行);每聊一轮一次。返回受影响行数 */
    @Query("UPDATE world_state SET granary = granary + 1, updated_at = :now WHERE subject = :assistantId")
    suspend fun incrementGranary(assistantId: String, now: Long): Int

    /** 用户世界动作已注入位点(下一轮只报该时刻之后的新动作) */
    @Query("UPDATE world_state SET last_notified_at = :at WHERE subject = :assistantId")
    suspend fun setLastNotifiedAt(assistantId: String, at: Long)
}
