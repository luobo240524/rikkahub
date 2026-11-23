package me.rerere.rikkahub.ui.pages.world

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.world.WorldRepository
import me.rerere.rikkahub.ui.components.webview.WEB_VIEW_ASSET_URL
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.compose.koinInject

/**
 * 「我的世界」页:完全全屏(无原生顶栏,页面内自带头部/返回键),WebView 载 assets/your-world/ 定稿页。
 * 内部跳转(封面→功能页→四空间)由页面 JS 调 AndroidWorld.nav() 走原生 loadUrl;
 * 页面内的「←」调 AndroidWorld.back() 走原生回退(优先 WebView 历史,没有历史才退出路由);
 * 系统返回键同样优先回退 WebView 历史。状态经 AndroidWorld.getState/操作桥拉取。
 */
@Composable
fun WorldPage() {
    val navController = LocalNavController.current
    val repository = koinInject<WorldRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val musicController = koinInject<me.rerere.rikkahub.data.music.MusicPlayerController>()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val assistant = remember { settingsStore.settingsFlow.value.getCurrentAssistant() }

    val bridge = remember(assistant.id) {
        WorldJsBridge(
            context = appContext,
            assistantId = assistant.id.toString(),
            assistantName = assistant.name.ifEmpty { "TA" },
            avatar = assistant.avatar,
            repository = repository,
            music = musicController,
        )
    }
    val webViewState = rememberWebViewState(
        url = "$WEB_VIEW_ASSET_URL/your-world/cover.html",
        interfaces = mapOf("AndroidWorld" to bridge),
    )
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    LaunchedEffect(webViewState) {
        bridge.onNav = { page ->
            mainHandler.post { webViewState.loadUrl("$WEB_VIEW_ASSET_URL/your-world/$page.html") }
        }
        bridge.onBack = {
            mainHandler.post {
                if (webViewState.canGoBack) webViewState.goBack() else navController.popBackStack()
            }
        }
    }

    // WebView 历史能退(封面→功能页→空间)时系统返回先退 WebView
    BackHandler(enabled = webViewState.canGoBack) { webViewState.goBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        WebView(state = webViewState, modifier = Modifier.fillMaxSize())
    }
}
