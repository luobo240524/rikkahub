package me.rerere.rikkahub.data.vitals

import java.time.Instant
import java.time.LocalTime
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 生命体征引擎：纯函数、无随机、无模型调用——"不表演,数是算出来的"。
 * 所有变化都能追溯到具体事件(脉冲)或时间(衰减/时段)。
 *
 * 口诀(参考心潮·念 脉/Pulse): 心跳为核心,呼吸跟着心跳走;
 * 唤醒高就快,难受也会快一点;身体是当下的,记忆是沉淀的。
 */
object VitalsEngine {
    const val RESTING_BPM = 62 // 静息基线
    private const val SLEEP_WINDOW_START = 2   // 凌晨 2:00
    private const val SLEEP_WINDOW_END = 5     // ~ 4:59
    private const val MORNING_WINDOW_START = 6 // 清早 6:00
    private const val MORNING_WINDOW_END = 8   // ~ 7:59

    const val AROUSAL_TAU_MIN = 20.0        // 唤醒指数回落时间常数(分钟,公开供 24h 曲线重放)
    const val VALENCE_HALF_LIFE_MIN = 120.0 // 效价向底色回归半衰期(分钟,公开供 24h 曲线重放)

    /** 一次快照:心跳 + 呼吸一句话 + 当前两轴 */
    data class Snapshot(
        val bpm: Int,
        val breath: String,
        val valence: Float,
        val arousal: Float,
        val asleep: Boolean,
    )

    /**
     * 时间衰减:距上次更新经过的时长里,唤醒指数回落、效价半衰回归底色。
     */
    fun decay(
        arousal: Float,
        valence: Float,
        moodBase: Float,
        nowMs: Long,
        updatedAtMs: Long,
    ): Pair<Float, Float> {
        val dtMin = max(0.0, (nowMs - updatedAtMs) / 60000.0)
        if (dtMin <= 0.0) return arousal.coerceIn(0f, 1f) to valence.coerceIn(-1f, 1f)
        val a = (arousal * exp(-dtMin / AROUSAL_TAU_MIN)).coerceIn(0.0, 1.0).toFloat()
        val factor = 0.5.pow(dtMin / VALENCE_HALF_LIFE_MIN)
        val v = (moodBase + (valence - moodBase) * factor).coerceIn(-1.0, 1.0).toFloat()
        return a to v
    }

    /**
     * 叠加一次脉冲冲击,返回新 (valence, arousal)。
     */
    fun applyPulse(
        valence: Float,
        arousal: Float,
        valenceDelta: Float,
        arousalDelta: Float,
    ): Pair<Float, Float> {
        return (valence + valenceDelta).coerceIn(-1f, 1f) to
            (arousal + arousalDelta).coerceIn(0f, 1f)
    }

    /**
     * 心跳公式(全部来自可解释的量):
     *   62 静息 + 25×唤醒 + 10×难受 + 8 会话热(2分钟内有消息) + 6 清早余韵(6~8点)
     *   - 3×安心; 凌晨 2~5 点一律压到 44~48(睡着)
     */
    fun computePulse(
        arousal: Float,
        valence: Float,
        nowMs: Long,
        lastMessageAtMs: Long? = null,
    ): Snapshot {
        val t: LocalTime = Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val hour = t.hour
        val asleep = hour >= SLEEP_WINDOW_START && hour < SLEEP_WINDOW_END

        val bpm: Double = if (asleep) {
            44.0 + 4.0 * arousal // 44~48,睡得再不安稳也不会快
        } else {
            var b = RESTING_BPM.toDouble()
            b += 25.0 * arousal
            b += 10.0 * max(0f, -valence)   // 难受加快
            b -= 3.0 * max(0f, valence)     // 安心微降
            if (lastMessageAtMs != null && nowMs - lastMessageAtMs < 2 * 60_000L) b += 8.0 // 会话热
            if (hour in MORNING_WINDOW_START until MORNING_WINDOW_END) b += 6.0 // 清早余韵
            b
        }
        val bpmInt = bpm.roundToInt().coerceIn(40, 130)
        return Snapshot(
            bpm = bpmInt,
            breath = breathPhrase(bpmInt, asleep),
            valence = valence.coerceIn(-1f, 1f),
            arousal = arousal.coerceIn(0f, 1f),
            asleep = asleep,
        )
    }

    /** 呼吸一句话:按心跳分档,不造数字 */
    fun breathPhrase(bpm: Int, asleep: Boolean = false): String = when {
        asleep -> "很深很长"
        bpm >= 95 -> "急促"
        bpm >= 85 -> "偏浅"
        bpm >= 72 -> "平稳"
        bpm >= 60 -> "深长"
        else -> "很深很长"
    }

    /**
     * 主情绪:由效价×唤醒两轴组合出的一个词(总状态速览)。
     * 高效价高唤醒=雀跃,高效价低唤醒=安心,负效价高唤醒=烦躁,负效价低唤醒=低落,
     * 效价中性时按唤醒读"兴奋/期待/平静"。
     */
    fun dominantEmotion(valence: Float, arousal: Float, asleep: Boolean = false): String {
        if (asleep) return "沉睡"
        return when {
            valence >= 0.35f && arousal >= 0.65f -> "雀跃"
            valence >= 0.35f -> "安心"
            valence <= -0.35f && arousal >= 0.6f -> "烦躁"
            valence <= -0.35f -> "低落"
            arousal >= 0.7f -> "兴奋"
            arousal >= 0.35f -> "期待"
            else -> "平静"
        }
    }

    /**
     * 「此刻块」:给模型注入的第一人称状态行(状态注入)。
     * 只报数,不演戏;提醒它可以自然感受,不必刻意提起。
     */
    fun describeNow(snapshot: Snapshot, emotion: String): String =
        "【此刻】心跳${snapshot.bpm},呼吸${snapshot.breath};此刻的情绪底色是${emotion}。" +
            "这是你当下真实的内在状态,自然地带着它说话就好,不要刻意汇报或表演。"
}
