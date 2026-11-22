package me.rerere.rikkahub.ui.pages.world

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.world.WorldRepository
import me.rerere.rikkahub.ui.components.webview.WEB_VIEW_ASSET_URL
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.LocalNavController
import org.koin.compose.koinInject

/** 顶栏标题按当前 HTML 页名映射;封面不显示顶栏(沉浸动效) */
private val PAGE_TITLES = mapOf(
    "home" to "你的世界",
    "thoughts" to "碎碎念",
    "lights" to "留灯",
    "garden" to "花园",
    "pet" to "宠物",
)

/**
 * 「你的世界」页:原生壳(顶栏/返回)+ WebView 载 assets/your-world/ 定稿页。
 * 内部跳转(封面→功能页→四空间)由页面 JS 调 AndroidWorld.nav() 走原生 loadUrl,
 * 返回键优先回退 WebView 历史,没有历史才退出路由;状态经 AndroidWorld.getState/操作桥拉取。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldPage() {
    val navController = LocalNavController.current
    val repository = koinInject<WorldRepository>()
    val settingsStore = koinInject<SettingsStore>()
    val assistant = remember { settingsStore.settingsFlow.value.getCurrentAssistant() }

    val bridge = remember(assistant.id) {
        WorldJsBridge(
            assistantId = assistant.id.toString(),
            assistantName = assistant.name.ifEmpty { "TA" },
            repository = repository,
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
    }

    // WebView 历史能退(封面→功能页→空间)时系统返回先退 WebView
    BackHandler(enabled = webViewState.canGoBack) { webViewState.goBack() }

    val currentUrl = webViewState.currentUrl
    val page = currentUrl?.substringAfterLast('/')?.substringBefore('.') ?: "cover"
    val title = PAGE_TITLES[page]

    Scaffold(
        topBar = {
            if (title != null) {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (webViewState.canGoBack) webViewState.goBack() else navController.popBackStack()
                        }) {
                            Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
    ) { padding ->
        WebView(state = webViewState, modifier = Modifier.fillMaxSize().padding(padding))
    }
}
