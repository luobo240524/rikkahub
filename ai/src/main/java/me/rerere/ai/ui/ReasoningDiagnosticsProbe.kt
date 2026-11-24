package me.rerere.ai.ui

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.util.json
import me.rerere.common.http.jsonArrayOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.common.http.jsonPrimitiveOrNull
import me.rerere.common.android.Logging
import okhttp3.Headers

/** Temporary verbose diagnostics for the Chat Completions reasoning-history investigation. */
object ReasoningDiagnosticsProbe {
    private const val TAG = "ChatCompletionsReasoning"
    private const val LOG_CHUNK_SIZE = 3000
    private val reasoningKeys = setOf("reasoning_content", "reasoning", "reasoning_details", "thinking")

    fun logRawSse(data: String) {
        if (data == "[DONE]") {
            log("raw_response [DONE]")
            return
        }
        val payload = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
        val choice = payload?.get("choices")?.jsonArrayOrNull?.firstOrNull()?.jsonObjectOrNull
        val source = choice?.get("delta")?.jsonObjectOrNull ?: choice?.get("message")?.jsonObjectOrNull
        val fields = source?.filterKeys { it in reasoningKeys }.orEmpty()
        val arrayContent = source?.get("content")?.takeIf { it is JsonArray }
        log("raw_response id=${payload?.get("id")?.jsonPrimitiveOrNull?.contentOrNull} " +
            "choiceKeys=${choice?.keys?.sorted()} sourceKeys=${source?.keys?.sorted()} " +
            "reasoningFields=${JsonObject(fields)}" + if (arrayContent != null) " content=$arrayContent" else "")
    }

    fun logParsedMessage(stage: String, message: UIMessage) = log("$stage ${describe(message)}")

    fun logMessages(stage: String, messages: List<UIMessage>) {
        messages.filter { it.role == MessageRole.ASSISTANT }.forEachIndexed { index, message ->
            log("$stage assistant[$index] ${describe(message)}")
        }
    }

    fun logHistoryAssistant(stage: String, message: UIMessage, serialized: JsonObject? = null) {
        log("$stage ${describe(message)}")
        serialized?.let { log("$stage serialized=$it") }
    }

    fun logRequest(body: JsonObject, headers: Headers, apiKey: String?, includeHistoryReasoning: Boolean) {
        val secrets = listOfNotNull(apiKey).filter(String::isNotBlank)
        val safeHeaders = headers.names().sorted().joinToString(", ", prefix = "{", postfix = "}") { name ->
            val value = headers.values(name).joinToString(",") { headerValue ->
                if (isSecretHeader(name)) "***" else redact(headerValue, secrets)
            }
            "$name=[$value]"
        }
        log("request includeHistoryReasoning=$includeHistoryReasoning headers=$safeHeaders body=${redact(body.toString(), secrets)}")
    }

    private fun describe(message: UIMessage): String {
        val parts = message.parts.joinToString(prefix = "[", postfix = "]") { part ->
            when (part) {
                is UIMessagePart.Text -> "Text(text=${part.text.quoted()}, metadata=${part.metadata})"
                is UIMessagePart.Reasoning -> "Reasoning(type=${part.reasoningType}, text=${part.reasoning.quoted()}, metadata=${part.metadata})"
                is UIMessagePart.Tool -> "Tool(id=${part.toolCallId}, name=${part.toolName}, input=${part.input.quoted()}, outputParts=${part.output.size}, metadata=${part.metadata})"
                is UIMessagePart.ServerTool -> "ServerTool(id=${part.toolCallId}, name=${part.toolName}, status=${part.status}, input=${part.input}, output=${part.output}, metadata=${part.metadata})"
                is UIMessagePart.Image -> "Image(urlLength=${part.url.length}, metadata=${part.metadata})"
                is UIMessagePart.Video -> "Video(urlLength=${part.url.length}, metadata=${part.metadata})"
                is UIMessagePart.Audio -> "Audio(urlLength=${part.url.length}, metadata=${part.metadata})"
                is UIMessagePart.Document -> "Document(name=${part.fileName.quoted()}, urlLength=${part.url.length}, metadata=${part.metadata})"
                else -> part::class.simpleName.orEmpty()
            }
        }
        return "role=${message.role} id=${message.id} finishedAt=${message.finishedAt} parts=$parts"
    }

    private fun log(message: String) {
        message.chunked(LOG_CHUNK_SIZE).forEachIndexed { index, chunk ->
            val line = "part=${index + 1} $chunk"
            Log.i(TAG, line)
            // 同时写入 App 内置日志页（日志页只读 Logging.log，不读 logcat）
            Logging.log(TAG, line)
        }
    }

    private fun isSecretHeader(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized == "authorization" || normalized == "proxy-authorization" ||
            normalized == "api-key" || normalized == "x-api-key" ||
            normalized.contains("token") || normalized.contains("secret")
    }

    private fun redact(value: String, secrets: List<String>): String {
        var safe = value.replace(Regex("(?i)(Bearer\\s+)[^\\s,}\\\"]+"), "\\$1***")
        secrets.forEach { safe = safe.replace(it, "***") }
        return safe
    }

    private fun String.quoted(): String = replace("\\", "\\\\").replace("\n", "\\n")
        .replace("\r", "\\r").replace("\"", "\\\"").let { "\"$it\"" }
}
