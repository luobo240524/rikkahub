package me.rerere.rikkahub.ui.pages.secrets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 心里话/小秘密:私密随笔列表 + 底部发布框(可见性开关)。
 * 对方可见=助手可用 secrets_read 读到内容;不可见=只显示"对方设置为不可见"。
 */
private val secretTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
fun SecretsPage(
    vm: SecretsVM = koinViewModel(),
) {
    val secrets by vm.secrets.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("心里话") },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (secrets.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "把不敢说、不好说的心里话写在这里,只属于你。\n对方可见时,TA 才能读到。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        )
                    }
                }
                items(secrets.size, key = { secrets[it].id }) { index ->
                    val secret = secrets[index]
                    SecretCard(
                        content = secret.content,
                        visible = secret.visible == 1,
                        createdAt = secret.createdAt,
                        onDelete = { vm.delete(secret.id) },
                    )
                }
            }
            // 底部发布框 + 可见性开关
            Surface(tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = visible,
                            onClick = { visible = true },
                            label = { Text("对方可见") },
                            leadingIcon = {
                                Icon(HugeIcons.Eye, null, modifier = Modifier.size(16.dp))
                            },
                        )
                        FilterChip(
                            selected = !visible,
                            onClick = { visible = false },
                            label = { Text("对方不可见") },
                            leadingIcon = {
                                Icon(HugeIcons.ViewOff, null, modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("写下心里话…") },
                            maxLines = 3,
                            shape = RoundedCornerShape(24.dp),
                        )
                        FilledIconButton(
                            onClick = {
                                vm.add(input, visible)
                                input = ""
                            },
                            enabled = input.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(HugeIcons.Forward02, contentDescription = "发布")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretCard(
    content: String,
    visible: Boolean,
    createdAt: Long,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (visible) HugeIcons.Eye else HugeIcons.ViewOff,
                    contentDescription = null,
                    tint = if (visible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (visible) "对方可见" else "对方不可见",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (visible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = HugeIcons.Delete02,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onDelete),
                )
            }
            Text(text = content, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = secretTimeFormatter.format(
                    Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault())
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
