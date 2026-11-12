package me.rerere.rikkahub.ui.pages.music

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Pause
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.music.MusicPlayerController
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.discover.DiscoverCard
import me.rerere.rikkahub.ui.pages.discover.DiscoverTheme
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

/**
 * 一起听主页(发现页风格,复用 7 套主题):
 * 播放控制卡(暂停/继续/上一首/下一首/关闭) + 点歌搜索卡(结果点选即播) + 登录提示。
 */
@Composable
fun MusicPage() {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val musicPlayer: MusicPlayerController = koinInject()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val theme = rememberDiscoverTheme()
    val scope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<me.rerere.rikkahub.data.music.NeteaseClient.Song>>(emptyList()) }

    fun doSearch() {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        scope.launch {
            searching = true
            hint = null
            results = musicPlayer.search(kw, limit = 15)
            if (results.isEmpty()) hint = "没有搜到相关歌曲"
            searching = false
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("一起听") },
                navigationIcon = { BackButton() },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "player") {
                MusicPlayerCard(theme, musicPlayer, onHint = { hint = it })
            }
            item(key = "search") {
                DiscoverCard(theme, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "点一首歌",
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.text,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                placeholder = { Text("歌名 / 歌手", style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(theme.btnBg)
                                    .clickable { doSearch() },
                            ) {
                                Icon(
                                    HugeIcons.Search01,
                                    contentDescription = "搜索",
                                    tint = theme.btnFg,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        if (searching) {
                            Spacer(Modifier.height(8.dp))
                            Text("搜索中…", style = MaterialTheme.typography.bodySmall, color = theme.sub)
                        }
                        hint?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = theme.sub)
                        }
                        results.forEach { song ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        hint = "正在播放《${song.name}》…"
                                        scope.launch {
                                            musicPlayer.playSong(song).fold(
                                                onSuccess = { r -> hint = "已播放《${r.name}》· ${r.artist}" },
                                                onFailure = { e ->
                                                    hint = if (e.message == "NO_PLAYABLE_URL") {
                                                        "《${song.name}》拿不到播放链接(VIP/版权),换一首试试"
                                                    } else {
                                                        "播放失败,再试一次"
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    HugeIcons.MusicNote03,
                                    contentDescription = null,
                                    tint = theme.name,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = song.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = theme.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.sub,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = formatMs(song.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.sub,
                                )
                            }
                        }
                    }
                }
            }
            if (settings.neteaseCookie.isBlank()) {
                item(key = "login") {
                    DiscoverCard(
                        theme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.SettingMusic) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "未登录网易云音乐",
                                style = MaterialTheme.typography.titleSmall,
                                color = theme.text,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "现在可搜可播免费歌;点此去扫码登录后,VIP 歌也能用你账号的权益播放。Cookie 仅保存在本机。",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.sub,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPlayerCard(
    theme: DiscoverTheme,
    musicPlayer: MusicPlayerController,
    onHint: (String) -> Unit,
) {
    val state by musicPlayer.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val song = state.song
    val playing = state.phase == MusicPlayerController.Phase.Playing

    DiscoverCard(theme, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.chipBg),
                ) {
                    Icon(
                        HugeIcons.MusicNote03,
                        contentDescription = null,
                        tint = theme.name,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song?.name ?: "没有在播放",
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (state.phase) {
                            MusicPlayerController.Phase.Playing -> "播放中" + if (song != null) " · ${song.artist}" else ""
                            MusicPlayerController.Phase.Paused -> "已暂停" + if (song != null) " · ${song.artist}" else ""
                            MusicPlayerController.Phase.Loading -> "加载中…"
                            else -> "在下方点一首歌,或让助手帮你点"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 上一首
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                val s = musicPlayer.prevAwait()
                                onHint(if (s == null) "队列里没有上一首了" else "已切到《${s.name}》")
                            }
                        },
                ) {
                    Icon(HugeIcons.ArrowLeft01, contentDescription = "上一首", tint = theme.text)
                }
                // 播放/暂停
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(theme.btnBg)
                        .clickable {
                            scope.launch {
                                musicPlayer.togglePlayAwait()
                            }
                        },
                ) {
                    Icon(
                        if (playing) HugeIcons.Pause else HugeIcons.Play,
                        contentDescription = if (playing) "暂停" else "播放",
                        tint = theme.btnFg,
                        modifier = Modifier.size(26.dp),
                    )
                }
                // 下一首
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                val s = musicPlayer.nextAwait()
                                onHint(if (s == null) "队列里没有下一首了" else "已切到《${s.name}》")
                            }
                        },
                ) {
                    Icon(HugeIcons.ArrowRight01, contentDescription = "下一首", tint = theme.text)
                }
                // 关闭音乐
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                musicPlayer.stopAwait()
                                onHint("已停止播放")
                            }
                        },
                ) {
                    Icon(HugeIcons.StopCircle, contentDescription = "关闭音乐", tint = theme.text)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
