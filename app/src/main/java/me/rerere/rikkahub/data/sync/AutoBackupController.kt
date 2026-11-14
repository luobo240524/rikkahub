package me.rerere.rikkahub.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.WebDavConfig
import me.rerere.rikkahub.data.sync.webdav.WebDavSync

private const val TAG = "AutoBackupController"

/**
 * 自动备份:开启后每天一次把「设置+数据库」推到 WebDAV(不备文件,体积小),
 * 云端保留最近 N 份,超出自动删最旧;失败静默,下次启动重试。
 * 触发点在 RikkaHubApp.onCreate(AppScope 后台协程),无需前台服务/常驻。
 */
class AutoBackupController(
    private val settingsStore: SettingsStore,
    private val webDavSync: WebDavSync,
) {
    suspend fun runIfDue() = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlowRaw.first()
        val config = settings.backupReminderConfig
        if (!config.autoBackupEnabled) return@withContext
        val dav = settings.webDavConfig
        // WebDAV 未配置(url 为空)时静默跳过
        if (dav.url.isBlank()) return@withContext
        val now = System.currentTimeMillis()
        if (now - config.lastAutoBackupTime < 24L * 60 * 60 * 1000) return@withContext

        runCatching {
            // settings.json 恒包含;DATABASE 项控制数据库;FILES 体积大不备
            webDavSync.backup(dav.copy(items = listOf(WebDavConfig.BackupItem.DATABASE)))
            settingsStore.update { latest ->
                latest.copy(
                    backupReminderConfig = latest.backupReminderConfig.copy(
                        lastAutoBackupTime = now,
                        // 自动备份成功也算完成一次备份,手动备份提醒不再重复打扰
                        lastBackupTime = now,
                    )
                )
            }
            Log.i(TAG, "auto backup uploaded")
            pruneOldBackups(dav, config.autoBackupKeepCount)
        }.onFailure {
            Log.w(TAG, "auto backup failed, will retry on next launch", it)
        }
    }

    /** 云端备份按修改时间倒序,超出保留份数的从最旧开始删(单条失败不影响其余) */
    private suspend fun pruneOldBackups(config: WebDavConfig, keepCount: Int) {
        if (keepCount <= 0) return
        runCatching {
            val files = webDavSync.listBackupFiles(config).sortedByDescending { it.lastModified }
            files.drop(keepCount).forEach { item ->
                runCatching { webDavSync.deleteBackupFile(config, item) }
                    .onFailure { Log.w(TAG, "prune backup failed: ${item.name}", it) }
            }
        }.onFailure { Log.w(TAG, "list backups for prune failed", it) }
    }
}
