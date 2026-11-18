package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 快速滚动条:右侧细轨道+滑块,拖动快进(拖到顶/底即到顶/底),点轨道跳转。
 * 内容没超屏(maxValue<=0)时不显示。拖动量映射到整份内容,内容越长滚得越快。
 * 调用方用 Box + Modifier.align(Alignment.CenterEnd) 放右侧。
 */
@Composable
fun FastScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return
    var dragging by remember { mutableStateOf(false) }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    // 主题色半透明:浅色米色主题下灰色 outline 几乎看不见,改用 primary 保证可见
    val thumbColor = if (dragging) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(scrollState.maxValue) {
                detectTapGestures { offset ->
                    val target = (offset.y / size.height.toFloat())
                        .coerceIn(0f, 1f) * scrollState.maxValue
                    scrollState.dispatchRawDelta(target - scrollState.value)
                }
            }
            .pointerInput(scrollState.maxValue) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    // dragAmount 已是纵向像素;手指在轨道上移动 1px ≈ 内容走 (maxValue/轨道高) px
                    scrollState.dispatchRawDelta(
                        (dragAmount / size.height.toFloat()) * scrollState.maxValue
                    )
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (scrollState.maxValue <= 0) return@Canvas
            val visibleFraction = (size.height / (size.height + scrollState.maxValue)).coerceIn(0.06f, 1f)
            val thumbH = size.height * visibleFraction
            val progress = scrollState.value.toFloat() / scrollState.maxValue
            val top = (size.height - thumbH) * progress
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(size.width / 2f - 2.dp.toPx(), 0f),
                size = Size(4.dp.toPx(), size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(size.width / 2f - 4.dp.toPx(), top),
                size = Size(8.dp.toPx(), thumbH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}
