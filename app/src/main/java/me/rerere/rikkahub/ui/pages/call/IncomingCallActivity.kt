package me.rerere.rikkahub.ui.pages.call

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import me.rerere.rikkahub.RouteActivity

/** 来电页 → 主界面接听意图(RouteActivity handleIntent 分支同名常量) */
const val ACTION_ANSWER_VOICE_CALL = "me.rerere.rikkahub.action.ANSWER_VOICE_CALL"
const val EXTRA_CALLER_NAME = "callerName"
const val EXTRA_CALL_REASON = "callReason"
const val EXTRA_CALL_ASSISTANT_ID = "assistantId"
const val EXTRA_CALL_AVATAR_KIND = "avatarKind" // image / emoji / dummy
const val EXTRA_CALL_AVATAR_VALUE = "avatarValue"
const val EXTRA_CALL_RINGTONE = "ringtoneUri"

/**
 * 助手来电全屏提醒页(voice_call_ring 触发,全屏 Intent 在锁屏/任意界面上弹出):
 * 大头像 + 名字 + 来电理由,响铃循环 + 震动,接听 → 语音通话页,挂断/超时 45s 关闭。
 */
class IncomingCallActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 锁屏可见 + 亮屏(来电式)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val name = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "助手"
        val reason = intent.getStringExtra(EXTRA_CALL_REASON).orEmpty()
        val avatarKind = intent.getStringExtra(EXTRA_CALL_AVATAR_KIND) ?: "dummy"
        val avatarValue = intent.getStringExtra(EXTRA_CALL_AVATAR_VALUE).orEmpty()
        val ringtoneUri = intent.getStringExtra(EXTRA_CALL_RINGTONE).orEmpty()
        val assistantId = intent.getStringExtra(EXTRA_CALL_ASSISTANT_ID)

        // 响铃:自定义铃声优先 → 系统默认铃声;失败静默(来电页本身仍是提醒)
        runCatching {
            val uri = if (ringtoneUri.isNotBlank()) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@IncomingCallActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }
        // 震动:铃声间隙震一下,与系统来电体感一致
        runCatching {
            vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(500, 900, 300, 900), 0)
            )
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                IncomingCallScreen(
                    name = name,
                    reason = reason,
                    avatarKind = avatarKind,
                    avatarValue = avatarValue,
                    onAnswer = {
                        runCatching {
                            startActivity(
                                Intent(this, RouteActivity::class.java).apply {
                                    action = ACTION_ANSWER_VOICE_CALL
                                    putExtra("assistantId", assistantId)
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                            or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    )
                                }
                            )
                        }
                        stopRinging()
                        finish()
                    },
                    onDecline = {
                        stopRinging()
                        finish()
                    },
                )
            }
            // 超时自动关闭
            LaunchedEffect(Unit) {
                delay(45_000)
                stopRinging()
                finish()
            }
        }
    }

    private fun stopRinging() {
        runCatching {
            player?.run { stop(); release() }
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

@Composable
private fun IncomingCallScreen(
    name: String,
    reason: String,
    avatarKind: String,
    avatarValue: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF14161B),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))
            Text(
                text = "📞 助手来电",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFB9AEFF),
            )
            Spacer(Modifier.weight(1f))
            CallAvatar(avatarKind, avatarValue, name)
            Spacer(Modifier.height(20.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            if (reason.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC9C5D1),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onDecline,
                    shape = CircleShape,
                    color = Color(0xFF3A3D45),
                    modifier = Modifier.size(76.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("拒接", color = Color.White, fontSize = 16.sp)
                    }
                }
                Surface(
                    onClick = onAnswer,
                    shape = CircleShape,
                    color = Color(0xFF3BC46A),
                    modifier = Modifier.size(76.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("接听", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun CallAvatar(kind: String, value: String, name: String) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(Color(0xFF262A33)),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            "image" -> AsyncImage(
                model = value,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            "emoji" -> Text(text = value, fontSize = 64.sp)
            else -> Text(
                text = name.firstOrNull()?.toString() ?: "助",
                fontSize = 48.sp,
                color = Color(0xFFB9AEFF),
            )
        }
    }
}
