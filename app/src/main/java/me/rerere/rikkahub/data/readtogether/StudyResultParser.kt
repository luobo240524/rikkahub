package me.rerere.rikkahub.data.readtogether

import kotlinx.serialization.json.Json

/**
 * 章节学习结果解析:模型只该吐一个 JSON,但常见围栏/前后废话/尾逗号。
 * 先截首个 { 到末个 },去围栏与尾逗号,再严格解析;失败返回 null(调用方报错重试)。
 */
object StudyResultParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(raw: String): StudyPayload? {
        val body = extractJson(raw) ?: return null
        return runCatching { json.decodeFromString<StudyPayload>(body) }.getOrNull()
    }

    fun parseMerge(raw: String): BookMergePayload? {
        val body = extractJson(raw) ?: return null
        return runCatching { json.decodeFromString<BookMergePayload>(body) }.getOrNull()
    }

    /** 供测试使用:把模型原文清洗成可解析的 JSON 文本 */
    fun extractJson(raw: String): String? {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .removeSuffix("```").trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        var body = text.substring(start, end + 1)
        // 尾逗号:{"a":1,} / [1,2,]
        body = body.replace(Regex(",\\s*([}\\]])"), "$1")
        return body
    }

    fun isEmpty(payload: StudyPayload): Boolean {
        val recap = payload.recap
        val recapEmpty = recap == null ||
            (recap.plot.isBlank() && recap.characters.isEmpty() && recap.threads.isEmpty() && recap.quotes.isEmpty())
        return recapEmpty && payload.notes.all { it.title.isBlank() && it.content.isBlank() } &&
            payload.memory?.content.isNullOrBlank() &&
            payload.skill?.description.isNullOrBlank()
    }
}
