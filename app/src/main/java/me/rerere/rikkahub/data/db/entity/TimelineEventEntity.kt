package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 时间轴事件：四期能力(自动提炼/手动记录/串联动态/节点提醒)的锚点表。
 * - type=auto: 助手对话中自动提炼的事件(时间轴工具写入)
 * - type=manual: 用户手动记录(FAB)
 * - type=reminder: 节点提醒(remind_at 到期后在列表高亮)
 * - 串联动态不落本表:朋友圈/打卡/心里话在页面层按日期聚合展示(纯读,免同步)
 * remind_fired 语义=「提醒已被用户看过」(打开时间轴页置 1),同时驱动发现页红点。
 */
@Entity(
    tableName = "timeline_events",
    indices = [
        Index(value = ["event_date"]),
    ]
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo("event_date")
    val eventDate: String, // yyyy-MM-dd
    @ColumnInfo("event_time")
    val eventTime: String, // HH:mm
    @ColumnInfo("title")
    val title: String, // 一句话标题
    @ColumnInfo("content")
    val content: String = "", // 详情(可空)
    @ColumnInfo("type")
    val type: String = "manual", // auto / manual / reminder
    @ColumnInfo("author")
    val author: String = "user", // user / assistant
    @ColumnInfo("remind_at")
    val remindAt: Long? = null, // 节点提醒时间(epoch ms, null = 不提醒)
    @ColumnInfo("remind_fired")
    val remindFired: Int = 0, // 1 = 已看过/已到期处理
    @ColumnInfo("conversation_id")
    val conversationId: String? = null, // 助手提炼时可关联来源会话
    @ColumnInfo("ref_id")
    val refId: String? = null, // 外部引用键(通话摘要存 call_id 去重,每通电话只摘要一次)
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("updated_at")
    val updatedAt: Long = 0,
)
