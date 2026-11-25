package me.rerere.rikkahub.data.world

import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.dao.TimelineEventDAO
import me.rerere.rikkahub.data.db.dao.VitalsDAO
import me.rerere.rikkahub.data.readtogether.ReadTogetherRepository

/** 宠物性格/关系5维(0-100),JSON 存 world_pet_state.personality */
@Serializable
data class PetPersonality(
    val affection: Int = 0,
    val reading: Int = 0,
    val watching: Int = 0,
    val listening: Int = 0,
    val exploration: Int = 0,
)

/**
 * 关系信号(批次B):破壳时读本地数据算五维分,主导维度定孵出物种+由来文案。
 * 隔离口径:只有 affection 走 VitalsDAO 按 assistantId 隔离;
 * 心情打卡/碎碎念/时间线三表无 assistant_id 列(全局),阅读为全局书架——多助手场景信号共享。
 * 全空(全 0)返回 null 主导维度,走四兽随机兜底,绝不因没数据破壳失败。
 */
class WorldRelationshipSignals(
    private val vitalsDao: VitalsDAO,
    private val moodDao: MoodCheckinDAO,
    private val momentsDao: MomentsPostDAO,
    private val timelineDao: TimelineEventDAO,
    private val readTogether: ReadTogetherRepository,
) {

    data class Signal(
        val affection: Int,
        val reading: Int,
        val watching: Int,
        val listening: Int,
        val exploration: Int,
    ) {
        /** 主导维度 key,全空返回 null */
        fun dominant(): String? {
            val m = mapOf(
                "affection" to affection,
                "reading" to reading,
                "watching" to watching,
                "listening" to listening,
                "exploration" to exploration,
            )
            val best = m.maxByOrNull { it.value } ?: return null
            return if (best.value > 0) best.key else null
        }

        fun toPersonality(): PetPersonality = PetPersonality(
            affection = affection, reading = reading, watching = watching,
            listening = listening, exploration = exploration,
        )
    }

    suspend fun compute(assistantId: String): Signal {
        val now = System.currentTimeMillis()
        // 亲近:Vitals valence(-1~1 → 0~100,按助手隔离);无体征数据用心情打卡次数兜底
        val affection = vitalsDao.getState(assistantId)?.valence?.let { v ->
            (((v + 1f) / 2f) * 100).toInt().coerceIn(0, 100)
        } ?: (moodDao.recent(30).size * 10).coerceIn(0, 100)
        // 阅读:近7天在读过的书数(全局书架)
        val weekAgo = now - 7L * 86_400_000L
        val reading = (readTogether.listBooks().count { it.lastReadAt >= weekAgo } * 20).coerceIn(0, 100)
        // 观影:碎碎念近期条数(全局)
        val watching = (momentsDao.recent(50).size * 4).coerceIn(0, 100)
        // 听歌:播放历史未落库,本期留空
        val listening = 0
        // 探索:时间线事件数(全局)
        val exploration = (timelineDao.flowAll().first().size * 5).coerceIn(0, 100)
        return Signal(affection, reading, watching, listening, exploration)
    }
}
