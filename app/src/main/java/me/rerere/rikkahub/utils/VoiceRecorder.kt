package me.rerere.rikkahub.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * 语音条录音器:MediaRecorder→AAC/m4a,存 filesDir/voice_notes/。
 * 权限(RECORD_AUDIO)由调用方 UI 处理;stop 返回 (文件, 秒数),过短/失败返回 (null, 0)。
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0

    fun start(): File {
        val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        outputFile = file
        startedAt = System.currentTimeMillis()
        return file
    }

    /** 停止并返回 (文件, 时长秒);失败时清理并返回 (null, 0) */
    fun stop(): Pair<File?, Int> {
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(1)
        val ok = runCatching { recorder?.stop() }.isSuccess
        runCatching { recorder?.release() }
        recorder = null
        val file = outputFile
        outputFile = null
        return if (ok && file != null && file.exists() && file.length() > 0) {
            file to seconds
        } else {
            file?.delete()
            null to 0
        }
    }

    /** 取消并删除半成品 */
    fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
