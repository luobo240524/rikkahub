package me.rerere.rikkahub.service

import me.rerere.rikkahub.AppScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.dao.VitalsDAO
import me.rerere.rikkahub.data.db.entity.VitalPulseEntity
import me.rerere.rikkahub.data.db.entity.VitalsStateEntity
import me.rerere.rikkahub.data.vitals.VitalsEngine
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
}
