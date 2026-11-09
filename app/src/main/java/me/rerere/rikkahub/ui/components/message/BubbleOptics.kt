package me.rerere.rikkahub.ui.components.message

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 气泡光学修饰符共享库(ChatMessage 与设置页 MiniBubblePreview 共用)。
 * 注意:阴影类(drawBehind)必须挂在 Surface 之前,边缘类(drawWithContent)必须挂在 Surface 之后。
 */

/**
 * 实心立体(neumorph)柔影:光源在左上。
 * 凸起 = 右下暗影 + 左上亮影;凹陷 = 裁剪到气泡轮廓后画偏移模糊色块(内阴影)。
 * 模糊用 BlurMaskFilter,画在 Surface 之下(drawBehind),失败静默(老机型禁 MaskFilter 时退化为纯色气泡)。
 * r216 加浓档:blur 20dp / offset 9dp,亮色主题对比明显拉高。
 */
@Composable
internal fun Modifier.neumorphShadow(
    shape: Shape,
    raised: Boolean,
): Modifier {
    val bg = MaterialTheme.colorScheme.background
    val isDark = bg.luminance() < 0.5f
    val density = LocalDensity.current
    val blurPx = with(density) { 14.dp.toPx() }
    val offPx = with(density) { 6.dp.toPx() }
    val darkPaint = remember(isDark, blurPx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.Black.copy(alpha = if (isDark) 0.55f else 0.38f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    // 亮影不透明度下调(原1.0在彩底/星空壁纸上糊出白块,即"四角直角阴影"观感来源之一)
    val lightPaint = remember(isDark, blurPx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.copy(alpha = if (isDark) 0.10f else 0.80f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    // 凹陷(内阴影)描边环画笔:实心块会污染文字,环只在边缘内侧留暗/亮晕
    val darkInsetPaint = remember(isDark, blurPx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.Black.copy(alpha = if (isDark) 0.50f else 0.34f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = blurPx * 2f
        }
    }
    val lightInsetPaint = remember(isDark, blurPx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.copy(alpha = if (isDark) 0.10f else 0.85f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = blurPx * 2f
        }
    }
    // 实际轮廓的 android Path:贴角圆角/非对称角/任意形状,阴影与气泡严格同形
    fun outlinePath(outline: Outline): android.graphics.Path? = when (outline) {
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }.asAndroidPath()
        is Outline.Generic -> Path().apply { addPath(outline.path) }.asAndroidPath()
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }.asAndroidPath()
        else -> null
    }
    return if (raised) {
        // 凸起:双向柔影画在体色之下,按实际轮廓平移绘制
        Modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val p = outlinePath(outline) ?: return@drawBehind
            val nc = drawContext.canvas.nativeCanvas
            nc.save(); nc.translate(offPx, offPx); nc.drawPath(p, darkPaint); nc.restore()
            nc.save(); nc.translate(-offPx, -offPx); nc.drawPath(p, lightPaint); nc.restore()
        }
    } else {
        // 凹陷:内阴影画在内容之上——drawBehind 会被不透明气泡底完全盖住(按压无立体感的根因);
        // 描边环沿轮廓平移,裁剪进气泡后只在边缘内侧留暗/亮晕,中心区域不受污染
        Modifier.drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            val p = outlinePath(outline) ?: return@drawWithContent
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.clipPath(p)
            nc.translate(-offPx, -offPx)
            nc.drawPath(p, darkInsetPaint)
            nc.translate(2f * offPx, 2f * offPx)
            nc.drawPath(p, lightInsetPaint)
            nc.restore()
        }
    }
}

/**
 * 液态玻璃双层投影(liquid-glass-note 聊天材质口径):
 * 近层 0 7dp 18dp rgba(20,26,38,.105) 管轮廓分离,远层 0 2dp 5dp rgba(20,26,38,.035) 管悬浮感。
 */
@Composable
internal fun Modifier.liquidGlassShadow(shape: Shape): Modifier {
    val density = LocalDensity.current
    val nearBlur = with(density) { 18.dp.toPx() }
    val nearDy = with(density) { 7.dp.toPx() }
    val farBlur = with(density) { 5.dp.toPx() }
    val farDy = with(density) { 2.dp.toPx() }
    val nearPaint = remember(nearBlur) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((255 * 0.105f).toInt(), 20, 26, 38)
            maskFilter = android.graphics.BlurMaskFilter(nearBlur, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    val farPaint = remember(farBlur) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((255 * 0.035f).toInt(), 20, 26, 38)
            maskFilter = android.graphics.BlurMaskFilter(farBlur, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    return Modifier.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        // 按实际轮廓绘制(与 neumorphShadow 同口径,杜绝角落残影)
        val p = when (outline) {
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }.asAndroidPath()
            is Outline.Generic -> Path().apply { addPath(outline.path) }.asAndroidPath()
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }.asAndroidPath()
            else -> null
        } ?: return@drawBehind
        val nc = drawContext.canvas.nativeCanvas
        nc.save(); nc.translate(0f, nearDy); nc.drawPath(p, nearPaint); nc.restore()
        nc.save(); nc.translate(0f, farDy); nc.drawPath(p, farPaint); nc.restore()
    }
}

/**
 * 液态玻璃边缘光学(drawWithContent,画在内容之上、裁剪进气泡轮廓):
 * - 对角高光罩(轻)
 * - 内侧烟灰暗边:亮度自适应 —— 亮背景边窄且轻,暗背景保留厚度(liquid-glass-note 口径,非整圈白边)
 * - 方向性弧形高光:左上主高光 + 右下弱反光;亮背景中右上转暗色点缀
 * smoked = 烟灰预设:暗边更重、高光更收敛
 */
@Composable
internal fun Modifier.liquidGlassEdge(shape: Shape, smoked: Boolean): Modifier {
    val isBrightBg = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val density = LocalDensity.current
    val edgeW = with(density) { (if (isBrightBg) 2.5f else 4f).dp.toPx() }
    val strokeW = with(density) { 2.5f.dp.toPx() }
    return Modifier.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        if (outline !is Outline.Rounded) return@drawWithContent
        val rr = outline.roundRect
        val r = maxOf(
            rr.topLeftCornerRadius.x, rr.topRightCornerRadius.x,
            rr.bottomRightCornerRadius.x, rr.bottomLeftCornerRadius.x,
        )
        val path = Path().apply { addRoundRect(rr) }
        clipPath(path) {
            // 对角高光罩:左上到右下的轻亮扫掠
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (smoked) 0.05f else 0.10f),
                        Color.Transparent,
                        Color.White.copy(alpha = if (smoked) 0.02f else 0.05f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )
            // 内侧烟灰暗边:两道描边(宽淡 + 窄浓)叠加出渐变带,外半被 clip 裁掉只留内侧
            // 清透预设减灰(原0.10/0.20在浅底上读作"磨砂灰");烟灰预设保持浓度
            val smokeAlpha = ((if (isBrightBg) 0.10f else 0.20f) * if (smoked) 1.35f else 0.45f).coerceAtMost(0.30f)
            val smokeColor = Color(0xFF141A26).copy(alpha = smokeAlpha)
            drawPath(path, smokeColor.copy(alpha = smokeAlpha * 0.5f), style = Stroke(width = edgeW * 2f))
            drawPath(path, smokeColor, style = Stroke(width = edgeW))
            // 方向性弧形高光(描在角圆弧上,内半留在轮廓内)
            drawArc(
                color = Color.White.copy(
                    alpha = (if (isBrightBg) 0.40f else 0.55f) * if (smoked) 0.7f else 1f
                ),
                topLeft = Offset(rr.left, rr.top),
                size = Size(r * 2f, r * 2f),
                startAngle = 180f, sweepAngle = 90f, useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(
                    alpha = (if (isBrightBg) 0.14f else 0.20f) * if (smoked) 0.7f else 1f
                ),
                topLeft = Offset(rr.right - r * 2f, rr.bottom - r * 2f),
                size = Size(r * 2f, r * 2f),
                startAngle = 0f, sweepAngle = 90f, useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            // 亮背景:右上高光转暗色点缀(参考实现口径)
            if (isBrightBg && !smoked) {
                drawArc(
                    color = Color(0xFF141A26).copy(alpha = 0.10f),
                    topLeft = Offset(rr.right - r * 2f, rr.top),
                    size = Size(r * 2f, r * 2f),
                    startAngle = 270f, sweepAngle = 90f, useCenter = false,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }
        }
    }
}
