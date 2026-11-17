package me.rerere.rikkahub.ui.pages.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.MomentsPostDAO
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.db.entity.MomentsPostEntity
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity
import me.rerere.rikkahub.data.db.entity.dueTickerFlow
import me.rerere.rikkahub.data.db.entity.isDue
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.SocialNotifyController
import me.rerere.rikkahub.service.VitalsRecorder

class MomentsVM(
    private val momentsPostDAO: MomentsPostDAO,
    private val socialInteractionDAO: SocialInteractionDAO,
    private val socialNotifyController: SocialNotifyController,
    private val settingsStore: SettingsStore,
    private val vitalsRecorder: VitalsRecorder,
) : ViewModel() {
    /** 生命体征打点:仅当前助手开启 vitalsEnabled 时生效(fire-and-forget,失败静默) */
    private fun pulseCurrent(kind: String, v: Float, a: Float, label: String) {
        val assistant = settingsStore.settingsFlow.value.getCurrentAssistant()
        if (assistant.vitalsEnabled) {
            vitalsRecorder.pulse(assistant.id.toString(), kind, v, a, label)
        }
    }
    // 定时帖到点自动出现:与 30 秒心跳 combine,过滤未生效条目
    val posts = combine(momentsPostDAO.flowAll(), dueTickerFlow()) { list, now ->
        list.filter { isDue(it.scheduledAt, now) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            // 生命体征:用户发朋友圈 → 助手惦记度/唤醒小幅上抬
            pulseCurrent("moment_post", VitalsRecorder.MOMENT_POST_V, VitalsRecorder.MOMENT_POST_A, "TA 发布了朋友圈")
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
            var inserted = false
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
                        inserted = true
                    }
                }
            }
            // 生命体征:点了一个新赞 → 小幅脉冲(取消赞不打)
            if (inserted) pulseCurrent("moment_like", VitalsRecorder.LIKE_V, VitalsRecorder.LIKE_A, "TA 点了个赞")
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
            // 生命体征:被评论/被回复 → 中幅脉冲
            pulseCurrent("moment_comment", VitalsRecorder.COMMENT_V, VitalsRecorder.COMMENT_A, "TA 评论了你的朋友圈")
        }
    }
}
