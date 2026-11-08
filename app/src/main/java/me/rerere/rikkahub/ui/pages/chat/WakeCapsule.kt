package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.hugeicons.stroke.Voice
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.WakeRecord
import me.rerere.rikkahub.service.WakeRegistry
import me.rerere.rikkahub.ui.pages.discover.SocialBadgeVM
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

/**
 * 聊天页顶栏社交入口(常驻)：点按弹出下拉(发现/朋友圈/心里话/打卡/时间轴/语音通话,点击直达对应页)。
 * 下拉内容与发现页入口保持一致;「语音通话」仅当前助手开启 voiceCallEnabled 时出现,点按 = 拨打当前助手。
 * 长按打开本会话的唤醒任务列表。
 * (r208 起不再受 showFeatureHub 影响:历史残留 false 曾让顶栏退回时钟形态)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WakeCapsule(conversationId: Uuid, assistant: Assistant? = null) {
    val registry = remember { GlobalContext.get().get<WakeRegistry>() }
    val navController = me.rerere.rikkahub.ui.context.LocalNavController.current
    val socialBadgeVm: SocialBadgeVM = koinViewModel()
    val socialUnread by socialBadgeVm.unreadCounts.collectAsStateWithLifecycle()
    val timelineUnread by socialBadgeVm.timelineUnread.collectAsStateWithLifecycle()
    var records by remember(conversationId) { mutableStateOf<List<WakeRecord>>(emptyList()) }
    var showSheet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // 初次加载 + 每 5 秒轮询（唤醒记录文件很小，开销可忽略）
    LaunchedEffect(conversationId) {
        while (true) {
            records = runCatching {
                registry.list(conversationId.toString())
            }.getOrElse { emptyList() }
            delay(5_000)
        }
    }

    val pending = records.count { it.status == "PENDING" || it.status == "RETRYING" }
    // 地球聚合红点 = 全部社交未读 + 时间轴未读提醒
    val socialUnreadTotal = socialUnread.sumOf { it.cnt } + timelineUnread

    fun unreadOf(type: String): Int =
        socialUnread.firstOrNull { it.targetType == type }?.cnt ?: 0

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Icon(
                imageVector = HugeIcons.Earth,
                contentDescription = "社交入口",
            )
            // 角标:社交未读红点(右上)+唤醒待触发点(左上)
            if (socialUnreadTotal > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
            if (pending > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        // 点按=社交下拉(发现/朋友圈/心里话/打卡);长按=唤醒任务列表
        // (showFeatureHub 旧路径已删:历史残留 false 会让顶栏退回时钟形态,r208 起无条件常驻)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .combinedClickable(
                    onClick = { menuOpen = !menuOpen },
                    onLongClick = { showSheet = true },
                ),
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("发现") },
                leadingIcon = { Icon(HugeIcons.Earth, null) },
                onClick = {
                    menuOpen = false
                    navController.navigate(Screen.Discover)
                },
            )
            DropdownMenuItem(
                text = { Text("朋友圈") },
                leadingIcon = { Icon(HugeIcons.InLove, null) },
                trailingIcon = {
                    if (unreadOf("moment") > 0) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    }
                },
                onClick = {
                    menuOpen = false
                    navController.navigate(Screen.Moments)
                },
            )
            DropdownMenuItem(
                text = { Text("心里话") },
                leadingIcon = { Icon(HugeIcons.QuillWrite01, null) },
                trailingIcon = {
                    if (unreadOf("secret") > 0) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    }
                },
                onClick = {
                    menuOpen = false
                    navController.navigate(Screen.Secrets)
                },
            )
            DropdownMenuItem(
                text = { Text("每日心情打卡") },
                leadingIcon = { Icon(HugeIcons.Clock02, null) },
                trailingIcon = {
                    if (unreadOf("checkin") > 0) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    }
                },
                onClick = {
                    menuOpen = false
                    navController.navigate(Screen.MoodCheckin)
                },
            )
            // r216:下拉与发现页入口完全一致——补时间轴(带未读红点)与语音通话(仅开启通话的助手)
            DropdownMenuItem(
                text = { Text("时间轴") },
                leadingIcon = { Icon(HugeIcons.Calendar03, null) },
                trailingIcon = {
                    if (timelineUnread > 0) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                    }
                },
                onClick = {
                    menuOpen = false
                    navController.navigate(Screen.Timeline)
                },
            )
            if (assistant?.voiceCallEnabled == true) {
                DropdownMenuItem(
                    text = { Text("语音通话") },
                    leadingIcon = { Icon(HugeIcons.Voice, null) },
                    onClick = {
                        menuOpen = false
                        navController.navigate(Screen.VoiceCall())
                    },
                )
            }
        }
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
                val retrying = records.count { it.status == "RETRYING" }
                Text(
                    text = if (retrying > 0) {
                        "本对话共 ${records.size} 项 · 待触发 $pending · 重试中 $retrying"
                    } else {
                        "本对话共 ${records.size} 项，待触发 $pending 项"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val statusRank: (String) -> Int = {
                when (it) {
                    "PENDING" -> 0
                    "RETRYING" -> 1
                    "FIRED" -> 2
                    "CANCELLED" -> 3
                    else -> 4
                }
            }
            val sorted = records.sortedWith(
                compareBy<WakeRecord> { statusRank(it.status) }
                    .thenComparator { a, b ->
                        if (a.status == "PENDING" || a.status == "RETRYING") {
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
                    "RETRYING" -> "重试中"
                    "FIRED" -> "已触发"
                    "FAILED" -> "已放弃"
                    "CANCELLED" -> "已取消"
                    else -> r.status
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (r.status) {
                    "PENDING" -> MaterialTheme.colorScheme.primary
                    "RETRYING" -> MaterialTheme.colorScheme.tertiary
                    "FAILED" -> MaterialTheme.colorScheme.error
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
