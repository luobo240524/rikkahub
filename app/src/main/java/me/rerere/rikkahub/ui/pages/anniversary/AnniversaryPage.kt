package me.rerere.rikkahub.ui.pages.anniversary

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.data.ai.tools.local.nextOccurrence
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.AnniversaryEntity
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import me.rerere.rikkahub.utils.plus
import java.time.LocalDate

/**
 * 纪念日管理页(第二波):时间轴顶栏入口进入,列表+增删改编辑弹层。
 * 与 TA 的 anniversary_write 工具同库:双方录入互相可见、都能编辑。
 * 页面文案用真实称呼(助手名/用户昵称),不用「TA/用户」代称(r273验收反馈)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryPage(
    vm: AnniversaryVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val list by vm.list.collectAsStateWithLifecycle()
    val theme = rememberDiscoverTheme()
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val assistantName = assistant.name.ifBlank { "助手" }

    var editorTarget by remember { mutableStateOf<AnniversaryEntity?>(null) } // 非空=编辑既有条目
    var editorOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AnniversaryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("纪念日") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                colors = discoverTopBarColors(theme),
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .discoverPageBackground(theme),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editorTarget = null
                editorOpen = true
            }) {
                Icon(HugeIcons.Add01, contentDescription = "新增纪念日")
            }
        },
    ) { innerPadding ->
        if (list.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "还没有纪念日\n点右下角添加,或在对话里让${assistantName}记下重要日子",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding + PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                state = rememberLazyListState(),
            ) {
                items(list, key = { it.id }) { a ->
                    AnniversaryRow(
                        entity = a,
                        onClick = {
                            editorTarget = a
                            editorOpen = true
                        },
                        onLongClick = { deleteTarget = a },
                    )
                }
            }
        }
    }

    if (editorOpen) {
        val editingNow = editorTarget
        val onDeleteFn: (() -> Unit)? = editingNow?.let { target ->
            {
                editorOpen = false
                deleteTarget = target
            }
        }
        AnniversaryEditorSheet(
            target = editingNow,
            assistantName = assistantName,
            onSave = { id, name, month, day, year, yearly, note ->
                vm.save(id, name, month, day, year, yearly, note)
                editorOpen = false
            },
            onDelete = onDeleteFn,
            onDismiss = { editorOpen = false },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除纪念日") },
            text = { Text("删除「${target.name}」?删除后 $assistantName 不会再收到当天的提醒。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target.id)
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 一行纪念日:蛋糕块+名称+日期类型+倒计时;点行编辑,长按快捷删除 */
@Composable
private fun AnniversaryRow(
    entity: AnniversaryEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val next = nextOccurrence(entity)
    val dateLabel = buildString {
        append("${entity.month}月${entity.day}日")
        append(" · ")
        append(if (entity.repeatYearly) "每年" else "单次")
        if (!entity.repeatYearly && entity.targetYear != null) append(" · ${entity.targetYear}")
    }
    val countLabel = when {
        next != null && next.second == 0L -> "就是今天"
        next != null -> "还有${next.second}天"
        else -> "已过去"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("🎂", fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entity.note.isNotBlank()) {
                Text(
                    text = entity.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = if (countLabel == "就是今天") {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            },
        ) {
            Text(
                text = countLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (countLabel == "就是今天") {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** 新增/编辑底部弹层:名称+每年重复开关+年月日选择+备注;编辑态带删除入口(确认后删) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnniversaryEditorSheet(
    target: AnniversaryEntity?,
    assistantName: String,
    onSave: (id: Long?, name: String, month: Int, day: Int, year: Int?, yearly: Boolean, note: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var name by remember { mutableStateOf(target?.name.orEmpty()) }
    var yearly by remember { mutableStateOf(target?.repeatYearly ?: true) }
    var month by remember { mutableStateOf(target?.month ?: today.monthValue) }
    var day by remember { mutableStateOf(target?.day ?: today.dayOfMonth) }
    var year by remember { mutableStateOf(target?.targetYear ?: today.year) }
    var note by remember { mutableStateOf(target?.note.orEmpty()) }

    val maxDay = remember(month, yearly, year) {
        val y = if (yearly) 2024 else year.coerceAtLeast(2000) // 每年重复按闰年口径取2月最大天数
        java.time.YearMonth.of(y, month).lengthOfMonth()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = if (target == null) "新增纪念日" else "编辑纪念日",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "每年重复",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = yearly, onCheckedChange = { yearly = it })
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (yearly) "月 / 日" else "年 / 月 / 日",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!yearly) {
                    PickerBox(
                        label = "${year}年",
                        modifier = Modifier.weight(1f),
                    ) { close ->
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(listOf(today.year - 1, today.year, today.year + 1, today.year + 2)) { y ->
                                DropdownMenuItem(
                                    text = { Text("${y}年") },
                                    onClick = { year = y; close() },
                                )
                            }
                        }
                    }
                }
                PickerBox(
                    label = "${month}月",
                    modifier = Modifier.weight(1f),
                ) { close ->
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items((1..12).toList()) { m ->
                            DropdownMenuItem(
                                text = { Text("${m}月") },
                                onClick = {
                                    month = m
                                    val y = if (yearly) 2024 else year
                                    val cap = java.time.YearMonth.of(y.coerceAtLeast(2000), m).lengthOfMonth()
                                    if (day > cap) day = cap
                                    close()
                                },
                            )
                        }
                    }
                }
                PickerBox(
                    label = "${day}日",
                    modifier = Modifier.weight(1f),
                ) { close ->
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items((1..maxDay).toList()) { d ->
                            DropdownMenuItem(
                                text = { Text("${d}日") },
                                onClick = { day = d; close() },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注($assistantName 看得见,当天会收到提示)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete() }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onSave(target?.id, name.trim(), month, day, year, yearly, note)
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("保存")
                }
            }
            if (onDelete != null) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
            }
        }
    }
}

/** 下拉选择块:点击展开菜单(内容为菜单项 lambda) */
@Composable
private fun PickerBox(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}
