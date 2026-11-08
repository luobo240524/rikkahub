package me.rerere.rikkahub.ui.pages.secrets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.SecretDAO
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.db.entity.SecretEntity
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity
import me.rerere.rikkahub.service.SocialNotifyController

/**
 * 心里话/小秘密:用户写私密内容,可设置对助手是否可见。
 * 发布(无论可见与否)都后台提醒助手;不可见的条目助手只能看到"对方设置为不可见"。
 * 便签支持点赞/评论(评论可回复某条);评助手写的心里话时后台提醒助手。
 */
class SecretsVM(
    private val secretDAO: SecretDAO,
    private val socialInteractionDAO: SocialInteractionDAO,
    private val socialNotifyController: SocialNotifyController,
) : ViewModel() {
    val secrets = secretDAO.flowAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 全局互动流,UI 按 targetId(secret id)分组取各便签的点赞/评论 */
    val interactions = socialInteractionDAO.flowRecent(500)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 打开心里话页即清掉"secret"类红点
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { socialInteractionDAO.markReadByType("secret") }
            }
        }
    }

    fun add(content: String, visible: Boolean) {
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    secretDAO.insert(
                        SecretEntity(
                            content = text.take(2000),
                            visible = if (visible) 1 else 0,
                            conversationId = null,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            // 发布均提醒;不可见时提醒但不带内容
            runCatching {
                socialNotifyController.notifyUserActivity(
                    if (visible) "TA写了一条心里话：$text"
                    else "TA写了一条心里话(对方设置为不可见,内容你看不到,不要追问)"
                )
            }
        }
    }

    /** 编辑内容+可见性;用户侧转为可见时后台通知助手(之前看不到,现在能看了) */
    fun update(id: Long, content: String, visible: Boolean) {
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val was = withContext(Dispatchers.IO) {
                runCatching { secretDAO.getById(id) }.getOrNull()
            }
            withContext(Dispatchers.IO) {
                runCatching { secretDAO.updateRow(id, text.take(2000), if (visible) 1 else 0) }
            }
            if (visible && was != null && was.author == "user" && was.visible == 0) {
                runCatching {
                    socialNotifyController.notifyUserActivity("TA把一条心里话转为可见了：$text")
                }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { secretDAO.deleteById(id) }
                runCatching { socialInteractionDAO.deleteByTarget("secret", id) }
            }
        }
    }

    /** 用户给便签点赞/取消(助手心里话的点赞不后台提醒,防噪音) */
    fun toggleLike(secretId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (socialInteractionDAO.countLike("secret", secretId, "user") > 0) {
                        socialInteractionDAO.deleteLike("secret", secretId, "user")
                    } else {
                        socialInteractionDAO.insert(
                            SocialInteractionEntity(
                                targetType = "secret",
                                targetId = secretId,
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

    /** 用户评论便签(可回复某条评论);评的是助手写的心里话时后台提醒助手 */
    fun comment(secretId: Long, content: String, author: String, replyTo: String? = null) {
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    socialInteractionDAO.insert(
                        SocialInteractionEntity(
                            targetType = "secret",
                            targetId = secretId,
                            actor = "user",
                            type = "comment",
                            content = text.take(500),
                            replyTo = replyTo,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            if (author == "assistant") {
                runCatching {
                    socialNotifyController.notifyUserActivity("TA评论了你的心里话：$text")
                }
            }
        }
    }
}
