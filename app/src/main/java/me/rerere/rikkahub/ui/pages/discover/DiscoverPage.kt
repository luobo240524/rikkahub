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
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.QuillWrite01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * 「发现」聚合页：二期新功能的统一入口，后续功能都往这里加，顶栏收纳菜单不膨胀。
 * 卡片带红点:助手点赞/评论了你的内容且未读时点亮。
 */
@Composable
fun DiscoverPage() {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val badgeVm: SocialBadgeVM = koinViewModel()
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

