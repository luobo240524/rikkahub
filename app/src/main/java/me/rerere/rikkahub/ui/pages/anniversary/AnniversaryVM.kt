package me.rerere.rikkahub.ui.pages.anniversary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.dao.AnniversaryDAO
import me.rerere.rikkahub.data.db.entity.AnniversaryEntity

/**
 * 纪念日管理(第二波):手动管理页的增删改。
 * 数据层与 TA 的 anniversary_write 工具同库同表,双方录入/编辑互相可见。
 */
class AnniversaryVM(
    private val anniversaryDAO: AnniversaryDAO,
) : ViewModel() {

    val list: StateFlow<List<AnniversaryEntity>> = anniversaryDAO.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        id: Long?,
        name: String,
        month: Int,
        day: Int,
        targetYear: Int?,
        repeatYearly: Boolean,
        note: String,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                if (id != null) {
                    val old = anniversaryDAO.getById(id) ?: return@launch
                    anniversaryDAO.update(
                        old.copy(
                            name = name.trim(),
                            month = month,
                            day = day,
                            targetYear = if (repeatYearly) null else targetYear,
                            repeatYearly = repeatYearly,
                            note = note.trim(),
                        )
                    )
                } else {
                    anniversaryDAO.insert(
                        AnniversaryEntity(
                            name = name.trim(),
                            month = month,
                            day = day,
                            targetYear = if (repeatYearly) null else targetYear,
                            repeatYearly = repeatYearly,
                            note = note.trim(),
                            createdAt = now,
                        )
                    )
                }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching {
                anniversaryDAO.getById(id)?.let { anniversaryDAO.delete(it) }
            }
        }
    }
}
