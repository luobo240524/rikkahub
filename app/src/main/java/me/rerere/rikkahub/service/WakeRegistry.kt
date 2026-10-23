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
    val assistantId: String,
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
    }.getOrElse { mutableListOf() }.also { records ->
        // 僵尸清理：RETRYING 超过 2 小时无活动 → 已放弃
        // （重试链可能因应用重启/装包等中断，登记表状态没人更新，一直挂在重试中）
        val now = System.currentTimeMillis()
        val hasStale = records.any {
            it.status == "RETRYING" && now - it.updatedAt > 2 * 60 * 60 * 1000L
        }
        if (hasStale) {
            val fixed = records.map { r ->
                if (r.status == "RETRYING" && now - r.updatedAt > 2 * 60 * 60 * 1000L) {
                    r.copy(status = "FAILED", updatedAt = now)
                } else r
            }
            save(fixed)
            records.clear()
            records.addAll(fixed)
            android.util.Log.i("WakeRegistry", "swept stale RETRYING records to FAILED")
        }
    }

    private fun save(records: List<WakeRecord>) {
        runCatching {
            recordsFile.parentFile?.mkdirs()
            recordsFile.writeText(json.encodeToString(records.sortedByDescending { it.updatedAt }))
            android.util.Log.i("WakeRegistry", "saved ${records.size} records to ${recordsFile.name}")
        }.onFailure {
            android.util.Log.w("WakeRegistry", "WRITE FAILED", it)
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

    /** 取消（note 为空 = 全部；否则事项包含关键词且状态为 PENDING/RETRYING 的标记取消）。
     *  返回被取消记录的 taskKey 列表，供调用方按 key 精确取消 WorkManager 排程。 */
    fun markCancelled(conversationId: String, keyword: String): List<String> {
        val records = load()
        val cancelledKeys = mutableListOf<String>()
        val updated = records.map { r ->
            if (r.conversationId == conversationId &&
                (r.status == "PENDING" || r.status == "RETRYING") &&
                (keyword.isBlank() || r.note.contains(keyword))
            ) {
                cancelledKeys.add(r.taskKey)
                r.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis())
            } else r
        }
        if (cancelledKeys.isNotEmpty()) save(updated)
        return cancelledKeys
    }

    fun markAllCancelled(conversationId: String): List<String> = markCancelled(conversationId, "")

    /** 生成失败进入重试 */
    fun markRetrying(taskKey: String) = updateStatus(taskKey, "RETRYING")

    /** 重试耗尽 / 超时放弃 */
    fun markFailed(taskKey: String) = updateStatus(taskKey, "FAILED")

    private fun updateStatus(taskKey: String, status: String) {
        val records = load()
        val idx = records.indexOfFirst { it.taskKey == taskKey }
        if (idx >= 0) {
            records[idx] = records[idx].copy(status = status, updatedAt = System.currentTimeMillis())
            save(records)
        }
    }

    /** 循环续期：重新置为待触发（fireAt=下一次触发时间） */
    fun markPending(taskKey: String, fireAt: Long) {
        val records = load()
        val idx = records.indexOfFirst { it.taskKey == taskKey }
        if (idx >= 0) {
            records[idx] = records[idx].copy(
                status = "PENDING",
                fireAt = fireAt,
                updatedAt = System.currentTimeMillis(),
            )
            save(records)
        }
    }

    /** 查询单个任务状态（供重试入口检查：已取消的任务不再重试） */
    fun statusOf(taskKey: String): String? =
        load().firstOrNull { it.taskKey == taskKey }?.status

    fun list(conversationId: String? = null, assistantId: String? = null): List<WakeRecord> =
        load().filter {
            (conversationId == null || it.conversationId == conversationId) &&
                (assistantId == null || it.assistantId == assistantId)
        }
}
