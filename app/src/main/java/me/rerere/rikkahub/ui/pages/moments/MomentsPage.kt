package me.rerere.rikkahub.ui.pages.moments

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.MomentsPostEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.social.SocialActionRow
import me.rerere.rikkahub.ui.components.social.SocialCommentDialog
import me.rerere.rikkahub.ui.components.social.SocialInteractionBox
import me.rerere.rikkahub.ui.components.social.socialAuthorAvatar
import me.rerere.rikkahub.ui.components.social.socialAuthorName
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 朋友圈:文字 feed + 常驻发布框。
 * 参考 JetInstagram / 微信朋友圈:封面头图 + 头像卡片 + 灰底点赞评论区 + 操作行。
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

    val momentInteractions = remember(interactions) {
        interactions.filter { it.targetType == "moment" }.groupBy { it.targetId }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.moments_title)) },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "cover") {
                    MomentsCoverHeader(assistant)
                }
                if (posts.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.moments_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        interactions = momentInteractions[post.id].orEmpty(),
                        onDelete = { vm.delete(post.id) },
                        onLike = { vm.toggleLike(post.id) },
                        onComment = { commentTarget = post },
                    )
                }
            }
            // 底部常驻发布框
            Surface(tonalElevation = 2.dp) {
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
                        placeholder = { Text(stringResource(R.string.moments_input_hint)) },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                    )
                    FilledIconButton(
                        onClick = {
                            vm.post(input, null)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(HugeIcons.Forward02, contentDescription = stringResource(R.string.moments_post))
                    }
                }
            }
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

/** 封面头图:渐变底 + 助手头像与名字(对齐微信朋友圈的封面氛围) */
@Composable
private fun MomentsCoverHeader(assistant: Assistant) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                ),
                shape = RoundedCornerShape(20.dp),
            )
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "在这里记录和分享日常",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private val momentTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
private fun MomentCard(
    post: MomentsPostEntity,
    assistant: Assistant,
    interactions: List<me.rerere.rikkahub.data.db.entity.SocialInteractionEntity>,
    onDelete: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
) {
    val likes = interactions.count { it.type == "like" }
    val userLiked = interactions.any { it.type == "like" && it.actor == "user" }
    val commentCount = interactions.count { it.type == "comment" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
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
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = momentTimeFormatter.format(
                            Instant.ofEpochMilli(post.createdAt).atZone(ZoneId.systemDefault())
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = HugeIcons.Delete02,
                    contentDescription = stringResource(R.string.moments_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onDelete),
                )
            }
            // 正文
            Text(text = post.content, style = MaterialTheme.typography.bodyMedium)
            if (!post.mood.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = post.mood,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            // 灰底互动区 + 操作行
            SocialInteractionBox(interactions = interactions, assistant = assistant)
            SocialActionRow(
                likeCount = likes,
                liked = userLiked,
                commentCount = commentCount,
                onLike = onLike,
                onComment = onComment,
            )
        }
    }
}
