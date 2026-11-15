package me.rerere.rikkahub.ui.pages.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.dao.AnniversaryDAO
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.dao.SecretDAO
import me.rerere.rikkahub.data.db.dao.TimelineEventDAO
import me.rerere.rikkahub.data.db.entity.AnniversaryEntity
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import me.rerere.rikkahub.data.db.entity.dueTickerFlow
import me.rerere.rikkahub.data.db.entity.isDue
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** 时间轴条目:事件(事件表)、串联动态(朋友圈/打卡/心里话)或纪念日(页面层聚合) */
sealed interface TimelineItem {
    val sortMillis: Long

    data class Event(val event: TimelineEventEntity) : TimelineItem {
        override val sortMillis: Long get() = event.createdAt
    }

    data class Dynamic(
        val kind: String, // moment / checkin / secret
        val sourceId: Long,
        val author: String,
        val text: String,
        val masked: Boolean,
        override val sortMillis: Long,
    ) : TimelineItem

    /** 纪念日:落在今年发生日(单次=目标年那天),当天 09:00 排序 */
    data class Anniversary(
        val entity: AnniversaryEntity,
        override val sortMillis: Long,
    ) : TimelineItem
}

data class TimelineDay(
    val date: String, // yyyy-MM-dd
    val items: List<TimelineItem>,
)

class TimelineVM(
    private val timelineEventDAO: TimelineEventDAO,
    private val anniversaryDAO: AnniversaryDAO,
    momentsPostDAO: MomentsPostDAO,
    moodCheckinDAO: MoodCheckinDAO,
    secretDAO: SecretDAO,
) : ViewModel() {

    init {
        // 打开时间轴页 = 助手写的提醒已被看过,清掉发现页「时间轴」红点
        viewModelScope.launch {
            runCatching { timelineEventDAO.markAllRemindersSeen() }
        }
    }

    /** 事件+动态的基底流(纪念日在外面叠加,combine 类型化重载最多 5 流) */
    private val baseDays: Flow<List<TimelineDay>> = combine(
        timelineEventDAO.flowAll(),
        momentsPostDAO.flowAll(),
        moodCheckinDAO.flowAll(),
        secretDAO.flowAll(),
        dueTickerFlow(), // 定时条目到点自动出现
    ) { events, moments, checkins, secrets, now ->
        val items = buildList {
            events.forEach { add(TimelineItem.Event(it)) }
            moments.filter { isDue(it.scheduledAt, now) }.forEach {
                add(
                    TimelineItem.Dynamic(
                        kind = "moment", sourceId = it.id, author = it.author,
                        text = it.content, masked = false, sortMillis = it.createdAt,
                    )
                )
            }
            checkins.filter { isDue(it.scheduledAt, now) }.forEach {
                add(
                    TimelineItem.Dynamic(
                        kind = "checkin", sourceId = it.id, author = it.subject,
                        text = "${it.mood} ${it.phrase}", masked = false, sortMillis = it.createdAt,
                    )
                )
            }
            secrets.filter { isDue(it.scheduledAt, now) }.forEach {
                // 用户视角:助手写且设为自己可见的才掩码;用户自己的始终显示
                val masked = it.author == "assistant" && it.visible == 0
                add(
                    TimelineItem.Dynamic(
                        kind = "secret", sourceId = it.id, author = it.author,
                        text = if (masked) "(这条是TA留给自己的)" else it.content,
                        masked = masked, sortMillis = it.createdAt,
                    )
                )
            }
        }
        items.groupBy { dayKeyOf(it.sortMillis) }
            .map { (date, list) -> TimelineDay(date, list.sortedBy { it.sortMillis }) }
            .sortedByDescending { it.date }
    }

    val days: StateFlow<List<TimelineDay>> = combine(
        baseDays,
        anniversaryDAO.flowAll(),
    ) { days, anniversaries ->
        if (anniversaries.isEmpty()) {
            days
        } else {
            val annivItems = anniversaries.mapNotNull { a ->
                anniversaryOccurrenceMillis(a)?.let { TimelineItem.Anniversary(a, it) }
            }
            (annivItems + days.flatMap { it.items })
                .groupBy { dayKeyOf(it.sortMillis) }
                .map { (date, list) -> TimelineDay(date, list.sortedBy { it.sortMillis }) }
                .sortedByDescending { it.date }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 纪念日的时间轴落点:每年重复=今年那天;单次=目标年那天(无效日期丢弃);当天 09:00 排序 */
    private fun anniversaryOccurrenceMillis(a: AnniversaryEntity): Long? {
        val date = if (a.repeatYearly) {
            runCatching { LocalDate.of(LocalDate.now().year, a.month, a.day) }.getOrNull()
        } else {
            a.targetYear?.let { y -> runCatching { LocalDate.of(y, a.month, a.day) }.getOrNull() }
        } ?: return null
        return date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun addManual(title: String, content: String, date: String, time: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                timelineEventDAO.insert(
                    TimelineEventEntity(
                        eventDate = date,
                        eventTime = time,
                        title = title.trim(),
                        content = content.trim(),
                        type = "manual",
                        author = "user",
                        remindAt = null,
                        remindFired = 0,
                        conversationId = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching { timelineEventDAO.deleteById(id) }
        }
    }

    companion object {
        private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun dayKeyOf(millis: Long): String = DAY_FORMAT.format(Date(millis))
    }
}
