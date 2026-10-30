package me.rerere.rikkahub

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * 主界面卡死（ANR）时的免桌面急救入口。
 *
 * 部分桌面解析长按快捷方式会失败（报"未安装该应用"），不能只靠快捷方式进安全模式。
 * 这里走应用侧自判：上次退出是 ANR 就连续计数，连续 2 次的冷启动直接进安全模式。
 * 任何一次非 ANR 退出（用户正常关闭/手动杀后台/系统回收）都会清零计数，
 * 偶发一次卡顿不会误触发；修复完成正常退出后自动恢复常规启动。
 */
object SafeModeEntry {
    private const val PREFS = "safe_mode_entry"
    private const val KEY_ANR_STREAK = "anr_streak"

    /** 最近一次退出是否为 ANR（REASON_ANR = 6；低于 Android 12 无此数据，恒为 false） */
    private fun lastExitWasAnr(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@runCatching false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val last: ApplicationExitInfo? =
            am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
        last != null && last.reason == ApplicationExitInfo.REASON_ANR
    }.getOrDefault(false)

    /**
     * 主入口冷启动时调用：更新连续 ANR 计数，
     * 返回 true 表示应当跳过主界面直接进安全模式。
     */
    fun shouldEnterSafeMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val streak = if (lastExitWasAnr(context)) prefs.getInt(KEY_ANR_STREAK, 0) + 1 else 0
        prefs.edit().putInt(KEY_ANR_STREAK, streak).apply()
        return streak >= 2
    }
}
