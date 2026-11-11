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
                    next()
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

    /** 按关键词点歌:搜索并播放第一个能拿到直链的 */
    suspend fun playByKeyword(keyword: String): Result<NeteaseClient.Song> {
        val songs = search(keyword)
        if (songs.isEmpty()) return Result.failure(IllegalStateException("NO_RESULT"))
        for (song in songs) {
            val url = runCatching { neteaseClient.getSongUrl(song.id) }.getOrNull()
            if (!url.isNullOrBlank()) {
                queue = listOf(song)
                queueIndex = 0
                loadAndPlay(song, url)
                return Result.success(song)
            }
        }
        return Result.failure(IllegalStateException("NO_PLAYABLE_URL"))
    }

    /** 播放指定歌曲(工具点歌指定 id 时用) */
    suspend fun playSong(song: NeteaseClient.Song): Result<NeteaseClient.Song> {
        val url = runCatching { neteaseClient.getSongUrl(song.id) }.getOrNull()
        if (url.isNullOrBlank()) return Result.failure(IllegalStateException("NO_PLAYABLE_URL"))
        queue = listOf(song)
        queueIndex = 0
        loadAndPlay(song, url)
        return Result.success(song)
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

    fun pause() {
        scope.launch { player.pause() }
    }

    fun resume() {
        scope.launch { player.play() }
    }

    fun togglePlay() {
        scope.launch {
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun stop() {
        scope.launch {
            player.stop()
            player.clearMediaItems()
            queue = emptyList()
            queueIndex = 0
            _state.value = MusicState()
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        val nextIndex = (queueIndex + 1).mod(queue.size)
        queueIndex = nextIndex
        scope.launch { playSong(queue[nextIndex]) }
    }

    fun prev() {
        if (queue.isEmpty()) return
        val prevIndex = (queueIndex - 1).mod(queue.size)
        queueIndex = prevIndex
        scope.launch { playSong(queue[prevIndex]) }
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
            val line = cur.lyrics.getOrNull(cur.currentLineIndex)?.text
            if (!line.isNullOrBlank()) {
                append(";当前歌词:「${line}」")
            }
        }
    }
}
