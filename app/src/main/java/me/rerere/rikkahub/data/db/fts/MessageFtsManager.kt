package me.rerere.rikkahub.data.db.fts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Conversation
import java.time.Instant

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Instant,
    val snippet: String,
)

enum class MessageSearchSort(val orderBy: String) {
    RELEVANCE("rank, update_at DESC"),
    NEWEST_FIRST("update_at DESC, rank"),
    OLDEST_FIRST("update_at ASC, rank"),
}

private const val TAG = "MessageFtsManager"

class MessageFtsManager(private val database: AppDatabase) {

    private val db get() = database.openHelper.writableDatabase
    private val ftsLock = Any()

    /** FTS 表损坏时置位：本会话跳过所有索引写入（搜索失效但对话/唤醒不受影响），重启后重试 */
    @Volatile
    private var ftsDisabled = false

        suspend fun indexConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        // 应急禁用：数据库 FTS 表已损坏，任何触碰都会 native 崩溃。
        // 搜索功能暂停，对话/唤醒/保存全部正常。恢复方案待定后再启用
        return@withContext
        // FTS 写入串行化：并发 DELETE/INSERT 交错曾在中断场景炸掉 native 层
        // （FTS 虚表 + jieba 分词器对并发写不设防），表现为无声闪退。
        // synchronized 串行化（块内无挂起调用，无死锁风险）；withTransaction+原始句柄曾死锁（不同连接互等）
        if (ftsDisabled) return@withContext
        synchronized(ftsLock) {
            runCatching {
                doIndex(conversation)
            }.onFailure { e ->
                // FTS 虚表可能已损坏（此前的 native 崩溃/WAL 残留）：rebuild 后重试一次；
                // 再失败则放弃本次索引——搜索可能暂时失效，但对话与唤醒绝不能被拖入崩溃循环
                Log.e(TAG, "FTS index failed, rebuilding table", e)
                runCatching { db.execSQL("INSERT INTO message_fts(message_fts) VALUES('rebuild')") }
                runCatching {
                    doIndex(conversation)
                }.onFailure { e2 ->
                    Log.e(TAG, "FTS rebuild retry failed, DISABLE FTS indexing this session", e2)
                    ftsDisabled = true
                }
            }
        }
    }

    private fun doIndex(conversation: Conversation) {
        val conversationId = conversation.id.toString()
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
        conversation.messageNodes.forEach { node ->
            node.messages.forEach { message ->
                val text = message.extractFtsText()
                if (text.isNotBlank()) {
                    db.execSQL(
                        "INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            text,
                            node.id.toString(),
                            message.id.toString(),
                            conversationId,
                            conversation.title,
                            conversation.updateAt.toEpochMilli().toString(),
                        )
                    )
                }
            }
        }
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        return@withContext
        db.execSQL("DELETE FROM message_fts WHERE conversation_id = ?", arrayOf(conversationId))
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.execSQL("DELETE FROM message_fts")
    }

    suspend fun search(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
        assistantId: String? = null,
    ): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MessageSearchResult>()
        val assistantFilter = if (assistantId != null) {
            """
            AND EXISTS (
                SELECT 1 FROM conversationentity AS conversation
                WHERE conversation.id = message_fts.conversation_id
                  AND conversation.assistant_id = ?
            )
            """.trimIndent()
        } else {
            ""
        }
        val cursor = db.query(
            """
            SELECT node_id, message_id, conversation_id, title, update_at,
                   simple_snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
            FROM message_fts
            WHERE text MATCH jieba_query(?)
            $assistantFilter
            ORDER BY ${sort.orderBy}
            LIMIT 50
            """.trimIndent(),
            if (assistantId != null) arrayOf(keyword, assistantId) else arrayOf(keyword)
        )
        Log.i(TAG, "search: $keyword")
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    MessageSearchResult(
                        nodeId = it.getString(0),
                        messageId = it.getString(1),
                        conversationId = it.getString(2),
                        title = it.getString(3),
                        updateAt = Instant.ofEpochMilli(it.getLong(4)),
                        snippet = it.getString(5),
                    )
                )
            }
        }
        results
    }
}

private fun UIMessage.extractFtsText(): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .take(10_000)
