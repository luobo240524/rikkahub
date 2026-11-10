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
 */
internal fun buildMoodReadTool(
    moodCheckinDAO: MoodCheckinDAO,
): Tool = Tool(
    name = "mood_read",
    description = "Read recent mood check-in records of the user and yourself. " +
        "Use it before commenting on moods, doing weekly reports, or when the user asks about mood history.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("days", buildJsonObject {
                    put("type", "integer")
                    put("description", "How many days back to read, default 30")
                })
            },
            required = listOf()
        )
    },
    execute = { args ->
        val days = (args as? JsonObject)?.get("days")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.contentOrNull?.toIntOrNull()?.coerceIn(1, 365) ?: 30
        val since = dateSince(days)
        val userRecords = withContext(Dispatchers.IO) {
            runCatching { moodCheckinDAO.recentBySubject("user", since) }.getOrElse { emptyList() }
        }.filter { isDue(it.scheduledAt) } // 未到点的定时打卡不出现
        val assistantRecords = withContext(Dispatchers.IO) {
            runCatching { moodCheckinDAO.recentBySubject(SUBJECT_ASSISTANT, since) }.getOrElse { emptyList() }
        }.filter { isDue(it.scheduledAt) }
        val payload = buildJsonObject {
            put("days", days)
            put("user", buildJsonArrayOfCheckins(userRecords))
            put("assistant", buildJsonArrayOfCheckins(assistantRecords))
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildJsonArrayOfCheckins(records: List<MoodCheckinEntity>) = kotlinx.serialization.json.buildJsonArray {
    for (r in records) {
        add(
            buildJsonObject {
                put("id", r.id)
                put("date", r.date)
                put("mood", r.mood)
                put("phrase", r.phrase)
            }
        )
    }
}
