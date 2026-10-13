package me.rerere.rikkahub.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 唤醒任务登记表：记录所有唤醒任务（进行中/已触发/已取消），
 * 供 query_wake_records 工具查询。按 taskKey 去重（REPLACE 语义）。
 */
@Serializable
data class WakeRecord(
    val taskKey: String,
    val conversationId: String,
    val note: String,
    val type: String,          // once / daily / weekly / monthly
    val fireAt: Long,          // 预计触发时间（循环=下一次）
    val status: String,        // PENDING / FIRED / CANCELLED
    val createdAt: Long,
    val updatedAt: Long,
)

class WakeRegistry(private val recordsFile: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun load(): MutableList<WakeRecord> = runCatching {
        json.decodeFromString<List<WakeRecord>>(recordsFile.readText()).toMutableList()
    }.getOrElse { mutableListOf() }

    private fun save(records: List<WakeRecord>) {
        runCatching {
            recordsFile.parentFile?.mkdirs()
            recordsFile.writeText(json.encodeToString(records.sortedByDescending { it.updatedAt }))
        }
    }

    /** 新增/更新登记（同 taskKey 覆盖，保留原状态若为终态则重置为 PENDING） */
    fun record(record: WakeRecord) {
        val records = load()
        val idx = records.indexOfFirst { it.taskKey == record.taskKey }
        if (idx >= 0) records[idx] = record else records.add(record)
        // 最多保留 200 条
        save(records.takeLast(200))
    }

    /** 触发完成 */
    fun markFired(taskKey: String) {
        val records = load()
        val idx = records.indexOfFirst { it.taskKey == taskKey }
        if (idx >= 0) {
            records[idx] = records[idx].copy(status = "FIRED", updatedAt = System.currentTimeMillis())
            save(records)
        }
    }

    /** 取消（note 为空 = 全部；否则事项包含关键词且状态为 PENDING 的标记取消） */
    fun markCancelled(conversationId: String, keyword: String) {
        val records = load()
        var changed = false
        val updated = records.map { r ->
            if (r.conversationId == conversationId && r.status == "PENDING" &&
                (keyword.isBlank() || r.note.contains(keyword))
            ) {
                changed = true
                r.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis())
            } else r
        }
        if (changed) save(updated)
    }

    fun markAllCancelled(conversationId: String) = markCancelled(conversationId, "")

    fun list(conversationId: String? = null): List<WakeRecord> =
        load().filter { conversationId == null || it.conversationId == conversationId }
}
