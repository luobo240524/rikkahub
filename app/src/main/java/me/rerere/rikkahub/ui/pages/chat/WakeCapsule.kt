package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.rikkahub.service.WakeRecord
import me.rerere.rikkahub.service.WakeRegistry
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

/**
 * 唤醒胶囊：顶栏小胶囊显示当前会话的唤醒任务概况，
 * 点击弹出面板可视化全部记录（待触发/已触发/已取消）。
 * 没有任何记录时不显示，出现/消失本身就是「唤醒有没有生效」的信号。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeCapsule(conversationId: Uuid) {
    val registry = remember { GlobalContext.get().get<WakeRegistry>() }
    var records by remember(conversationId) { mutableStateOf<List<WakeRecord>>(emptyList()) }
    var showSheet by remember { mutableStateOf(false) }

    // 初次加载 + 每 5 秒轮询（唤醒记录文件很小，开销可忽略）
    LaunchedEffect(conversationId) {
        while (true) {
            records = runCatching {
                registry.list(conversationId.toString())
            }.getOrElse { emptyList() }
            delay(5_000)
        }
    }

    if (records.isEmpty()) return

    val pending = records.filter { it.status == "PENDING" }

    // 就一个时钟线条图标（透明底，与顶栏其他图标一致），点开看全部唤醒
    IconButton(onClick = { showSheet = true }) {
        Icon(
            imageVector = HugeIcons.Clock02,
            contentDescription = "唤醒任务",
        )
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "⏰ 唤醒任务",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "本对话共 ${records.size} 项，待触发 ${pending.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val sorted = records.sortedWith(
                compareByDescending<WakeRecord> { it.status == "PENDING" }
                    .thenComparator { a, b ->
                        if (a.status == "PENDING" && b.status == "PENDING") {
                            a.fireAt.compareTo(b.fireAt)
                        } else {
                            b.updatedAt.compareTo(a.updatedAt)
                        }
                    }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.taskKey }) { r ->
                    WakeRecordRow(r)
                }
            }
        }
    }
}

private val fireTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

@Composable
private fun WakeRecordRow(r: WakeRecord) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = when (r.type) {
                    "once" -> "⏰"
                    "daily" -> "🔁"
                    "weekly" -> "📅"
                    else -> "🗓"
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = r.note.ifBlank { "（未注明事项）" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = typeLabel(r.type) + " · " + fireTimeFormat.format(Date(r.fireAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = when (r.status) {
                    "PENDING" -> "待触发"
                    "FIRED" -> "已触发"
                    "CANCELLED" -> "已取消"
                    else -> r.status
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (r.status) {
                    "PENDING" -> MaterialTheme.colorScheme.primary
                    "FIRED" -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}

private fun typeLabel(type: String): String = when (type) {
    "once" -> "一次性"
    "daily" -> "每天"
    "weekly" -> "每周"
    "monthly" -> "每月"
    else -> type
}
