package me.rerere.rikkahub.ui.pages.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Calendar03
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.hugeicons.stroke.Voice
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant

/**
 * 「发现」聚合页：二期新功能的统一入口，后续功能都往这里加，顶栏收纳菜单不膨胀。
 * 卡片带红点:助手点赞/评论了你的内容且未读时点亮。
 */
@Composable
fun DiscoverPage() {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val badgeVm: SocialBadgeVM = koinViewModel()
    val settingsStore = koinInject<SettingsStore>()
    val setting by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val unreadCounts by badgeVm.unreadCounts.collectAsStateWithLifecycle()

    fun unreadOf(type: String) = unreadCounts.firstOrNull { it.targetType == type }?.cnt ?: 0

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.discovery_title))
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
                actions = { DiscoverThemeMenuButton() },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Moments) },
                        leadingContent = {
                            Icon(HugeIcons.InLove, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(stringResource(R.string.discovery_entry_moments)) },
                        supportingContent = { Text(stringResource(R.string.discovery_entry_moments_desc)) },
                        trailingContent = { RedDot(unreadOf("moment")) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.MoodCheckin) },
                        leadingContent = {
                            Icon(HugeIcons.Clock02, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(stringResource(R.string.discovery_entry_mood)) },
                        supportingContent = { Text(stringResource(R.string.discovery_entry_mood_desc)) },
                        trailingContent = { RedDot(unreadOf("checkin")) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Secrets) },
                        leadingContent = {
                            Icon(HugeIcons.QuillWrite01, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text("心里话") },
                        supportingContent = { Text("写给自己/对方的小秘密,可设置是否可见") },
                        trailingContent = { RedDot(unreadOf("secret")) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Timeline) },
                        leadingContent = {
                            Icon(HugeIcons.Calendar03, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text("时间轴") },
                        supportingContent = { Text("自动提炼/手动记录/串联动态/节点提醒") },
                        trailingContent = { RedDot(unreadOf("timeline")) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Music) },
                        leadingContent = {
                            Icon(HugeIcons.MusicNote03, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text("一起听") },
                        supportingContent = { Text("点歌/切歌/暂停,和 TA 边听边聊") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.ReadTogether) },
                        leadingContent = {
                            Icon(HugeIcons.BookOpen01, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text("共同阅读") },
                        supportingContent = { Text("导入 txt/epub/pdf,人机同步进度,标注评论共享") },
                        trailingContent = { RedDot(unreadOf("read_annotation")) },
                    )
                    // 语音通话:仅在当前助手开启开关后显示(助手页→语音通话)
                    if (setting.getCurrentAssistant().voiceCallEnabled) {
                        item(
                            onClick = { navController.navigate(Screen.VoiceCall()) },
                            leadingContent = {
                                Icon(HugeIcons.Voice, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            headlineContent = { Text("语音通话") },
                            supportingContent = { Text("像打电话一样和 TA 实时聊天") },
                        )
                    }
                }
            }
            item {
                // 预留位：后续功能(主题包/热力图等三期候选)往 CardGroup 里加
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.discovery_more_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(HugeIcons.Earth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
        }
    }
}

/** 8dp 红点(count>0 时显示) */
@Composable
private fun RedDot(count: Int) {
    if (count > 0) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(8.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
    }
}

