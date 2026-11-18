package me.rerere.rikkahub.data.vitals

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * VAD 情绪词典:中文高频情绪词 → (效价 v / 唤醒 a / 支配 d)。
 * 数据在 assets/vitals_vad.json,来源:按情感语义人工标定的常用词表(后续可校准大连理工情感词汇本体)。
 *
 * 设计用途:模型自评只负责「选词 + 报强度(w) + 报否定(neg)」,
 * 数值坐标由词典查表给出——数是查表算出来的,不是模型随口报的,可解释且口径稳定。
 * 查不到的词(自造词/生僻词)返回 null,调用方回退用模型自报的 v/a。
 */
object VitalsVadDict : KoinComponent {
    data class VAD(val v: Float, val a: Float, val d: Float)

    @Volatile
    private var dict: Map<String, VAD>? = null

    private fun ensureLoaded(): Map<String, VAD> {
        dict?.let { return it }
        return synchronized(this) {
            dict?.let { return it }
            val loaded = runCatching {
                val context = get<android.content.Context>()
                val raw = context.assets.open("vitals_vad.json").bufferedReader().use { it.readText() }
                val arr = Json.parseToJsonElement(raw).jsonArray
                buildMap {
                    arr.forEach { el ->
                        runCatching {
                            val obj = el.jsonObject
                            val word = obj["word"]?.jsonPrimitive?.content?.trim().orEmpty()
                            if (word.isNotEmpty()) {
                                put(
                                    word,
                                    VAD(
                                        v = obj["v"]?.jsonPrimitive?.float ?: 0f,
                                        a = obj["a"]?.jsonPrimitive?.float ?: 0f,
                                        d = obj["d"]?.jsonPrimitive?.float ?: 0f,
                                    )
                                )
                            }
                        }
                    }
                }
            }.getOrDefault(emptyMap())
            dict = loaded
            loaded
        }
    }

    /** 查词:精确匹配优先;容忍模型带「了/起来」等尾缀(剥一次再查) */
    fun lookup(word: String): VAD? {
        if (word.isBlank()) return null
        val map = ensureLoaded()
        val w = word.trim()
        map[w]?.let { return it }
        // 常见尾缀剥离:「开心了」「激动起来」
        for (suffix in listOf("了", "起来", "得很", "极了")) {
            if (w.endsWith(suffix) && w.length > suffix.length) {
                map[w.removeSuffix(suffix)]?.let { return it }
            }
        }
        // 「被X」→查 X:被夸→夸... 词典里被X类词已自带,这里兜「被开心」这类组合
        if (w.startsWith("被") && w.length > 1) {
            map[w.removePrefix("被")]?.let { return it }
        }
        return null
    }

    fun size(): Int = ensureLoaded().size
}
