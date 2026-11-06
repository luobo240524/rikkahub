package me.rerere.rikkahub.ui.pages.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.InLove
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors

/**
 * 「发现」聚合页：二期新功能的统一入口，后续功能都往这里加，顶栏收纳菜单不膨胀。
 */
@Composable
fun DiscoverPage() {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.discovery_title))
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
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
                    )
                    item(
                        onClick = { navController.navigate(Screen.MoodCheckin) },
                        leadingContent = {
                            Icon(HugeIcons.Clock02, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        headlineContent = { Text(stringResource(R.string.discovery_entry_mood)) },
                        supportingContent = { Text(stringResource(R.string.discovery_entry_mood_desc)) },
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
