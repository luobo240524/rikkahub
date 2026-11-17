package me.rerere.rikkahub.ui.pages.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.dao.VitalsDAO
import me.rerere.rikkahub.data.db.entity.VitalPulseEntity
import me.rerere.rikkahub.data.db.entity.VitalsStateEntity
import me.rerere.rikkahub.data.db.entity.dueTickerFlow
import me.rerere.rikkahub.data.vitals.VitalsEngine
import kotlin.math.exp

/** 24h 心跳曲线采样点 */
data class VitalsCurvePoint(val t: Long, val bpm: Int)

data class VitalsUiState(
    val subject: String? = null,
    val name: String = "",
    val enabled: Boolean = false,
    val stateRow: VitalsStateEntity? = null,
    val snapshot: VitalsEngine.Snapshot? = null,
    val pulses: List<VitalPulseEntity> = emptyList(), // 新→旧
    val curve: List<VitalsCurvePoint> = emptyList(), // 24h,每 5 分钟一个点
)

/**
 * 生命体征 VM:当前助手的内在状态。dueTickerFlow 30 秒心跳驱动重算
 * (衰减/时段/会话热都是时间的函数),与 mood/secrets/moments VM 同套路。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VitalsVM(
    private val vitalsDAO: VitalsDAO,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val assistantFlow = settingsStore.settingsFlow
        .map { it.getCurrentAssistant() }
        .distinctUntilChangedBy { it.id }

    val state: StateFlow<VitalsUiState> = assistantFlow
        .flatMapLatest { assistant ->
            val subject = assistant.id.toString()
            combine(
                vitalsDAO.flowState(subject),
                vitalsDAO.flowPulsesSince(subject, System.currentTimeMillis() - 24L * 3600_000L),
                dueTickerFlow(),
            ) { row, pulses, _ ->
                val now = System.currentTimeMillis()
                val since = now - 24L * 3600_000L
                val recent = pulses.filter { it.createdAt >= since }
                val lastMsgAt = recent.lastOrNull { it.kind == "msg_in" || it.kind == "msg_out" }?.createdAt
                val snapshot = if (row == null) {
                    VitalsEngine.computePulse(0f, 0f, now, lastMsgAt)
                } else {
                    val (a, v) = VitalsEngine.decay(row.arousal, row.valence, row.moodBase, now, row.updatedAt)
                    VitalsEngine.computePulse(a, v, now, lastMsgAt)
                }
                VitalsUiState(
                    subject = subject,
                    name = assistant.name,
                    enabled = assistant.vitalsEnabled,
                    stateRow = row,
                    snapshot = snapshot,
                    pulses = recent.sortedByDescending { it.createdAt },
                    curve = replayCurve(recent.sortedBy { it.createdAt }, row?.moodBase ?: 0f, now),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VitalsUiState())

    /**
     * 24h 曲线重放:从 24h 前的静息态出发,每 5 分钟推进一次(逐步衰减+叠脉冲),
     * 用与实时完全相同的 computePulse 算每个采样点的心跳。纯函数,不查库。
     */
    private fun replayCurve(
        pulsesAsc: List<VitalPulseEntity>,
        moodBase: Float,
        now: Long,
    ): List<VitalsCurvePoint> {
        val start = now - 24L * 3600_000L
        val stepMs = 5L * 60_000L
        val aDecay = exp(-5.0 / VitalsEngine.AROUSAL_TAU_MIN).toFloat()
        val vFactor = Math.pow(0.5, 5.0 / VitalsEngine.VALENCE_HALF_LIFE_MIN).toFloat()
        var arousal = 0f
        var valence = moodBase
        var lastMsgAt: Long? = null
        val points = mutableListOf<VitalsCurvePoint>()
        var idx = 0
        var t = start
        while (t <= now) {
            arousal *= aDecay
            valence = moodBase + (valence - moodBase) * vFactor
            while (idx < pulsesAsc.size && pulsesAsc[idx].createdAt <= t) {
                val p = pulsesAsc[idx]
                val (v, a) = VitalsEngine.applyPulse(valence, arousal, p.valenceDelta, p.arousalDelta)
                valence = v
                arousal = a
                if (p.kind == "msg_in" || p.kind == "msg_out") lastMsgAt = p.createdAt
                idx++
            }
            points.add(VitalsCurvePoint(t, VitalsEngine.computePulse(arousal, valence, t, lastMsgAt).bpm))
            t += stepMs
        }
        return points
    }
}
