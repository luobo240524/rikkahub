package me.rerere.rikkahub.data.vitals

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 生命体征自评标签解析器(共享):主模型自评标签(VitalsReportTransformer)与
 * 小模型兜底分析(VitalsFallbackAnalyzer)都用同一套解析,保证两路数据格式一致。
 *
 * 协议字段:v=效价(-1~1) a=唤醒(0~1) emo=情绪名 cause=起因 w=强度(0~1,可选) neg=否定(可选)
 */
object VitalsTagParser {
    data class Parsed(
        val v: Float,
        val a: Float,
        val emo: String,
        val cause: String,
        val w: Float?, // 情绪强度0~1,词典算分用;null=模型没报
        val neg: Boolean, // 否定(「不难受」)
    )

    // 未闭合也要剥(流式中途标签只来了一半):闭了吃闭,没闭吃到结尾。
    // IGNORE_CASE:有的模型会吐 <VITALS>;标签名两侧容忍空白。
    private val TAG_REGEX = Regex("\\n?<vitals\\s*>[\\s\\S]*?(</vitals\\s*>|\\z)", RegexOption.IGNORE_CASE)
    private val JSON_REGEX = Regex("<vitals\\s*>\\s*(\\{[\\s\\S]*?\\})\\s*(</vitals\\s*>)?", RegexOption.IGNORE_CASE)

    // 宽松兜底正则全部预编译:①初始化期就把 ICU 不认的模式暴露出来而不是生成中途炸 ②不重复编译
    // (r301 事故:Regex(",\\s*}") 在 Android ICU 正则里 `}` 被当区间量词开头直接 PatternSyntaxException,
    // 桌面 JVM 能编过所以 CI 编译不报;只有标签真的出现时才执行到,于是带出了运行时炸)
    private val LENIENT_V = Regex("[\"']?v[\"']?\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    private val LENIENT_A = Regex("[\"']?a[\"']?\\s*:\\s*(\\d+(?:\\.\\d+)?)")
    private val LENIENT_EMO = Regex("[\"']?emo[\"']?\\s*:\\s*[\"']?([^,\"}\\n]*)")
    private val LENIENT_CAUSE = Regex("[\"']?cause[\"']?\\s*:\\s*[\"']?([^,\"}\\n]*)")
    private val LENIENT_W = Regex("[\"']?w[\"']?\\s*:\\s*(\\d+(?:\\.\\d+)?)")
    private val LENIENT_NEG = Regex("[\"']?neg[\"']?\\s*:\\s*(true|false|1|0)")
    private val FENCE_REGEX = Regex("```(?:json)?")

    fun stripTag(text: String): String = text.replace(TAG_REGEX, "").trimEnd()

    /** 从文本里抓 <vitals>{...}</vitals> 的 JSON 段;没有标签返回 null */
    fun extractJson(text: String): String? =
        JSON_REGEX.find(text)?.groupValues?.get(1)

    private fun JsonObject.numField(key: String): Float? {
        val p = this[key]?.jsonPrimitive ?: return null
        return p.floatOrNull ?: p.contentOrNull?.trim()?.toFloatOrNull()
    }

    private fun JsonObject.negField(): Boolean {
        val p = this["neg"]?.jsonPrimitive ?: return false
        return p.booleanOrNull
            ?: when (p.contentOrNull?.lowercase()) {
                "true", "1" -> true
                else -> false
            }
    }

    /** 解析标签内的 JSON(严格 JSON 优先,失败退宽松正则);整段受保护绝不抛出 */
    fun parseVitals(raw: String): Parsed? = runCatching {
        val cleaned = clean(raw)
        runCatching {
            val obj = Json.parseToJsonElement(cleaned).jsonObject
            val v = obj.numField("v") ?: return@runCatching null
            val a = obj.numField("a") ?: return@runCatching null
            return Parsed(
                v = v,
                a = a,
                emo = obj["emo"]?.jsonPrimitive?.contentOrNull ?: "",
                cause = obj["cause"]?.jsonPrimitive?.contentOrNull ?: "",
                w = obj.numField("w")?.coerceIn(0f, 1f),
                neg = obj.negField(),
            )
        }
        // 宽松兜底:引号可有可无,值允许裸数字/裸词
        val v = LENIENT_V.find(cleaned)?.groupValues?.get(1)?.toFloatOrNull() ?: return@runCatching null
        val a = LENIENT_A.find(cleaned)?.groupValues?.get(1)?.toFloatOrNull() ?: return@runCatching null
        val emo = LENIENT_EMO.find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        val cause = LENIENT_CAUSE.find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        val w = LENIENT_W.find(cleaned)?.groupValues?.get(1)?.toFloatOrNull()?.coerceIn(0f, 1f)
        val neg = LENIENT_NEG.find(cleaned)?.groupValues?.get(1)?.let { it == "true" || it == "1" } ?: false
        Parsed(v, a, emo, cause, w, neg)
    }.getOrNull()

    /**
     * 弹性解析(小模型兜底用):小模型可能带 <vitals> 标签也可能直接裸输出 JSON,
     * 有标签就抓标签内 JSON,没有就把整段(剥围栏后)当 JSON 候选。
     */
    fun parseFlexible(text: String): Parsed? {
        val candidate = extractJson(text) ?: clean(text)
        return parseVitals(candidate)
    }

    private fun clean(raw: String): String = raw
        .replace(FENCE_REGEX, "")
        .replace("“", "\"").replace("”", "\"")
        .replace("：", ":") // 全角冒号
        .replace(",}", " }").replace(", }", " }") // 尾逗号(纯字符串替换,不用正则)
        .trim()
}
