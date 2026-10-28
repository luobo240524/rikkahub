package me.rerere.rikkahub

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.SkillManager
import java.io.File
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.sync.BackupManager
import me.rerere.rikkahub.data.sync.RestoreFailedException
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.service.WebServerService
import me.rerere.rikkahub.utils.CrashHandler
import me.rerere.rikkahub.utils.DatabaseUtil
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceManager
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

private const val TAG = "RikkaHubApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"
const val WEB_SERVER_NOTIFICATION_CHANNEL_ID = "web_server"

class RikkaHubApp : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // 诊断：崩溃安全日志落盘——native 崩溃进程秒死，内存日志全丢。
        // 最早初始化：重试失败原因/崩溃堆栈在崩溃后重开 app 仍能在日志页看到。
        runCatching {
            me.rerere.common.android.Logging.initCrashSafeLog(
                java.io.File(filesDir, "logs/crash-safe.log")
            )
        }
        // 诊断版：最早的生命周期装崩溃捕获，连 ContentProvider 阶段的崩溃也能抓到
        CrashHandler.install(this)
    }

    private fun launchCrashReport() {
        try {
            startActivity(Intent(this, me.rerere.rikkahub.ui.activity.CrashReportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Throwable) {}
    }

    override fun onCreate() {
        // 构建版本戳：Logs 页可见，用于确认安装的包版本
        android.util.Log.i("BUILD", "APP BUILD: r172 2026-10-28 crash-safe-log-diag")
        me.rerere.common.android.Logging.log("BUILD", "APP BUILD: r172 2026-10-28 crash-safe-log-diag")
        // native 崩溃诊断：Java 层 CrashHandler 抓不到 native crash（SIGSEGV 等），
        // 通过系统 ApplicationExitInfo 读上次退出原因写进 Logs。
        // 用途：重试闪退（换模型可复现/可绕过）但 Logs 无 CRASH → 疑似 native，
        // 此处能确认 REASON_CRASH_NATIVE(5) 与 signal 描述，把排查从猜测变成有据可依。
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val reasons = am.getHistoricalProcessExitReasons(packageName, 0, 5)
                if (reasons.isNotEmpty()) {
                    val sb = StringBuilder("上次退出记录(最多5条):\n")
                    reasons.forEach { info ->
                        val reasonName = when (info.reason) {
                            0 -> "REASON_UNKNOWN"; 1 -> "REASON_EXIT_SELF"
                            2 -> "REASON_SIGNALED"; 3 -> "REASON_LOW_MEMORY"
                            4 -> "REASON_CRASH"; 5 -> "REASON_CRASH_NATIVE(原生崩溃!)"
                            6 -> "REASON_ANR"; 7 -> "REASON_EXCESSIVE_RESOURCE_USAGE"
                            else -> "REASON_${info.reason}"
                        }
                        sb.append("- $reasonName status=${info.status}")
                        info.description?.let { sb.append(" desc=$it") }
                        sb.append("\n")
                    }
                    me.rerere.common.android.Logging.log("EXIT-INFO", sb.toString())
                }
            }
        }

        super.onCreate()
        if (CrashHandler.hasCrashed(this)) {
            // 上次启动崩过：跳过可能再次崩溃的初始化，直接展示崩溃报告
            launchCrashReport()
            return
        }
        // Restore files and settings before eager Koin singletons or workers can access them.
        try {
            val restored = runBlocking(Dispatchers.IO) {
                BackupManager.applyPendingRestore(this@RikkaHubApp, JsonInstant)
            }
            if (restored) {
                Toast.makeText(this, R.string.backup_page_restore_success, Toast.LENGTH_LONG).show()
            }
        } catch (e: RestoreFailedException) {
            Log.e(TAG, "Backup restore rolled back", e)
            Toast.makeText(this, "备份恢复失败，已保留原数据。请重新导入备份。", Toast.LENGTH_LONG).show()
        }
        try {
            startKoin {
                androidLogger()
                androidContext(this@RikkaHubApp)
                workManagerFactory()
                modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
            }
            this.createNotificationChannel()
            DatabaseUtil.setCursorWindowSize(32 * 1024 * 1024)
        } catch (e: Throwable) {
            Log.e(TAG, "App init(startKoin) crashed", e)
            CrashHandler.recordCrash(this, e)
            launchCrashReport()
            return
        }

        // (crash handler 已挪到 onCreate 最前面)

        // 索引自愈后处理：若数据库检测到 message_fts 损坏并已重建结构，
        // 这里在后台把所有对话重新灌入索引（只读对话、只写索引表），完成后清除标记
        if (me.rerere.rikkahub.data.db.AppDatabaseFactory.needsFullReindex(this)) {
            Thread {
                runCatching {
                    val repo = org.koin.core.context.GlobalContext.get()
                        .get<me.rerere.rikkahub.data.repository.ConversationRepository>()
                    // 复用仓库现成的全量重建（内部：清空索引表 → 遍历所有对话重新索引）
                    kotlinx.coroutines.runBlocking { repo.rebuildAllIndexes() }
                    me.rerere.rikkahub.data.db.AppDatabaseFactory.clearFullReindexFlag(this)
                    Log.i(TAG, "FTS full reindex done")
                }.onFailure { e ->
                    Log.e(TAG, "FTS full reindex failed", e)
                }
            }.start()
        }

        // delete temp files
        deleteTempFiles()

        // cleanup stale tool output files
        cleanupToolOutputs()

        // cleanup workspace temp dirs (proot + rootfs /tmp)
        cleanupWorkspaceTempDirs()

        // check workspace integrity (mark workspaces with missing files as broken after backup restore)
        checkWorkspaceIntegrity()

        // sync upload files to DB
        syncManagedFiles()

        // Extract builtin skills from assets after install/update
        extractBuiltinSkills()

        // Start WebServer if enabled in settings
        startWebServerIfEnabled()

        // Increment launch count
        incrementLaunchCount()

        // Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.Auto)
    }

    private fun incrementLaunchCount() {
        get<AppScope>().launch {
            runCatching {
                val count = get<SettingsStore>().incrementLaunchCount()
                Log.i(TAG, "incrementLaunchCount: $count")
            }.onFailure {
                Log.e(TAG, "incrementLaunchCount failed", it)
            }
        }
    }

    private fun cleanupWorkspaceTempDirs() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<WorkspaceManager>().cleanupAllTempDirs()
            }.onFailure {
                Log.e(TAG, "cleanupWorkspaceTempDirs failed", it)
            }
        }
    }

    private fun checkWorkspaceIntegrity() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<WorkspaceRepository>().checkIntegrity()
            }.onFailure {
                Log.e(TAG, "checkWorkspaceIntegrity failed", it)
            }
        }
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun cleanupToolOutputs() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                val dir = File(filesDir, FileFolders.TOOL_OUTPUTS)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        }
    }

    private fun extractBuiltinSkills() {
        get<AppScope>().launch(Dispatchers.IO) {
            get<SkillManager>().ensureBuiltinSkillsExtracted()
        }
    }

    private fun syncManagedFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<FilesManager>().syncFolder()
            }.onFailure {
                Log.e(TAG, "syncManagedFiles failed", it)
            }
        }
    }

    private fun startWebServerIfEnabled() {
        get<AppScope>().launch {
            runCatching {
                delay(500)
                val settings = get<SettingsStore>().settingsFlowRaw.first()
                if (settings.webServerEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@RikkaHubApp,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "startWebServerIfEnabled: notification permission not granted, skipping")
                        return@launch
                    }
                    if (Build.VERSION.SDK_INT >= 37 &&
                        !settings.webServerLocalhostOnly &&
                        ContextCompat.checkSelfPermission(
                            this@RikkaHubApp,
                            android.Manifest.permission.ACCESS_LOCAL_NETWORK
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "startWebServerIfEnabled: local network permission not granted, skipping")
                        return@launch
                    }
                    val intent = Intent(this@RikkaHubApp, WebServerService::class.java).apply {
                        action = WebServerService.ACTION_START
                        putExtra(WebServerService.EXTRA_PORT, settings.webServerPort)
                        putExtra(WebServerService.EXTRA_LOCALHOST_ONLY, settings.webServerLocalhostOnly)
                    }
                    startForegroundService(intent)
                }
            }.onFailure {
                Log.e(TAG, "startWebServerIfEnabled failed", it)
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)

        val webServerChannel = NotificationChannelCompat
            .Builder(WEB_SERVER_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_web_server))
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(webServerChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
        stopService(Intent(this, WebServerService::class.java))
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "AppScope exception", e)
    }
)
