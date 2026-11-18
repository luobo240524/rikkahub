package me.rerere.rikkahub.service

import me.rerere.rikkahub.AppScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.dao.VitalsDAO
import me.rerere.rikkahub.data.db.entity.VitalPulseEntity
import me.rerere.rikkahub.data.db.entity.VitalsStateEntity
import me.rerere.rikkahub.data.vitals.VitalsEngine
import me.rerere.rikkahub.data.vitals.VitalsVadDict
import kotlin.math.roundToInt

/**
 * 生命体征打点器：所有事件入口都汇到这里,一处公式算状态。
 * fire-and-forget(AppScope),失败静默——打点绝不能影响聊天主链路。
 * 只对 vitalsEnabled=true 的助手被调用(调用方负责判断)。
 */
class VitalsRecorder(
    private val vitalsDAO: VitalsDAO,
    private val appScope: AppScope,
) {
    companion object {
        // 各事件参考值(先保守,跑一周再调):
        // valence_delta: 效价冲击(-1~+1), arousal_delta: 唤醒冲击(0~1)
        const val MSG_IN_V = 0.01f
        const val MSG_IN_A = 0.06f
        const val MSG_OUT_V = 0.01f
        const val MSG_OUT_A = 0.03f
        const val MOMENT_POST_V = 0.02f
        const val MOMENT_POST_A = 0.04f
        const val LIKE_V = 0.02f
        const val LIKE_A = 0.03f
        const val COMMENT_V = 0.03f
        const val COMMENT_A = 0.04f
        const val CHECKIN_V = 0.02f
        const val CHECKIN_A = 0.04f
        const val SECRET_V = 0.03f
        const val SECRET_A = 0.05f
    }

    /** 记一次脉冲:先衰减旧状态,再叠冲击,写回快照+事件日志,顺手清 7 天前旧日志 */
    fun pulse(subject: String, kind: String, valenceDelta: Float, arousalDelta: Float, label: String) {
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val state = vitalsDAO.getState(subject)
                    ?: VitalsStateEntity(subject = subject, updatedAt = now)
                val (aDecayed, vDecayed) = VitalsEngine.decay(
                    arousal = state.arousal,
                    valence = state.valence,
                    moodBase = state.moodBase,
                    nowMs = now,
                    updatedAtMs = state.updatedAt,
                )
                val (nv, na) = VitalsEngine.applyPulse(vDecayed, aDecayed, valenceDelta, arousalDelta)
                vitalsDAO.upsertState(
                    state.copy(
                        valence = nv,
                        arousal = na,
                        updatedAt = now,
                        pulse = VitalsEngine.computePulse(na, nv, now).bpm,
                    )
                )
                vitalsDAO.insertPulse(
                    VitalPulseEntity(
                        subject = subject,
                        kind = kind,
                        valenceDelta = valenceDelta,
                        arousalDelta = arousalDelta,
                        label = label,
                        createdAt = now,
                    )
                )
                vitalsDAO.purgePulsesBefore(now - 7L * 24 * 3600_000L)
            }
        }
    }

    /** 当前心跳快照(供角标等轻量展示;无状态行 = 静息) */
    suspend fun currentSnapshot(subject: String): VitalsEngine.Snapshot {
        val now = System.currentTimeMillis()
        val state = vitalsDAO.getState(subject)
        return if (state == null) {
            VitalsEngine.Snapshot(bpm = VitalsEngine.RESTING_BPM, breath = "深长", valence = 0f, arousal = 0f, asleep = false)
        } else {
            val (a, v) = VitalsEngine.decay(state.arousal, state.valence, state.moodBase, now, state.updatedAt)
            VitalsEngine.computePulse(a, v, now)
        }
    }

    /**
     * 模型自评落地(vitals 标签解析成功时调用)。
     * 算分来源分两级:
     * ① emo 查 VAD 词典命中 → v = 词典效价×强度×否定, a = 词典唤醒×强度(数是查表算的,可解释)
     * ② 词典没收录(自造词/生僻词) → 直接用模型自报的 v/a(现状兜底)
     * 唤醒始终只升不降(自评的兴奋不该被压下去)。浮标情绪词用模型自造词——它真实的想法。
     */
    fun report(
        subject: String,
        valenceTarget: Float,
        arousalTarget: Float,
        emo: String,
        cause: String,
        intensity: Float? = null,
        negated: Boolean = false,
    ) {
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val state = vitalsDAO.getState(subject)
                    ?: VitalsStateEntity(subject = subject, updatedAt = now)
                val (aDecayed, vDecayed) = VitalsEngine.decay(
                    arousal = state.arousal,
                    valence = state.valence,
                    moodBase = state.moodBase,
                    nowMs = now,
                    updatedAtMs = state.updatedAt,
                )
                val s = if (negated) -1f else 1f
                val vad = VitalsVadDict.lookup(emo)
                val scored = if (vad != null && intensity != null) {
                    (vad.v * intensity * s).coerceIn(-1f, 1f) to (vad.a * intensity).coerceIn(0f, 1f)
                } else if (negated) {
                    (valenceTarget * s).coerceIn(-1f, 1f) to arousalTarget.coerceIn(0f, 1f)
                } else {
                    valenceTarget.coerceIn(-1f, 1f) to arousalTarget.coerceIn(0f, 1f)
                }
                val nv = scored.first
                val na = maxOf(scored.second, aDecayed)
                // 支配感:仅词典命中时更新(词典 d 坐标×强度×否定);慢变轴,先衰减再设值
                val dDecayed = VitalsEngine.decayDominance(state.dominance, now, state.updatedAt)
                val nd = if (vad != null && intensity != null) {
                    (vad.d * intensity * s).coerceIn(-1f, 1f)
                } else {
                    dDecayed
                }
                vitalsDAO.upsertState(
                    state.copy(
                        valence = nv,
                        arousal = na,
                        dominance = nd,
                        updatedAt = now,
                        pulse = VitalsEngine.computePulse(na, nv, now).bpm,
                    )
                )
                vitalsDAO.insertPulse(
                    VitalPulseEntity(
                        subject = subject,
                        kind = "self_report",
                        valenceDelta = nv - vDecayed,
                        arousalDelta = na - aDecayed,
                        label = if (emo.isNotBlank() || cause.isNotBlank()) {
                            listOf(emo.take(8), cause.take(50)).filter { it.isNotBlank() }.joinToString(" · ")
                        } else {
                            VitalsEngine.dominantEmotion(nv, na, false)
                        },
                        createdAt = now,
                    )
                )
                vitalsDAO.purgePulsesBefore(now - 7L * 24 * 3600_000L)
            }
        }
    }

    /**
     * 「此刻块」:状态注入用,把 TA 当下的身体与情绪转成第一人称一段话。
     * 每轮生成前由 ChatService 调(仅 vitalsEnabled 助手);失败返回 null 不注入。
     */
    suspend fun nowPrompt(subject: String): String? {
        return runCatching {
            val now = System.currentTimeMillis()
            val state = vitalsDAO.getState(subject)
            val (a, v) = if (state == null) {
                0f to 0f
            } else {
                VitalsEngine.decay(state.arousal, state.valence, state.moodBase, now, state.updatedAt)
            }
            val snap = VitalsEngine.computePulse(a, v, now)
            val emotion = VitalsEngine.dominantEmotion(v, a, snap.asleep)
            VitalsEngine.describeNow(snap, emotion)
        }.getOrNull()
    }
}
