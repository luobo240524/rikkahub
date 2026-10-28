package me.rerere.common.android

import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.Executors
import kotlin.uuid.Uuid

private const val MAX_RECENT_LOGS = 100
private const val MAX_DISK_LOG_BYTES = 512L * 1024L
private const val MAX_RESTORE_LINES = 200

@Serializable
sealed class LogEntry {
    abstract val id: Uuid
    abstract val timestamp: Long
    abstract val tag: String

    @Serializable
    data class TextLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val message: String
    ) : LogEntry()

    @Serializable
    data class RequestLog(
        override val id: Uuid = Uuid.random(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val tag: String,
        val url: String,
        val method: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val requestBody: String? = null,
        val responseCode: Int? = null,
        val responseHeaders: Map<String, String> = emptyMap(),
        val durationMs: Long? = null,
        val error: String? = null
    ) : LogEntry()
}

object Logging {
    private val recentLogs = arrayListOf<LogEntry>()
    @Volatile
    private var requestLoggingEnabled = false

    // ---- 崩溃安全落盘（诊断）----
    // 纯内存日志在 native 崩溃（SIGSEGV 等进程秒死）时全部丢失，
    // 崩溃前最后的重试失败原因等关键线索拿不回来。
    // 文本日志逐条追加写盘（flush 不 fsync：进程死亡不丢，仅掉电才丢），
    // 下次启动读回内存，日志页即可看到崩溃前的记录。
    private val diskExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "logging-disk").apply { isDaemon = true }
    }
    @Volatile
    private var diskFile: File? = null

    /** 最早生命周期调用（attachBaseContext）：指定落盘文件并恢复历史日志 */
    fun initCrashSafeLog(file: File) {
        diskFile = file
        runCatching { file.parentFile?.mkdirs() }
        restoreFromDisk(file)
    }

    fun log(tag: String, message: String) {
        val entry = LogEntry.TextLog(tag = tag, message = message)
        addLog(entry)
        enqueueWrite(entry)
    }

    /** 关键日志（崩溃堆栈等）：调用线程同步写盘，确保进程死亡前已落盘 */
    fun logSync(tag: String, message: String) {
        val entry = LogEntry.TextLog(tag = tag, message = message)
        addLog(entry)
        runCatching { writeToDisk(entry) }
    }

    fun logRequest(entry: LogEntry.RequestLog) {
        if (!requestLoggingEnabled) return
        addLog(entry)
    }

    fun isRequestLoggingEnabled(): Boolean = requestLoggingEnabled

    fun setRequestLoggingEnabled(enabled: Boolean) {
        requestLoggingEnabled = enabled
    }

    private fun addLog(entry: LogEntry) {
        synchronized(recentLogs) {
            recentLogs.add(0, entry)
            if (recentLogs.size > MAX_RECENT_LOGS) {
                recentLogs.removeLastOrNull()
            }
        }
    }

    private fun enqueueWrite(entry: LogEntry.TextLog) {
        runCatching {
            diskExecutor.execute {
                runCatching { writeToDisk(entry) }
            }
        }
    }

    private fun writeToDisk(entry: LogEntry.TextLog) {
        val file = diskFile ?: return
        runCatching {
            if (file.length() > MAX_DISK_LOG_BYTES) {
                val old = File(file.parentFile, file.nameWithoutExtension + ".old.log")
                old.delete()
                file.renameTo(old)
            }
            file.appendText(encodeLine(entry) + "\n")
        }
    }

    private fun encodeLine(entry: LogEntry.TextLog): String {
        // 转义顺序必须先反斜杠再换行，才能保证逆变换无歧义
        val msg = entry.message
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        return "${entry.timestamp}|${entry.tag}|$msg"
    }

    private fun restoreFromDisk(file: File) {
        runCatching {
            if (!file.exists()) return
            var restored = 0
            // 恢复到内存环里 newest-first：按文件顺序（旧→新）逐条 add(0)
            file.readLines().takeLast(MAX_RESTORE_LINES).forEach { line ->
                runCatching {
                    val parts = line.split("|", limit = 3)
                    if (parts.size == 3) {
                        val ts = parts[0].toLongOrNull() ?: return@runCatching
                        addLog(
                            LogEntry.TextLog(
                                timestamp = ts,
                                tag = parts[1],
                                message = unescape(parts[2]),
                            )
                        )
                        restored++
                    }
                }
            }
            if (restored > 0) {
                addLog(LogEntry.TextLog(tag = "LOG", message = "已从磁盘恢复 $restored 条历史日志（含上次进程死亡前的记录）"))
            }
        }
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(c); i += 1 }
                }
            } else {
                sb.append(c); i += 1
            }
        }
        return sb.toString()
    }

    fun getRecentLogs(): List<LogEntry> {
        synchronized(recentLogs) {
            return recentLogs.toList()
        }
    }

    fun getTextLogs(): List<LogEntry.TextLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.TextLog>()
        }
    }

    fun getRequestLogs(): List<LogEntry.RequestLog> {
        synchronized(recentLogs) {
            return recentLogs.filterIsInstance<LogEntry.RequestLog>()
        }
    }

    fun clear() {
        synchronized(recentLogs) {
            recentLogs.clear()
        }
        val file = diskFile ?: return
        runCatching {
            diskExecutor.execute {
                runCatching {
                    file.writeText("")
                    File(file.parentFile, file.nameWithoutExtension + ".old.log").delete()
                }
            }
        }
    }
}
