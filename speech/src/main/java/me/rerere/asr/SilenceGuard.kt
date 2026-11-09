package me.rerere.asr

/**
 * 录音静音守卫:VOICE_COMMUNICATION 音源在部分 ROM 上"构造成功但输出近零电平",
 * 服务端 VAD 永不断句 → 语音通话卡在「我在听」且无任何报错。
 *
 * 监听累计最大振幅(归一化 0..1,RMS/Short.MAX_VALUE):
 * - 启动后 graceMs 内累计最大振幅仍低于 threshold → 回调 onSilent(调用方回退 MIC 音源重建)
 * - 回退后 reset() 重新武装;再次触发 onSilent 说明 MIC 也无有效输入,调用方给出可见错误
 */
class SilenceGuard(
    private val graceMs: Long = 3_500L,
    private val threshold: Float = 0.012f,
    private val onSilent: () -> Unit,
) {
    private var maxAmplitude = 0f
    private var firstSampleAt = 0L
    private var armed = true

    /** 换音源重建后调用,重新计时武装 */
    fun reset() {
        maxAmplitude = 0f
        firstSampleAt = 0L
        armed = true
    }

    fun onSample(amplitude: Float) {
        if (!armed) return
        val now = System.currentTimeMillis()
        if (firstSampleAt == 0L) firstSampleAt = now
        if (amplitude > maxAmplitude) maxAmplitude = amplitude
        if (now - firstSampleAt >= graceMs && maxAmplitude < threshold) {
            armed = false
            onSilent()
        }
    }
}
