package me.rerere.rikkahub.ui.pages.secrets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Eye
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.SecretsLayout
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.SecretEntity
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity
import me.rerere.rikkahub.ui.components.social.SocialActionRow
import me.rerere.rikkahub.ui.components.social.SocialCommentDialog
import me.rerere.rikkahub.ui.components.social.SocialInteractionBox
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
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 心里话/小秘密:便签墙(定稿方案A),排列样式可切换(瀑布/单列/三列)。
 * 便利贴纸色随主题(GREEN=绿纸/BLUE=蓝纸/STARRY=深色半透明),支持编辑+可见切换;
 * 转为可见时后台通知助手;便签可点赞/评论(评论可回复某条)。
 */
private val secretTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 经典糖果便签色(主题未指定时用) */
private val DefaultStickyColors = listOf(
    Color(0xFFFFF6C9), Color(0xFFFFE3EC), Color(0xFFE2F6E4), Color(0xFFE1EFFB), Color(0xFFF1E6FF),
)

/** 便签交替微倾角 */
private val StickyAngles = listOf(-1.4f, 1.2f, 0.8f, -1f, 1.5f, 0.6f)

private fun parseSecretsLayout(name: String?): SecretsLayout =
    SecretsLayout.entries.firstOrNull { it.name == name } ?: SecretsLayout.WATERFALL

@Composable
fun SecretsPage(
    vm: SecretsVM = koinViewModel(),
) {
    val secrets by vm.secrets.collectAsStateWithLifecycle()
    val interactions by vm.interactions.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    var editTarget by remember { mutableStateOf<SecretEntity?>(null) }
    var commentTarget by remember { mutableStateOf<SecretEntity?>(null) }
    var commentReplyTo by remember { mutableStateOf<String?>(null) }
    val theme = rememberDiscoverTheme()

    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val layout = parseSecretsLayout(settings.displaySetting.secretsLayout)

    val secretInteractions = remember(interactions) {
        interactions.filter { it.targetType == "secret" }.groupBy { it.targetId }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("心里话") },
                scrollBehavior = scrollBehavior,
                colors = discoverTopBarColors(theme),
                actions = {
                    SecretsLayoutMenuButton(
                        current = layout,
                        onSelect = { sel ->
                            scope.launch {
                                settingsStore.update { s ->
                                    s.copy(displaySetting = s.displaySetting.copy(secretsLayout = sel.name))
                                }
                            }
                        },
                    )
                    DiscoverThemeMenuButton()
                },
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
                    contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 10.dp),
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
                    // 便签墙:排列样式可切换
                    item(key = "sticky_wall") {
                        val sticky: @Composable (SecretEntity, Int) -> Unit = { s, index ->
                            StickyNote(
                                secret = s,
                                index = index,
                                assistant = assistant,
                                theme = theme,
                                interactions = secretInteractions[s.id].orEmpty(),
                                onEdit = { editTarget = s },
                                onToggleVisible = { vm.update(s.id, s.content, s.visible != 1) },
                                onDelete = { vm.delete(s.id) },
                                onLike = { vm.toggleLike(s.id) },
                                onComment = {
                                    commentTarget = s
                                    commentReplyTo = null
                                },
                                onCommentReply = { actor ->
                                    commentTarget = s
                                    commentReplyTo = actor
                                },
                            )
                        }
                        when (layout) {
                            SecretsLayout.WATERFALL -> {
                                // 双列瀑布,按奇偶分列(定稿方案A)
                                val left = secrets.filterIndexed { i, _ -> i % 2 == 0 }
                                val right = secrets.filterIndexed { i, _ -> i % 2 == 1 }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        left.forEach { s -> sticky(s, secrets.indexOf(s)) }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        right.forEach { s -> sticky(s, secrets.indexOf(s)) }
                                    }
                                }
                            }
                            SecretsLayout.LIST -> {
                                // 单列时间线
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    secrets.forEachIndexed { i, s -> sticky(s, i) }
                                }
                            }
                            SecretsLayout.GRID -> {
                                // 三列紧凑,按 i%3 分列
                                val cols = List(3) { col ->
                                    secrets.filterIndexed { i, _ -> i % 3 == col }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    cols.forEach { col ->
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            col.forEach { s -> sticky(s, secrets.indexOf(s)) }
                                        }
                                    }
                                }
                            }
                        }
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

    commentTarget?.let { target ->
        SocialCommentDialog(
            title = "心里话",
            onDismiss = {
                commentTarget = null
                commentReplyTo = null
            },
            onConfirm = { text ->
                vm.comment(target.id, text, target.author, commentReplyTo)
                commentTarget = null
                commentReplyTo = null
            },
            replyToName = commentReplyTo?.let { socialAuthorName(it, assistant) },
        )
    }
}

/** 排列样式切换:顶栏折叠下拉(收纳原则) */
@Composable
private fun SecretsLayoutMenuButton(
    current: SecretsLayout,
    onSelect: (SecretsLayout) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(HugeIcons.LeftToRightListBullet, contentDescription = "排列样式")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = "排列样式",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SecretsLayout.entries.forEach { l ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = when (l) {
                                SecretsLayout.WATERFALL -> "双列瀑布"
                                SecretsLayout.LIST -> "单列时间线"
                                SecretsLayout.GRID -> "三列紧凑"
                            },
                            color = if (l == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = {
                        if (l == current) {
                            Icon(HugeIcons.Tick02, null, tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Box(modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(l)
                    },
                )
            }
        }
    }
}

/** 一张便签:纸色随主题轮换+微倾+图钉,不可见=折角+淡化;眼睛切可见,铅笔编辑;可点赞评论 */
@Composable
private fun StickyNote(
    secret: SecretEntity,
    index: Int,
    assistant: me.rerere.rikkahub.data.model.Assistant,
    theme: me.rerere.rikkahub.ui.pages.discover.DiscoverTheme,
    interactions: List<SocialInteractionEntity>,
    onEdit: () -> Unit,
    onToggleVisible: () -> Unit,
    onDelete: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onCommentReply: (String) -> Unit,
) {
    val masked = secret.author != "assistant" && secret.visible == 0
    val colors = theme.noteColors ?: DefaultStickyColors
    val bg = colors[index % colors.size]
    val rot = StickyAngles[index % StickyAngles.size]
    val likes = interactions.count { it.type == "like" }
    val userLiked = interactions.any { it.type == "like" && it.actor == "user" }
    val commentCount = interactions.count { it.type == "comment" }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 10.dp, bottomStart = 4.dp),
            color = bg,
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .rotate(rot),
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 9.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Icon(
                        imageVector = if (secret.visible == 1) HugeIcons.Eye else HugeIcons.ViewOff,
                        contentDescription = if (secret.visible == 1) "转为不可见" else "转为可见",
                        tint = if (secret.visible == 1) theme.name else theme.sub,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable(onClick = onToggleVisible),
                    )
                }
                Text(
                    text = if (masked) "(对方设置为不可见)" else secret.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (masked) theme.sub else theme.text.copy(alpha = if (secret.visible == 1) 1f else 0.8f),
                )
                Spacer(Modifier.height(7.dp))
                // 互动区(灰底随主题;不可见便签不开放互动)
                if (!masked) {
                    SocialInteractionBox(
                        interactions = interactions,
                        assistant = assistant,
                        theme = theme,
                        author = secret.author,
                        onCommentClick = { c -> onCommentReply(c.actor) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UIAvatar(
                        name = socialAuthorName(secret.author, assistant),
                        value = socialAuthorAvatar(secret.author, assistant),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = socialAuthorName(secret.author, assistant),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.sub,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!masked) {
                        SocialActionRow(
                            likeCount = likes,
                            liked = userLiked,
                            commentCount = commentCount,
                            onLike = onLike,
                            onComment = onComment,
                            theme = theme,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        text = secretTimeFormatter.format(
                            Instant.ofEpochMilli(secret.createdAt).atZone(ZoneId.systemDefault())
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.sub,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = HugeIcons.PencilEdit01,
                        contentDescription = "编辑",
                        tint = theme.sub,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(onClick = onEdit),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = HugeIcons.Delete02,
                        contentDescription = "删除",
                        tint = theme.sub,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(onClick = onDelete),
                    )
                }
            }
        }
        // 图钉
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-5).dp)
                .size(12.dp)
                .shadow(2.dp, CircleShape)
                .background(theme.like, CircleShape),
        )
        // 不可见折角
        if (secret.visible == 0) {
            Canvas(modifier = Modifier.align(Alignment.TopEnd).size(16.dp)) {
                val p = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, 0f)
                    close()
                }
                drawPath(p, theme.sub.copy(alpha = 0.35f))
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
