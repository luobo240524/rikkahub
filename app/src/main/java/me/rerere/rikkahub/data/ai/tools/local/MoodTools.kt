package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.entity.MoodCheckinEntity
import me.rerere.rikkahub.data.db.entity.isDue
import java.time.LocalDate

private const val SUBJECT_ASSISTANT = "assistant"

private fun today(): String = LocalDate.now().toString()

private fun dateSince(days: Int): String = LocalDate.now().minusDays(days.toLong()).toString()

/**
 * mood_checkin：助手替自己打卡(也可以代用户记录,由助手在 description 里引导)。
 * 同一天可以多次打卡,每次各自成条,不覆盖。
 */
internal fun buildMoodCheckinTool(
    moodCheckinDAO: MoodCheckinDAO,
): Tool = Tool(
    name = "mood_checkin",
    description = "Do a mood check-in. Records the mood (an emoji) with a short phrase. " +
        "Use it when the user asks you to check in, or when doing a daily mood report. " +
        "Multiple check-ins per day are allowed; each one is kept as a separate entry. " +
        "Optionally schedule it: the check-in becomes visible at the given time (e.g. a good-morning check-in).",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("mood", buildJsonObject {
                    put("type", "string")
                    put("description", "Mood as a single emoji, e.g. 😊 😴 🤩")
                })
                put("phrase", buildJsonObject {
                    put("type", "string")
                    put("description", "A short phrase describing the feeling (one sentence)")
                })
                put("scheduledAt", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional effective time, local format 'yyyy-MM-dd HH:mm'. " +
                        "Until then the check-in stays invisible to the user. Omit = now. " +
                        "The check-in date is derived from this time when given.")
                })
            },
            required = listOf("mood", "phrase")
        )
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val mood = argsObj?.get("mood")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull.orEmpty().take(8)
        val phrase = argsObj?.get("phrase")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull.orEmpty().take(200)
        val scheduledAt = parseScheduledAt(argsObj?.get("scheduledAt")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull)
        val payload = if (mood.isBlank() || phrase.isBlank()) {
            buildJsonObject { put("ok", false); put("error", "MISSING_MOOD_OR_PHRASE") }
        } else {
            val checkinDate = scheduledAt?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            } ?: today()
            withContext(Dispatchers.IO) {
                runCatching {
                    moodCheckinDAO.insert(
                        MoodCheckinEntity(
                            date = checkinDate,
                            subject = SUBJECT_ASSISTANT,
                            mood = mood,
                            phrase = phrase,
                            createdAt = System.currentTimeMillis(),
                            scheduledAt = scheduledAt,
                        )
                    )
                }.isSuccess
            }
            buildJsonObject {
                put("ok", true)
                put("date", checkinDate)
                put("mood", mood)
                put("phrase", phrase)
                if (scheduledAt != null) {
                    put("scheduled", true)
                    put("dueAt", scheduledAt)
                }
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

/**
 * mood_read：读取最近 N 天的打卡记录(用户与助手各自的),供助手回顾心情趋势。
 * 省token:默认摘要(短语60字)+query 关键词检索+分页,确有需要 full=true 取全文。
 */
internal fun buildMoodReadTool(
    moodCheckinDAO: MoodCheckinDAO,
): Tool = Tool(
    name = "mood_read",
    description = "Read recent mood check-in records of the user and yourself. " +
        "Supports keyword search over mood/phrase. Returns short summaries by default; pass full=true " +
        "for complete phrases. Use it before commenting on moods, doing weekly reports, or when the user asks about mood history.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("days", buildJsonObject {
                    put("type", "integer")
                    put("description", "How many days back to read, default 30")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "Max records per page, default 20, max 50")
                })
                put("offset", buildJsonObject {
                    put("type", "integer")
                    put("description", "Skip N records for pagination, default 0")
                })
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional keyword to search mood emoji and phrase (case-insensitive)")
                })
                put("full", buildJsonObject {
                    put("type", "boolean")
                    put("description", "true = full phrase; default false = 60-char summary (saves tokens)")
                })
            },
            required = listOf()
        )
    },
    execute = { args ->
        val argsObj = args as? JsonObject
        val readArgs = parseSocialReadArgs(argsObj, defaultLimit = 20)
        val days = argsObj?.get("days")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toIntOrNull()?.coerceIn(1, 365) ?: 30
        val since = dateSince(days)
        val records = buildList {
            // 合并两主体,统一按时间倒序分页(旧结构 user/assistant 两组已改为 subject 字段)
            addAll(withContext(Dispatchers.IO) {
                runCatching { moodCheckinDAO.recentBySubject("user", since) }.getOrElse { emptyList() }
            }.filter { isDue(it.scheduledAt) }.map { "user" to it })
            addAll(withContext(Dispatchers.IO) {
                runCatching { moodCheckinDAO.recentBySubject(SUBJECT_ASSISTANT, since) }.getOrElse { emptyList() }
            }.filter { isDue(it.scheduledAt) }.map { SUBJECT_ASSISTANT to it })
        }
        val filtered = filterByQuery(records, readArgs.query) { (_, r) ->
            listOf(r.mood, r.phrase)
        }.sortedByDescending { (_, r) -> r.createdAt }
        val page = readArgs.slice(filtered)
        val payload = buildJsonObject {
            put("days", days)
            put("total", filtered.size)
            put("hasMore", readArgs.hasMore(filtered.size))
            put("summaryOnly", !readArgs.full)
            put("checkins", kotlinx.serialization.json.buildJsonArray {
                for ((subject, r) in page) {
                    add(
                        buildJsonObject {
                            put("id", r.id)
                            put("subject", subject)
                            put("date", r.date)
                            put("mood", r.mood)
                            put("phrase", if (readArgs.full) r.phrase else excerptForTool(r.phrase))
                        }
                    )
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
