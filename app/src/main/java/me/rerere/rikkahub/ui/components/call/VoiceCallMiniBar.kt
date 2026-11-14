package me.rerere.rikkahub.ui.components.call

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.call.VoiceCallSessionManager
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * 语音通话悬浮球(全页悬浮层,RouteActivity 根部挂载):通话最小化/退桌面后指示通话仍在继续。
 * - 52dp 圆球(助手头像+绿色呼吸点),可拖到屏幕任意位置
 * - 点按 = 回到通话页(通话页内部:重进只回绑,不起新电话)
 * - 通话结束(Off)整层消失;通话页自身路由不显示(已在通话页)
 */
@Composable
fun VoiceCallMiniBar(
    modifier: Modifier = Modifier,
    callManager: VoiceCallSessionManager = koinInject(),
) {
    val state by callManager.state.collectAsStateWithLifecycle()
    if (state.phase == VoicePhase.Off) return
    val navController = LocalNavController.current
    if (navController.currentKey is Screen.VoiceCall) return

    val name by callManager.assistantName.collectAsStateWithLifecycle()
    val avatar by callManager.assistantAvatar.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val ballPx = with(density) { 52.dp.toPx() }
        var offset by remember { mutableStateOf(Offset(-1f, -1f)) }
        // 默认落在右上角(顶栏下方一点);-1 = 未初始化
        val pos = if (offset.x < 0f) {
            Offset(maxW - ballPx - with(density) { 12.dp.toPx() }, with(density) { 96.dp.toPx() })
        } else {
            offset
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(52.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = Offset(
                            (pos.x + dragAmount.x).coerceIn(0f, maxW - ballPx),
                            (pos.y + dragAmount.y).coerceIn(0f, maxH - ballPx),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        // 回通话页:通话进行中页面只回绑,不会重复起呼;launchSingleTop 防连点叠双层
                        navController.navigate(Screen.VoiceCall(assistantId = callManager.targetAssistantId)) {
                            launchSingleTop = true
                        }
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                CallBallAvatar(avatar = avatar, name = name)
                // 呼吸绿点:指示通话进行中(通话页内不需要这个球,所以这里永远是后台态)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(Color(0xFF34C77B), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun CallBallAvatar(avatar: Avatar, name: String) {
    when (avatar) {
        is Avatar.Image -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatar.url)
                .crossfade(true)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        is Avatar.Emoji -> Text(text = avatar.content, fontSize = 24.sp)
        Avatar.Dummy -> Text(
            text = name.firstOrNull()?.toString() ?: "聊",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
