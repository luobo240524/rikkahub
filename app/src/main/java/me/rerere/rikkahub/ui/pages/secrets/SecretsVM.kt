package me.rerere.rikkahub.ui.pages.secrets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.SecretDAO
import me.rerere.rikkahub.data.db.entity.SecretEntity
import me.rerere.rikkahub.service.SocialNotifyController

/**
 * 心里话/小秘密:用户写私密内容,可设置对助手是否可见。
 * 发布(无论可见与否)都后台提醒助手;不可见的条目助手只能看到"对方设置为不可见"。
 */
class SecretsVM(
    private val secretDAO: SecretDAO,
    private val socialNotifyController: SocialNotifyController,
) : ViewModel() {
    val secrets = secretDAO.flowAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                    if (visible) "用户写了一条心里话：$text"
                    else "用户写了一条心里话(对方设置为不可见,内容你看不到,不要追问)"
                )
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { secretDAO.deleteById(id) }
            }
        }
    }
}
