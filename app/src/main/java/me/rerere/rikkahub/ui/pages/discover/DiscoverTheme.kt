package me.rerere.rikkahub.ui.pages.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PaintBoard
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.context.LocalSettings
import org.koin.compose.koinInject
import kotlin.random.Random

/** 发现页主题 id(存 DisplaySetting.discoverTheme) */
enum class DiscoverThemeId {
    BASELINE_LIGHT, BASELINE_DARK, JOURNAL, GREEN, BLUE, EDITORIAL, STARRY
}

/** 封面形态 */
enum class DiscoverCoverStyle { GRADIENT, POLAROID, PLAIN_PANEL, MOON }

/**
 * 发现页主题 tokens:朋友圈/打卡/心里话/发现 四页共用。
 * 颜色与装饰对应预览稿(r200-theme-picker.html)。
 */
data class DiscoverTheme(
    val id: DiscoverThemeId,
    val label: String,
    val scaffoldBg: Color,
    val bgGradient: List<Color>? = null,
    val drawDots: Boolean = false,      // 手账纸面点阵
    val drawStars: Boolean = false,     // 星空星点
    val drawWash: Boolean = false,      // 蓝色水彩晕染
    val drawFoliage: Boolean = false,   // 绿色绿叶层
    val drawGridPanel: Boolean = false, // 绿色米白内衬虚线框
    val cardBg: Color,
    val cardBorder: Color? = null,
    val cardDashed: Boolean = false,
    val cardCorner: Dp = 16.dp,
    val gbBg: Color,
    val gbBorder: Color? = null,
    val text: Color,
    val sub: Color,
    val name: Color,
    val like: Color,
    val chipBg: Color,
    val chipBorder: Color? = null,
    val cellBg: Color,
    val fldBg: Color,
    val btnBg: Color,
    val btnFg: Color,
    val composerBg: Color,
    val composerBorder: Color? = null,
    val coverStyle: DiscoverCoverStyle = DiscoverCoverStyle.GRADIENT,
    val coverGradient: List<Color>? = null,
)

private val C_B1 = Color(0xFFFCF9FF)
private val C_B2 = Color(0xFF131318)

/** 七套主题(与预览稿一一对应) */
val DiscoverThemes: List<DiscoverTheme> = listOf(
    DiscoverTheme(
        id = DiscoverThemeId.BASELINE_LIGHT, label = "基线 · 亮",
        scaffoldBg = C_B1, cardBg = Color(0xFFF3EEF6), gbBg = Color(0xFFEBE5EF),
        text = Color(0xFF1C1B1F), sub = Color(0xFF49454F), name = Color(0xFF4B5AC0), like = Color(0xFF4B5AC0),
        chipBg = Color(0xFFE4E1F2), cellBg = Color(0xFFEBE5EF), fldBg = Color(0xFFFFFFFF),
        btnBg = Color(0xFF4B5AC0), btnFg = Color(0xFFFFFFFF),
        composerBg = Color(0xFFF3EEF6), composerBorder = Color(0xFFE6DEF0),
        coverGradient = listOf(Color(0xFFDFE0FF), Color(0xFFE4E1F2)),
    ),
    DiscoverTheme(
        id = DiscoverThemeId.BASELINE_DARK, label = "基线 · 暗",
        scaffoldBg = C_B2, cardBg = Color(0xFF1E1E24), gbBg = Color(0xFF28282F),
        text = Color(0xFFE6E1E9), sub = Color(0xFFC9C5D1), name = Color(0xFFBDC3FF), like = Color(0xFFBDC3FF),
        chipBg = Color(0xFF3A3750), cellBg = Color(0xFF28282F), fldBg = Color(0xFF131318),
        btnBg = Color(0xFFBDC3FF), btnFg = Color(0xFF1B2464),
        composerBg = Color(0xFF1E1E24), composerBorder = Color(0xFF2E2B38),
        coverGradient = listOf(Color(0xFF32418C), Color(0xFF3A3750)),
    ),
    DiscoverTheme(
        id = DiscoverThemeId.JOURNAL, label = "手账贴纸",
        scaffoldBg = Color(0xFFFBF6EC), drawDots = true,
        cardBg = Color(0xFFFFFFFF), cardBorder = Color(0xFFD9CBAF), cardDashed = true, cardCorner = 14.dp,
        gbBg = Color(0xFFF7F0DF),
        text = Color(0xFF57503F), sub = Color(0xFFA3947A), name = Color(0xFFB0793B), like = Color(0xFFE05A7E),
        chipBg = Color(0xFFF9E9C7), chipBorder = Color(0xFFEAD3A0), cellBg = Color(0xFFFDF3DE), fldBg = Color(0xFFFFFFFF),
        btnBg = Color(0xFFE05A7E), btnFg = Color(0xFFFFFFFF),
        composerBg = Color(0xFFF4EBD7), composerBorder = Color(0xFFD9CBAF),
        coverStyle = DiscoverCoverStyle.POLAROID,
    ),
    DiscoverTheme(
        id = DiscoverThemeId.GREEN, label = "绿色小清新",
        scaffoldBg = Color(0xFFF2F7E8),
        bgGradient = listOf(Color(0xFFDFEDD3), Color(0xFFF2F7E8), Color(0xFFF6F9EC)),
        drawFoliage = true, drawGridPanel = true,
        cardBg = Color(0xB8FDF9F0), cardBorder = Color(0xFFD5E3C4), cardCorner = 16.dp,
        gbBg = Color(0xCCEFF4E3), gbBorder = Color(0xFFDFE9CE),
        text = Color(0xFF4A5240), sub = Color(0xFF8CA07E), name = Color(0xFF5B8C4A), like = Color(0xFFE8845C),
        chipBg = Color(0xFFE4F0D2), cellBg = Color(0xBFFDF9F0), fldBg = Color(0xE6FDF9F0),
        btnBg = Color(0xFF8FBF6E), btnFg = Color(0xFFFFFFFF),
        composerBg = Color(0xD9EDF3DC), composerBorder = Color(0xFFD5E3C4),
        coverStyle = DiscoverCoverStyle.PLAIN_PANEL,
    ),
    DiscoverTheme(
        id = DiscoverThemeId.BLUE, label = "蓝色磨砂",
        scaffoldBg = Color(0xFFE9F1F8),
        bgGradient = listOf(Color(0xFFD7E5F2), Color(0xFFE9F1F8), Color(0xFFDFEAF4)),
        drawWash = true,
        cardBg = Color(0xD1F7FBFE), cardBorder = Color(0xFF9FB4C8), cardCorner = 18.dp,
        gbBg = Color(0xD9EFF4F9), gbBorder = Color(0xFFD5E0EA),
        text = Color(0xFF3E4A56), sub = Color(0xFF7E93A6), name = Color(0xFF5B7A99), like = Color(0xFFE8845C),
        chipBg = Color(0xFFE3EDF6), cellBg = Color(0xBFFFFFFF), fldBg = Color(0xE6FFFFFF),
        btnBg = Color(0xFFFFFFFF), btnFg = Color(0xFF5B7A99),
        composerBg = Color(0xFFE3EDF6), composerBorder = Color(0xFF9FB4C8),
        coverStyle = DiscoverCoverStyle.PLAIN_PANEL,
    ),
    DiscoverTheme(
        id = DiscoverThemeId.EDITORIAL, label = "杂志编辑",
        scaffoldBg = Color(0xFFFAF8F4),
        cardBg = Color(0x00FAF8F4), gbBg = Color(0x00FAF8F4),
        text = Color(0xFF2B2721), sub = Color(0xFFA39B8B), name = Color(0xFFC74A2E), like = Color(0xFFC74A2E),
        chipBg = Color(0x00FAF8F4), chipBorder = Color(0xFFC74A2E), cellBg = Color(0x00FAF8F4), fldBg = Color(0x00FAF8F4),
        btnBg = Color(0x00FAF8F4), btnFg = Color(0xFFC74A2E),
        composerBg = Color(0xFFFAF8F4), composerBorder = Color(0xFFE0DACC),
        coverStyle = DiscoverCoverStyle.PLAIN_PANEL,
    ),
    DiscoverTheme(
        id = DiscoverThemeId.STARRY, label = "星空梦夜",
        scaffoldBg = Color(0xFF171C3E),
        bgGradient = listOf(Color(0xFF171C3E), Color(0xFF2A2550), Color(0xFF3A2E5C)),
        drawStars = true,
        cardBg = Color(0x12FFFFFF), cardBorder = Color(0x42B9AEFF), cardCorner = 18.dp,
        gbBg = Color(0x40000000), gbBorder = Color(0x29B9AEFF),
        text = Color(0xFFE3DEF6), sub = Color(0xFF8F89B8), name = Color(0xFFC9BFFF), like = Color(0xFFFFD98E),
        chipBg = Color(0x21C9BFFF), cellBg = Color(0x0DFFFFFF), fldBg = Color(0x47000000),
        btnBg = Color(0xFFB9AEFF), btnFg = Color(0xFF171C3E),
        composerBg = Color(0x0BFFFFFF), composerBorder = Color(0x33B9AEFF),
        coverStyle = DiscoverCoverStyle.MOON,
    ),
)

private val DefaultDiscoverTheme = DiscoverThemes.first()

fun parseDiscoverTheme(name: String?): DiscoverTheme =
    DiscoverThemes.firstOrNull { it.id.name == name } ?: DefaultDiscoverTheme

@Composable
fun rememberDiscoverTheme(): DiscoverTheme {
    val settingName = LocalSettings.current.displaySetting.discoverTheme
    return remember(settingName) { parseDiscoverTheme(settingName) }
}

@Composable
fun discoverTopBarColors(theme: DiscoverTheme): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = theme.text,
        titleContentColor = theme.text,
        actionIconContentColor = theme.text,
    )

/** 页面背景与主题装饰(点阵/星星/水彩/绿叶/内衬框),画在 Scaffold 底层 */
fun Modifier.discoverPageBackground(theme: DiscoverTheme): Modifier = drawBehind {
    drawRect(theme.scaffoldBg)
    theme.bgGradient?.let {
        drawRect(brush = Brush.verticalGradient(it, startY = 0f, endY = size.height))
    }
    if (theme.drawDots) drawJournalDots()
    if (theme.drawStars) drawStars()
    if (theme.drawWash) drawWash()
    if (theme.drawGridPanel) drawGridPanel()
    if (theme.drawFoliage) drawFoliage(overlay = false)
}

/** 前景层:绿叶压卡片角(画在内容之上,透明不拦截触摸) */
fun Modifier.discoverForegroundOverlay(theme: DiscoverTheme): Modifier =
    if (theme.drawFoliage) drawBehind { drawFoliage(overlay = true) } else this

private fun DrawScope.drawJournalDots() {
    val step = 16.dp.toPx()
    var y = step
    while (y < size.height) {
        var x = step
        while (x < size.width) {
            drawCircle(color = Color(0x29A08C64), radius = 1.dp.toPx(), center = Offset(x, y))
            x += step
        }
        y += step
    }
}

private fun DrawScope.drawStars() {
    val rand = Random(7)
    repeat(46) {
        val cx = size.width * rand.nextFloat()
        val cy = size.height * rand.nextFloat()
        val gold = it % 7 == 0
        drawCircle(
            color = if (gold) Color(0xCCFFD98E) else Color.White.copy(alpha = 0.15f + rand.nextFloat() * 0.6f),
            radius = (0.8f + rand.nextFloat() * 1.1f).dp.toPx(),
            center = Offset(cx, cy),
        )
    }
}

private fun DrawScope.drawWash() {
    fun blob(fx: Float, fy: Float, r: Float, color: Color) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = Offset(size.width * fx, size.height * fy),
                radius = r,
            ),
            radius = r,
            center = Offset(size.width * fx, size.height * fy),
        )
    }
    blob(0.9f, -0.05f, size.width * 0.62f, Color(0x8CA9CBE8))
    blob(-0.05f, 0.55f, size.width * 0.55f, Color(0x80BBD7EE))
    blob(1.05f, 0.72f, size.width * 0.45f, Color(0x73BFE0F0))
}

private fun DrawScope.drawGridPanel() {
    val inset = 7.dp.toPx()
    drawRoundRect(
        color = Color(0x8CFDFAF0),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(18.dp.toPx()),
    )
    drawRoundRect(
        color = Color(0x8C8BAA78),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(18.dp.toPx()),
        style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))),
    )
}

private data class LeafSpec(
    val fx: Float, val fy: Float,
    val w: Float, val deg: Float,
    val tone: Int, val alpha: Float,
    val overlay: Boolean = false, val faint: Boolean = false,
)

private val LeafTones = listOf(
    Color(0xFF8FB072), Color(0xFF5C8A46), Color(0xFFA8C686), Color(0xFF6E9A52), Color(0xFFC9DCA8),
)

private val LeafLayout = listOf(
    // 背景虚影大叶(铺满感)
    LeafSpec(0.50f, 0.32f, 120f, 38f, 1, 0.13f, faint = true),
    LeafSpec(0.04f, 0.55f, 120f, -18f, 3, 0.12f, faint = true),
    LeafSpec(0.72f, 0.82f, 110f, 72f, 0, 0.13f, faint = true),
    // 边缘叶簇:左上
    LeafSpec(0.035f, 0.095f, 26f, -32f, 0, 0.8f),
    LeafSpec(0.095f, 0.075f, 34f, 8f, 1, 0.7f),
    LeafSpec(0.025f, 0.135f, 30f, 58f, 2, 0.75f),
    // 右缘垂枝
    LeafSpec(0.965f, 0.185f, 28f, 68f, 4, 0.75f),
    LeafSpec(0.985f, 0.245f, 36f, 108f, 1, 0.7f),
    LeafSpec(0.96f, 0.305f, 24f, 92f, 2, 0.8f),
    LeafSpec(0.985f, 0.365f, 30f, 138f, 0, 0.7f),
    // 左下
    LeafSpec(0.04f, 0.90f, 30f, -42f, 1, 0.75f),
    LeafSpec(0.095f, 0.955f, 38f, 14f, 4, 0.7f),
    LeafSpec(0.025f, 0.985f, 26f, 66f, 2, 0.8f),
    // 压卡片角实叶(前景)
    LeafSpec(0.07f, 0.24f, 22f, -18f, 0, 0.85f, overlay = true),
    LeafSpec(0.94f, 0.52f, 20f, 32f, 1, 0.8f, overlay = true),
    LeafSpec(0.10f, 0.74f, 18f, 48f, 2, 0.8f, overlay = true),
    LeafSpec(0.90f, 0.94f, 22f, -24f, 3, 0.8f, overlay = true),
)

private fun DrawScope.drawLeaf(center: Offset, w: Float, deg: Float, tone: Color, alpha: Float, faint: Boolean) {
    rotate(deg, pivot = center) {
        val h = w * 1.9f
        val path = Path().apply {
            moveTo(center.x, center.y - h / 2)
            cubicTo(center.x + w * 0.55f, center.y - h * 0.28f, center.x + w * 0.55f, center.y + h * 0.28f, center.x, center.y + h / 2)
            cubicTo(center.x - w * 0.55f, center.y + h * 0.28f, center.x - w * 0.55f, center.y - h * 0.28f, center.x, center.y - h / 2)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(tone, tone.copy(alpha = 0.75f))))
        if (!faint) {
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(center.x, center.y - h * 0.4f),
                end = Offset(center.x, center.y + h * 0.4f),
                strokeWidth = 1.2.dp.toPx(),
            )
        }
    }
}

private fun DrawScope.drawFoliage(overlay: Boolean) {
    LeafLayout.filter { it.overlay == overlay }.forEach { spec ->
        val cx = size.width * spec.fx
        val cy = size.height * spec.fy
        drawLeaf(
            center = Offset(cx, cy),
            w = spec.w.dp.toPx(),
            deg = spec.deg,
            tone = LeafTones[spec.tone % LeafTones.size],
            alpha = spec.alpha,
            faint = spec.faint,
        )
    }
}

/** 主题化卡片描边:dashed 用 drawBehind,实线用 border */
fun Modifier.discoverCardBorder(theme: DiscoverTheme, corner: Dp = theme.cardCorner): Modifier {
    val border = theme.cardBorder ?: return this
    return if (theme.cardDashed) {
        drawBehind {
            drawRoundRect(
                color = border,
                cornerRadius = CornerRadius(corner.toPx()),
                style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))),
            )
        }
    } else {
        border(1.dp, border, RoundedCornerShape(corner))
    }
}

/** 手账拍立得封面渐变预设 */
val JournalCoverPresets: List<List<Color>> = listOf(
    listOf(Color(0xFFF9D9A0), Color(0xFFF2B7C6), Color(0xFFC9A7E8)),
    listOf(Color(0xFFC9A7E8), Color(0xFF8FA8F5)),
    listOf(Color(0xFFBFE3C0), Color(0xFFE8F0B8)),
    listOf(Color(0xFFA8D8FF), Color(0xFFD9E4FF)),
)

/** 主题折叠下拉选择器:各页面顶栏 🎨 按钮(收纳原则) */
@Composable
fun DiscoverThemeMenuButton(modifier: Modifier = Modifier) {
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val current = rememberDiscoverTheme()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = modifier) {
            Icon(HugeIcons.PaintBoard, contentDescription = "发现页主题")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Text(
            text = "发现页主题",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        DiscoverThemes.forEach { t ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = t.label,
                        color = if (t.id == current.id) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                },
                leadingIcon = {
                    if (t.id == current.id) {
                        Icon(HugeIcons.Tick02, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Box(modifier = Modifier.size(16.dp))
                    }
                },
                onClick = {
                    open = false
                    scope.launch {
                        settingsStore.update { s ->
                            s.copy(displaySetting = s.displaySetting.copy(discoverTheme = t.id.name))
                        }
                    }
                },
            )
        }
        }
    }
}

/** 主题化的简单 Surface 卡片(朋友圈/打卡/心里话共用底件) */
@Composable
fun DiscoverCard(
    theme: DiscoverTheme,
    modifier: Modifier = Modifier,
    corner: Dp = theme.cardCorner,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.discoverCardBorder(theme, corner),
        shape = RoundedCornerShape(corner),
        color = theme.cardBg,
    ) {
        Box { content() }
    }
}
