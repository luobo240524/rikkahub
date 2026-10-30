package me.rerere.rikkahub.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.utils.CrashHandler
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

class SafeModeActivity : ComponentActivity() {
    private val settingsStore by inject<SettingsStore>()
    private val appDatabase by inject<me.rerere.rikkahub.data.db.AppDatabase>()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stackTrace = CrashHandler.getStackTrace(this)
        CrashHandler.clearCrashed(this)
        enableEdgeToEdge()
        setContent {
            RikkahubTheme {
                val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
                var showAssistantPicker by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("待机") }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.safe_mode_title)) })
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.safe_mode_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = stringResource(
                                R.string.safe_mode_current_assistant,
                                settings.getCurrentAssistant().name.ifEmpty { stringResource(R.string.safe_mode_default_assistant) }),
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Button(
                            onClick = { showAssistantPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.safe_mode_switch_assistant))
                        }

                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(this@SafeModeActivity, RouteActivity::class.java))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.safe_mode_enter_app))
                        }

                        // ---- 应急工具（生图卡死专项 r177）----
                        Text(
                            text = "应急工具",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = {
                                status = "① 修复执行中（重置词典+清库+WAL压缩），请稍候…"
                                scope.launch {
                                    status = runCatching {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val dictOk = me.rerere.rikkahub.data.db.fts.SimpleDictManager.resetExtractedDict(applicationContext)
                                            val (scanned, cleaned) = me.rerere.rikkahub.data.db.DatabaseRepair.repairGiantBase64(applicationContext, appDatabase, force = true)
                                            appDatabase.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                                            "修复完成: 词典已重置=$dictOk, 扫描=$scanned 行, 清理=$cleaned 行, WAL已压缩。请点「进入应用」重试"
                                        }
                                    }.getOrElse { "修复失败: ${it.message}" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("① 一键修复（重置词典+清理库+WAL压缩）") }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    androidx.work.WorkManager.getInstance(applicationContext).cancelAllWork()
                                    status = "已请求取消全部后台任务（唤醒重试链等）"
                                }.onFailure { status = "取消失败: ${it.message}" }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("② 取消全部后台任务（唤醒重试等）") }
                        OutlinedButton(
                            onClick = {
                                status = "③ 诊断导出中…"
                                scope.launch {
                                    status = runCatching {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            exportDiagnostics(applicationContext, appDatabase)
                                        }
                                    }.getOrElse { "导出失败: ${it.message}" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("③ 导出诊断报告（发回给AI分析）") }

                        if (stackTrace != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.safe_mode_crash_report),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                OutlinedButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("crash", stackTrace))
                                    }
                                ) {
                                    Text(stringResource(R.string.safe_mode_copy))
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                val vScroll = rememberScrollState()
                                val hScroll = rememberScrollState()
                                Text(
                                    text = stackTrace,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .verticalScroll(vScroll)
                                        .horizontalScroll(hScroll),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

                if (showAssistantPicker) {
                    AssistantPickerSheet(
                        settings = settings,
                        onAssistantSelected = { assistantId ->
                            scope.launch { settingsStore.updateAssistant(assistantId) }
                            context.writeStringPreference("lastConversationId", null)
                            showAssistantPicker = false
                        },
                        onDismiss = { showAssistantPicker = false }
                    )
                }
            }
        }
    }
    /** 导出诊断报告：退出记录/文件大小/内存日志/crash-safe 日志/DB 统计。
     *  分两阶段写盘：先落基础报告，再做 DB 查询后重写——DB 若挂死，基础报告也已在磁盘上。 */
    private suspend fun exportDiagnostics(
        context: Context,
        db: me.rerere.rikkahub.data.db.AppDatabase,
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val ts = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
        val fileName = "rikkahub_diag_" +
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date(ts)) + ".txt"

        fun buildReport(includeDb: Boolean): String {
            val sb = StringBuilder()
            sb.appendLine("=== RikkaHub 诊断报告 r177 ===")
            sb.appendLine("导出时间: ${fmt.format(java.util.Date(ts))}")
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    sb.appendLine("== 退出记录 ==")
                    am.getHistoricalProcessExitReasons(context.packageName, 0, 5).forEach { info ->
                        val name = when (info.reason) {
                            0 -> "UNKNOWN"; 1 -> "EXIT_SELF"; 2 -> "SIGNALED"; 3 -> "LOW_MEMORY"
                            4 -> "CRASH"; 5 -> "CRASH_NATIVE"; 6 -> "ANR"; 7 -> "EXCESSIVE_RESOURCE_USAGE"
                            else -> "REASON_${info.reason}"
                        }
                        sb.appendLine("- $name status=${info.status} at=${fmt.format(java.util.Date(info.timestamp))} desc=${info.description ?: ""}")
                    }
                }
            }
            sb.appendLine("== 关键文件 ==")
            fun dumpDir(dir: java.io.File?, prefix: String) {
                val files = dir?.listFiles() ?: return
                for (f in files) {
                    if (f.isDirectory) dumpDir(f, "$prefix${f.name}/")
                    else sb.appendLine("$prefix${f.name}: ${f.length()} bytes")
                }
            }
            runCatching { dumpDir(context.getDatabasePath("rikka_hub").parentFile, "databases/") }
            runCatching { dumpDir(java.io.File(context.filesDir, "datastore"), "datastore/") }
            runCatching { dumpDir(java.io.File(context.filesDir, "logs"), "logs/") }
            runCatching { dumpDir(java.io.File(context.filesDir, "simple_dict"), "simple_dict/") }
            if (includeDb) {
                runCatching {
                    db.openHelper.writableDatabase.query(
                        "SELECT count(*), ifnull(max(length(messages)),0) FROM message_node"
                    ).use {
                        if (it.moveToFirst()) {
                            sb.appendLine("== 数据库统计 ==")
                            sb.appendLine("message_node: rows=${it.getLong(0)} maxRowLen=${it.getLong(1)}")
                        }
                    }
                }.onFailure { sb.appendLine("== 数据库统计 == 查询失败: ${it.message}") }
            }
            runCatching {
                sb.appendLine("== 最近内存日志 ==")
                me.rerere.common.android.Logging.getTextLogs().take(60).forEach {
                    sb.appendLine("${it.timestamp}|${it.tag}|${it.message.take(300)}")
                }
            }
            runCatching {
                val f = java.io.File(context.filesDir, "logs/crash-safe.log")
                if (f.exists()) {
                    sb.appendLine("== crash-safe.log 末尾 ==")
                    sb.appendLine(f.readText().takeLast(6000))
                }
            }
            return sb.toString()
        }

        val savedPaths = mutableListOf<String>()
        fun writeAll(content: String) {
            savedPaths.clear()
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val resolver = context.contentResolver
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        values.clear()
                        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        savedPaths.add("下载/$fileName")
                    }
                }
            }
            runCatching {
                val dir = java.io.File(context.getExternalFilesDir(null), "diag")
                dir.mkdirs()
                val f = java.io.File(dir, fileName)
                f.writeText(content)
                savedPaths.add(f.absolutePath)
            }
        }

        writeAll(buildReport(includeDb = false))
        val full = buildReport(includeDb = true)
        writeAll(full)
        if (savedPaths.isEmpty()) "导出失败"
        else "已导出: " + savedPaths.joinToString(" ; ")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantPickerSheet(
    settings: Settings,
    onAssistantSelected: (Uuid) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }
    val filteredAssistants = remember(settings.assistants, selectedTagIds) {
        if (selectedTagIds.isEmpty()) settings.assistants
        else settings.assistants.filter { it.tags.any { id -> id in selectedTagIds } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.safe_mode_assistants),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (settings.assistantTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(settings.assistantTags, key = { it.id }) { tag ->
                        FilterChip(
                            onClick = {
                                selectedTagIds = if (tag.id in selectedTagIds) {
                                    selectedTagIds - tag.id
                                } else {
                                    selectedTagIds + tag.id
                                }
                            },
                            label = { Text(tag.name) },
                            selected = tag.id in selectedTagIds,
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredAssistants, key = { it.id }) { assistant ->
                    val checked = assistant.id == settings.assistantId
                    Card(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onAssistantSelected(assistant.id)
                            }
                        },
                        modifier = Modifier.animateItem(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = assistant.name.ifEmpty { stringResource(R.string.safe_mode_default_assistant) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}
