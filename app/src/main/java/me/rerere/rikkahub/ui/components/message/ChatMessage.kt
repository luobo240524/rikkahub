package me.rerere.rikkahub.ui.components.message

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.BubbleBeautify
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.service.prettifyWakeTag
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.components.richtext.buildMarkdownPreviewHtml
import me.rerere.rikkahub.ui.components.webview.WebViewContentCache
import me.rerere.rikkahub.ui.components.ui.ChainOfThought
import me.rerere.rikkahub.ui.components.charts.ChartCard
import me.rerere.rikkahub.ui.components.charts.ChartSpec
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.stripSplitMarkers
import me.rerere.rikkahub.utils.highlightQuotes
import me.rerere.rikkahub.service.splitDisplaySegments
import me.rerere.rikkahub.ui.theme.LocalChatFontFamily
import me.rerere.rikkahub.ui.theme.resolveChatFontFamilyOrNull
import me.rerere.rikkahub.ui.theme.rememberChatFontFamily
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.urlDecode
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessage(
    node: MessageNode,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    model: Model? = null,
    modelProviderName: String? = null,
    assistant: Assistant? = null,
    lastMessage: Boolean = false,
    showDateTime: Boolean = true,
    showActionsRow: Boolean = true,
    showModelInfo: Boolean = true,
    onQuoteMessage: (UIMessage) -> Unit = {},
    hazeState: HazeState? = null,
    blurAllowed: Boolean = true, // 模糊降级守卫:消息过多时由调用方传 false,玻璃/磨砂退化为无模糊透色
    onFork: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
) {
    val message = node.messages[node.selectIndex]
    // 分条引用判断用的助手（assistant 可能为 null，回退当前助手）——须在 composable 上下文读取
    val splitRefAssistant = assistant ?: me.rerere.rikkahub.ui.context.LocalSettings.current.getCurrentAssistant()
    val settings = LocalSettings.current.displaySetting
    // 每助手字体覆盖优先, 否则跟随全局
    val chatFontFamily = assistant?.let { asst ->
        val ctx = LocalContext.current
        remember(asst.chatFontFamily) { asst.resolveChatFontFamilyOrNull(ctx) }
    } ?: LocalChatFontFamily.current
    ?: rememberChatFontFamily(settings)
    // 字体大小：每助手覆盖优先, 否则跟随全局
    val fontRatio = assistant?.fontSizeRatio ?: settings.fontSizeRatio
    val textStyle = LocalTextStyle.current.copy(
        fontSize = LocalTextStyle.current.fontSize * fontRatio,
        lineHeight = LocalTextStyle.current.lineHeight * fontRatio,
        fontFamily = chatFontFamily
    )
    var showActionsSheet by remember { mutableStateOf(false) }
    var showSelectCopySheet by remember { mutableStateOf(false) }
    val navController = LocalNavController.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    // 点击助手气泡：引用该消息。
                    // 分条开启时外层不得引用整条——气泡 Surface onClick 已引用单条，
                    // 两层同时触发时单条引用会被整条覆盖（用户实测引用总是整条的根因）。
                    // 分条关闭时保留原行为（点击任意位置引用整条）。
                    val splitOn = splitRefAssistant.splitMessages == true
                    if (message.role == MessageRole.ASSISTANT && !loading && !splitOn) {
                        onQuoteMessage(message)
                    }
                },
                onLongClick = {},
            ),
        horizontalAlignment = if (message.role == MessageRole.USER) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 分条气泡：模型图标/名称只在组内首条显示
        // 该助手的"显示模型名称与用户头像"开关关闭时, 整个头部信息行(模型名称/图标、用户名称/头像)都不显示
        val showHeaderRow = (message.role == MessageRole.USER || showModelInfo) &&
            (assistant?.showHeaderInfo ?: true)
        if (!message.parts.isEmptyUIMessage() && showHeaderRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                ChatMessageAssistantAvatar(
                    message = message,
                    model = model,
                    assistant = assistant,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                ChatMessageUserAvatar(
                    message = message,
                    avatar = assistant?.userAvatar ?: settings.userAvatar,
                    nickname = assistant?.userNickname ?: settings.userNickname,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ProvideTextStyle(textStyle) {
            MessagePartsBlock(
                assistant = assistant,
                role = message.role,
                parts = message.parts,
                annotations = message.annotations,
                loading = loading,
                model = model,
                hazeState = hazeState,
                blurAllowed = blurAllowed,
                onToolApproval = onToolApproval,
                onToolAnswer = onToolAnswer,
                onUserMessageClick = if (message.role == MessageRole.USER) onEdit else null,
                // 分条引用：只把当前气泡的 part 交给引用回调
                onQuotePart = { p -> onQuoteMessage(message.copy(parts = listOf(p))) },
            )

            message.translation?.let { translation ->
                CollapsibleTranslationText(
                    content = translation,
                    onClickCitation = {}
                )
            }
        }

        val showActions = when {
            lastMessage -> !loading
            !showActionsRow -> false // 分条中间气泡不显示操作按钮
            else -> message.parts.isEmptyUIMessage().not()
        }

        AnimatedVisibility(
            visible = showActions,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut()
        ) {
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                ChatMessageActionButtons(
                    message = message,
                    onRegenerate = onRegenerate,
                    node = node,
                    showDateTime = showDateTime,
                    onUpdate = onUpdate,
                    onOpenActionSheet = {
                        showActionsSheet = true
                    },
                    onTranslate = onTranslate,
                    onClearTranslation = onClearTranslation
                )
            }
        }

        EditedFilesList(
            parts = message.parts,
            assistant = assistant,
        )

        ProvideTextStyle(textStyle) {
            ChatMessageNerdLine(
                message = message,
                model = model,
                modelProviderName = modelProviderName,
                showModelInfo = assistant?.showFooterModelInfo ?: true,
            )
        }

    }
    if (showActionsSheet) {
        ChatMessageActionsSheet(
            message = message,
            onEdit = onEdit,
            onDelete = onDelete,
            onShare = onShare,
            onFork = onFork,
            model = model,
            onSelectAndCopy = {
                showSelectCopySheet = true
            },
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onWebViewPreview = {
                val textContent = message.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()
                if (textContent.isNotBlank()) {
                    val htmlContent = buildMarkdownPreviewHtml(
                        context = context,
                        markdown = textContent,
                        colorScheme = colorScheme
                    )
                    val contentId = WebViewContentCache.store(context.cacheDir, htmlContent)
                    navController.navigate(Screen.WebView(contentId = contentId))
                }
            },
            onDismissRequest = {
                showActionsSheet = false
            }
        )
    }

    if (showSelectCopySheet) {
        ChatMessageCopySheet(
            message = message,
            onDismissRequest = {
                showSelectCopySheet = false
            }
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MessagePartsBlock(
    assistant: Assistant?,
    role: MessageRole,
    model: Model?,
    parts: List<UIMessagePart>,
    annotations: List<UIMessageAnnotation>,
    loading: Boolean,
    hazeState: HazeState? = null,
    blurAllowed: Boolean = true, // 模糊降级守卫:消息过多时由调用方传 false,玻璃/磨砂退化为无模糊透色
    onToolApproval: ((toolCallId: String, approved: Boolean, reason: String) -> Unit)? = null,
    onToolAnswer: ((toolCallId: String, answer: String) -> Unit)? = null,
    onUserMessageClick: (() -> Unit)? = null,
    onQuotePart: ((UIMessagePart) -> Unit)? = null,
) {
    val context = LocalContext.current
    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)

    // 消息输出HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current
    val settings = LocalSettings.current
    val partsState by rememberUpdatedState(parts)

    val handleClickCitation: (String) -> Unit = remember {
        handler@{ citationId ->
            partsState.forEach { part ->
                if (part is UIMessagePart.Tool && part.toolName == "search_web" && part.isExecuted) {
                    val outputText = part.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    val items =
                        runCatching { JsonInstant.parseToJsonElement(outputText).jsonObject["items"]?.jsonArray }.getOrNull()
                            ?: return@forEach
                    items.forEach { item ->
                        val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                        val url = item.jsonObject["url"]?.jsonPrimitive?.content ?: return@forEach
                        if (citationId == id) {
                            context.openUrl(url)
                            return@handler
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(settings.displaySetting) {
        snapshotFlow { partsState }
            .debounce(50.milliseconds)
            .collect { parts ->
                if (parts.isNotEmpty() && loading && settings.displaySetting.enableMessageGenerationHapticEffect) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                }
            }
    }

    // Render parts in original order (group thinking/tool as chain-of-thought)
    // 显示层分条：数据只存一条消息，渲染时按 <split/> 标记拆成多条文本部件（各成气泡）。
    // 分条关闭时仅清理标记（模型偶发输出的标记不应原样显示）
    val displayParts = when {
        role == MessageRole.ASSISTANT && assistant?.splitMessages == true -> {
            parts.flatMap { p ->
                if (p is UIMessagePart.Text) {
                    splitDisplaySegments(p.text, assistant.splitMode == "force")
                        .map { seg -> UIMessagePart.Text(seg) }
                } else {
                    listOf(p)
                }
            }
        }
        role == MessageRole.ASSISTANT -> {
            parts.map { p ->
                if (p is UIMessagePart.Text) UIMessagePart.Text(stripSplitMarkers(p.text)) else p
            }
        }
        else -> parts
    }
    val groupedParts = remember(displayParts) { displayParts.groupMessageParts() }
    // 玻璃样式计算提升至此：ThinkingBlock(思考/工具卡)与 ContentBlock(文本气泡)共用
    // 气泡形状：助手独立设置优先，空 = 跟随全局
    val mpShapeName = assistant?.bubbleShape?.lowercase()?.takeIf { it.isNotEmpty() }
        ?: settings.displaySetting.bubbleShape.name.lowercase()
    val mpBubbleShape = when (mpShapeName) {
        "wechat" -> RoundedCornerShape(4.dp)
        "pill" -> RoundedCornerShape(24.dp)
        // 贴角圆角:大圆角为主,靠头像一角收小(用户消息右上收小,助手消息左上收小)
        "large" -> if (role == MessageRole.USER) RoundedCornerShape(22.dp, 5.dp, 22.dp, 22.dp)
                   else RoundedCornerShape(5.dp, 22.dp, 22.dp, 22.dp)
        else -> RoundedCornerShape(16.dp)
    }
    val mpIsGlass = settings.displaySetting.enableBlurEffect &&
        settings.displaySetting.backgroundEffectType == BackgroundEffectType.GLASS &&
        hazeState != null
    val mpBubbleOverride = assistant?.takeIf { it.bubbleOverride }
    val mpMaterial = mpBubbleOverride?.bubbleMaterial ?: ""
    val mpEffGlassStyle = when (mpMaterial) {
        "glass" -> "glass"      // 玻璃
        "droplet" -> "liquid"   // 水珠
        "frost" -> "frost"      // 磨砂:底色填充+背景模糊
        else -> when {
            mpBubbleOverride?.bubbleGlassStyle != null -> mpBubbleOverride.bubbleGlassStyle
            mpIsGlass -> "frost"
            else -> "none"
        }
    }
    // 模糊降级守卫:消息较多时自动去掉模糊(保留透色填充),避免长列表滚动掉帧
    val mpUseHaze = mpEffGlassStyle != "none" && hazeState != null && blurAllowed
    groupedParts.fastForEach { block ->
        when (block) {
            is MessagePartBlock.ThinkingBlock -> {
                if (block.steps.isNotEmpty()) {
                    val isReasoningOnlyBlock = block.steps.fastAll { it is ThinkingStep.ReasoningStep }
                    ChainOfThought(
                        modifier = Modifier
                            .animateContentSize()
                            .then(
                                if (settings.displaySetting.thinkingCardBorder) Modifier.border(
                                    1.4.dp,
                                    Brush.sweepGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.75f),
                                            Color.White.copy(alpha = 0.15f),
                                            Color.White.copy(alpha = 0.6f),
                                            Color.White.copy(alpha = 0.2f),
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                        steps = block.steps,
                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                        cardColors = CardDefaults.cardColors(
                            // 与气泡协调：透明底 + 扫掠描边（不再是实体卡）
                            containerColor = Color.Transparent,
                        ),
                    ) { step ->
                        when (step) {
                            is ThinkingStep.ReasoningStep -> {
                                key(step.reasoning.createdAt) {
                                    ChatMessageReasoningStep(
                                        reasoning = step.reasoning,
                                        model = model,
                                        assistant = assistant,
                                        collapsedAdaptiveWidth = isReasoningOnlyBlock,
                                    )
                                }
                            }

                            is ThinkingStep.ToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageToolStep(
                                        tool = step.tool,
                                        loading = loading && !step.tool.isExecuted,
                                        onToolApproval = onToolApproval,
                                        onToolAnswer = onToolAnswer,
                                    )
                                }
                            }

                            is ThinkingStep.ServerToolStep -> {
                                key(step.tool.toolCallId.ifBlank { step.hashCode().toString() }) {
                                    ChatMessageServerToolStep(tool = step.tool)
                                }
                            }
                        }
                    }
                }
            }

            is MessagePartBlock.ChartBlock -> key(block.index) {
                val spec = remember(block.tool.input) { ChartSpec.fromJson(block.tool.inputAsJson()) }
                spec?.let { ChartCard(spec = it) }
            }

            is MessagePartBlock.StickerBlock -> key(block.index) {
                val stickerUrl = block.tool.stickerUrlFromOutput()
                if (stickerUrl != null) {
                    ZoomableAsyncImage(
                        model = stickerUrl,
                        contentDescription = "sticker",
                        modifier = Modifier.height(140.dp)
                    )
                }
            }

            is MessagePartBlock.ContentBlock -> key(block.index) {
                when (val part = block.part) {
                    is UIMessagePart.Text -> {
                        // 分条引用：点击气泡即引用这一条（回调由 ChatMessage 注入）
                        // 气泡形状已在上方统一解析(助手优先,空=跟随全局)
                        val bubbleShape = mpBubbleShape
                        // 美化描边用的圆角半径(逐角与形状一致;贴角圆角为不对称角)
                        val bubbleCornerRadii = when (mpShapeName) {
                            "wechat" -> listOf(4.dp, 4.dp, 4.dp, 4.dp)
                            "pill" -> listOf(24.dp, 24.dp, 24.dp, 24.dp)
                            "large" -> if (role == MessageRole.USER) listOf(22.dp, 5.dp, 22.dp, 22.dp)
                                       else listOf(5.dp, 22.dp, 22.dp, 22.dp)
                            else -> listOf(16.dp, 16.dp, 16.dp, 16.dp)
                        }
                        // 每助手气泡样式覆盖（颜色/材质/清透度/不透明度/磨砂程度/边框美化）
                        val bubbleOverride = assistant?.takeIf { it.bubbleOverride }
                        val effGlassStyle = mpEffGlassStyle
                        val effGlass = mpUseHaze
                        @Composable
                        fun buildGlassStyle(style: String): GlassStyle {
                            // 统一走 GlassDefaults.optics.copy 稳写法(与输入框一致)；磨砂模糊半径随磨砂程度(4~26dp)
                            // 三材质用 tint 拉开肉眼区分：磨砂=白雾化、玻璃=近无染色、水珠=亮面高光
                            val frostLevel = bubbleOverride?.bubbleFrostLevel ?: 0.6f
                            val (blurDp, depthV, tintV) = when (style) {
                                "frost" -> Triple((4 + 22 * frostLevel).dp, 0.4f, Color.White.copy(alpha = 0.10f))   // 磨砂
                                "frosted" -> Triple(22.dp, 0.5f, Color.White.copy(alpha = 0.08f))  // 毛玻璃(旧)
                                "glass" -> Triple(8.dp, 0.3f, Color.White.copy(alpha = 0.04f))     // 玻璃
                                "liquid" -> Triple(12.dp, 0.6f, Color.White.copy(alpha = 0.14f))   // 水珠
                                else -> Triple(16.dp, 0.4f, Color.Transparent)
                            }
                            return GlassStyle.Material3(
                                containerColor = Color.Transparent,
                                tint = tintV,
                            ) {
                                optics(GlassDefaults.optics.copy(
                                    blurRadius = OpticalSizeValue.Fixed(blurDp),
                                    depth = OpticalSizeValue.Fixed(depthV),
                                ))
                                shape(bubbleShape)
                            }
                        }
                        val glassModifier = if (effGlass && hazeState != null) {
                            Modifier.hazeGlass(
                                input = HazeInput.Sources(hazeState),
                                style = buildGlassStyle(effGlassStyle),
                            )
                        } else Modifier
                        val userColorOverride = bubbleOverride?.bubbleUserColor?.let { Color(it) }
                        val assistantColorOverride = bubbleOverride?.bubbleAssistantColor?.let { Color(it) }
                        val overrideAlpha = bubbleOverride?.bubbleAlpha
                        // 边框美化：分侧配置(助手/用户/两侧同时)，总开关在助手页
                        val beautifyCfg = bubbleOverride?.takeIf { it.bubbleBeautifyEnabled }
                            ?.let { override ->
                                val scopeOk = when (override.bubbleBeautifyScope) {
                                    "assistant" -> role == MessageRole.ASSISTANT
                                    "user" -> role == MessageRole.USER
                                    else -> true
                                }
                                if (scopeOk) {
                                    if (role == MessageRole.USER) override.bubbleBeautifyUser
                                    else override.bubbleBeautifyAssistant
                                } else null
                            }
                        @Composable
                        fun effectiveBubbleColor(base: Color): Color {
                            val mat = bubbleOverride?.bubbleMaterial ?: ""
                            if (mat == "droplet") {
                                // 水珠：清透微亮珠体，立体感由折射边+rim光线提供
                                return Color.White.copy(alpha = 0.08f)
                            }
                            val baseColor = if (role == MessageRole.USER) {
                                userColorOverride ?: base
                            } else {
                                assistantColorOverride ?: base
                            }
                            return when (mat) {
                                // 磨砂：按所选颜色填充再模糊，填充浓度随磨砂程度略增
                                "frost" -> baseColor.copy(
                                    alpha = (0.40f + 0.38f * (bubbleOverride?.bubbleFrostLevel ?: 0.6f)).coerceAtMost(0.95f)
                                )
                                // 玻璃：清透度(仅玻璃生效)控制染色浓度
                                "glass" -> baseColor.copy(
                                    alpha = (0.06f + 0.64f * (overrideAlpha ?: 0.9f)).coerceIn(0.08f, 0.85f)
                                )
                                else -> {
                                    // 默认材质：不透明度(每助手独立,null=跟随全局)
                                    val opa = bubbleOverride?.bubbleOpacity ?: settings.displaySetting.bubbleOpacity
                                    val alpha = (overrideAlpha ?: 1f) * opa
                                    if (effGlassStyle != "none") {
                                        baseColor.copy(alpha = (alpha * 0.3f).coerceAtLeast(0.12f))
                                    } else {
                                        baseColor.copy(alpha = alpha)
                                    }
                                }
                            }
                        }
                        // 每助手自定义气泡宽度(0 = 官方默认铺满)
                        val customBubbleMaxWidth = assistant?.bubbleMaxWidth?.takeIf { it > 0 }?.dp
                        // 美化描边优先(画在内容之上,支持半包)；未开美化时玻璃材质保留扫掠亮边
                        val sweepBorderModifier = if (beautifyCfg == null && effGlass) Modifier.border(
                            1.4.dp,
                            Brush.sweepGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.75f),
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.2f),
                                )
                            ),
                            bubbleShape
                        ) else Modifier
                        val beautifyBorderModifier = beautifyCfg?.let {
                            Modifier.beautifyOverlay(it, bubbleCornerRadii, MaterialTheme.colorScheme.outline)
                        } ?: Modifier
                        // 降级静态假磨砂：无模糊(haze不可用/超120条/API<12)时,磨砂材质用渐变填充保持"雾面"质感,
                        // 与默认纯色填充拉开肉眼区分度
                        val frostNoBlur = (bubbleOverride?.bubbleMaterial == "frost") && !effGlass
                        val frostFallbackModifier = if (frostNoBlur) {
                            val frostBase = if (role == MessageRole.USER) {
                                userColorOverride ?: MaterialTheme.colorScheme.primaryContainer
                            } else {
                                assistantColorOverride ?: MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(
                                        frostBase.copy(alpha = 0.86f),
                                        frostBase.copy(alpha = 0.58f),
                                        frostBase.copy(alpha = 0.74f),
                                    )
                                ),
                                bubbleShape,
                            )
                        } else Modifier
                        val textContent = @Composable {
                            if (role == MessageRole.USER) {
                                Box {
                                    Surface(
                                        modifier = Modifier
                                            .let { if (customBubbleMaxWidth != null) it.widthIn(max = customBubbleMaxWidth) else it }
                                            .animateContentSize()
                                            .then(frostFallbackModifier)
                                            .then(glassModifier)
                                            .then(sweepBorderModifier)
                                            .then(beautifyBorderModifier),
                                        shape = bubbleShape,
                                        color = if (frostNoBlur) Color.Transparent
                                                else effectiveBubbleColor(MaterialTheme.colorScheme.primaryContainer),
                                        onClick = { onUserMessageClick?.invoke() },
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            MarkdownBlock(
                                                content = part.text.replaceRegexes(
                                                    assistant = assistant,
                                                    scope = AssistantAffectScope.USER,
                                                    visual = true,
                                                ),
                                                onClickCitation = handleClickCitation,
                                                quoteHighlightColor = quoteHighlightColorFor(assistant),
                                            )
                                        }
                                    }
                                    beautifyCfg?.takeIf { it.sticker.isNotBlank() }?.let { BubbleSticker(it) }
                                }
                            } else {
                                if (settings.displaySetting.showAssistantBubble) {
                                    Box {
                                        Surface(
                                            onClick = { onQuotePart?.invoke(part) },
                                            modifier = Modifier
                                                .let { if (customBubbleMaxWidth != null) it.widthIn(max = customBubbleMaxWidth) else it }
                                                .animateContentSize()
                                                .then(frostFallbackModifier)
                                                .then(glassModifier)
                                                .then(sweepBorderModifier)
                                                .then(beautifyBorderModifier),
                                            shape = bubbleShape,
                                            color = if (frostNoBlur) Color.Transparent
                                                    else effectiveBubbleColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                MarkdownBlock(
                                                    content = prettifyWakeTag(
                                                        part.text.replaceRegexes(
                                                            assistant = assistant,
                                                            scope = AssistantAffectScope.ASSISTANT,
                                                            visual = true,
                                                        )
                                                    ),
                                                    onClickCitation = handleClickCitation,
                                                    quoteHighlightColor = quoteHighlightColorFor(assistant),
                                                )
                                            }
                                        }
                                        beautifyCfg?.takeIf { it.sticker.isNotBlank() }?.let { BubbleSticker(it) }
                                    }
                                } else {
                                    MarkdownBlock(
                                        content = prettifyWakeTag(
                                            part.text.replaceRegexes(
                                                assistant = assistant,
                                                scope = AssistantAffectScope.ASSISTANT,
                                                visual = true,
                                            )
                                        ),
                                        onClickCitation = handleClickCitation,
                                        quoteHighlightColor = quoteHighlightColorFor(assistant),
                                        modifier = Modifier
                                            .animateContentSize()
                                            .clickable { onQuotePart?.invoke(part) }
                                    )
                                }
                            }
                        }

                        // 流式生成期间不启用 SelectionContainer：Markdown 在不断重渲染，
                        // 内部可选择的 Text 会频繁注册/注销，与 Compose 选择工具栏在绘制阶段
                        // 对 selectable 列表的排序产生并发修改，导致 ConcurrentModificationException。
                        // 生成结束后内容稳定，再启用文本选择。
                        if (loading) {
                            textContent()
                        } else {
                            SelectionContainer {
                                textContent()
                            }
                        }
                    }

                    is UIMessagePart.Video -> {
                        Surface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Icon(HugeIcons.Video01, null)
                            }
                        }
                    }

                    is UIMessagePart.Audio -> {
                        Surface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = HugeIcons.MusicNote03,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    is UIMessagePart.Image -> {
                        val isImageLoading =
                            part.url.isBlank() || part.url.matches(Regex("^data:image/[^;]*;base64,\\s*$"))
                        // 贴纸标记的图片以大尺寸展示, 不带圆角气泡感
                        val isSticker = part.metadata?.get("sticker")
                            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.contentOrNull == "true"
                        if (isImageLoading) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .shimmer(isLoading = true)
                            )
                        } else if (isSticker) {
                            ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = "sticker",
                                modifier = Modifier.height(140.dp)
                            )
                        } else {
                            ZoomableAsyncImage(
                                model = part.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.medium)
                                    .height(72.dp)
                            )
                        }
                    }

                    is UIMessagePart.Document -> {
                        Surface(
                            tonalElevation = 2.dp,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                intent.data = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    part.url.toUri().toFile()
                                )
                                val chooserIndent = Intent.createChooser(intent, null)
                                context.startActivity(chooserIndent)
                            },
                            modifier = Modifier,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when (part.mime) {
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                            Icon(
                                                painter = painterResource(R.drawable.docx),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        "application/pdf" -> {
                                            Icon(
                                                painter = painterResource(R.drawable.pdf),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        else -> {
                                            Icon(
                                                imageVector = HugeIcons.File02,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = part.fileName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // Skip unknown part types (e.g., deprecated ToolCall, ToolResult, Search)
                    }
                }
            }
        }
    }

    // Annotations (always rendered at the end)
    if (annotations.isNotEmpty()) {
        Column(
            modifier = Modifier.animateContentSize(),
        ) {
            var expand by remember { mutableStateOf(false) }
            if (expand) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.extendColors.gray8.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .drawWithContent {
                                drawContent()
                                drawRoundRect(
                                    color = contentColor.copy(alpha = 0.2f),
                                    size = Size(width = 10f, height = size.height),
                                )
                            }
                            .padding(start = 16.dp)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        annotations.fastForEachIndexed { index, annotation ->
                            when (annotation) {
                                is UIMessageAnnotation.UrlCitation -> {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Favicon(annotation.url, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = buildAnnotatedString {
                                                append("${index + 1}. ")
                                                withLink(LinkAnnotation.Url(annotation.url)) {
                                                    append(annotation.title.urlDecode())
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    expand = !expand
                }
            ) {
                Text(stringResource(R.string.citations_count, annotations.size))
            }
        }
    }
}


/**
 * 引号高亮颜色解析：把助手的配置值映射为 Compose Color。
 * theme → 跟随主题第三色（默认）；red/yellow/blue/green → 固定柔和色。
 * 开关关闭返回 null（渲染层跳过处理）。
 *
 * 注意：对话可能未绑定助手（getAssistantById 返回 null），此处回退到当前助手，
 * 否则开关打开也不会生效。
 */
@Composable
private fun quoteHighlightColorFor(assistant: Assistant?): androidx.compose.ui.graphics.Color? {
    val settings = LocalSettings.current
    val effective = assistant ?: settings.getCurrentAssistant()
    if (!effective.quoteHighlight) return null
    val scheme = MaterialTheme.colorScheme
    return when (effective.quoteHighlightColor) {
        "red" -> androidx.compose.ui.graphics.Color(0xFFE57373)
        "yellow" -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
        "blue" -> androidx.compose.ui.graphics.Color(0xFF64B5F6)
        "green" -> androidx.compose.ui.graphics.Color(0xFF81C784)
        "primary" -> scheme.primary
        "secondary" -> scheme.secondary
        else -> scheme.tertiary
    }
}

// ---- 气泡边框美化辅助 ----

/** 解析 #RRGGBB/#AARRGGBB 颜色,失败返回 null */
private fun parseBubbleHexColor(s: String): Color? {
    if (s.isBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(s.trim())) }.getOrNull()
}

/** 三边开口的描边路径(openTop=true 缺顶边,否则缺底边;两侧带对侧圆角) */
private fun bubbleOpenEdgePath(
    size: androidx.compose.ui.geometry.Size,
    inset: Float,
    radiiPx: List<Float>, // topStart, topEnd, bottomEnd, bottomStart(与 RoundedCornerShape 参数序一致)
    openTop: Boolean,
): androidx.compose.ui.graphics.Path =
    androidx.compose.ui.graphics.Path().apply {
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        if (openTop) {
            // 顶边留白：描左/下/右三边，走底部两角(半径取各自角的值)
            val bl = radiiPx[3]
            val br = radiiPx[2]
            moveTo(left, top)
            lineTo(left, bottom - bl)
            arcTo(androidx.compose.ui.geometry.Rect(left, bottom - 2 * bl, left + 2 * bl, bottom), 180f, -90f, false)
            lineTo(right - br, bottom)
            arcTo(androidx.compose.ui.geometry.Rect(right - 2 * br, bottom - 2 * br, right, bottom), 90f, -90f, false)
            lineTo(right, top)
        } else {
            // 底边留白：描左/上/右三边，走顶部两角
            val tl = radiiPx[0]
            val tr = radiiPx[1]
            moveTo(left, bottom)
            lineTo(left, top + tl)
            arcTo(androidx.compose.ui.geometry.Rect(left, top, left + 2 * tl, top + 2 * tl), 180f, 90f, false)
            lineTo(right - tr, top)
            arcTo(androidx.compose.ui.geometry.Rect(right - 2 * tr, top, right, top + 2 * tr), 270f, 90f, false)
            lineTo(right, bottom)
        }
    }

/**
 * 边框颜色深浅：0.5=原色，<0.5 向白变浅，>0.5 向黑加深。
 */
private fun shadeBubbleColor(c: Color, shade: Float): Color {
    val s = shade.coerceIn(0f, 1f)
    return when {
        s < 0.5f -> androidx.compose.ui.graphics.lerp(c, Color.White, (0.5f - s) * 2f)
        s > 0.5f -> androidx.compose.ui.graphics.lerp(c, Color.Black, (s - 0.5f) * 2f)
        else -> c
    }
}

/**
 * 美化描边：画在气泡内容之上(避免被 Surface 填充盖住一半),支持全包/半包。
 * 描边内缩半个线宽(外缘恰好贴齐气泡边界)；逐角半径与形状严格一致(贴角圆角不对称角也对齐)。
 * 只用稳定 DrawScope API(drawPath/arcTo/RoundRect),兼容新 BOM(ClipMode/drawOutline 已移除)。
 */
private fun Modifier.beautifyOverlay(
    cfg: BubbleBeautify,
    cornerRadii: List<androidx.compose.ui.unit.Dp>,
    themeColor: Color,
): Modifier = this.drawWithContent {
    drawContent()
    val base = parseBubbleHexColor(cfg.borderColor) ?: themeColor
    val color = shadeBubbleColor(base, cfg.borderColorShade)
    val stroke = cfg.borderWidth.dp.toPx().coerceAtLeast(0.5f)
    val inset = stroke / 2f
    val maxR = (size.minDimension / 2f - inset).coerceAtLeast(0f)
    val radiiPx = cornerRadii.map { it.toPx().coerceIn(0f, maxR) }
    when (cfg.borderStyle) {
        "half-top" -> drawPath(
            bubbleOpenEdgePath(size, inset, radiiPx, openTop = true),
            color,
            style = Stroke(width = stroke),
        )
        "half-bottom" -> drawPath(
            bubbleOpenEdgePath(size, inset, radiiPx, openTop = false),
            color,
            style = Stroke(width = stroke),
        )
        else -> drawPath(
            androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(
                            inset, inset, size.width - inset, size.height - inset
                        ),
                        topLeft = androidx.compose.ui.geometry.CornerRadius(radiiPx[0]),
                        topRight = androidx.compose.ui.geometry.CornerRadius(radiiPx[1]),
                        bottomRight = androidx.compose.ui.geometry.CornerRadius(radiiPx[2]),
                        bottomLeft = androidx.compose.ui.geometry.CornerRadius(radiiPx[3]),
                    )
                )
            },
            color,
            style = Stroke(width = stroke),
        )
    }
}

/**
 * 气泡贴纸：骑在框线角上(emoji 趴在框线上,颜文字贴线上沿)。
 * 放在 Surface 外层的 Box 里,避免被气泡 shape 裁切。
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BubbleSticker(cfg: BubbleBeautify) {
    val themeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color = parseBubbleHexColor(cfg.borderColor) ?: themeColor
    // emoji 类(含 0x1F000+/⭐/❄ 等)用大字号"趴"在框线上；颜文字/符号用小字号贴线上沿
    val isEmoji = cfg.sticker.any {
        it.code >= 0x1F000 || it.code == 0x2B50 || it.code == 0x2728 || it.code in 0x2744..0x2749
    }
    val align = when (cfg.position) {
        "tl" -> Alignment.TopStart
        "bl" -> Alignment.BottomStart
        "br" -> Alignment.BottomEnd
        else -> Alignment.TopEnd
    }
    val dx = if (cfg.position.startsWith("t") || cfg.position.startsWith("b")) {
        if (cfg.position.endsWith("l")) 8.dp else (-8).dp
    } else 8.dp
    val dy = when {
        cfg.position.startsWith("t") -> if (isEmoji) (-15).dp else (-12).dp
        else -> if (isEmoji) 13.dp else 10.dp
    }
    Text(
        text = cfg.sticker,
        fontSize = if (isEmoji) 20.sp else 11.sp,
        fontWeight = if (isEmoji) FontWeight.Normal else FontWeight.Bold,
        color = color,
        modifier = Modifier
            .align(align)
            .graphicsLayer {
                // 贴纸倾斜角度：绕自身中心旋转，让趴框不呆板
                rotationZ = cfg.stickerAngle
            }
            .offset(x = dx, y = dy),
    )
}
