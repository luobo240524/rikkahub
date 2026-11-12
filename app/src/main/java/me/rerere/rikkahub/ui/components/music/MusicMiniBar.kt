package me.rerere.rikkahub.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.rikkahub.data.music.MusicPlayerController
import org.koin.compose.koinInject

/**
 * 一起听悬浮播放器:全页悬浮层(ChatPage 根部挂载)。
 * - 悬浮球形态:52dp 圆球(封面),可拖到屏幕任意位置,点按展开完整条;暂停时球面盖播放角标
 * - 完整条形态:歌名/进度/上一首/播放暂停/下一首/收成球;向左滑 = 停止播放并关闭
 * - 点歌名区域弹歌词面板(当前行高亮随播放滚动)
 * - 无播放时整层隐藏
 */
@Composable
fun MusicMiniBar(
    modifier: Modifier = Modifier,
    musicPlayer: MusicPlayerController = koinInject(),
) {
    val state by musicPlayer.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    if (!state.isActive) return

    var ballMode by rememberSaveable { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (ballMode) {
        // ---------- 悬浮球:可拖动,点按展开 ----------
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
                    .offset { IntOffset(pos.x.toInt(), pos.y.toInt()) }
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
                        detectTapGestures { ballMode = false }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (song.coverUrl != null) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = song.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = HugeIcons.MusicNote03,
                            contentDescription = song.name,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (state.phase == MusicPlayerController.Phase.Paused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = HugeIcons.Play,
                                contentDescription = "已暂停",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    } else {
        // ---------- 完整条:左滑停止,可收成球 ----------
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { v ->
                if (v == SwipeToDismissBoxValue.EndToStart) {
                    scope.launch { musicPlayer.stopAwait() }
                    true
                } else {
                    false
                }
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            backgroundContent = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Icon(HugeIcons.Cancel01, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("松开停止播放", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { showLyrics = true },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (song.coverUrl != null) {
                                    AsyncImage(
                                        model = song.coverUrl,
                                        contentDescription = song.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    )
                                } else {
                                    Icon(
                                        imageVector = HugeIcons.MusicNote03,
                                        contentDescription = song.name,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showLyrics = true },
                        ) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${song.artist} · 一起听",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { musicPlayer.prev() }) {
                            Icon(HugeIcons.ArrowLeft01, contentDescription = "上一首")
                        }
                        IconButton(onClick = { musicPlayer.togglePlay() }) {
                            Icon(
                                imageVector = if (state.phase == MusicPlayerController.Phase.Playing) {
                                    HugeIcons.Pause
                                } else {
                                    HugeIcons.Play
                                },
                                contentDescription = null,
                            )
                        }
                        IconButton(onClick = { musicPlayer.next() }) {
                            Icon(HugeIcons.ArrowRight01, contentDescription = "下一首")
                        }
                        IconButton(onClick = { ballMode = true }) {
                            Icon(
                                imageVector = HugeIcons.ArrowRight01,
                                contentDescription = "收成悬浮球",
                                modifier = Modifier.rotate(90f),
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (state.durationMs > 0) {
                                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                            } else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .padding(bottom = 6.dp),
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    )
                }
            }
        }
    }

    if (showLyrics) {
        MusicLyricsSheet(
            musicPlayer = musicPlayer,
            onDismiss = { showLyrics = false },
        )
    }
}

@Composable
private fun MusicLyricsSheet(
    musicPlayer: MusicPlayerController,
    onDismiss: () -> Unit,
) {
    val state by musicPlayer.state.collectAsStateWithLifecycle()
    val song = state.song ?: return
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(120.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (song.coverUrl != null) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = song.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                        )
                    } else {
                        Icon(
                            imageVector = HugeIcons.MusicNote03,
                            contentDescription = song.name,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 歌词区:当前行高亮 + 自动跟随
            val listState = rememberLazyListState()
            LaunchedEffect(state.currentLineIndex) {
                val idx = state.currentLineIndex
                if (idx >= 0 && state.lyrics.isNotEmpty()) {
                    listState.animateScrollToItem(idx)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.lyrics.isEmpty()) {
                    item {
                        Text(
                            text = "(暂无歌词)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(state.lyrics) { index, line ->
                    val isCurrent = index == state.currentLineIndex
                    Text(
                        text = line.text,
                        style = if (isCurrent) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = { musicPlayer.prev() }) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = "上一首")
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(56.dp)
                        .clickable { musicPlayer.togglePlay() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.phase == MusicPlayerController.Phase.Playing) {
                                HugeIcons.Pause
                            } else {
                                HugeIcons.Play
                            },
                            contentDescription = null,
                        )
                    }
                }
                IconButton(onClick = { musicPlayer.next() }) {
                    Icon(HugeIcons.ArrowRight01, contentDescription = "下一首")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatMs(state.positionMs) + " / " + formatMs(
                    if (state.durationMs > 0) state.durationMs else song.durationMs
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
