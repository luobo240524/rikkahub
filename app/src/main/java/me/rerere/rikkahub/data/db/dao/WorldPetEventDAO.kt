package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.WorldPetEventEntity

@Dao
interface WorldPetEventDAO {
    @Insert
    suspend fun insert(event: WorldPetEventEntity): Long

    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit")
    fun flowRecent(assistantId: String, limit: Int = 50): Flow<List<WorldPetEventEntity>>

    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecent(assistantId: String, limit: Int = 50, offset: Int = 0): List<WorldPetEventEntity>

    /** 日记去重用:最新一条(仓库层比对 actor+action+petId 是否连续重复) */
    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(assistantId: String): WorldPetEventEntity?

    /** 同一只宠物的最近动作,用于成年宠物和孵化蛋的冷却真值判定。 */
    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId AND pet_id = :petId AND action = :action ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestForPetAction(assistantId: String, petId: Long, action: String): WorldPetEventEntity?

    /** 同一只宠物、同一操作者、同一动作的最近记录,用于对方分别计时。 */
    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId AND pet_id = :petId AND actor = :actor AND action = :action ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestForPetActorAction(assistantId: String, petId: Long, actor: String, action: String): WorldPetEventEntity?

    /** TA 的照顾是一个整体动作:喂/玩/梳任一项都会占用 TA 冷却。 */
    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId AND pet_id = :petId AND actor = :actor AND action IN (:actions) ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestForPetActorActions(assistantId: String, petId: Long, actor: String, actions: List<String>): WorldPetEventEntity?

    /** 同一只宠物的动作流水,用于连续同类成长收益计算。 */
    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId AND pet_id = :petId ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentForPet(assistantId: String, petId: Long, limit: Int = 30): List<WorldPetEventEntity>

    /** 用户的宠物互动(时间位点后,升序)——注入告知助手用 */
    @Query("SELECT * FROM world_pet_events WHERE assistant_id = :assistantId AND actor = 'user' AND created_at >= :since ORDER BY created_at ASC")
    suspend fun getUserSince(assistantId: String, since: Long): List<WorldPetEventEntity>
}
