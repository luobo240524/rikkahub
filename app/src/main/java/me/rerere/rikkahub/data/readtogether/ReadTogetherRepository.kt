package me.rerere.rikkahub.data.readtogether

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * 共同阅读:人机同步读书(发现页入口,r252 三期)。
 * - 导入 txt/epub/pdf 自动解析成"页"(pdf 按自然页,txt/epub 按段落聚合 ~900 字/页)
 * - 单一共享进度:人翻到第几页,机器就知道在第几页(books.json 里的 currentPage)
 * - 双方标注与评论共享(annotations.json):用户长按段落标注,助手走 read_together 工具标注
 * - 存储全在 filesDir/read_together/(books.json + <id>/pNNN.txt + annotations.json),不走 Room 免迁移
 */
class ReadTogetherRepository(private val context: Context) {

    @Serializable
    data class BookMeta(
        val id: String,
        val title: String,
        val author: String = "",
        val format: String,          // txt / epub / pdf
        val totalPages: Int,
        val currentPage: Int = 1,    // 人机共享的阅读进度
        val addedAt: Long = 0,
        val lastReadAt: Long = 0,    // 0 = 从未打开;"最近在读"按它取
        val chapters: List<ChapterMeta> = emptyList(), // 章节边界;旧数据为空,首次打开懒生成
    )

    @Serializable
    data class ReadAnnotation(
        val id: String,
        val bookId: String,
        val page: Int,
        val paragraphIndex: Int,
        val excerpt: String,         // 标注的原文片段(截断保存):整段=段落原文,单句=句子原文
        val comment: String,         // 评论内容
        val author: String,          // "user" / "ai"
        val createdAt: Long,
        val kind: String = "paragraph", // paragraph / sentence(r257 单句划线新增,旧数据默认整段)
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val rootDir: File
        get() = File(context.filesDir, "read_together").apply { mkdirs() }

    private val booksFile: File
        get() = File(rootDir, "books.json")

    private fun bookDir(id: String) = File(rootDir, id).apply { mkdirs() }

    private fun annotationsFile(bookId: String) = File(bookDir(bookId), "annotations.json")

    private fun digestsFile(bookId: String) = File(bookDir(bookId), "digests.json")

    private fun notesFile(bookId: String) = File(bookDir(bookId), "notes.json")

    private fun skillProposalsFile(bookId: String) = File(bookDir(bookId), "skill_proposals.json")

    /** 新产物 JSON 统一走临时文件+rename,避免写一半损坏;失败回退直写 */
    private fun writeAtomically(file: File, content: String) {
        runCatching {
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(file)) {
                file.writeText(content)
                tmp.delete()
            }
        }.onFailure { file.writeText(content) }
    }

    // ---------- 书架 ----------

    @Synchronized
    fun listBooks(): List<BookMeta> {
        if (!booksFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<BookMeta>>(booksFile.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun saveBooks(books: List<BookMeta>) {
        booksFile.writeText(json.encodeToString(books))
    }

    /** 最近在读的书(按 lastReadAt);从未打开过则取最新导入 */
    fun currentBook(): BookMeta? {
        val books = listBooks()
        return books.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }
            ?: books.maxByOrNull { it.addedAt }
    }

    @Synchronized
    fun getBook(id: String): BookMeta? = listBooks().firstOrNull { it.id == id }

    @Synchronized
    fun deleteBook(id: String) {
        saveBooks(listBooks().filterNot { it.id == id })
        File(rootDir, id).deleteRecursively()
    }

    // ---------- 导入与解析 ----------

    /** 从 SAF uri 导入并解析;失败抛异常(带中文原因) */
    suspend fun import(uri: Uri): BookMeta = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val mime = resolver.getType(uri).orEmpty()
        val lower = displayName.lowercase()
        val format = when {
            mime.contains("pdf") || lower.endsWith(".pdf") -> "pdf"
            mime.contains("epub") || lower.endsWith(".epub") -> "epub"
            else -> "txt"
        }
        // 统一拷到临时文件(pdf/epub 的解析库都要 File)
        val tmp = File(context.cacheDir, "rt_import_${System.currentTimeMillis()}.${format}")
        try {
            resolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取所选文件")

            val parsed = when (format) {
                "pdf" -> parsePdf(tmp)
                "epub" -> parseEpub(tmp)
                else -> parseTxt(tmp, displayName)
            }
            if (parsed.pages.isEmpty()) error("解析不到正文内容,可能是不支持的排版/扫描版")

            val id = java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            val dir = bookDir(id)
            parsed.pages.forEachIndexed { i, text ->
                File(dir, "p%04d.txt".format(i + 1)).writeText(text)
            }
            val meta = BookMeta(
                id = id,
                title = parsed.title.ifBlank { displayName.removeSuffix(".${format}") },
                author = parsed.author,
                format = format,
                totalPages = parsed.pages.size,
                currentPage = 1,
                addedAt = System.currentTimeMillis(),
                lastReadAt = 0,
                chapters = parsed.chapters,
            )
            saveBooks(listBooks() + meta)
            meta
        } finally {
            tmp.delete()
        }
    }

    private data class ParsedBook(
        val title: String,
        val author: String,
        val pages: List<String>,
        val chapters: List<ChapterMeta> = emptyList(),
    )

    private fun queryDisplayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx).orEmpty() else ""
            } ?: ""
        }.getOrDefault("").ifBlank { "未命名书籍" }
    }

    /** 修复 r256:txt 书名取导入文件名(之前误用临时文件名导致书名是一串乱码数字) */
    private fun parseTxt(file: File, title: String): ParsedBook {
        val bytes = file.readBytes()
        // UTF-8 严格解码,失败率高则回退 GBK(很多中文 txt 是 GBK)
        val text = run {
            val strict = runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            }.getOrNull()
            strict ?: String(bytes, charset("GBK"))
        }
        val paragraphs = splitParagraphs(text)
        val paged = ReadTogetherChaptering.chunkIntoPages(paragraphs)
        val spans = ReadTogetherChaptering.txtSpans(paragraphs)
        val chapters = if (spans.isEmpty()) {
            ReadTogetherChaptering.pageBlockChapters(paged.pages.size)
        } else {
            ReadTogetherChaptering.chapterPages(spans, paged.paragraphPage, paged.pages.size)
        }
        return ParsedBook(
            title = title.removeSuffix(".txt").removeSuffix(".TXT"),
            author = "",
            pages = paged.pages,
            chapters = chapters,
        )
    }

    private fun parseEpub(file: File): ParsedBook {
        ZipFile(file).use { zip ->
            // META-INF/container.xml → OPF 路径
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: error("不是有效的 EPUB(缺 container.xml)")
            val containerXml = zip.getInputStream(containerEntry).readBytes().decodeToString()
            val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(containerXml)
                ?.groupValues?.get(1) ?: error("EPUB 缺 OPF 配置")
            val opfEntry = zip.getEntry(opfPath) ?: error("EPUB 缺 OPF 文件")
            val opf = zip.getInputStream(opfEntry).readBytes().decodeToString()
            val baseDir = opfPath.substringBeforeLast('/', "")

            val title = Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(opf)?.groupValues?.get(1)?.trim().orEmpty()
            val author = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>").find(opf)?.groupValues?.get(1)?.trim().orEmpty()

            // manifest id→href + spine 顺序
            val manifest = Regex("<item\\b[^>]*>").findAll(opf).mapNotNull { m ->
                val id = Regex("id\\s*=\\s*\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)
                val href = Regex("href\\s*=\\s*\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)
                if (id != null && href != null) id to href else null
            }.toMap()
            val spine = Regex("<itemref\\b[^>]*idref\\s*=\\s*\"([^\"]+)\"[^>]*>").findAll(opf)
                .map { it.groupValues[1] }
                .toList()

            val docs = mutableListOf<Pair<String, List<String>>>()
            val ordered = if (spine.isNotEmpty()) spine.mapNotNull { manifest[it] } else {
                // spine 解析失败兜底:取全部 html 按文件名排序
                zip.entries().toList()
                    .filter { it.name.endsWith(".xhtml") || it.name.endsWith(".html") }
                    .map { it.name }.sorted()
            }
            for (href in ordered) {
                val path = if (baseDir.isBlank()) href else "$baseDir/$href"
                val entry = zip.getEntry(path) ?: zip.getEntry(href) ?: continue
                val html = zip.getInputStream(entry).readBytes().decodeToString()
                val paragraphs = htmlToParagraphs(html)
                if (paragraphs.isNotEmpty()) docs.add(htmlDocTitle(html) to paragraphs)
            }
            val paged = ReadTogetherChaptering.chunkIntoPages(docs.flatMap { it.second })
            val spans = ReadTogetherChaptering.epubSpans(docs)
            val chapters = ReadTogetherChaptering.chapterPages(spans, paged.paragraphPage, paged.pages.size)
            return ParsedBook(title, author, paged.pages, chapters)
        }
    }

    private fun parsePdf(file: File): ParsedBook {
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
        try {
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val pages = (1..doc.numberOfPages).map { pageNo ->
                stripper.startPage = pageNo
                stripper.endPage = pageNo
                stripper.getText(doc)
                    .replace("\\r\\n?|\\r".toRegex(), "\n")
                    .split('\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .trim()
            }.filter { it.isNotBlank() }
            // 元数据尽量取书名
            val info = doc.documentInformation
            val title = info.title?.trim().orEmpty()
            val author = info.author?.trim().orEmpty()
            // PDF 不读书签,先每 10 页一章;页号是应用内过滤空白页后的页序
            return ParsedBook(title, author, pages, ReadTogetherChaptering.pageBlockChapters(pages.size))
        } finally {
            doc.close()
        }
    }

    // ---------- 页内容 ----------

    fun splitParagraphs(text: String): List<String> =        text.split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
            .flatMap { block ->
                // 单一超长行(硬换行少的 txt)按句号/问号再切,防止单段爆长
                if (block.length > 500) {
                    block.split(Regex("(?<=[。!?!?」』\"])")).map { it.trim() }.filter { it.isNotBlank() }
                } else {
                    listOf(block.trim())
                }
            }
            .filter { it.isNotBlank() }

    // ---------- 章节 ----------

    /** 旧书(无 chapters)首次打开时从已存页文本重建并写回 books.json */
    @Synchronized
    fun ensureChapters(bookId: String): List<ChapterMeta> {
        val books = listBooks()
        val idx = books.indexOfFirst { it.id == bookId }
        if (idx < 0) return emptyList()
        val book = books[idx]
        if (book.chapters.isNotEmpty()) return book.chapters
        val pages = (1..book.totalPages).mapNotNull { getPageText(bookId, it) }
        val chapters = ReadTogetherChaptering.inferChaptersFromPages(pages)
        if (chapters.isEmpty()) return emptyList()
        val updated = books.toMutableList()
        updated[idx] = book.copy(chapters = chapters)
        saveBooks(updated)
        return chapters
    }

    fun chaptersOf(bookId: String): List<ChapterMeta> = ensureChapters(bookId)

    fun chapterOfPage(bookId: String, page: Int): ChapterMeta? {
        val chapters = ensureChapters(bookId)
        return chapters.firstOrNull { page in it.startPage..it.endPage } ?: chapters.firstOrNull()
    }

    /** 章正文(页拼接);[maxChars] 截断防上下文爆掉 */
    fun getChapterText(bookId: String, chapterIndex: Int, maxChars: Int = 16000): String? {
        val chapter = chaptersOf(bookId).getOrNull(chapterIndex) ?: return null
        val sb = StringBuilder()
        for (p in chapter.startPage..chapter.endPage) {
            val text = getPageText(bookId, p) ?: continue
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(text)
            if (sb.length >= maxChars) break
        }
        return sb.toString().take(maxChars).ifBlank { null }
    }

    /** 章节正文指纹:摘要缓存据此判断本章是否需要重读 */
    fun chapterSourceHash(bookId: String, chapterIndex: Int): String {
        val text = getChapterText(bookId, chapterIndex) ?: return ""
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    // ---------- TA 学习产物(摘要/笔记/技能提案) ----------

    @Synchronized
    fun listDigests(bookId: String): List<ChapterDigest> = readList(digestsFile(bookId))

    @Synchronized
    fun getDigest(bookId: String, chapterIndex: Int): ChapterDigest? =
        listDigests(bookId).firstOrNull { it.chapterIndex == chapterIndex }

    @Synchronized
    fun upsertDigest(bookId: String, digest: ChapterDigest) {
        val next = listDigests(bookId).filterNot { it.chapterIndex == digest.chapterIndex } + digest
        writeAtomically(digestsFile(bookId), json.encodeToString(next.sortedBy { it.chapterIndex }))
    }

    @Synchronized
    fun listNotes(bookId: String): List<ReadNote> = readList(notesFile(bookId))

    /** 同章同标题覆盖(重读一章更新原笔记,不重复堆;保留原 id/创建时间) */
    @Synchronized
    fun addNote(bookId: String, note: ReadNote): ReadNote {
        val notes = listNotes(bookId)
        val existing = notes.firstOrNull { it.chapterIndex == note.chapterIndex && it.title == note.title }
        val saved = note.copy(
            id = note.id.ifBlank {
                existing?.id ?: java.util.UUID.randomUUID().toString().replace("-", "").take(12)
            },
            createdAt = existing?.createdAt ?: note.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        val next = notes.filterNot {
            it.chapterIndex == saved.chapterIndex && it.title == saved.title
        } + saved
        writeAtomically(notesFile(bookId), json.encodeToString(next))
        return saved
    }

    @Synchronized
    fun deleteNote(bookId: String, noteId: String) {
        writeAtomically(notesFile(bookId), json.encodeToString(listNotes(bookId).filterNot { it.id == noteId }))
    }

    @Synchronized
    fun listSkillProposals(bookId: String): List<SkillProposal> = readList(skillProposalsFile(bookId))

    /** 同一章重读覆盖旧的 pending 提案;已创建/已忽略的保留 */
    @Synchronized
    fun upsertSkillProposal(bookId: String, proposal: SkillProposal): SkillProposal {
        val list = listSkillProposals(bookId).filterNot {
            it.chapterIndex == proposal.chapterIndex && it.status == "pending"
        }
        val saved = proposal.copy(
            id = proposal.id.ifBlank { java.util.UUID.randomUUID().toString().replace("-", "").take(12) },
            createdAt = proposal.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        writeAtomically(skillProposalsFile(bookId), json.encodeToString(list + saved))
        return saved
    }

    @Synchronized
    fun updateSkillProposalStatus(bookId: String, proposalId: String, status: String) {
        val next = listSkillProposals(bookId).map { if (it.id == proposalId) it.copy(status = status) else it }
        writeAtomically(skillProposalsFile(bookId), json.encodeToString(next))
    }

    private inline fun <reified T> readList(file: File): List<T> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<T>>(file.readText()) }.getOrDefault(emptyList())
    }

    /** 单句切分(划线标注用):中英文句末标点后断句,过短碎片并回上一句 */
    fun splitSentences(paragraph: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        paragraph.forEach { ch ->
            buf.append(ch)
            if (ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '…' || ch == '；' || ch == ';') {
                out.add(buf.toString())
                buf.clear()
            }
        }
        if (buf.isNotBlank()) out.add(buf.toString())
        val merged = mutableListOf<String>()
        for (s in out) {
            if (merged.isNotEmpty() && (s.length <= 2 || merged.last().length <= 2)) {
                merged[merged.lastIndex] = merged.last() + s
            } else {
                merged.add(s)
            }
        }
        return merged.filter { it.isNotBlank() }
    }

    /** EPUB 单文档标题:优先 <title>,其次首个 h1-h3;都没有返回空 */
    private fun htmlDocTitle(html: String): String {
        fun stripTags(s: String) = s.replace(Regex("(?s)<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        // 先取正文标题(h1-h3),很多 EPUB 每个 xhtml 的 <title> 都是书名
        val h = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(html)?.groupValues?.get(1)?.let(::stripTags)
        if (!h.isNullOrBlank()) return h.take(40)
        val t = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let(::stripTags)
        return t?.take(40).orEmpty()
    }

    private fun htmlToParagraphs(html: String): List<String> {
        val body = html
            .substringAfter("<body", "")
            .let { if (it.isBlank()) html else it }
            .substringAfter('>', "")
            .substringBefore("</body")
        return body
            .replace(Regex("(?is)<(script|style)\\b.*?</\\1>"), " ")
            .replace(Regex("(?is)<(p|div|br|h[1-6]|li)\\b[^>]*>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .split('\n')
            .map { it.trim() }
            .filter { it.length > 1 }
    }

    /** 读某页原文;段落列表(长按标注按段定位) */
    fun getPageText(bookId: String, page: Int): String? {
        val f = File(bookDir(bookId), "p%04d.txt".format(page))
        return if (f.exists()) f.readText() else null
    }

    fun getPageParagraphs(bookId: String, page: Int): List<String> =
        getPageText(bookId, page)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    // ---------- 共享进度(人机同页) ----------

    @Synchronized
    fun setProgress(bookId: String, page: Int) {
        val books = listBooks().toMutableList()
        val idx = books.indexOfFirst { it.id == bookId }
        if (idx < 0) return
        val b = books[idx]
        books[idx] = b.copy(
            currentPage = page.coerceIn(1, b.totalPages),
            lastReadAt = System.currentTimeMillis(),
        )
        saveBooks(books)
    }

    // ---------- 双方标注 ----------

    @Synchronized
    fun listAnnotations(bookId: String): List<ReadAnnotation> {
        val f = annotationsFile(bookId)
        if (!f.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ReadAnnotation>>(f.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun addAnnotation(
        bookId: String,
        page: Int,
        paragraphIndex: Int,
        excerpt: String,
        comment: String,
        author: String,
        kind: String = "paragraph",
    ): ReadAnnotation {
        val ann = ReadAnnotation(
            id = java.util.UUID.randomUUID().toString().replace("-", "").take(12),
            bookId = bookId,
            page = page,
            paragraphIndex = paragraphIndex,
            excerpt = excerpt.take(120),
            comment = comment.trim(),
            author = author,
            createdAt = System.currentTimeMillis(),
            kind = kind,
        )
        saveAnnotations(bookId, listAnnotations(bookId) + ann)
        return ann
    }

    @Synchronized
    fun deleteAnnotation(bookId: String, annotationId: String) {
        saveAnnotations(bookId, listAnnotations(bookId).filterNot { it.id == annotationId })
    }

    private fun saveAnnotations(bookId: String, annotations: List<ReadAnnotation>) {
        annotationsFile(bookId).writeText(json.encodeToString(annotations))
    }

    // ---------- 书内搜索(给助手用) ----------

    fun search(bookId: String, query: String, maxHits: Int = 5): List<Triple<Int, String, Int>> {
        val hits = mutableListOf<Triple<Int, String, Int>>() // (page, snippet, indexInPage)
        val book = getBook(bookId) ?: return hits
        val q = query.trim()
        if (q.isEmpty()) return hits
        for (page in 1..book.totalPages) {
            val text = getPageText(bookId, page) ?: continue
            var idx = text.indexOf(q, ignoreCase = true)
            while (idx >= 0) {
                val start = (idx - 40).coerceAtLeast(0)
                val end = (idx + q.length + 60).coerceAtMost(text.length)
                hits.add(Triple(page, text.substring(start, end).replace('\n', ' '), idx))
                if (hits.size >= maxHits) return hits
                idx = text.indexOf(q, idx + q.length, ignoreCase = true)
            }
        }
        return hits
    }
}
