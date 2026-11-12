package me.rerere.rikkahub.data.music

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "一起听"播放器:应用内独立 ExoPlayer 实例(不与 TTS/通话共享,见决策「通话页独占 TTS 实例」),
 * 由 music_control 工具驱动,聊天页迷你条/歌词面板观察 [state]。
 */
class MusicPlayerController(
    context: Context,
    val neteaseClient: NeteaseClient,
    private val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore,
) {
    enum class Phase { Idle, Loading, Playing, Paused, Error }

    data class LyricLine(val timeMs: Long, val text: String)

    data class MusicState(
        val phase: Phase = Phase.Idle,
        val song: NeteaseClient.Song? = null,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val lyrics: List<LyricLine> = emptyList(),
        val currentLineIndex: Int = -1,
        val message: String? = null,
    ) {
        val isActive: Boolean get() = phase != Phase.Idle
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ExoPlayer 必须在带 Looper 的线程创建;koin 解析可能发生在后台协程,兜底切主线程创建
    private val player: ExoPlayer by lazy {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            ExoPlayer.Builder(context).build()
        } else {
            kotlinx.coroutines.runBlocking(Dispatchers.Main.immediate) {
                ExoPlayer.Builder(context).build()
            }
        }
    }

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var queue: List<NeteaseClient.Song> = emptyList()
    private var queueIndex = 0
    private var urlJob: Job? = null

    init {
        // 启动时恢复已保存的网易云登录态
        scope.launch {
            neteaseClient.cookie = settingsStore.settingsFlow.first().neteaseCookie
        }
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true, // 系统音频焦点:来电/其他媒体播放时自动暂停
        )
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(
                    phase = when {
                        isPlaying -> Phase.Playing
                        _state.value.phase == Phase.Loading -> Phase.Loading
                        else -> Phase.Paused
                    }
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // 一首播完:队列还有下一首才切,播完整个队列就停(不无限循环,悬浮球自然收场)
                    if (queue.isNotEmpty() && queueIndex < queue.size - 1) {
                        next()
                    } else {
                        stop()
                    }
                }
            }
        })
        // 进度/歌词行推进 ticker
        scope.launch {
            while (isActive) {
                delay(400)
                val cur = _state.value
                if (cur.phase == Phase.Playing || cur.phase == Phase.Paused) {
                    val pos = player.currentPosition.coerceAtLeast(0)
                    val dur = if (player.duration > 0) player.duration else cur.song?.durationMs ?: 0
                    _state.value = cur.copy(
                        positionMs = pos,
                        durationMs = dur,
                        currentLineIndex = lyricIndexAt(cur.lyrics, pos),
                    )
                }
            }
        }
    }

    private fun lyricIndexAt(lyrics: List<LyricLine>, posMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var idx = -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= posMs) idx = i else break
        }
        return idx
    }

    /** 搜歌(供工具返回候选列表) */
    suspend fun search(keyword: String, limit: Int = 20): List<NeteaseClient.Song> =
        withContext(Dispatchers.IO) { neteaseClient.search(keyword, limit) }

    /** 按关键词点歌:搜索并把整个结果作为歌单,播放第一个能拿到直链的(next/prev 在歌单内切换) */
    suspend fun playByKeyword(keyword: String): Result<NeteaseClient.Song> {
        val songs = search(keyword)
        if (songs.isEmpty()) return Result.failure(IllegalStateException("NO_RESULT"))
        for ((index, song) in songs.withIndex()) {
            val url = fetchSongUrl(song.id)
            if (!url.isNullOrBlank()) {
                queue = songs
                queueIndex = index
                loadAndPlay(song, url)
                return Result.success(song)
            }
        }
        queue = emptyList()
        return Result.failure(IllegalStateException("NO_PLAYABLE_URL"))
    }

    /** 播放指定歌曲(搜索结果点选时用) */
    suspend fun playSong(song: NeteaseClient.Song): Result<NeteaseClient.Song> {
        val url = fetchSongUrl(song.id)
        if (url.isNullOrBlank()) return Result.failure(IllegalStateException("NO_PLAYABLE_URL"))
        if (queue.none { it.id == song.id }) queue = listOf(song)
        queueIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        loadAndPlay(song, url)
        return Result.success(song)
    }

    private suspend fun fetchSongUrl(id: Long): String? =
        withContext(Dispatchers.IO) {
            runCatching { neteaseClient.getSongUrl(id) }.getOrNull()
        }

    private fun loadAndPlay(song: NeteaseClient.Song, url: String) {
        _state.value = MusicState(phase = Phase.Loading, song = song)
        urlJob?.cancel()
        urlJob = scope.launch {
            // 歌词与播放并行取,歌词晚到不影响出声
            launch {
                val lrc = runCatching { neteaseClient.getLyric(song.id) }.getOrNull()
                val lines = lrc?.let { NeteaseClient.parseLrc(it) }.orEmpty()
                _state.value = _state.value.copy(
                    lyrics = lines.map { LyricLine(it.first, it.second) },
                )
            }
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
    }

    // ---------- 控制(UI 用即发即忘版 / 工具与页面用 Await 版) ----------

    fun pause() { scope.launch { pauseAwait() } }
    fun resume() { scope.launch { resumeAwait() } }
    fun togglePlay() { scope.launch { togglePlayAwait() } }
    fun stop() { scope.launch { stopAwait() } }
    fun next() { scope.launch { nextAwait() } }
    fun prev() { scope.launch { prevAwait() } }

    /** 暂停:挂起直到生效,状态即时落定(修复工具返回"播放中"的假状态) */
    suspend fun pauseAwait() {
        withContext(Dispatchers.Main) {
            player.pause()
            if (_state.value.phase == Phase.Playing) {
                _state.value = _state.value.copy(phase = Phase.Paused)
            }
        }
    }

    /** 继续 */
    suspend fun resumeAwait() {
        withContext(Dispatchers.Main) {
            player.play()
            if (_state.value.phase == Phase.Paused) {
                _state.value = _state.value.copy(phase = Phase.Playing)
            }
        }
    }

    suspend fun togglePlayAwait() {
        withContext(Dispatchers.Main) {
            if (player.isPlaying) pauseAwait() else resumeAwait()
        }
    }

    /** 停止并清空队列(悬浮球随之消失) */
    suspend fun stopAwait() {
        withContext(Dispatchers.Main) {
            player.stop()
            player.clearMediaItems()
            queue = emptyList()
            queueIndex = 0
            _state.value = MusicState()
        }
    }

    /** 切歌:沿队列跳 delta 首,跳过拿不到直链的;返回实际切到的歌(null=队列没有可切的了) */
    suspend fun nextAwait(): NeteaseClient.Song? = skipTo(1)

    suspend fun prevAwait(): NeteaseClient.Song? = skipTo(-1)

    private suspend fun skipTo(delta: Int): NeteaseClient.Song? {
        if (queue.isEmpty()) return null
        var idx = (queueIndex + delta).mod(queue.size)
        repeat(queue.size) {
            val song = queue[idx]
            val url = fetchSongUrl(song.id)
            if (!url.isNullOrBlank()) {
                queueIndex = idx
                loadAndPlay(song, url)
                return song
            }
            idx = (idx + delta).mod(queue.size)
        }
        return null
    }

    /** 当前状态摘要(给工具结果与上下文注入用) */
    fun currentStatusText(): String {
        val cur = _state.value
        val song = cur.song ?: return "当前没有在播放音乐"
        val phaseText = when (cur.phase) {
            Phase.Playing -> "播放中"
            Phase.Paused -> "已暂停"
            Phase.Loading -> "加载中"
            Phase.Error -> "出错了"
            Phase.Idle -> "已停止"
        }
        return buildString {
            append("当前${phaseText}:《${song.name}》- ${song.artist}")
            if (queue.isNotEmpty()) {
                append("(歌单第${queueIndex + 1}/${queue.size}首)")
            }
            val line = cur.lyrics.getOrNull(cur.currentLineIndex)?.text
            if (!line.isNullOrBlank()) {
                append(";当前歌词:「${line}」")
            }
        }
    }
}
