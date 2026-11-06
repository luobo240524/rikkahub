package me.rerere.rikkahub.ui.pages.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.entity.MoodCheckinEntity
import java.time.LocalDate
import java.time.YearMonth

class MoodCheckinVM(
    private val moodCheckinDAO: MoodCheckinDAO,
) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month = _month.asStateFlow()

    private val _records = MutableStateFlow<List<MoodCheckinEntity>>(emptyList())
    val records = _records.asStateFlow()

    init {
        viewModelScope.launch {
            _month.collect { reload(it) }
        }
    }

    fun prevMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    private suspend fun reload(m: YearMonth) {
        val from = "${m.year}-%02d-01".format(m.monthValue)
        val to = "${m.year}-%02d-%02d".format(m.monthValue, m.lengthOfMonth())
        _records.value = withContext(Dispatchers.IO) {
            runCatching { moodCheckinDAO.betweenDates(from, to) }.getOrElse { emptyList() }
        }
    }

    /** 用户今日打卡(同日重复覆盖) */
    fun checkin(mood: String, phrase: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    moodCheckinDAO.upsert(
                        MoodCheckinEntity(
                            date = LocalDate.now().toString(),
                            subject = "user",
                            mood = mood,
                            phrase = phrase,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            reload(_month.value)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { moodCheckinDAO.deleteById(id) }
            }
            reload(_month.value)
        }
    }
}
