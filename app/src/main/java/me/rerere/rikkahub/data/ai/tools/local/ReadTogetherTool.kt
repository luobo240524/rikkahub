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
import me.rerere.rikkahub.data.readtogether.ReadTogetherRepository

/**
 * read_together:"共同阅读"工具。助手与用户同步读书:
 * 人翻页机器同步感知(共享进度),助手可读当前页/标注评论/搜索全书/翻到指定页。
 */
internal fun buildReadTogetherTool(
    readTogether: ReadTogetherRepository,
): Tool {
    return Tool(
        name = "read_together",
        description = "Read a book together with the user (shared progress and annotations). " +
            "Use when the user wants to read/discuss a book together, or to annotate the page you are both on.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("description", "One of: status / get_page / set_progress / annotate / list_annotations / search_book")
                        put("enum", buildJsonArray {
                            add("status"); add("get_page"); add("set_progress")
                            add("annotate"); add("list_annotations"); add("search_book")
                        })
                    })
                    put("page", buildJsonObject {
                        put("type", "integer")
                        put("description", "Page number (for get_page/set_progress; default = current page)")
                    })
                    put("comment", buildJsonObject {
                        put("type", "string")
                        put("description", "Your annotation/comment (for annotate)")
                    })
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Text to search inside the book (for search_book)")
                    })
                },
                required = listOf("action")
            )
        },
        systemPrompt = { _, _ ->
            buildString {
                append("共同阅读使用规则：\n")
                append("- 人机共享同一进度:用户读到第几页你就'读'到第几页;set_progress 翻页后用户端也会跳到该页\n")
                append("- 想看当前/指定页原文用 get_page;想聊剧情先 get_page 再聊,别凭记忆瞎编书内容\n")
                append("- 用户提到某段原文时可用 search_book 定位页码\n")
                append("- 有感触就在当前页 annotate 写下你的标注评论(与用户的标注共享可见,读后感简短为主)\n")
                append("- 用户明确想翻到某章/某页时用 set_progress;标注与评论不要刷屏,自然为主\n")
            }
        },
        execute = { args ->
            val obj = args as? JsonObject
            val action = obj?.get("action")?.let { (it as? JsonPrimitive)?.contentOrNull }
            val page = obj?.get("page")?.let { (it as? JsonPrimitive)?.contentOrNull }?.toIntOrNull()
            val comment = obj?.get("comment")?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val query = obj?.get("query")?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

            val payload = when (action) {
                "status" -> {
                    val book = readTogether.currentBook()
                    if (book == null) {
                        buildJsonObject {
                            put("success", true)
                            put("message", "书架是空的,请用户先到 发现页→共同阅读 导入一本(txt/epub/pdf)")
                        }
                    } else {
                        val anns = readTogether.listAnnotations(book.id)
                        buildJsonObject {
                            put("success", true)
                            put("title", book.title)
                            put("author", book.author)
                            put("format", book.format)
                            put("total_pages", book.totalPages)
                            put("current_page", book.currentPage)
                            put("annotation_count", anns.size)
                            put("message", "正在共读《${book.title}》第${book.currentPage}/${book.totalPages}页")
                        }
                    }
                }
                "get_page" -> {
                    val book = readTogether.currentBook()
                    if (book == null) {
                        errorPayload("EMPTY_BOOK", "书架是空的,请用户先导入一本书")
                    } else {
                        val target = (page ?: book.currentPage).coerceIn(1, book.totalPages)
                        val text = readTogether.getPageText(book.id, target).orEmpty()
                        buildJsonObject {
                            put("success", true)
                            put("title", book.title)
                            put("page", target)
                            put("total_pages", book.totalPages)
                            // 控制上下文体积:超长页截断
                            put("content", text.take(2200).ifBlank { "(本页无文本,可能是扫描版)" })
                            put("truncated", text.length > 2200)
                        }
                    }
                }
                "set_progress" -> {
                    val book = readTogether.currentBook()
                    if (book == null) {
                        errorPayload("EMPTY_BOOK", "书架是空的,请用户先导入一本书")
                    } else if (page == null) {
                        errorPayload("MISSING_PAGE", "需要 page 参数(1-${book.totalPages})")
                    } else {
                        val p = page.coerceIn(1, book.totalPages)
                        readTogether.setProgress(book.id, p)
                        buildJsonObject {
                            put("success", true)
                            put("page", p)
                            put("message", "已同步翻到《${book.title}》第${p}/${book.totalPages}页,用户端阅读页会同步跳转")
                        }
                    }
                }
                "annotate" -> {
                    val book = readTogether.currentBook()
                    if (book == null) {
                        errorPayload("EMPTY_BOOK", "书架是空的,请用户先导入一本书")
                    } else if (comment.isBlank()) {
                        errorPayload("MISSING_COMMENT", "需要 comment 参数(标注评论内容)")
                    } else {
                        val target = (page ?: book.currentPage).coerceIn(1, book.totalPages)
                        val paragraphs = readTogether.getPageParagraphs(book.id, target)
                        val excerpt = paragraphs.firstOrNull().orEmpty()
                        val ann = readTogether.addAnnotation(
                            bookId = book.id,
                            page = target,
                            paragraphIndex = 0,
                            excerpt = excerpt,
                            comment = comment,
                            author = "ai",
                        )
                        buildJsonObject {
                            put("success", true)
                            put("annotation_id", ann.id)
                            put("message", "已在《${book.title}》第${target}页写下你的标注,用户端可见")
                        }
                    }
                }
                "list_annotations" -> {
                    val book = readTogether.currentBook()
                    if (book == null) {
                        errorPayload("EMPTY_BOOK", "书架是空的")
                    } else {
                        val anns = readTogether.listAnnotations(book.id)
                        buildJsonObject {
                            put("success", true)
                            put("total", anns.size)
                            put("annotations", buildJsonArray {
                                anns.takeLast(20).forEach { a ->
                                    add(buildJsonObject {
                                        put("page", a.page)
                                        put("author", if (a.author == "ai") "你(助手)" else "用户")
                                        put("excerpt", a.excerpt.take(60))
                                        put("comment", a.comment)
                                    })
                                }
                            })
                        }
                    }
                }
                "search_book" -> {
                    val book = readTogether.currentBook()
                    if (book == null) {
                        errorPayload("EMPTY_BOOK", "书架是空的")
                    } else if (query.isBlank()) {
                        errorPayload("MISSING_QUERY", "需要 query 参数")
                    } else {
                        val hits = readTogether.search(book.id, query)
                        buildJsonObject {
                            put("success", true)
                            put("total_hits", hits.size)
                            put("hits", buildJsonArray {
                                hits.forEach { (pg, snippet, _) ->
                                    add(buildJsonObject {
                                        put("page", pg)
                                        put("snippet", snippet)
                                    })
                                }
                            })
                            put("message", if (hits.isEmpty()) "全书没搜到该内容" else "共${hits.size}处命中")
                        }
                    }
                }
                else -> errorPayload("UNKNOWN_ACTION", "action 必须是 status/get_page/set_progress/annotate/list_annotations/search_book")
            }
            listOf(UIMessagePart.Text(payload.toString()))
        }
    )
}

private fun errorPayload(code: String, message: String): JsonObject = buildJsonObject {
    put("error", code)
    put("message", message)
}
