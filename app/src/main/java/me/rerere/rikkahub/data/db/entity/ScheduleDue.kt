package me.rerere.rikkahub.data.db.entity

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 定时生效:朋友圈/打卡/心里话条目可带 scheduled_at(epoch millis),
 * 到点前对用户侧一律不可见,到点后自动出现。
 * scheduledAt == null = 立即生效(历史数据与新写的未定时条目)。
 */
fun isDue(scheduledAt: Long?, now: Long = System.currentTimeMillis()): Boolean =
    scheduledAt == null || scheduledAt <= now

/**
 * 定时生效的秒级心跳:VM 把数据流与它 combine,到点的条目无需手动刷新即自动出现。
 */
fun dueTickerFlow(intervalMs: Long = 30_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(intervalMs)
    }
}
