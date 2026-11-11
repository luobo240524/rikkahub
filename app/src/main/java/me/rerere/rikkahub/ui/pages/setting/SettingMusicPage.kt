package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.rerere.rikkahub.data.music.MusicPlayerController
import me.rerere.rikkahub.data.music.NeteaseClient
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * 一起听(网易云音乐)设置页:扫码登录 + 风险声明 + 播放状态。
 * 社区接口无官方保障(VIP/版权/接口失效),一期已向用户声明。
 */
@Composable
fun SettingMusicPage(
    vm: SettingVM = koinViewModel(),
    musicPlayer: MusicPlayerController = koinInject(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val musicState by musicPlayer.state.collectAsStateWithLifecycle()
    var showQrDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("一起听") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            CardGroup {
                item(
                    onClick = { showQrDialog = true },
                    headlineContent = {
                        Text(
                            if (settings.neteaseNickname.isNotBlank()) {
                                "网易云音乐 · ${settings.neteaseNickname}"
                            } else {
                                "网易云音乐账号"
                            }
                        )
                    },
                    supportingContent = {
                        Text(
                            if (settings.neteaseCookie.isNotBlank()) {
                                "已登录(Cookie 保存在本机)· 点按重新扫码"
                            } else {
                                "未登录 · 登录后可用你账号的权益听歌(VIP 歌也能播)"
                            }
                        )
                    },
                )
                if (settings.neteaseCookie.isNotBlank()) {
                    item(
                        onClick = {
                            musicPlayer.neteaseClient.cookie = ""
                            musicPlayer.stop()
                            vm.updateSettings(
                                settings.copy(neteaseCookie = "", neteaseNickname = "")
                            )
                        },
                        headlineContent = { Text("退出登录") },
                        supportingContent = { Text("清除本机保存的登录 Cookie") },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "关于「一起听」",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "在助手的「本地工具」里打开「一起听」后,TA 可以点歌/切歌/暂停,并知道当前播放的歌与歌词,边听边聊。" +
                    "播放使用网易云音乐社区接口(非官方):VIP/版权歌可能拿不到播放链接,接口也可能随网易调整而失效;" +
                    "未登录时免费歌可搜可播,登录后用你账号的权益。Cookie 仅保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (musicState.song != null && musicState.isActive) {
                CardGroup {
                    item(
                        headlineContent = { Text("正在播放:《${musicState.song?.name}》") },
                        supportingContent = { Text(musicPlayer.currentStatusText()) },
                    )
                }
            }
        }
    }

    if (showQrDialog) {
        NetEaseQrLoginDialog(
            neteaseClient = musicPlayer.neteaseClient,
            onAuthorized = { cookie, nickname ->
                musicPlayer.neteaseClient.cookie = cookie
                vm.updateSettings(
                    settings.copy(neteaseCookie = cookie, neteaseNickname = nickname.orEmpty())
                )
                showQrDialog = false
            },
            onDismiss = { showQrDialog = false },
        )
    }
}

/**
 * 扫码登录弹窗:zxing 生成二维码,2s 轮询状态(待扫码/已扫码/成功/过期自动重建)。
 * session 递增 = 整轮重来(手动「刷新」)。
 */
@Composable
private fun NetEaseQrLoginDialog(
    neteaseClient: NeteaseClient,
    onAuthorized: (cookie: String, nickname: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("正在生成二维码…") }
    var scanned by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf(0) }

    LaunchedEffect(session) {
        suspend fun newQr() {
            runCatching {
                val login = neteaseClient.createQrLogin()
                key = login.key
                qrUrl = login.qrUrl
                statusText = "打开网易云音乐 App 扫一扫"
                scanned = false
            }.onFailure {
                key = null
                statusText = "创建二维码失败:${it.message}(接口可能暂时不可用)"
            }
        }
        newQr()
        while (isActive && key != null) {
            delay(2000)
            val currentKey = key ?: break
            when (val st = neteaseClient.checkQrLogin(currentKey)) {
                is NeteaseClient.QrState.Scanned -> {
                    scanned = true
                    statusText = "已扫码,请在手机上确认登录"
                }
                is NeteaseClient.QrState.Authorized -> {
                    statusText = "登录成功"
                    val nickname = runCatching { neteaseClient.getNickname() }.getOrNull()
                    onAuthorized(st.cookies, nickname)
                    return@LaunchedEffect
                }
                is NeteaseClient.QrState.Waiting -> {
                    statusText = "二维码已过期,正在刷新…"
                    newQr()
                }
                else -> {}
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录网易云音乐") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val url = qrUrl
                if (url != null) {
                    val bitmap = remember(url) { generateQrBitmap(url) }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Spacer(Modifier.height(200.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = { session++ }) { Text("刷新") }
        },
    )
}

private fun generateQrBitmap(content: String): android.graphics.Bitmap {
    val size = 512
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 0),
    )
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
