package me.rerere.rikkahub.ui.components.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.Message01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.context.LocalSettings

/**
 * 发现页共用的社交互动组件:作者身份、灰底互动区、点赞/评论操作行、评论弹窗。
 * 朋友圈与心情打卡两页复用,actor 约定 "user"/"assistant"。
 */

/** 作者名:助手用助手配置,用户用用户昵称(含每助手覆盖) */
@Composable
fun socialAuthorName(author: String, assistant: Assistant): String {
    return if (author == "assistant") {
        assistant.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) }
    } else {
        (assistant.userNickname?.takeIf { it.isNotBlank() }
            ?: LocalSettings.current.displaySetting.userNickname)
            .ifEmpty { stringResource(R.string.user_default_name) }
    }
}

/** 作者头像:同上 */
@Composable
fun socialAuthorAvatar(author: String, assistant: Assistant): Avatar {
    return if (author == "assistant") assistant.avatar
    else (assistant.userAvatar ?: LocalSettings.current.displaySetting.userAvatar)
}

/** 微信式灰底互动区:点赞行 + 评论列表(theme 传入发现页主题色,null 时走默认 Material 色) */
@Composable
fun SocialInteractionBox(
    interactions: List<SocialInteractionEntity>,
    assistant: Assistant,
    modifier: Modifier = Modifier,
    theme: me.rerere.rikkahub.ui.pages.discover.DiscoverTheme? = null,
) {
    val likes = interactions.filter { it.type == "like" }
    val comments = interactions.filter { it.type == "comment" }
    if (likes.isEmpty() && comments.isEmpty()) return
    val accent = theme?.name ?: MaterialTheme.colorScheme.primary
    val bodyColor = theme?.text ?: MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme?.gbBg ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        border = theme?.gbBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (likes.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = HugeIcons.Favourite,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    // joinToString 的 transform 非内联,composable 调用要先 map 出来
                    val likeNames = likes.map { socialAuthorName(it.actor, assistant) }
                    Text(
                        text = likeNames.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                    )
                }
            }
            comments.forEach { c ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.bodySmall.copy(
                            color = accent,
                        ).toSpanStyle()) {
                            append(socialAuthorName(c.actor, assistant))
                        }
                        append("：")
                        append(c.content.orEmpty())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor,
                )
            }
        }
    }
}

/** 点赞/评论操作行(右对齐) */
@Composable
fun SocialActionRow(
    likeCount: Int,
    liked: Boolean,
    commentCount: Int,
    onLike: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier,
    theme: me.rerere.rikkahub.ui.pages.discover.DiscoverTheme? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAction(HugeIcons.Favourite, likeCount.takeIf { it > 0 }?.toString().orEmpty(), liked, onLike, theme)
        Spacer(Modifier.width(20.dp))
        SocialAction(HugeIcons.Message01, commentCount.takeIf { it > 0 }?.toString().orEmpty(), false, onComment, theme)
    }
}

@Composable
private fun SocialAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    theme: me.rerere.rikkahub.ui.pages.discover.DiscoverTheme? = null,
) {
    val tint = when {
        active -> theme?.like ?: MaterialTheme.colorScheme.primary
        theme != null -> theme.sub
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

/** 评论弹窗 */
@Composable
fun SocialCommentDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("说点什么…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
