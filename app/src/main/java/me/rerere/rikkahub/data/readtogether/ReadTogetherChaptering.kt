package me.rerere.rikkahub.data.readtogether

import kotlinx.serialization.Serializable

/**
 * 章节元数据(随 BookMeta 存 books.json;旧数据无此字段时默认空,首次打开懒生成)。
 * startPage/endPage 为应用内页号(1-based,闭区间)。
 */
@Serializable
data class ChapterMeta(
    val index: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int,
)

/** 章节在段落序列中的范围(end 不含),分页后换算成页范围 */
data class ChapterSpan(
    val title: String,
    val startParagraph: Int,
    val endParagraph: Int,
)

/** 分页结果 + 每个原始段落在第几页(1-based),章节边界靠它定位 */
data class PagedText(
    val pages: List<String>,
    val paragraphPage: List<Int>,
)

/**
 * 共同阅读的章节切分:纯函数,不碰 Context/文件,便于单测。
 * - EPUB:每个 spine 文档一章(标题取 dc:title 之外的 <title>/h1-h3)
 * - TXT:按「第X章/Chapter N/序章…」标题行切;没有标题返回空,由调用方退化为页块
 * - PDF/兜底:每 [PAGE_BLOCK] 页一块
 */
object ReadTogetherChaptering {

    const val PAGE_BLOCK = 10

    // 标题行特征:章节序号 + 章/节/回/卷/部/篇,或常见书名结构词;限制长度避免误命中正文长句
    private val headingRegex = Regex(
        "^\\s*(第[0-9０-９一二三四五六七八九十百千零两〇]+[章节回卷部篇]|chapter\\s+\\d+|序章|序言|楔子|引子|尾声|后记|番外[0-9０-９一二三四五六七八九十]*).*$",
        RegexOption.IGNORE_CASE,
    )

    fun isHeading(line: String): Boolean {
        val t = line.trim()
        return t.length in 1..48 && headingRegex.matches(t)
    }

    /**
     * 段落聚合为 ~[target] 字/页(段不跨页),同时记录每段落在第几页。
     * 单段超过 2 倍目标时硬切(与原 chunkIntoPages 口径一致),该段记在切分后的第一页。
     */
    fun chunkIntoPages(paragraphs: List<String>, target: Int = 900): PagedText {
        if (paragraphs.isEmpty()) return PagedText(emptyList(), emptyList())
        val pages = mutableListOf<String>()
        val paragraphPage = IntArray(paragraphs.size)
        val buf = StringBuilder()
        var pageNo = 1
        fun flush() {
            pages.add(buf.toString())
            buf.clear()
            pageNo++
        }
        paragraphs.forEachIndexed { i, p ->
            if (buf.isNotEmpty() && buf.length + p.length > target) flush()
            val startPage = pageNo
            if (buf.isEmpty() && p.length > target * 2) {
                // 超长单段硬切:除最后一块外都独立成页,最后一块留在 buf
                val chunks = p.chunked(target)
                chunks.forEachIndexed { ci, piece ->
                    if (ci == chunks.size - 1) {
                        buf.append(piece)
                    } else {
                        pages.add(piece)
                        pageNo++
                    }
                }
            } else {
                if (buf.isNotEmpty()) buf.append("\n")
                buf.append(p)
            }
            paragraphPage[i] = startPage
        }
        if (buf.isNotEmpty()) pages.add(buf.toString())
        return PagedText(pages, paragraphPage.toList())
    }

    /** TXT:按标题行切章;标题行前若有正文,记作「开篇」 */
    fun txtSpans(paragraphs: List<String>): List<ChapterSpan> {
        val starts = paragraphs.indices.filter { isHeading(paragraphs[it]) }
        if (starts.isEmpty()) return emptyList()
        val spans = mutableListOf<ChapterSpan>()
        if (starts.first() > 0) {
            spans.add(ChapterSpan("开篇", 0, starts.first()))
        }
        starts.forEachIndexed { i, start ->
            val end = starts.getOrNull(i + 1) ?: paragraphs.size
            spans.add(ChapterSpan(paragraphs[start].trim().take(40), start, end))
        }
        return spans
    }

    /** EPUB:每个非空 spine 文档一章,标题按 文档标题 → 第 N 节 兜底 */
    fun epubSpans(docs: List<Pair<String, List<String>>>): List<ChapterSpan> {
        val spans = mutableListOf<ChapterSpan>()
        var cursor = 0
        docs.forEachIndexed { i, (docTitle, paragraphs) ->
            if (paragraphs.isEmpty()) return@forEachIndexed
            val end = cursor + paragraphs.size
            spans.add(ChapterSpan(docTitle.ifBlank { "第${i + 1}节" }.trim().take(40), cursor, end))
            cursor = end
        }
        return spans
    }

    /** 段落范围 → 页范围;空章跳过,索引重排为连续值 */
    fun chapterPages(
        spans: List<ChapterSpan>,
        paragraphPage: List<Int>,
        totalPages: Int,
    ): List<ChapterMeta> {
        if (totalPages <= 0) return emptyList()
        return spans.mapIndexedNotNull { idx, span ->
            val start = paragraphPage.getOrNull(span.startParagraph) ?: return@mapIndexedNotNull null
            val last = paragraphPage.getOrNull(span.endParagraph - 1) ?: start
            ChapterMeta(
                index = idx,
                title = span.title,
                startPage = start.coerceIn(1, totalPages),
                endPage = last.coerceIn(start.coerceIn(1, totalPages), totalPages),
            )
        }.let { list ->
            // 过滤空范围后按新顺序重排 index,保证 index 与列表下标一致
            list.mapIndexed { i, c -> c.copy(index = i) }
        }
    }

    /** PDF/兜底:每 PAGE_BLOCK 页一章 */
    fun pageBlockChapters(totalPages: Int, size: Int = PAGE_BLOCK): List<ChapterMeta> {
        if (totalPages <= 0) return emptyList()
        val step = size.coerceAtLeast(1)
        return (1..totalPages step step).mapIndexed { idx, start ->
            val end = (start + step - 1).coerceAtMost(totalPages)
            ChapterMeta(
                index = idx,
                title = if (totalPages <= step) "全书" else "第 $start–$end 页",
                startPage = start,
                endPage = end,
            )
        }
    }

    /**
     * 旧书/无章节信息兜底:扫每页首行找标题;有标题按标题切,没有就每 10 页一块。
     * 原文件导入后已删除,只能基于已存页文本重建。
     */
    fun inferChaptersFromPages(pages: List<String>): List<ChapterMeta> {
        if (pages.isEmpty()) return emptyList()
        val starts = pages.mapIndexedNotNull { i, text ->
            val firstLine = text.lineSequence().firstOrNull().orEmpty()
            if (isHeading(firstLine)) i + 1 else null
        }
        if (starts.isEmpty()) return pageBlockChapters(pages.size)
        val chapters = mutableListOf<ChapterMeta>()
        if (starts.first() > 1) {
            chapters.add(ChapterMeta(0, "开篇", 1, starts.first() - 1))
        }
        starts.forEachIndexed { idx, start ->
            val end = (starts.getOrNull(idx + 1) ?: (pages.size + 1)) - 1
            val title = pages[start - 1].lineSequence().firstOrNull()?.trim()
                ?.take(40).orEmpty().ifBlank { "第${idx + 1}章" }
            chapters.add(ChapterMeta(chapters.size, title, start, end))
        }
        return chapters
    }

    /** 句末标点(视觉行合并用):命中说明该行是自然段的结尾 */
    private val PDF_SENTENCE_ENDS = charArrayOf('。', '！', '!', '？', '?', '…', '；', ';', '.', '"', '”', '」', '』')

    /**
     * r377: PDF 视觉行合并。PDF 按排版行宽硬换行(手机排版 ~25 字/行),提取后每个视觉行
     * 都自带换行,阅读页按行渲染会把一句话腰斩成多个"段落"(「上帝」都能被劈开)。
     * 规则:行以句末标点收尾、或明显短行(长度 < 页最大行长 60%,标题/列表)、或页内最后一行
     * 时结束当前自然段;两侧都是 ASCII 字母数字时补一个空格防英文单词粘连。
     */
    fun joinPdfVisualLines(lines: List<String>): List<String> {
        if (lines.size <= 1) return lines
        val maxLen = lines.maxOf { it.length }
        if (maxLen <= 1) return lines
        val buf = StringBuilder()
        val result = mutableListOf<String>()
        fun flush() {
            val s = buf.toString().trim()
            if (s.isNotEmpty()) result.add(s)
            buf.setLength(0)
        }
        for ((index, line) in lines.withIndex()) {
            if (buf.isNotEmpty()) {
                val prev = buf.last()
                val next = line.first()
                if (prev.isLetterOrDigit() && prev.code < 128 && next.isLetterOrDigit() && next.code < 128) {
                    buf.append(' ')
                }
            }
            buf.append(line)
            val endsSentence = line.last() in PDF_SENTENCE_ENDS
            val shortStandalone = line.length < maxLen * 0.6
            if (endsSentence || shortStandalone || index == lines.lastIndex) flush()
        }
        flush()
        return result
    }
}
