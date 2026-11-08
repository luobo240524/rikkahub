package me.rerere.rikkahub.ui.pages.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.db.entity.MomentsPostEntity
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity
import me.rerere.rikkahub.service.SocialNotifyController

class MomentsVM(
    private val momentsPostDAO: MomentsPostDAO,
    private val socialInteractionDAO: SocialInteractionDAO,
    private val socialNotifyController: SocialNotifyController,
) : ViewModel() {
    val posts = momentsPostDAO.flowAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 全局互动流,UI 按 (targetType,targetId) 分组取各帖子的点赞/评论 */
    val interactions = socialInteractionDAO.flowRecent(500)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _posting = MutableStateFlow(false)
    val posting = _posting.asStateFlow()

    init {
        // 打开朋友圈即清掉"moment"类红点
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { socialInteractionDAO.markReadByType("moment") }
            }
        }
    }

    fun post(content: String, mood: String?) {
        val text = content.trim()
        if (text.isEmpty() || _posting.value) return
        viewModelScope.launch {
            _posting.value = true
            withContext(Dispatchers.IO) {
                runCatching {
                    momentsPostDAO.upsert(
                        MomentsPostEntity(
                            author = "user",
                            content = text,
                            mood = mood?.takeIf { it.isNotBlank() },
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            _posting.value = false
            // 用户发布 → 后台提醒助手(失败不影响发帖)
            runCatching {
                socialNotifyController.notifyUserActivity("TA发布了一条朋友圈：$text")
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { momentsPostDAO.deleteById(id) }
                runCatching { socialInteractionDAO.deleteByTarget("moment", id) }
            }
        }
    }

    /** 用户点赞/取消点赞(助手内容的点赞不后台提醒,避免噪音) */
    fun toggleLike(postId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (socialInteractionDAO.countLike("moment", postId, "user") > 0) {
                        socialInteractionDAO.deleteLike("moment", postId, "user")
                    } else {
                        socialInteractionDAO.insert(
                            SocialInteractionEntity(
                                targetType = "moment",
                                targetId = postId,
                                actor = "user",
                                type = "like",
                                content = null,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            }
        }
    }

    /** 用户评论朋友圈(可回复某条评论);评的是助手帖子时后台提醒助手 */
    fun comment(postId: Long, content: String, postAuthor: String, replyTo: String? = null) {
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    socialInteractionDAO.insert(
                        SocialInteractionEntity(
                            targetType = "moment",
                            targetId = postId,
                            actor = "user",
                            type = "comment",
                            content = text.take(500),
                            replyTo = replyTo,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            // 评助手帖子,或在任何帖子下回复了助手的评论 → 后台提醒助手
            if (postAuthor == "assistant" || replyTo == "assistant") {
                runCatching {
                    socialNotifyController.notifyUserActivity(
                        if (replyTo == "assistant") "TA回复了你的评论：$text"
                        else "TA评论了你的朋友圈：$text"
                    )
                }
            }
        }
    }
}
