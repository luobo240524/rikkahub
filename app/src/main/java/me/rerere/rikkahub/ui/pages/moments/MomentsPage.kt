package me.rerere.rikkahub.ui.pages.moments

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.MomentsPostEntity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.pages.discover.DiscoverCoverStyle
import me.rerere.rikkahub.ui.pages.discover.DiscoverTheme
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.JournalCoverPresets
import me.rerere.rikkahub.ui.pages.discover.discoverCardBorder
import me.rerere.rikkahub.ui.pages.discover.discoverForegroundOverlay
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import me.rerere.rikkahub.ui.components.social.SocialActionRow
import me.rerere.rikkahub.ui.components.social.SocialCommentDialog
import me.rerere.rikkahub.ui.components.social.SocialInteractionBox
import me.rerere.rikkahub.ui.components.social.socialAuthorAvatar
import me.rerere.rikkahub.ui.components.social.socialAuthorName
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 朋友圈:文字 feed + 常驻发布框。
 * 主题化:七套发现页主题(基线亮暗/手账/绿色/蓝色/杂志/星空),顶栏 🎨 折叠下拉切换。
 */
@Composable
fun MomentsPage(
    vm: MomentsVM = koinViewModel(),
) {
    val posts by vm.posts.collectAsStateWithLifecycle()
    val interactions by vm.interactions.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var input by remember { mutableStateOf("") }
    var commentTarget by remember { mutableStateOf<MomentsPostEntity?>(null) }

    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val theme = rememberDiscoverTheme()

    val momentInteractions = remember(interactions) {
        interactions.filter { it.targetType == "moment" }.groupBy { it.targetId }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.moments_title)) },
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "cover") {
                        MomentsCover(theme)
                    }
                    if (posts.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.moments_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.sub,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            )
                        }
                    }
                    items(posts.size, key = { posts[it].id }) { index ->
                        val post = posts[index]
                        MomentCard(
                            post = post,
                            assistant = assistant,
                            theme = theme,
                            interactions = momentInteractions[post.id].orEmpty(),
                            onDelete = { vm.delete(post.id) },
                            onLike = { vm.toggleLike(post.id) },
                            onComment = { commentTarget = post },
                        )
                    }
                }
                // 底部常驻发布框
                Surface(color = theme.composerBg, border = theme.composerBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.moments_input_hint), color = theme.sub) },
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
                                vm.post(input, null)
                                input = ""
                            },
                            enabled = input.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = theme.btnBg,
                                contentColor = theme.btnFg,
                            ),
                        ) {
                            Icon(HugeIcons.Forward02, contentDescription = stringResource(R.string.moments_post))
                        }
                    }
                }
            }
            // 前景装饰层(绿色主题压卡片的叶子),不拦截触摸
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .discoverForegroundOverlay(theme),
            )
        }
    }

    commentTarget?.let { target ->
        SocialCommentDialog(
            title = stringResource(R.string.moments_title),
            onDismiss = { commentTarget = null },
            onConfirm = { text ->
                vm.comment(target.id, text, target.author)
                commentTarget = null
            },
        )
    }
}

/** 封面:按主题分三种形态 */
@Composable
private fun MomentsCover(theme: DiscoverTheme) {
    when (theme.coverStyle) {
        DiscoverCoverStyle.POLAROID -> PolaroidCover()
        DiscoverCoverStyle.MOON -> MoonCover(theme)
        DiscoverCoverStyle.GRADIENT -> GradientCover(theme)
        DiscoverCoverStyle.PLAIN_PANEL -> PlainPanelCover(theme)
    }
}

@Composable
private fun GradientCover(theme: DiscoverTheme) {
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val gradient = theme.coverGradient ?: listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(brush = Brush.linearGradient(gradient), shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UIAvatar(
                name = assistant.name,
                value = assistant.avatar,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                )
                Text(
                    text = "在这里记录和分享日常",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
            }
        }
    }
}

@Composable
private fun PlainPanelCover(theme: DiscoverTheme) {
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = theme.cardBg,
        border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UIAvatar(
                name = assistant.name,
                value = assistant.avatar,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    text = assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                )
                Text(
                    text = "在这里记录和分享日常",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
            }
        }
    }
}

@Composable
private fun MoonCover(theme: DiscoverTheme) {
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = theme.cardBg,
        border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        brush = Brush.radialGradient(listOf(Color(0x33FFD98E), Color(0x0DFFD98E))),
                        shape = CircleShape,
                    )
                    .border(1.dp, Color(0x66FFD98E), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🌙", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                )
                Text(
                    text = "晚安,今天也辛苦了",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
            }
        }
    }
}

/** 手账拍立得封面:白框+胶带+渐变预设/相册图,点击自定义 */
@Composable
private fun PolaroidCover() {
    val settings = LocalSettings.current
    val display = settings.displaySetting
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    val albumPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            scope.launch {
                settingsStore.update { s ->
                    s.copy(displaySetting = s.displaySetting.copy(journalCoverUri = uri.toString()))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clickable { showPicker = true },
        ) {
            Column(modifier = Modifier.padding(9.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .clip(RoundedCornerShape(4.dp)),
                ) {
                    val uri = display.journalCoverUri
                    if (uri != null) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val preset = JournalCoverPresets.getOrElse(display.journalCoverPreset) { JournalCoverPresets[0] }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(brush = Brush.linearGradient(preset)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "🌸🌙", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
                Text(
                    text = "我们的日常 · 十一月",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B5D45),
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .align(Alignment.CenterHorizontally),
                )
            }
        }
        // 两条和纸胶带
        Box(
            modifier = Modifier
                .offset(x = (-2).dp, y = (-6).dp)
                .width(62.dp)
                .height(16.dp)
                .background(Color(0x8CF6C177))
                .rotate(-38f),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-6).dp)
                .width(62.dp)
                .height(16.dp)
                .background(Color(0x8CF6C177))
                .rotate(38f),
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("封面背景") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("点色块即换,也可以从相册选图", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JournalCoverPresets.forEachIndexed { index, preset ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(brush = Brush.linearGradient(preset))
                                    .border(
                                        width = if (display.journalCoverUri == null && display.journalCoverPreset == index) 2.dp else 1.dp,
                                        color = if (display.journalCoverUri == null && display.journalCoverPreset == index) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        scope.launch {
                                            settingsStore.update { s ->
                                                s.copy(
                                                    displaySetting = s.displaySetting.copy(
                                                        journalCoverPreset = index,
                                                        journalCoverUri = null,
                                                    )
                                                )
                                            }
                                        }
                                    },
                            )
                        }
                    }
                    TextButton(onClick = {
                        showPicker = false
                        albumPicker.launch(arrayOf("image/*"))
                    }) {
                        Text("🖼 从相册选择")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("完成") }
            },
        )
    }
}

private val momentTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
private fun MomentCard(
    post: MomentsPostEntity,
    assistant: Assistant,
    theme: DiscoverTheme,
    interactions: List<me.rerere.rikkahub.data.db.entity.SocialInteractionEntity>,
    onDelete: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
) {
    val likes = interactions.count { it.type == "like" }
    val userLiked = interactions.any { it.type == "like" && it.actor == "user" }
    val commentCount = interactions.count { it.type == "comment" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .discoverCardBorder(theme),
        shape = RoundedCornerShape(theme.cardCorner),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 作者行
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(
                    name = socialAuthorName(post.author, assistant),
                    value = socialAuthorAvatar(post.author, assistant),
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = socialAuthorName(post.author, assistant),
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.name,
                    )
                    Text(
                        text = momentTimeFormatter.format(
                            Instant.ofEpochMilli(post.createdAt).atZone(ZoneId.systemDefault())
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.sub,
                    )
                }
                Icon(
                    imageVector = HugeIcons.Delete02,
                    contentDescription = stringResource(R.string.moments_delete),
                    tint = theme.sub,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onDelete),
                )
            }
            // 正文
            Text(text = post.content, style = MaterialTheme.typography.bodyMedium, color = theme.text)
            if (!post.mood.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = theme.chipBg,
                    border = theme.chipBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                ) {
                    Text(
                        text = post.mood,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.text,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            // 灰底互动区 + 操作行
            SocialInteractionBox(interactions = interactions, assistant = assistant, theme = theme, author = post.author)
            SocialActionRow(
                likeCount = likes,
                liked = userLiked,
                commentCount = commentCount,
                onLike = onLike,
                onComment = onComment,
                theme = theme,
            )
        }
    }
}
