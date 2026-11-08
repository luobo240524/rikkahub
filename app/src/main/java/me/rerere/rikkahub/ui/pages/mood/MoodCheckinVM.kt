package me.rerere.rikkahub.ui.pages.mood

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
import me.rerere.rikkahub.data.db.dao.MoodCheckinDAO
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.db.entity.MoodCheckinEntity
import me.rerere.rikkahub.data.db.entity.SocialInteractionEntity
import me.rerere.rikkahub.service.SocialNotifyController
import java.time.LocalDate
import java.time.YearMonth

class MoodCheckinVM(
    private val moodCheckinDAO: MoodCheckinDAO,
    private val socialInteractionDAO: SocialInteractionDAO,
    private val socialNotifyController: SocialNotifyController,
) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month = _month.asStateFlow()

    private val _records = MutableStateFlow<List<MoodCheckinEntity>>(emptyList())
    val records = _records.asStateFlow()

    /** 全局互动流,UI 按 (targetType,targetId) 分组取各打卡的点赞/评论 */
    val interactions = socialInteractionDAO.flowRecent(500)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 最近 30 天打卡(跨月心情胶囊条用) */
    private val _recentRecords = MutableStateFlow<List<MoodCheckinEntity>>(emptyList())
    val recentRecords = _recentRecords.asStateFlow()

    /** 当前选中的日期(点月历日期查看当日打卡,默认今天) */
    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate = _selectedDate.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    /** 选中日期的打卡记录(替代一直累积的历史列表) */
    val dayRecords: StateFlow<List<MoodCheckinEntity>> =
        combine(_selectedDate, records) { date, recs ->
            recs.filter { it.date == date }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            _month.collect { reload(it) }
        }
        // 打开打卡页即清掉"checkin"类红点 + 载入最近30天
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { socialInteractionDAO.markReadByType("checkin") }
            }
        }
        viewModelScope.launch {
            val since = LocalDate.now().minusDays(29).toString()
            _recentRecords.value = withContext(Dispatchers.IO) {
                runCatching { moodCheckinDAO.recentBySubject("user", since) }.getOrElse { emptyList() }
            }
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
            // 用户打卡 → 后台提醒助手
            runCatching {
                socialNotifyController.notifyUserActivity("TA发布了一条心情打卡：$mood $phrase")
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { moodCheckinDAO.deleteById(id) }
                runCatching { socialInteractionDAO.deleteByTarget("checkin", id) }
            }
            reload(_month.value)
        }
    }

    /** 用户给打卡点赞/取消(助手打卡的点赞不后台提醒) */
    fun toggleLike(checkinId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (socialInteractionDAO.countLike("checkin", checkinId, "user") > 0) {
                        socialInteractionDAO.deleteLike("checkin", checkinId, "user")
                    } else {
                        socialInteractionDAO.insert(
                            SocialInteractionEntity(
                                targetType = "checkin",
                                targetId = checkinId,
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

    /** 用户评论打卡;评的是助手打卡时后台提醒助手 */
    fun comment(checkinId: Long, content: String, subject: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    socialInteractionDAO.insert(
                        SocialInteractionEntity(
                            targetType = "checkin",
                            targetId = checkinId,
                            actor = "user",
                            type = "comment",
                            content = text.take(500),
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            if (subject == "assistant") {
                runCatching {
                    socialNotifyController.notifyUserActivity("TA评论了你的心情打卡：$text")
                }
            }
        }
    }
}
