package me.rerere.rikkahub.ui.pages.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.ui.context.LocalNavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelinePage(
    vm: TimelineVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val days by vm.days.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时间轴") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(HugeIcons.Add01, contentDescription = "手动记录")
            }
        },
    ) { innerPadding ->
        if (days.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "还没有记录\n对话里被提到的重要日子会自动出现在这里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                days.forEach { day ->
                    item(key = "day_${day.date}") {
                        Text(
                            text = day.date,
                            modifier = Modifier
                                .padding(start = 40.dp, top = 6.dp, end = 16.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(day.items, key = { item ->
                        when (item) {
                            is TimelineItem.Event -> "e${item.event.id}"
                            is TimelineItem.Dynamic -> "d${item.kind}_${item.sourceId}"
                        }
                    }) { item ->
                        TimelineItemRow(item = item, onDelete = { vm.delete(it) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        TimelineAddDialog(
            onConfirm = { title, content, date, time ->
                vm.addManual(title, content, date, time)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun TimelineItemRow(
    item: TimelineItem,
    onDelete: (Long) -> Unit,
) {
    val spineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .drawBehind {
                // 主轴线:贯穿所有节点的竖线(drawBehind 在 padding 之后,坐标为内容区,节点列 22dp 宽的中心 = 11dp)
                val x = 11.dp.toPx()
                drawLine(
                    color = spineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    alpha = 0.35f,
                )
            },
        verticalAlignment = Alignment.Top,
    ) {
        // 节点列:圆点 + 上下延伸的轴线
        Box(
            modifier = Modifier
                .width(22.dp)
                .align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(nodeBrush(item)),
            )
        }
        Spacer(Modifier.width(10.dp))
        when (item) {
            is TimelineItem.Event -> EventCard(item.event, onDelete)
            is TimelineItem.Dynamic -> DynamicCard(item)
        }
    }
}

private fun nodeBrush(item: TimelineItem): Brush = when {
    item is TimelineItem.Event && item.event.type == "reminder" ->
        Brush.linearGradient(listOf(Color(0xFFF2B85C), Color(0xFFE09B32)))
    item is TimelineItem.Event && item.event.author == "assistant" ->
        Brush.linearGradient(listOf(Color(0xFF6F9BFF), Color(0xFFC07BFF)))
    item is TimelineItem.Dynamic && item.kind == "secret" ->
        Brush.linearGradient(listOf(Color(0xFFC07BFF), Color(0xFF9B6BFF)))
    else -> Brush.linearGradient(
        listOf(
            MaterialThemeTokenFallback,
            MaterialThemeTokenFallback,
        )
    )
}

// 手动/动态节点用中性色;不直接取 MaterialTheme(此处为非 composable 上下文)
private val MaterialThemeTokenFallback = Color(0xFF8A8A8E)

@Composable
private fun EventCard(
    event: me.rerere.rikkahub.data.db.entity.TimelineEventEntity,
    onDelete: (Long) -> Unit,
) {
    val now = System.currentTimeMillis()
    val due = event.remindAt != null && event.remindAt <= now
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onDelete(event.id) }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (due) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.eventTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        event.type == "reminder" -> "节点提醒"
                        event.type == "auto" -> "AI 提炼"
                        else -> "手动"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.type == "reminder") {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (event.author == "assistant") {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "TA 记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (event.content.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DynamicCard(item: TimelineItem.Dynamic) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TIME_FORMAT.format(Date(item.sortMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (item.kind) {
                        "moment" -> "朋友圈"
                        "checkin" -> "打卡"
                        else -> "心里话"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.masked) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun TimelineAddDialog(
    onConfirm: (title: String, content: String, date: String, time: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = TimelineVM.dayKeyOf(System.currentTimeMillis())
    val nowTime = TIME_FORMAT.format(Date())
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var time by remember { mutableStateOf(nowTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("记点什么") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("详情(可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("日期") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("时间") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, content, date, time) },
                enabled = title.isNotBlank(),
            ) { Text("记录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
