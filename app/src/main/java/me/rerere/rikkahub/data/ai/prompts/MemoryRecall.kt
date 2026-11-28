package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.model.MemoryFile

/**
 * 记忆行级预取。
 *
 * 主对话的记忆注入只有两档:`/profile.md` 与 `/preferences.md` 全文,其余文件在 `<memory_listing>`
 * 里各占一行,想看内容得让模型主动 `memory_read`。这里补一层本地预取:用户这句话里提到的名字或主题
 * 若出现在某条记忆条目里,就把**那几行**直接带进 prompt —— 模型不用再调工具。
 *
 * 只做字符串匹配:不调模型、不改记忆写入格式、不动数据库。召回不到就什么都不加,行为退化成现状。
 */
internal data class RecalledMemory(
    val path: String,
    val section: String?,
    val text: String,
)

internal object MemoryRecall {
    /** 一次最多带回几条条目 */
    const val MAX_ENTRIES = 8

    /** 带回内容的字符预算(只算条目正文) */
    const val MAX_CHARS = 1200

    private const val MIN_QUERY_CHARS = 2
    private const val MIN_MATCH_CJK = 3
    private const val MIN_MATCH_LATIN = 4

    /** 这两个文件已经全文注入,不再重复召回 */
    private val PINNED_PATHS = setOf(MemoryFile.PROFILE_PATH, MemoryFile.PREFERENCES_PATH)

    /** 纯虚词命中,不算相关 */
    private val FUNCTION_CHARS = "的了是我你他她它这那有和就都很也还说在吗呢吧啊么什没不才把被给让对从到为以及或但而".toSet()

    /** 常见无信息量的两三字组合 */
    private val NOISE_MATCHES = setOf(
        "什么", "怎么", "这个", "那个", "一个", "可以", "不是", "就是", "我们", "你们", "他们",
        "现在", "时候", "问题", "东西", "这样", "那样", "如果", "因为", "所以", "但是", "还是",
    )

    private data class Scored(val entry: RecalledMemory, val score: Int)

    /**
     * @param userText 用户最新一条消息的纯文本
     * @return 命中的记忆条目,按相关度从高到低;无命中返回空列表
     */
    fun recall(files: List<MemoryFile>, userText: String): List<RecalledMemory> {
        val query = normalize(userText)
        if (query.length < MIN_QUERY_CHARS) return emptyList()

        val scored = mutableListOf<Scored>()
        for (file in files) {
            if (file.path in PINNED_PATHS) continue
            var section: String? = null
            for (raw in file.body.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    section = line.trimStart('#').trim().ifEmpty { null }
                    continue
                }
                val entry = stripEntryPrefix(line)
                if (entry.length < MIN_MATCH_CJK) continue
                val matched = longestCommonSubstring(normalize(entry), query)
                if (!isMeaningful(matched)) continue
                // 命中越长越可信;条目越短,说明这条就是专门讲它的,略加分
                val score = matched.length * 10 + if (entry.length <= 80) 2 else 0
                scored += Scored(RecalledMemory(file.path, section, entry), score)
            }
        }
        if (scored.isEmpty()) return emptyList()

        val out = mutableListOf<RecalledMemory>()
        var usedChars = 0
        val seen = HashSet<String>()
        for (item in scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.entry.path })) {
            if (out.size >= MAX_ENTRIES) break
            val key = item.entry.path + '\u0000' + item.entry.text
            if (!seen.add(key)) continue
            if (usedChars + item.entry.text.length > MAX_CHARS && out.isNotEmpty()) break
            out += item.entry
            usedChars += item.entry.text.length
        }
        return out
    }

    /** 只保留字母与数字(CJK 也算字母),用来做与标点无关的匹配 */
    private fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            if (c.isLetterOrDigit()) append(c.lowercaseChar())
        }
    }

    /** 去掉列表符号与 `[stated]` 标记,只留条目正文 */
    private fun stripEntryPrefix(line: String): String = line
        .replace(Regex("""^([-*+]|\d+[.)])\s+"""), "")
        .replace(Regex("""^\[stated\]\s*""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun isMeaningful(matched: String): Boolean {
        if (matched.isEmpty()) return false
        if (matched in NOISE_MATCHES) return false
        if (matched.all { it in FUNCTION_CHARS }) return false
        val hasCjk = matched.any { isCjk(it) }
        return if (hasCjk) matched.length >= MIN_MATCH_CJK else matched.length >= MIN_MATCH_LATIN
    }

    private fun isCjk(c: Char): Boolean = c.code in 0x3400..0x9FFF || c.code in 0xF900..0xFAFF

    /** 最长公共子串(两行滚动 DP,记忆条目都很短,成本可忽略) */
    private fun longestCommonSubstring(a: String, b: String): String {
        if (a.isEmpty() || b.isEmpty()) return ""
        var bestLen = 0
        var bestEnd = 0
        var prev = IntArray(b.length + 1)
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                if (a[i - 1] == b[j - 1]) {
                    cur[j] = prev[j - 1] + 1
                    if (cur[j] > bestLen) {
                        bestLen = cur[j]
                        bestEnd = i
                    }
                } else {
                    cur[j] = 0
                }
            }
            val swap = prev
            prev = cur
            cur = swap
            cur.fill(0)
        }
        return if (bestLen == 0) "" else a.substring(bestEnd - bestLen, bestEnd)
    }
}
