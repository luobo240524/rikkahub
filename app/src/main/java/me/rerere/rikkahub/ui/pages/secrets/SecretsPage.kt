package me.rerere.rikkahub.ui.pages.secrets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.SecretEntity
import me.rerere.rikkahub.ui.components.social.socialAuthorAvatar
import me.rerere.rikkahub.ui.components.social.socialAuthorName
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.discoverForegroundOverlay
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 心里话/小秘密:双方都能写,可设置对对方是否可见;支持编辑+可见转换。
 * 主题化:七套发现页主题,顶栏 🎨 折叠下拉切换。
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
    var editTarget by remember { mutableStateOf<SecretEntity?>(null) }
    val theme = rememberDiscoverTheme()

    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("心里话") },
                scrollBehavior = scrollBehavior,
                colors = discoverTopBarColors(theme),
                actions = { DiscoverThemeMenuButton() },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .discoverPageBackground(theme)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                                color = theme.sub,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            )
                        }
                    }
                    items(secrets.size, key = { secrets[it].id }) { index ->
                        val secret = secrets[index]
                        SecretCard(
                            secret = secret,
                            assistant = assistant,
                            theme = theme,
                            onEdit = { editTarget = secret },
                            onToggleVisible = {
                                vm.update(secret.id, secret.content, secret.visible != 1)
                            },
                            onDelete = { vm.delete(secret.id) },
                        )
                    }
                }
                // 底部发布框 + 可见性开关
                Surface(color = theme.composerBg, border = theme.composerBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) }) {
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
                                label = { Text("对方可见", color = if (visible) theme.name else theme.sub) },
                                leadingIcon = {
                                    Icon(HugeIcons.Eye, null, modifier = Modifier.size(16.dp), tint = if (visible) theme.name else theme.sub)
                                },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    containerColor = theme.chipBg,
                                    selectedContainerColor = theme.chipBg,
                                ),
                            )
                            FilterChip(
                                selected = !visible,
                                onClick = { visible = false },
                                label = { Text("对方不可见", color = if (!visible) theme.name else theme.sub) },
                                leadingIcon = {
                                    Icon(HugeIcons.ViewOff, null, modifier = Modifier.size(16.dp), tint = if (!visible) theme.name else theme.sub)
                                },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    containerColor = theme.chipBg,
                                    selectedContainerColor = theme.chipBg,
                                ),
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
                                placeholder = { Text("写下心里话…", color = theme.sub) },
                                maxLines = 3,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.fldBg,
                                    unfocusedContainerColor = theme.fldBg,
                                    focusedTextColor = theme.text,
                                    unfocusedTextColor = theme.text,
                                    cursorColor = theme.like,
                                    focusedBorderColor = theme.like,
                                    unfocusedBorderColor = theme.sub,
                                ),
                            )
                            FilledIconButton(
                                onClick = {
                                    vm.add(input, visible)
                                    input = ""
                                },
                                enabled = input.isNotBlank(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = theme.btnBg,
                                    contentColor = theme.btnFg,
                                ),
                            ) {
                                Icon(HugeIcons.Forward02, contentDescription = "发布")
                            }
                        }
                    }
                }
            }
            // 前景装饰层(绿色主题叶子)
            Box(modifier = Modifier.matchParentSize().discoverForegroundOverlay(theme))
        }
    }

    editTarget?.let { target ->
        SecretEditDialog(
            initial = target,
            onDismiss = { editTarget = null },
            onSave = { content, newVisible ->
                vm.update(target.id, content, newVisible)
                editTarget = null
            },
        )
    }
}

@Composable
private fun SecretCard(
    secret: SecretEntity,
    assistant: me.rerere.rikkahub.data.model.Assistant,
    theme: me.rerere.rikkahub.ui.pages.discover.DiscoverTheme,
    onEdit: () -> Unit,
    onToggleVisible: () -> Unit,
    onDelete: () -> Unit,
) {
    val ownByAssistant = secret.author == "assistant"
    // 对方写的且对方设为不可见 → 不显示内容
    val masked = !ownByAssistant && secret.visible == 0
    val readVisible = if (ownByAssistant) secret.visible == 1 else secret.visible == 1

    Surface(
        shape = RoundedCornerShape(theme.cardCorner),
        color = theme.cardBg,
        border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 作者行:头像+称呼+可见性(点击切换)+编辑+删除
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(
                    name = socialAuthorName(secret.author, assistant),
                    value = socialAuthorAvatar(secret.author, assistant),
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = socialAuthorName(secret.author, assistant),
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.name,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (secret.visible == 1) HugeIcons.Eye else HugeIcons.ViewOff,
                    contentDescription = if (secret.visible == 1) "转为不可见" else "转为可见",
                    tint = if (secret.visible == 1) theme.name else theme.sub,
                    modifier = Modifier
                        .size(17.dp)
                        .clickable(onClick = onToggleVisible),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = PencilEdit01,
                    contentDescription = "编辑",
                    tint = theme.sub,
                    modifier = Modifier
                        .size(17.dp)
                        .clickable(onClick = onEdit),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Delete02,
                    contentDescription = "删除",
                    tint = theme.sub,
                    modifier = Modifier
                        .size(17.dp)
                        .clickable(onClick = onDelete),
                )
            }
            Text(
                text = if (masked) "(对方设置为不可见)" else secret.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (masked) theme.sub else theme.text,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (secret.visible == 1) "对方可见" else "对方不可见",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (secret.visible == 1) theme.name else theme.sub,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = secretTimeFormatter.format(
                        Instant.ofEpochMilli(secret.createdAt).atZone(ZoneId.systemDefault())
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.sub,
                )
            }
        }
    }
}

/** 编辑心里话:改内容+切可见性 */
@Composable
private fun SecretEditDialog(
    initial: SecretEntity,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var text by remember { mutableStateOf(initial.content) }
    var visible by remember { mutableStateOf(initial.visible == 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑心里话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = visible,
                        onClick = { visible = true },
                        label = { Text("对方可见") },
                        leadingIcon = { Icon(HugeIcons.Eye, null, modifier = Modifier.size(16.dp)) },
                    )
                    FilterChip(
                        selected = !visible,
                        onClick = { visible = false },
                        label = { Text("对方不可见") },
                        leadingIcon = { Icon(HugeIcons.ViewOff, null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text, visible) },
                enabled = text.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
