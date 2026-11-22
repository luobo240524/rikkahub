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
}
