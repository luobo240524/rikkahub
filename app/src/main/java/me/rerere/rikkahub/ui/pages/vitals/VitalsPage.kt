package me.rerere.rikkahub.ui.pages.vitals

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.vitals.VitalsEngine
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.SocialScrollHeader
import me.rerere.rikkahub.ui.pages.discover.discoverCardBorder
import me.rerere.rikkahub.ui.pages.discover.discoverForegroundOverlay
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.US)

/**
 * 「生命体征」页:TA 此刻的心跳与情绪。
 * 与发现页四页共用主题系统(8 套,🎨 按钮切换,含粉嫩少女);
 * 顶部大数字心跳+总状态 → 心电波形 → 情绪罗盘(valence/arousal/底色) → 24h 曲线 → 潮汐带(事件浮标)。
 * 未开启的助手显示引导文案(开关在助手设置里,每个助手独立)。
 */
@Composable
fun VitalsPage() {
    val navController = LocalNavController.current
    val vm: VitalsVM = koinViewModel()
    val ui by vm.state.collectAsStateWithLifecycle()
    val theme = rememberDiscoverTheme()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .discoverForegroundOverlay(theme),
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .discoverPageBackground(theme),
        ) {
            if (!ui.enabled) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(HugeIcons.Favourite, null, tint = theme.sub)
                    Spacer(Modifier.height(12.dp))
                    Text("TA 还没有心跳", style = MaterialTheme.typography.titleMedium, color = theme.text)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "在 助手设置 → 生命体征 里为 TA 开启后,\n这里会显示心跳、呼吸与情绪的实时变化。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.sub,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Box
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 头:返回键+🎨 主题切换在上一行,大标题随内容滚走(与发现页四页同款)
                SocialScrollHeader(
                    title = "生命体征" + if (ui.name.isNotBlank()) " · ${ui.name}" else "",
                    theme = theme,
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(HugeIcons.ArrowLeft01, null, tint = theme.text)
                        }
                    },
                    actions = { DiscoverThemeMenuButton() },
                )

                val snap = ui.snapshot
                if (snap != null) {
                    // 大数字心跳 + 总状态(此刻是什么情绪) + 呼吸
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "${snap.bpm}",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.name,
                        )
                        Text(
                            text = " bpm",
                            fontSize = 16.sp,
                            color = theme.sub,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(
                                text = "此刻 · " + VitalsEngine.dominantEmotion(snap.valence, snap.arousal, snap.asleep),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.name,
                            )
                            Text(
                                text = if (snap.asleep) "睡着了" else "呼吸${snap.breath}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.sub,
                            )
                        }
                    }
                    PulseWaveform(bpm = snap.bpm, asleep = snap.asleep, accent = theme.name)

                    // 情绪罗盘(两轴+安全感/自信由支配感投影+底色)
                    CompassCard(
                        theme = theme,
                        valence = snap.valence,
                        arousal = snap.arousal,
                        safety = ui.safety,
                        confidence = ui.confidence,
                        moodBase = ui.stateRow?.moodBase ?: 0f,
                    )
                }

                // 24h 心跳曲线
                if (ui.curve.isNotEmpty()) {
                    BpmCurveChart(curve = ui.curve, accent = theme.name, grid = theme.text)
                }

                // 潮汐带:24h 事件浮标
                Text("潮汐 · 最近 24 小时", style = MaterialTheme.typography.titleSmall, color = theme.text)
                if (ui.pulses.isEmpty()) {
                    Text(
                        "还很平静,没有起因情绪漂过。",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.sub,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ui.pulses.take(30).forEach { p ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.chipBg)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(text = p.label, style = MaterialTheme.typography.labelMedium, color = theme.text)
                                Text(
                                    text = TIME_FORMAT.format(Date(p.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.sub,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** 心电波形:按 bpm 变速的简化 PQRS-T 波,睡着时变平缓 */
@Composable
private fun PulseWaveform(bpm: Int, asleep: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (60000 / bpm.coerceIn(40, 130)).coerceAtLeast(300), easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val beatLen = w / 3f // 可见窗口 3 个心跳
        val offset = phase * beatLen
        fun ecg(x: Float): Float {
            val u = ((x + offset) % beatLen) / beatLen // 0..1 一个心跳周期
            return when {
                asleep -> 0.12f * kotlin.math.sin(u * 2f * Math.PI.toFloat()) // 睡眠:低幅慢波
                u < 0.12f -> -0.10f * kotlin.math.sin(u / 0.12f * Math.PI.toFloat())          // P
                u < 0.22f -> 0f                                                                // PR 段
                u < 0.30f -> -1f + ((u - 0.22f) / 0.08f) * 1.15f                               // Q 下探→R 尖峰
                u < 0.40f -> 0.15f - ((u - 0.30f) / 0.10f) * 0.85f                             // S 回落
                u < 0.62f -> 0f                                                                // ST 段
                u < 0.85f -> 0.28f * kotlin.math.sin((u - 0.62f) / 0.23f * Math.PI.toFloat())  // T
                else -> 0f
            }
        }
        val path = Path()
        var x = 0f
        while (x <= w) {
            val y = mid - ecg(x) * h * 0.42f
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 4f
        }
        drawPath(path, accent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

/** 情绪罗盘卡片:效价/唤醒/底色三件套,横条 + 档位名 */
@Composable
private fun CompassCard(
    theme: me.rerere.rikkahub.ui.pages.discover.DiscoverTheme,
    valence: Float,
    arousal: Float,
    safety: Float,
    confidence: Float,
    moodBase: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.cardCorner))
            .background(theme.cardBg)
            .discoverCardBorder(theme)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GaugeRow(theme, "情绪", valence, -1f to 1f, "低落" to "愉快")
        GaugeRow(theme, "精力", arousal, 0f to 1f, "平静" to "兴奋")
        GaugeRow(theme, "安全感", safety, 0f to 1f, "不安" to "安心")
        GaugeRow(theme, "自信", confidence, 0f to 1f, "受挫" to "笃定")
        GaugeRow(theme, "底色心境", moodBase, -1f to 1f, "沉" to "明")
    }
}

@Composable
private fun GaugeRow(
    theme: me.rerere.rikkahub.ui.pages.discover.DiscoverTheme,
    label: String,
    value: Float,
    range: Pair<Float, Float>,
    low: Pair<String, String>,
) {
    val frac = ((value - range.first) / (range.second - range.first)).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = theme.text)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (value >= 0) low.second else low.first,
                style = MaterialTheme.typography.labelSmall,
                color = theme.sub,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(theme.chipBg),
        ) {
            val accent = if (value >= 0) theme.name else theme.like
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
        }
    }
}

/** 24h 心跳曲线(每 5 分钟一个采样点) */
@Composable
private fun BpmCurveChart(curve: List<VitalsCurvePoint>, accent: Color, grid: Color) {
    Column {
        Text("心跳 · 最近 24 小时", style = MaterialTheme.typography.titleSmall, color = accent)
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            if (curve.size < 2) return@Canvas
            val minBpm = 40f
            val maxBpm = 130f
            fun y(bpm: Int) = size.height * (1f - (bpm - minBpm) / (maxBpm - minBpm))
            // 网格:60/80/100
            listOf(60, 80, 100).forEach { g ->
                drawLine(grid.copy(alpha = 0.15f), Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 1f)
            }
            val path = Path()
            curve.forEachIndexed { i, p ->
                val x = size.width * i / (curve.size - 1).toFloat()
                val yy = y(p.bpm)
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            drawPath(path, accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
        Row {
            Text("一天前", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("现在", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * 聊天页顶栏迷你角标:♥ bpm,点按进生命体征页。
 * 只在助手开启 vitalsEnabled 时由 ChatPage 渲染(在助手名右边)。
 */
@Composable
fun VitalsMiniBadge() {
    val navController = LocalNavController.current
    val vm: VitalsVM = koinViewModel()
    val ui by vm.state.collectAsStateWithLifecycle()
    val snap = ui.snapshot ?: return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable { navController.navigate(Screen.Vitals) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            HugeIcons.Favourite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp),
        )
        Text(
            // ♡bpm·两轴情绪·模型自造词,如 "88·期待·平静"(两词相同也并排显示,自造词=模型真实的想法,30分钟内有效)
            text = buildString {
                append("${snap.bpm}·${ui.emotion}")
                if (ui.modelWord.isNotBlank()) {
                    append("·${ui.modelWord}")
                }
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    if (snap.asleep) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary
                ),
        )
    }
}
