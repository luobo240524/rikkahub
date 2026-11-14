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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.social.socialAuthorName
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.discover.DiscoverTheme
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.discoverForegroundOverlay
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.US)

/** 经典糖果便签色(与心里话页一致;主题带 noteColors 时用主题纸色) */
private val DefaultStickyColors = listOf(
    Color(0xFFFFF6C9), Color(0xFFFFE3EC), Color(0xFFE2F6E4), Color(0xFFE1EFFB), Color(0xFFF1E6FF),
)

/** 便签交替微倾角(与心里话页一致) */
private val StickyAngles = listOf(-1.4f, 1.2f, 0.8f, -1f, 1.5f, 0.6f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelinePage(
    vm: TimelineVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val days by vm.days.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    // 主题复用发现页(朋友圈/打卡同一套),便签式卡片底色/圆角/文字色全部取自 theme
    val theme = rememberDiscoverTheme()
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时间轴") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                colors = discoverTopBarColors(theme),
                actions = { DiscoverThemeMenuButton() },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .discoverPageBackground(theme),
        containerColor = Color.Transparent,
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
            Box(modifier = Modifier.fillMaxSize().discoverForegroundOverlay(theme)) {
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
                                color = theme.sub,
                            )
                        }
                        items(day.items, key = { item ->
                            when (item) {
                                is TimelineItem.Event -> "e${item.event.id}"
                                is TimelineItem.Dynamic -> "d${item.kind}_${item.sourceId}"
                            }
                        }) { item ->
                            TimelineItemRow(item = item, theme = theme, assistant = assistant, onDelete = { vm.delete(it) })
                        }
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
    theme: DiscoverTheme,
    assistant: Assistant,
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
            is TimelineItem.Event -> EventCard(item.event, theme, assistant, onDelete)
            is TimelineItem.Dynamic -> DynamicCard(item, theme, assistant)
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
    theme: DiscoverTheme,
    assistant: Assistant,
    onDelete: (Long) -> Unit,
) {
    val now = System.currentTimeMillis()
    val due = event.remindAt != null && event.remindAt <= now
    val bgOverride = if (due) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f) else null
    // 作者署真名:assistant → 当前助手名,替代"TA 记录"代词
    val authorLabel = if (event.author == "user") null else socialAuthorName(event.author, assistant)
    TimelineStickyCard(
        seed = event.id,
        theme = theme,
        bgOverride = bgOverride,
        onLongClick = { onDelete(event.id) },
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.eventTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.sub,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        event.type == "reminder" -> "节点提醒"
                        event.type == "call" -> "通话"
                        event.type == "auto" -> "AI 提炼"
                        else -> "手动"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.type == "reminder") {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        theme.sub
                    },
                )
                if (authorLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$authorLabel 记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.name,
                    )
                }
            }
        },
        body = {
            Spacer(Modifier.height(4.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.text,
            )
            if (event.content.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
            }
        },
    )
}

@Composable
private fun DynamicCard(
    item: TimelineItem.Dynamic,
    theme: DiscoverTheme,
    assistant: Assistant,
) {
    val authorLabel = socialAuthorName(item.author, assistant)
    TimelineStickyCard(
        seed = item.sourceId,
        theme = theme,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TIME_FORMAT.format(Date(item.sortMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.sub,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (item.kind) {
                        "moment" -> "朋友圈"
                        "checkin" -> "打卡"
                        else -> "心里话"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.sub,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = authorLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.name,
                )
            }
        },
        body = {
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.masked) {
                    theme.sub.copy(alpha = 0.7f)
                } else {
                    theme.text
                },
            )
        },
    )
}

/**
 * 心里话同款便签底件:纸色随主题轮换(noteColors)+微倾+投影,
 * 上缘贴和纸胶带(时间轴签名装饰);杂志编辑等扁平主题(cardFlat)不倾不投影,底部分隔线。
 */
@Composable
private fun TimelineStickyCard(
    seed: Long,
    theme: DiscoverTheme,
    bgOverride: Color? = null,
    onLongClick: (() -> Unit)? = null,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    val colors = theme.noteColors ?: DefaultStickyColors
    val paper = colors[((seed % colors.size) + colors.size).toInt() % colors.size]
    val rot = StickyAngles[((seed % StickyAngles.size) + StickyAngles.size).toInt() % StickyAngles.size]
    val flat = theme.cardFlat
    val bg = bgOverride ?: if (flat) Color.Transparent else paper
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = bg,
            shadowElevation = if (flat) 0.dp else 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .rotate(if (flat) 0f else rot)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                header()
                body()
                if (flat && theme.divider != null) {
                    Spacer(Modifier.height(2.dp))
                    HorizontalDivider(color = theme.divider, thickness = 0.8.dp)
                }
            }
        }
        if (!flat) {
            // 和纸胶带:便签贴在时间轴上的视觉(倾斜方向与便签相反,像按住一角)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-6).dp)
                    .width(56.dp)
                    .height(14.dp)
                    .background(Color(0x8CF6C177))
                    .rotate(if (rot >= 0f) -2.5f else 2.5f),
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
