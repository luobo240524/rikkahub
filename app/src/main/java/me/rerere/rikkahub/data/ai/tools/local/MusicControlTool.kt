package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.music.MusicPlayerController

/**
 * music_control:"一起听"工具。助手可搜歌/点歌/暂停/继续/切歌/停止,
 * 播放状态与歌词由系统按轮注入上下文(见 ChatService),TA 边听边聊。
 */
internal fun buildMusicControlTool(
    musicPlayer: MusicPlayerController,
): Tool {
    return Tool(
        name = "music_control",
        description = "Play music together with the user (NetEase Cloud Music). " +
            "Use when the user asks to listen to music, or wants a song for the mood; you can also start a song " +
            "yourself and chat about it while it plays.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("description", "One of: search / play / pause / resume / next / prev / stop / status")
                        put("enum", buildJsonArray {
                            add("search"); add("play"); add("pause"); add("resume")
                            add("next"); add("prev"); add("stop"); add("status")
                        })
                    })
                    put("keyword", buildJsonObject {
                        put("type", "string")
                        put("description", "Song title (and artist). Required for search/play")
                    })
                },
                required = listOf("action")
            )
        },
        systemPrompt = { _, _ ->
            buildString {
                append("一起听使用规则：\n")
                append("- 用户想听歌/点歌时用 music_control;play 传歌名(可带歌手),系统会自动选第一个能播的版本\n")
                append("- 播放中每轮你都能在上下文看到当前歌与歌词,可以自然地聊歌、解读歌词,不要复述工具返回的 JSON\n")
                append("- 拿不到播放链接(VIP/版权限制)时,如实告诉用户这首歌放不了,建议换一首或登录网易云音乐账号\n")
                append("- 一次点一首歌;用户想换歌就再调 play;「下一首/上一首」用 next/prev\n")
                append("- 长对话不要频繁切歌打断聊天;尊重用户「暂停/别放了」的指令,stop 会停止播放\n")
            }
        },
        execute = { args ->
            val obj = args as? JsonObject
            val action = obj?.get("action")?.let { (it as? JsonPrimitive)?.contentOrNull }
            val keyword = obj?.get("keyword")?.let { (it as? JsonPrimitive)?.contentOrNull }
            val payload = when (action) {
                "search" -> {
                    if (keyword.isNullOrBlank()) {
                        buildJsonObject {
                            put("error", "EMPTY_KEYWORD"); put("message", "keyword is required for search")
                        }
                    } else {
                        val songs = musicPlayer.search(keyword)
                        buildJsonObject {
                            put("success", true)
                            put("songs", buildJsonObject {
                                songs.take(10).forEachIndexed { i, s ->
                                    put(i.toString(), buildJsonObject {
                                        put("id", s.id)
                                        put("title", s.name)
                                        put("artist", s.artist)
                                        put("duration_sec", s.durationMs / 1000)
                                    })
                                }
                            })
                            if (songs.isEmpty()) put("message", "没有搜到相关歌曲")
                        }
                    }
                }
                "play" -> {
                    if (keyword.isNullOrBlank()) {
                        buildJsonObject {
                            put("error", "EMPTY_KEYWORD"); put("message", "keyword is required for play")
                        }
                    } else {
                        musicPlayer.playByKeyword(keyword).fold(
                            onSuccess = { song ->
                                buildJsonObject {
                                    put("success", true)
                                    put("title", song.name)
                                    put("artist", song.artist)
                                    put("status", musicPlayer.currentStatusText())
                                }
                            },
                            onFailure = { e ->
                                buildJsonObject {
                                    put("error", e.message ?: "PLAY_FAILED")
                                    put(
                                        "message",
                                        when (e.message) {
                                            "NO_RESULT" -> "没有搜到这首歌,换个关键词试试"
                                            else -> "这首歌拿不到播放链接(VIP 或版权限制),可以换一首,或请用户登录网易云音乐账号"
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
                "pause" -> { musicPlayer.pause(); statusPayload(musicPlayer) }
                "resume" -> { musicPlayer.resume(); statusPayload(musicPlayer) }
                "next" -> { musicPlayer.next(); statusPayload(musicPlayer) }
                "prev" -> { musicPlayer.prev(); statusPayload(musicPlayer) }
                "stop" -> { musicPlayer.stop(); statusPayload(musicPlayer) }
                "status" -> statusPayload(musicPlayer)
                else -> buildJsonObject {
                    put("error", "UNKNOWN_ACTION")
                    put("message", "action 必须是 search/play/pause/resume/next/prev/stop/status")
                }
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    )
}

private fun statusPayload(musicPlayer: MusicPlayerController): kotlinx.serialization.json.JsonObject {
    return buildJsonObject {
        put("success", true)
        put("status", musicPlayer.currentStatusText())
    }
}
