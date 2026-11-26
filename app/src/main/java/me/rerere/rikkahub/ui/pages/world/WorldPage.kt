package me.rerere.rikkahub.ui.pages.world

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.music.MusicPlayerController
import me.rerere.rikkahub.data.world.WorldRepository
import me.rerere.rikkahub.ui.components.webview.MyWebChromeClient
import me.rerere.rikkahub.ui.components.webview.MyWebViewClient
import me.rerere.rikkahub.ui.components.webview.WEB_VIEW_ASSET_URL
import me.rerere.rikkahub.ui.components.webview.WebContent
import me.rerere.rikkahub.ui.components.webview.WebViewState
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.compose.koinInject

/**
 * 「我的世界」WebView 池(每助手一个槽):离开页面只 detach 不 destroy,再进来直接复用
 * 上次的渲染画面——消除 WebView 冷启动黑屏与整页重建(r340 验收:进页黑屏/加载慢)。
 * JS 桥一并池化:缓存页的 window.AndroidWorld 代理绑着旧桥实例,只有同一实例才活着,
 * onNav/onBack 每次进页由 LaunchedEffect 重绑到新组合的回调上。
 * 代价:池内 WebView(渲染进程)常驻内存,切助手等场景统一 drop()。
 */
private object WorldWebViewPool {
    var assistantId: String? = null
    var sig: String? = null // 助手名+头像签名:改名/换头像后池整体作废,避免封面显示旧称呼
    var webView: WebView? = null
    var bridge: WorldJsBridge? = null

    fun drop() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        bridge = null
        assistantId = null
        sig = null
    }
}

private const val PAPER_COLOR = "#EDF2FA"

private const val FADE_IN_JS =
    "(function(){try{var b=document.body;if(b){b.style.transition='opacity .36s ease';b.style.opacity='1';}}catch(e){}})()"

private const val SHOW_BODY_JS =
    "(function(){try{var b=document.body;if(b){b.style.transition='none';b.style.opacity='1';}}catch(e){}})()"

/**
 * 前进跳转把旧页 body 留在 opacity 0(淡出后直接换页);WebView 历史回退恢复的是那页的
 * DOM 状态——回到 opacity 0 的旧页会黑屏。回退后延迟注入淡入(两个时点幂等)。
 */
private fun scheduleWebViewFadeRestore(mainHandler: Handler, state: WebViewState) {
    listOf(220L, 600L).forEach { delayMs ->
        mainHandler.postDelayed({
            state.webView?.evaluateJavascript(FADE_IN_JS, null)
        }, delayMs)
    }
}

/**
 * 「我的世界」页:完全全屏(无原生顶栏,页面内自带头部/返回键),WebView 载 assets/your-world/ 定稿页。
 * 内部跳转(封面→功能页→四空间)由页面 JS 调 AndroidWorld.nav() 走原生 loadUrl;
 * 页面内的「←」调 AndroidWorld.back() 走原生回退(优先 WebView 历史,没有历史才退出路由);
 * 系统返回键同样优先回退 WebView 历史。状态经 AndroidWorld.getState/操作桥拉取。
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WorldPage(
    initialPage: String = "cover",
    initialLetterId: Long = 0L,
    assistantIdOverride: String? = null,
) {
    val navController = LocalNavController.current
    val repository = koinInject<WorldRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val musicController = koinInject<MusicPlayerController>()
    val appContext = LocalContext.current.applicationContext
    val assistant = remember(assistantIdOverride) {
        val settings = settingsStore.settingsFlow.value
        assistantIdOverride
            ?.let { wanted -> settings.assistants.firstOrNull { it.id.toString() == wanted } }
            ?: settings.getCurrentAssistant()
    }
    val assistantId = assistant.id.toString()

    // 用户昵称:助手下设置优先,空退全局显示昵称,再空则页面按各自默认(你/我)展示
    val userAlias = assistant.userNickname?.takeIf { it.isNotBlank() }
        ?: settingsStore.settingsFlow.value.displaySetting.userNickname.takeIf { it.isNotBlank() }
        ?: ""

    // JS 桥按助手池化(见 WorldWebViewPool 注释);助手名/头像变更时池作废重建
    val poolSig = "$assistantId|${assistant.name}|${assistant.avatar}|$userAlias"
    val bridge = remember(assistantId, initialLetterId) {
        val pooled = WorldWebViewPool.bridge
        if (WorldWebViewPool.assistantId == assistantId &&
            WorldWebViewPool.sig == poolSig && pooled != null
        ) {
            pooled.updateInitialLetterId(initialLetterId)
            pooled
        } else {
            WorldWebViewPool.drop()
            WorldJsBridge(
                context = appContext,
                assistantId = assistantId,
                assistantName = assistant.name.ifEmpty { "TA" },
                userAlias = userAlias,
                avatar = assistant.avatar,
                repository = repository,
                music = musicController,
                initialLetterId = initialLetterId,
            ).also {
                WorldWebViewPool.bridge = it
                WorldWebViewPool.assistantId = assistantId
                WorldWebViewPool.sig = poolSig
            }
        }
    }

    val webViewState = remember(assistantId, initialPage, initialLetterId) {
        WebViewState(
            initialContent = WebContent.Url("$WEB_VIEW_ASSET_URL/your-world/$initialPage.html"),
            interfaces = mapOf("AndroidWorld" to bridge),
        )
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // 通知深链:池化复用停在同页时 URL 不变不会重载,页面就不会重新读 getInitialLetterId——强制重载一次
    val deepLinkReloaded = remember(webViewState) { booleanArrayOf(false) }
    val socialNotify = koinInject<me.rerere.rikkahub.service.SocialNotifyController>()
    val notifyScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(webViewState) {
        bridge.onNav = { page ->
            mainHandler.post { webViewState.loadUrl("$WEB_VIEW_ASSET_URL/your-world/$page.html") }
        }
        bridge.onBack = {
            mainHandler.post {
                if (webViewState.canGoBack) {
                    webViewState.goBack()
                    scheduleWebViewFadeRestore(mainHandler, webViewState)
                } else navController.popBackStack()
            }
        }
        // 用户世界交互 → 后台提醒助手(与社交通知同机制,15s 去抖在控制器内)
        bridge.onUserAction = { summary ->
            mainHandler.post {
                notifyScope.launch {
                    socialNotify.notifyWorldActivity(summary)
                }
            }
        }
    }

    // WebView 历史能退(封面→功能页→空间)时系统返回先退 WebView
    BackHandler(enabled = webViewState.canGoBack) {
        webViewState.goBack()
        scheduleWebViewFadeRestore(mainHandler, webViewState)
    }

    // 纸色底:WebView 首帧前的任何间隙都露纸色,不露黑
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFEDF2FA))) {
        AndroidView(
            factory = { ctx ->
                val cached = WorldWebViewPool.webView
                if (cached != null && WorldWebViewPool.assistantId == assistantId) {
                    // 复用池内实例:重新绑 clients 到本次组合的 state,接管出池
                    (cached.parent as? ViewGroup)?.removeView(cached)
                    cached.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    cached.webChromeClient = MyWebChromeClient(webViewState)
                    cached.webViewClient = MyWebViewClient(webViewState)
                    webViewState.webView = cached
                    WorldWebViewPool.webView = null // 已出池,onRelease 时再回池
                    // 上次离开时 body 可能停在淡入半程/淡出态:复用时立即恢复完整显示
                    cached.evaluateJavascript(SHOW_BODY_JS, null)
                    cached
                } else {
                    if (WorldWebViewPool.assistantId != assistantId) WorldWebViewPool.drop()
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewState.webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = true
                        // 世界页淡入淡出:换页瞬间新文档 body 尚在 opacity 0,底色用页面纸色兜底,避免白闪
                        setBackgroundColor(android.graphics.Color.parseColor(PAPER_COLOR))
                        // debug 构建开 WebView 远程调试(chrome://inspect 看 console/DOM,USB 调试「我的世界」JS 桥用)
                        if (BuildConfig.DEBUG) {
                            WebView.setWebContentsDebuggingEnabled(true)
                        }
                        webChromeClient = MyWebChromeClient(webViewState)
                        webViewClient = MyWebViewClient(webViewState)
                        addJavascriptInterface(bridge, "AndroidWorld")
                    }
                }
            },
            onRelease = { wv ->
                if (WorldWebViewPool.assistantId == assistantId && WorldWebViewPool.webView == null) {
                    // 出路由不销毁,detach 后回池(下次进页直接复用渲染画面)
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    WorldWebViewPool.webView = wv
                } else {
                    // 助手已切换等场景:池里已有归属,这条只能销毁
                    wv.stopLoading()
                    wv.destroy()
                }
            },
            update = { wv ->
                webViewState.webView = wv
                wv.addJavascriptInterface(bridge, "AndroidWorld")
                val content = webViewState.content
                if (content is WebContent.Url) {
                    val url = content.url
                    // 池化复用时 wv.url 可能停在上次离开时的页面:与期望页不符才重载
                    val needLoad = url.isNotEmpty() && (wv.url.isNullOrBlank() || url != wv.url)
                    val needDeepLinkReload = initialLetterId > 0 && !needLoad && !deepLinkReloaded[0]
                    if (needLoad || needDeepLinkReload) {
                        if (needDeepLinkReload) deepLinkReloaded[0] = true
                        wv.loadUrl(url)
                    }
                }
            },
            modifier = Modifier.fillMaxSize().imePadding(),
        )
    }
}
