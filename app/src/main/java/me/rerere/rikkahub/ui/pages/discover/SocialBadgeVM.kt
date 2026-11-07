package me.rerere.rikkahub.ui.pages.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO

/**
 * 发现页红点:助手产生的未读互动计数(按目标类型)。
 * 数据源 social_interactions(actor='assistant' AND is_read=0)。
 */
class SocialBadgeVM(
    socialInteractionDAO: SocialInteractionDAO,
) : ViewModel() {
    val unreadCounts = socialInteractionDAO.flowUnreadCounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun unreadOfType(type: String): Int =
        unreadCounts.value.firstOrNull { it.targetType == type }?.cnt ?: 0

    val unreadTotal: Int get() = unreadCounts.value.sumOf { it.cnt }
}
