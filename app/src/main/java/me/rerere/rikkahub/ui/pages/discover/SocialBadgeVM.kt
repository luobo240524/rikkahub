package me.rerere.rikkahub.ui.pages.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.db.dao.TimelineEventDAO

/**
 * 发现页红点:助手产生的未读互动计数(按目标类型)。
 * 数据源 social_interactions(actor='assistant' AND is_read=0)。
 * "timeline" 类型 = 时间轴里助手写的、还没被看过的节点提醒(timeline_events)。
 */
class SocialBadgeVM(
    socialInteractionDAO: SocialInteractionDAO,
    timelineEventDAO: TimelineEventDAO,
) : ViewModel() {
    val unreadCounts = socialInteractionDAO.flowUnreadCounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val timelineUnread = timelineEventDAO.flowUnreadReminderCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun unreadOfType(type: String): Int = when (type) {
        "timeline" -> timelineUnread.value
        else -> unreadCounts.value.firstOrNull { it.targetType == type }?.cnt ?: 0
    }

    val unreadTotal: Int get() = unreadCounts.value.sumOf { it.cnt } + timelineUnread.value
}
