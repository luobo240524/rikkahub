package me.rerere.rikkahub.ui.activity

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import me.rerere.rikkahub.utils.CrashHandler

/**
 * 诊断用极简崩溃报告页。
 * 只用纯原生 View，不依赖 Koin / Compose / Settings / 主题，
 * 保证即使在 Application 初始化、DI、设置加载阶段崩溃，也能把堆栈显示出来。
 */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = CrashHandler.getStackTrace(this) ?: "(没有抓到崩溃信息)"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply {
            text = "⚠️ 崩溃报告（截图或点下面复制，发给开发者）"
            textSize = 18f
        }
        val btn = Button(this).apply {
            text = "复制堆栈"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
            }
        }
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            text = trace
            textSize = 12f
            setTextIsSelectable(true)
        }
        scroll.addView(tv)
        root.addView(title)
        root.addView(btn)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)
    }
}
