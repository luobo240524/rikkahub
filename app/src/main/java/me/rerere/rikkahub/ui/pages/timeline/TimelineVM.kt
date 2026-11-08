package me.rerere.rikkahub.ui.pages.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.dao.SecretDAO
import me.rerere.rikkahub.data.db.dao.TimelineEventDAO
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间轴条目:事件(事件表)或串联动态(朋友圈/打卡/心里话,页面层聚合) */
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
}

data class TimelineDay(
    val date: String, // yyyy-MM-dd
    val items: List<TimelineItem>,
)

class TimelineVM(
    private val timelineEventDAO: TimelineEventDAO,
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

    val days: StateFlow<List<TimelineDay>> = combine(
        timelineEventDAO.flowAll(),
        momentsPostDAO.flowAll(),
        moodCheckinDAO.flowAll(),
        secretDAO.flowAll(),
    ) { events, moments, checkins, secrets ->
        val items = buildList {
            events.forEach { add(TimelineItem.Event(it)) }
            moments.forEach {
                add(
                    TimelineItem.Dynamic(
                        kind = "moment", sourceId = it.id, author = it.author,
                        text = it.content, masked = false, sortMillis = it.createdAt,
                    )
                )
            }
            checkins.forEach {
                add(
                    TimelineItem.Dynamic(
                        kind = "checkin", sourceId = it.id, author = it.subject,
                        text = "${it.mood} ${it.phrase}", masked = false, sortMillis = it.createdAt,
                    )
                )
            }
            secrets.forEach {
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
