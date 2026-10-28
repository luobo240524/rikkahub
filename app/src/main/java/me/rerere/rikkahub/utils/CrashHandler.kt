package me.rerere.rikkahub.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit

private const val TAG = "CrashHandler"
private const val PREFS_NAME = "crash_handler"
private const val KEY_CRASHED = "crashed"
private const val KEY_STACKTRACE = "stacktrace"
private const val MAX_STACKTRACE_LENGTH = 8000

object CrashHandler {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            try {
                // 堆栈同步写入应用日志页：后台场景报告页 Activity 会被系统拦截（后台启动限制），
                // 日志页是唯一兜底可见渠道；logSync 同步落盘，确保进程死亡前写完
                runCatching {
                    me.rerere.common.android.Logging.logSync(
                        "CRASH",
                        "thread=${thread.name} ex=${throwable.javaClass.name}: ${throwable.message}\n" +
                            throwable.stackTraceToString().take(6000)
                    )
                }
                markCrashed(appContext, thread, throwable)
                val intent = Intent(appContext, me.rerere.rikkahub.ui.activity.CrashReportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "launch crash report failed", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrashed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CRASHED, false)
    }

    fun getStackTrace(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STACKTRACE, null)
    }

    fun clearCrashed(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_CRASHED).remove(KEY_STACKTRACE) }
    }

    private fun markCrashed(context: Context, thread: Thread, throwable: Throwable) {
        val stackTrace = buildString {
            appendLine("Thread: ${thread.name}")
            appendLine(throwable.stackTraceToString())
        }.take(MAX_STACKTRACE_LENGTH)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putBoolean(KEY_CRASHED, true)
                putString(KEY_STACKTRACE, stackTrace)
            } // commit() 同步写入，确保崩溃前写完
        // 诊断：同时写到外部 files 目录(Android/data/me.rerere.rikkahub/files/crash_report.txt)，文件管理器可取
        try {
            context.getExternalFilesDir(null)?.let { dir ->
                java.io.File(dir, "crash_report.txt").writeText(stackTrace)
            }
        } catch (_: Throwable) {}
    }

    fun recordCrash(context: Context, throwable: Throwable) {
        markCrashed(context, Thread.currentThread(), throwable)
    }
}
