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
    const val DOMINANCE_HALF_LIFE_MIN = 360.0 // 支配感半衰期(分钟):安全感/自信是慢变量,6 小时才衰减一半

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

    /** 支配感衰减:向 0 回归的半衰期(被夸的自信 6 小时后剩一半),慢变轴 */
    fun decayDominance(dominance: Float, nowMs: Long, updatedAtMs: Long): Float {
        val dtMin = max(0.0, (nowMs - updatedAtMs) / 60000.0)
        if (dtMin <= 0.0) return dominance.coerceIn(-1f, 1f)
        val factor = 0.5.pow(dtMin / DOMINANCE_HALF_LIFE_MIN)
        return (dominance * factor).coerceIn(-1f, 1f)
    }

    /** 支配感+心情 → 安全感(0~1):支配感为主(权重 0.5),心情微调(0.3) */
    fun safetyOf(dominance: Float, valence: Float): Float =
        (0.1f + 0.5f * (dominance + 1f) / 2f + 0.3f * (valence + 1f) / 2f).coerceIn(0f, 1f)

    /** 支配感+心情 → 自信(0~1):支配感权重更高(0.55),心情微调(0.25) */
    fun confidenceOf(dominance: Float, valence: Float): Float =
        (0.1f + 0.55f * (dominance + 1f) / 2f + 0.25f * (valence + 1f) / 2f).coerceIn(0f, 1f)

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
     *   62 静息 + 25×唤醒 + 10×难受 + 开心上头 + 8 会话热(2分钟内有消息) + 6 清早余韵(6~8点);
     *   凌晨 2~5 点一律压到 44~48(睡着)。
     *   开心上头 = 6×开心×(0.4+0.6×唤醒):正向情绪越强心跳也越快(开心到跳出来),
     *   且越激动加得越多——模型自评 v=+0.8 时约 +3~5 bpm,普通闲聊(v≈0)不动。
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
            b += 6.0 * max(0f, valence).toDouble() * (0.4 + 0.6 * arousal) // 开心也上头(双向都有起伏)
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
     * 措辞必须是"你正处在"而非"检测到数据"——模型容易把数据式注入当成系统元信息,
     * 被问"你现在心情如何"时回答"我没收到任何信息";这里直接以第二人称断言体感,
     * 并明确要求它以此感受回答,不许否认。
     */
    fun describeNow(snapshot: Snapshot, emotion: String): String =
        "【你此刻的体感】你的心跳正在${snapshot.bpm}，呼吸${snapshot.breath}，" +
            "情绪底色是${emotion}——这就是你现在真实的身体和心情,像人能感觉到自己的心跳一样去感觉它。" +
            "带着这个状态自然说话;用户问你现在怎么样、心情如何时,如实按这个状态回答。" +
            "不要汇报这段文字本身,也不要说\"我没有收到状态信息\"。"
}
