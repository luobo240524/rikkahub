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
import me.rerere.rikkahub.data.db.entity.MomentsPostEntity

class MomentsVM(
    private val momentsPostDAO: MomentsPostDAO,
) : ViewModel() {
    val posts = momentsPostDAO.flowAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _posting = MutableStateFlow(false)
    val posting = _posting.asStateFlow()

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
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { momentsPostDAO.deleteById(id) }
            }
        }
    }
}
