package me.rerere.rikkahub.ui.pages.read

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.MagicWand01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.db.dao.SocialInteractionDAO
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.readtogether.ChapterMeta
import me.rerere.rikkahub.data.readtogether.ReadTogetherRepository
import me.rerere.rikkahub.data.readtogether.SkillProposal
import me.rerere.rikkahub.service.ReadTogetherStudyService
import me.rerere.rikkahub.service.SocialNotifyController
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.social.socialAuthorName
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.discover.DiscoverCard
import me.rerere.rikkahub.ui.pages.discover.DiscoverTheme
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.SocialScrollHeader
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

/**
 * 共同阅读(发现页风格,复用 7 套主题):
 * 书架(导入 txt/epub/pdf) → 阅读器。
 * - 点段落 = 单句选择面板:点句子 → 已标注则显示标注详情,未标注则划线/写评论
 * - 长按段落 = 整段标注
 * - 已标注句子正文渲染下划线,点开即见标注
 * - 用户写标注 → 后台提醒助手(SocialNotifyController);AI 标注 → 入口红点
 * - 标注署名复用当前助手名与用户昵称(socialAuthorName 同口径)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadTogetherPage() {
    val theme = rememberDiscoverTheme()
    val repo = koinInject<ReadTogetherRepository>()
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val socialNotify = koinInject<SocialNotifyController>()
    val socialDao = koinInject<SocialInteractionDAO>()

    var books by remember { mutableStateOf<List<ReadTogetherRepository.BookMeta>>(emptyList()) }
    var openBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        books = repo.listBooks().sortedByDescending { it.lastReadAt + it.addedAt }
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(openBookId) { if (openBookId != null) reload() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                importError = null
                runCatching { repo.import(uri) }
                    .onSuccess {
                        reload()
                        openBookId = it.id
                    }
                    .onFailure { importError = "导入失败:${it.message}" }
                importing = false
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .discoverPageBackground(theme),
        containerColor = Color.Transparent,
    ) { contentPadding ->
        val openBook = openBookId?.let { id -> books.firstOrNull { it.id == id } }
        if (openBook == null) {
            // ---------- 书架 ----------
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 顶栏头随内容滚走(r278验收);阅读器有自带顶行不受影响
                item(key = "header") {
                    SocialScrollHeader(
                        title = "共同阅读",
                        theme = theme,
                        navigationIcon = { BackButton() },
                        actions = { DiscoverThemeMenuButton() },
                    )
                }
                item(key = "import") {
                    DiscoverCard(theme, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "一起读一本书",
                                style = MaterialTheme.typography.titleSmall,
                                color = theme.text,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "导入 txt / epub / pdf 自动解析成页;你和 TA 共享同一阅读进度,读到哪里 TA 就知道哪里;句子划线、单句评论,双方标注互见。",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.sub,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        importLauncher.launch(
                                            arrayOf("application/pdf", "application/epub+zip", "text/plain", "application/octet-stream")
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.btnBg,
                                        contentColor = theme.btnFg,
                                    ),
                                ) {
                                    Icon(HugeIcons.FileImport, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (importing) "解析中…" else "导入书籍")
                                }
                            }
                            importError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                itemsIndexed(books, key = { _, b -> b.id }) { _, book ->
                    DiscoverCard(
                        theme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { openBookId = book.id },
                                onLongClick = { pendingDeleteId = book.id },
                            ),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.chipBg),
                            ) {
                                Text(
                                    text = book.format.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.name,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = theme.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        book.author.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                                        append("${book.format} · 共${book.totalPages}页")
                                        if (book.lastReadAt > 0) append(" · 读到第${book.currentPage}页")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.sub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                HugeIcons.BookOpen01,
                                contentDescription = null,
                                tint = theme.name,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (books.isEmpty()) {
                    item {
                        Text(
                            text = "书架还是空的,导入一本书开始共读吧",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.sub,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        } else {
            // ---------- 阅读器 ----------
            BookReader(
                theme = theme,
                book = openBook,
                repo = repo,
                assistant = assistant,
                socialNotify = socialNotify,
                socialDao = socialDao,
                contentPadding = contentPadding,
                onBackToShelf = { openBookId = null },
                onProgress = { reload() },
            )
        }
    }

    pendingDeleteId?.let { delId ->
        val delBook = books.firstOrNull { it.id == delId }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除《${delBook?.title ?: ""}》?") },
            text = { Text("将删除该书的正文缓存与全部标注,进度一并清除") },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteBook(delId)
                    pendingDeleteId = null
                    reload()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }
}

/** 待标注目标:kind = sentence / paragraph / custom(手选) */
private data class AnnotateTarget(
    val kind: String,
    val paraIdx: Int,
    val excerpt: String,
)

/**
 * 阅读背景五预设(r377):米黄纸/纯白/护眼绿/羊皮/夜间。
 * 阅读页整页(顶栏/正文/底栏)统一吃这套颜色,与主题系统解耦;抽屉/浮层同源。
 */
private data class ReadPalette(
    val key: String,
    val paper: Color,
    val solid: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val dot: Color,
) {
    val accentSoft: Color get() = accent.copy(alpha = 0.14f)
    val line: Color get() = text.copy(alpha = 0.22f)
}

private fun readPaletteFor(key: String): ReadPalette = when (key) {
    "white" -> ReadPalette("white", Color(0xFFFCFCFA), Color(0xFFFFFFFF), Color(0xFF2B2B2B), Color(0xFF9A9A94), Color(0xFF5E8A5E), Color(0x0D000000))
    "green" -> ReadPalette("green", Color(0xFFCCE8CF), Color(0xFFD6EEE0), Color(0xFF26382B), Color(0xFF6E8A74), Color(0xFF3F7A4A), Color(0x243C643C))
    "parch" -> ReadPalette("parch", Color(0xFFEFE3C8), Color(0xFFF5EBD3), Color(0xFF4A3B26), Color(0xFFA08D6A), Color(0xFF9A7B3F), Color(0x338C6E3C))
    "night" -> ReadPalette("night", Color(0xFF191816), Color(0xFF232220), Color(0xFFB5AFA0), Color(0xFF6E6A60), Color(0xFF7FA97F), Color(0x0DFFFFFF))
    else -> ReadPalette("paper", Color(0xFFF7F2E7), Color(0xFFFBF7EE), Color(0xFF3F3B33), Color(0xFFA69F8C), Color(0xFF6E9070), Color(0x38A09678))
}

/** 阅读字体:仿宋/楷体走打包字体,宋体/黑体走系统(r377) */
@Composable
private fun rememberReadFontFamily(fontKey: String): FontFamily = when (fontKey) {
    "fang" -> FontFamily(Font(R.font.fz_fangsong))
    "kai" -> FontFamily(Font(R.font.lxgw_wenkai))
    "hei" -> FontFamily.SansSerif
    else -> FontFamily.Serif
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookReader(
    theme: DiscoverTheme,
    book: ReadTogetherRepository.BookMeta,
    repo: ReadTogetherRepository,
    assistant: Assistant,
    socialNotify: SocialNotifyController,
    socialDao: SocialInteractionDAO,
    contentPadding: PaddingValues,
    onBackToShelf: () -> Unit,
    onProgress: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable(book.id) { mutableIntStateOf(book.currentPage.coerceIn(1, book.totalPages)) }
    var paragraphs by remember(page) { mutableStateOf(repo.getPageParagraphs(book.id, page)) }
    var showAnnotations by remember { mutableStateOf(false) }
    var annotateTarget by remember { mutableStateOf<AnnotateTarget?>(null) }
    var sentenceSheetFor by remember { mutableStateOf<Pair<Int, List<String>>?>(null) }
    var annDetailsFor by remember { mutableStateOf<List<ReadTogetherRepository.ReadAnnotation>?>(null) }
    var annDeleteTarget by remember { mutableStateOf<ReadTogetherRepository.ReadAnnotation?>(null) } // 长按删标注先确认(r278验收)
    var refreshTick by remember { mutableIntStateOf(0) } // 标注增删后刷新缓存

    // ---------- TA 学习 ----------
    val studyService = koinInject<ReadTogetherStudyService>()
    val skillManager = koinInject<SkillManager>()
    val settingsStore = koinInject<SettingsStore>()
    val studyState by studyService.state.collectAsState()

    // ---------- 阅读偏好(字体/背景,全局 DisplaySetting)与目录抽屉/手选状态 ----------
    val settings = LocalSettings.current
    val readPalette = readPaletteFor(settings.displaySetting.readBg)
    val readFont = rememberReadFontFamily(settings.displaySetting.readFont)
    var showReadSettings by remember { mutableStateOf(false) }
    var tocOpen by remember { mutableStateOf(false) }
    var selPara by remember(page) { mutableStateOf<Int?>(null) }
    var chapters by remember(book.id) { mutableStateOf<List<ChapterMeta>>(emptyList()) }
    var showStudySheet by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showBookCostDialog by remember { mutableStateOf(false) }
    var skillConfirm by remember { mutableStateOf<SkillProposal?>(null) }
    var noteRefresh by remember { mutableIntStateOf(0) }
    var noteError by remember { mutableStateOf<String?>(null) }

    val chapter = remember(chapters, page) {
        chapters.firstOrNull { page in it.startPage..it.endPage }
    }
    // 旧书首次打开:章节从已存页文本重建(IO,别卡首帧)
    LaunchedEffect(book.id) {
        chapters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.ensureChapters(book.id)
        }
    }
    // 学习产物落库后刷新笔记列表
    LaunchedEffect(studyState.epoch) {
        if (studyState.epoch > 0) noteRefresh++
    }
    // 自动学习:翻到新章节且该章没有摘要时,后台用快速模型读一遍(默认关)
    LaunchedEffect(chapter?.index, assistant.readTogetherAutoStudy, studyState.running) {
        val ch = chapter ?: return@LaunchedEffect
        if (!assistant.readTogetherAutoStudy) return@LaunchedEffect
        if (!assistant.localTools.contains(LocalToolOption.ReadTogether)) return@LaunchedEffect
        if (studyState.running) return@LaunchedEffect
        // 是否已有最新摘要由服务在 IO 线程判断;这里只在进入新章节时发起一次
        studyService.ensureAutoStudy(book.id, ch.index, assistant.id)
    }

    // 本页标注(含双方)
    val pageAnnos = remember(page, refreshTick) {
        repo.listAnnotations(book.id).filter { it.page == page }
    }

    // 打开即同步"人已读到"并刷新书架进度
    LaunchedEffect(book.id) {
        repo.setProgress(book.id, page)
        onProgress()
    }

    fun turnTo(newPage: Int) {
        val p = newPage.coerceIn(1, book.totalPages)
        if (p != page) {
            page = p
            repo.setProgress(book.id, p)
            onProgress()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readPalette.paper)
            .drawBehind {
                // 纸面点纹(与阅读背景预设同源)
                val step = 22.dp.toPx()
                val r = 1.1.dp.toPx()
                var y = 0f
                while (y <= size.height) {
                    var x = 0f
                    while (x <= size.width) {
                        drawCircle(readPalette.dot, r, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp),
        ) {
        // 顶行:返回书架 / 书名+页码 / 阅读设置+标注+让TA读
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToShelf) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = "返回书架", tint = readPalette.text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = readPalette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        chapter?.let { append("第 ${it.index + 1} 章 ${it.title} · ") }
                        append("第 $page / ${book.totalPages} 页 · 进度人机同步")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = readPalette.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Aa",
                style = MaterialTheme.typography.titleSmall,
                color = readPalette.text,
                modifier = Modifier
                    .clickable { showReadSettings = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            IconButton(onClick = { noteError = null; showNotes = true }) {
                Icon(HugeIcons.Bookshelf01, contentDescription = "TA 的笔记", tint = readPalette.text)
            }
            IconButton(onClick = { showAnnotations = true }) {
                Icon(HugeIcons.PencilEdit01, contentDescription = "标注列表", tint = readPalette.text)
            }
            IconButton(onClick = { showStudySheet = true }) {
                Icon(HugeIcons.MagicWand01, contentDescription = "让 TA 读", tint = readPalette.text)
            }
        }
        Spacer(Modifier.height(4.dp))
        // 正文:点段=选句面板;长按段=整段标注;已划线句子渲染下划线
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(paragraphs) { idx, para ->
                ParagraphText(
                    para = para,
                    paraIdx = idx,
                    annotations = pageAnnos,
                    theme = theme,
                    palette = readPalette,
                    font = readFont,
                    selActive = selPara == idx,
                    onTap = {
                        selPara = null
                        sentenceSheetFor = idx to repo.splitSentences(para)
                    },
                    onSelectStart = { selPara = idx },
                    onClearSelection = { selPara = null },
                    onMarkCustom = { s, e ->
                        repo.addAnnotation(
                            bookId = book.id,
                            page = page,
                            paragraphIndex = idx,
                            excerpt = para.substring(s, e + 1),
                            comment = "",
                            author = "user",
                            kind = "custom",
                            sOffset = s,
                            eOffset = e,
                        )
                        refreshTick++
                        selPara = null
                    },
                    onMarkParagraph = {
                        annotateTarget = AnnotateTarget("paragraph", idx, para)
                    },
                )
            }
            if (paragraphs.isEmpty()) {
                item {
                    Text("(本页无内容)", style = MaterialTheme.typography.bodySmall, color = readPalette.sub)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 翻页行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { turnTo(page - 1) }, enabled = page > 1) {
                Icon(HugeIcons.ArrowLeft01, contentDescription = null, modifier = Modifier.size(16.dp), tint = readPalette.text)
                Spacer(Modifier.width(4.dp))
                Text("上一页", color = if (page > 1) readPalette.text else readPalette.sub)
            }
            Text(
                text = "${(page * 100 / book.totalPages)}%",
                style = MaterialTheme.typography.labelMedium,
                color = readPalette.accent,
            )
            TextButton(onClick = { turnTo(page + 1) }, enabled = page < book.totalPages) {
                Text("下一页", color = if (page < book.totalPages) readPalette.text else readPalette.sub)
                Spacer(Modifier.width(4.dp))
                Icon(HugeIcons.ArrowRight01, contentDescription = null, modifier = Modifier.size(16.dp), tint = readPalette.text)
            }
        }
        }

        // ---------- 目录抽屉(左缘滑入,r377) ----------
        val density = LocalDensity.current
        val drawerWpx = with(density) { 300.dp.toPx() }
        // 左缘手势条:点按或右拖拉出目录(先声明=底层,打开后被抽屉盖住)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(26.dp)
                .pointerInput(Unit) {
                    detectTapGestures { tocOpen = true }
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        },
                        onDragEnd = {
                            if (total > drawerWpx * 0.3f) tocOpen = true
                            total = 0f
                        },
                        onDragCancel = { total = 0f },
                    )
                },
        )
        val tocAnim = remember { Animatable(0f) }
        LaunchedEffect(tocOpen) {
            tocAnim.animateTo(if (tocOpen) 1f else 0f, tween(280))
        }
        val tocShownPx = tocAnim.value * drawerWpx
        val tocScrimAlpha = (tocShownPx / drawerWpx) * 0.36f
        if (tocScrimAlpha > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = tocScrimAlpha))
                    .pointerInput(Unit) {
                        detectTapGestures { tocOpen = false }
                    },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(300.dp)
                .fillMaxHeight()
                .offset { IntOffset((-drawerWpx + tocShownPx).roundToInt(), 0) }
                .background(readPalette.solid)
                .drawBehind {
                    drawLine(
                        color = readPalette.line,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = 12.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, color = readPalette.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "PDF/EPUB · 共 ${book.totalPages} 页 · ${chapters.size.coerceAtLeast(1)} 章",
                        style = MaterialTheme.typography.labelSmall,
                        color = readPalette.sub,
                    )
                }
                if (chapters.isEmpty()) {
                    Text(
                        "本书没有章节信息(整本连排),翻页阅读即可",
                        style = MaterialTheme.typography.bodySmall,
                        color = readPalette.sub,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(chapters, key = { _, c -> "toc-${c.index}" }) { _, ch ->
                            val isCur = page in ch.startPage..ch.endPage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isCur) readPalette.accentSoft else Color.Transparent)
                                    .clickable {
                                        turnTo(ch.startPage)
                                        tocOpen = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCur) readPalette.accent else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, readPalette.line),
                                ) {
                                    Text(
                                        text = "${ch.index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCur) Color.White else readPalette.sub,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "第 ${ch.index + 1} 章 ${ch.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = readPalette.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "第 ${ch.startPage}–${ch.endPage} 页" + if (isCur) " · 正在读" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = readPalette.sub,
                                    )
                                }
                                if (ch.endPage < page) {
                                    Text("✓", color = readPalette.accent, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                Text(
                    "从左缘滑入 · 点章节直达章首",
                    style = MaterialTheme.typography.labelSmall,
                    color = readPalette.sub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(readPalette.line.copy(alpha = 0.25f))
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    // ---------- 阅读设置(Aa:字体/背景) ----------
    if (showReadSettings) {
        ModalBottomSheet(onDismissRequest = { showReadSettings = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
                Text(
                    "阅读设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("字体", style = MaterialTheme.typography.labelMedium, color = theme.sub, modifier = Modifier.width(44.dp))
                    Spacer(Modifier.width(10.dp))
                    listOf("fang" to "仿宋", "kai" to "楷体", "song" to "宋体", "hei" to "黑体").forEach { (key, label) ->
                        val selected = settings.displaySetting.readFont == key
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) readPalette.accent else theme.chipBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) readPalette.accent else (theme.divider ?: theme.sub.copy(alpha = 0.3f))),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clickable {
                                    scope.launch {
                                        settingsStore.update { it.copy(displaySetting = it.displaySetting.copy(readFont = key)) }
                                    }
                                },
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) Color.White else theme.text,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("背景", style = MaterialTheme.typography.labelMedium, color = theme.sub, modifier = Modifier.width(44.dp))
                    Spacer(Modifier.width(10.dp))
                    listOf(
                        "paper" to "米黄纸",
                        "white" to "纯白",
                        "green" to "护眼绿",
                        "parch" to "羊皮",
                        "night" to "夜间",
                    ).forEach { (key, label) ->
                        val pal = readPaletteFor(key)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .clickable {
                                    scope.launch {
                                        settingsStore.update { it.copy(displaySetting = it.displaySetting.copy(readBg = key)) }
                                    }
                                },
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = pal.paper,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (settings.displaySetting.readBg == key) 2.dp else 1.dp,
                                    color = if (settings.displaySetting.readBg == key) readPalette.accent else (theme.divider ?: theme.sub.copy(alpha = 0.3f)),
                                ),
                                modifier = Modifier.size(34.dp),
                            ) {}
                            Spacer(Modifier.height(3.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = theme.sub)
                        }
                    }
                }
            }
        }
    }

    // ---------- 单句选择面板(点段落弹出) ----------
    sentenceSheetFor?.let { (paraIdx, sentences) ->
        ModalBottomSheet(onDismissRequest = { sentenceSheetFor = null }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "点一个句子划线或评论(第 $page 页)",
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已划线的句子带底色,点开即显示双方标注;想自己圈范围就长按正文进入手选,拖两端手柄调整",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 顶部:本页双方标注一览(AI 标注不一定能定位到具体句子,保证永远可见)
                if (pageAnnos.isNotEmpty()) {
                    item(key = "page_annos_header") {
                        Text(
                            text = "本页双方标注 · ${pageAnnos.size} 条(点开看详情)",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.name,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    itemsIndexed(pageAnnos, key = { _, a -> "hdr_" + a.id }) { _, ann ->
                        val isAi = ann.author == "ai"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.cellBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    onClick = {
                                        sentenceSheetFor = null
                                        annDetailsFor = listOf(ann)
                                    },
                                ),
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = socialAuthorName(ann.author, assistant) +
                                            (when (ann.kind) { "sentence" -> " · 划线"; "custom" -> " · 手选"; else -> " · 整页" }),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAi) theme.name else theme.sub,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "第${ann.page}页",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.sub,
                                    )
                                }
                                if (ann.excerpt.isNotBlank()) {
                                    Text(
                                        text = "「${ann.excerpt.take(50)}${if (ann.excerpt.length > 50) "…" else ""}」",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.text,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Text(
                                    text = ann.comment,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.sub,
                                )
                            }
                        }
                    }
                }
                item(key = "pick_sentence_header") {
                    Text(
                        text = "—— 点一个句子划线或评论 ——",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.sub,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(sentences) { _, sentence ->
                    val matched = pageAnnos.filter {
                        it.kind == "sentence" && it.excerpt.length > 3 &&
                            (sentence.contains(it.excerpt) || it.excerpt.contains(sentence))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (matched.isNotEmpty()) theme.chipBg else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    sentenceSheetFor = null
                                    if (matched.isNotEmpty()) {
                                        annDetailsFor = matched
                                    } else {
                                        annotateTarget = AnnotateTarget("sentence", paraIdx, sentence)
                                    }
                                },
                                onLongClick = {
                                    sentenceSheetFor = null
                                    annotateTarget = AnnotateTarget("sentence", paraIdx, sentence)
                                },
                            ),
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                text = buildAnnotatedString {
                                    append(sentence)
                                    if (matched.isNotEmpty()) {
                                        addStyle(
                                            SpanStyle(textDecoration = TextDecoration.Underline),
                                            0,
                                            sentence.length,
                                        )
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.text,
                            )
                            if (matched.isNotEmpty()) {
                                Text(
                                    text = "📝 ${matched.size} 条标注 · 点开查看",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.name,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 标注对话框(整段或单句) ----------
    annotateTarget?.let { target ->
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { annotateTarget = null },
            title = { Text(if (target.kind == "sentence") "划线并评论这句" else "标注这一段") },
            text = {
                Column {
                    Text(
                        text = target.excerpt.take(120) + if (target.excerpt.length > 120) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.sub,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        placeholder = { Text("写下你的评论/感想") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = comment.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repo.addAnnotation(
                                bookId = book.id,
                                page = page,
                                paragraphIndex = target.paraIdx,
                                excerpt = target.excerpt,
                                comment = comment,
                                author = "user",
                                kind = target.kind,
                            )
                            // 后台提醒助手:用户写了新标注(助手开了共同阅读工具时才会真正唤醒)
                            socialNotify.notifyUserActivity(
                                "${me.rerere.rikkahub.service.USER_ALIAS_TOKEN}在《${book.title}》第${page}页新增了一条${if (target.kind == "sentence") "句子" else "段落"}标注:「${comment.take(60)}」"
                            )
                            annotateTarget = null
                            refreshTick++
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { annotateTarget = null }) { Text("取消") }
            },
        )
    }

    // ---------- 已标注句子的标注详情(点开即见) ----------
    annDetailsFor?.let { anns ->
        AlertDialog(
            onDismissRequest = { annDetailsFor = null },
            title = { Text("这句的标注 · ${anns.size} 条") },
            text = {
                Column {
                    Text(
                        text = anns.firstOrNull()?.excerpt?.take(100) ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.sub,
                    )
                    Spacer(Modifier.height(8.dp))
                    anns.forEach { a ->
                        Text(
                            text = "${socialAuthorName(a.author, assistant)}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (a.author == "ai") theme.name else theme.sub,
                        )
                        Text(
                            text = a.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.text,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    annDetailsFor = null
                    annotateTarget = AnnotateTarget("sentence", anns.first().paragraphIndex, anns.first().excerpt)
                }) { Text("再写一条") }
            },
            dismissButton = {
                TextButton(onClick = { annDetailsFor = null }) { Text("关闭") }
            },
        )
    }

    // ---------- 标注列表(双方共享,打开即清共同阅读红点) ----------
    if (showAnnotations) {
        LaunchedEffect(Unit) {
            runCatching { socialDao.markReadByType("read_annotation") }
        }
        val annotations = remember(showAnnotations, refreshTick) { repo.listAnnotations(book.id) }
        ModalBottomSheet(onDismissRequest = { showAnnotations = false }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "双方标注 · ${annotations.size} 条",
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "你和 TA 的划线与评论都在这里;点某条跳到对应页(长按删除自己的)",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (annotations.isEmpty()) {
                    item {
                        Text(
                            "还没有标注:点段落选句子划线评论,或长按段落整段标注",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.sub,
                        )
                    }
                }
                itemsIndexed(annotations, key = { _, a -> a.id }) { _, ann ->
                    val isAi = ann.author == "ai"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.cellBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    showAnnotations = false
                                    if (ann.page != page) turnTo(ann.page)
                                },
                                onLongClick = {
                                    if (!isAi) {
                                        annDeleteTarget = ann
                                    }
                                },
                            ),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isAi) theme.btnBg else theme.chipBg,
                                    modifier = Modifier.size(22.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = socialAuthorName(ann.author, assistant).take(2),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = theme.btnFg,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = socialAuthorName(ann.author, assistant) +
                                        " · 第 ${ann.page} 页" +
                                        (when (ann.kind) { "sentence" -> " · 划线"; "custom" -> " · 手选"; else -> " · 整段" }),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.sub,
                                )
                                Spacer(Modifier.weight(1f))
                                if (!isAi) {
                                    Icon(
                                        HugeIcons.Delete02,
                                        contentDescription = "长按删除",
                                        tint = theme.sub,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (ann.excerpt.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = buildAnnotatedString {
                                        append("「${ann.excerpt.take(60)}${if (ann.excerpt.length > 60) "…" else ""}」")
                                        if (ann.kind == "sentence") {
                                            addStyle(
                                                SpanStyle(textDecoration = TextDecoration.Underline),
                                                0,
                                                length,
                                            )
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.text,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = ann.comment,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAi) theme.name else theme.sub,
                            )
                        }
                    }
                }
            }
        }
    }

    // 长按删标注先确认(r278验收防误删)
    annDeleteTarget?.let { ann ->
        RikkaConfirmDialog(
            show = true,
            title = "删除标注",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                repo.deleteAnnotation(book.id, ann.id)
                showAnnotations = false
                refreshTick++
                annDeleteTarget = null
            },
            onDismiss = { annDeleteTarget = null },
            text = { Text("删除「${ann.excerpt.take(20)}…」的标注?不可恢复") },
        )
    }

    // ---------- 让 TA 读(读这章 / 读整本) ----------
    if (showStudySheet) {
        val currentChapter = chapter
        val hasDigest = remember(showStudySheet, studyState.epoch, currentChapter?.index) {
            currentChapter?.let { repo.getDigest(book.id, it.index) != null } == true
        }
        ModalBottomSheet(onDismissRequest = { showStudySheet = false }) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 22.dp),
            ) {
                Text("让 TA 读", style = MaterialTheme.typography.titleMedium, color = theme.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "TA 会整理剧情、记读书笔记、存记忆;书里教了可复用的方法时会起草一个技能给你确认",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
                Spacer(Modifier.height(12.dp))

                if (studyState.running && studyState.bookId == book.id) {
                    Surface(
                        color = theme.cellBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = theme.name,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = studyState.message.ifBlank { "TA 正在读…" },
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.text,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { studyService.cancel() }) { Text("停止") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (currentChapter == null) {
                    Text("正在读取章节…", style = MaterialTheme.typography.bodySmall, color = theme.sub)
                } else {
                    StudyOptionRow(
                        theme = theme,
                        icon = "📖",
                        title = if (hasDigest) "重读这一章" else "读这一章",
                        desc = "第 ${currentChapter.index + 1} 章 ${currentChapter.title} · 用聊天模型,质量优先",
                        tag = "1 次",
                        enabled = !studyState.running,
                    ) {
                        showStudySheet = false
                        studyService.studyChapter(
                            bookId = book.id,
                            chapterIndex = currentChapter.index,
                            assistantId = assistant.id,
                            useChatModel = true,
                            force = hasDigest,
                        )
                    }
                }
                StudyOptionRow(
                    theme = theme,
                    icon = "📚",
                    title = "读整本",
                    desc = "共 ${chapters.size} 章 · 用快速模型逐章读,读完再合并总览",
                    tag = "≈${chapters.size + 1} 次",
                    enabled = !studyState.running && chapters.isNotEmpty(),
                ) {
                    showStudySheet = false
                    showBookCostDialog = true
                }
            }
        }
    }

    if (showBookCostDialog) {
        AlertDialog(
            onDismissRequest = { showBookCostDialog = false },
            title = { Text("读完整本《${book.title}》?") },
            text = {
                Text(
                    "共 ${chapters.size} 章,大约 ${chapters.size + 1} 次快速模型调用;" +
                        "已经读过且正文没变的章节会跳过,中途可以停止。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBookCostDialog = false
                    studyService.studyBook(book.id, assistant.id)
                }) { Text("开始读") }
            },
            dismissButton = {
                TextButton(onClick = { showBookCostDialog = false }) { Text("再想想") }
            },
        )
    }

    // ---------- TA 的笔记(摘要/笔记/技能提案) ----------
    if (showNotes) {
        val digests = remember(showNotes, noteRefresh, studyState.epoch) { repo.listDigests(book.id) }
        val allNotes = remember(showNotes, noteRefresh, studyState.epoch) { repo.listNotes(book.id) }
        val proposals = remember(showNotes, noteRefresh, studyState.epoch) {
            repo.listSkillProposals(book.id).filter { it.status == "pending" }
        }
        val bookSummary = allNotes.firstOrNull { it.kind == "book_summary" }
        // 章行 = 有摘要的章 + 只有 TA 笔记的章(聊天里 save_note 可能落在没跑过学习的章)
        val chapterRows = remember(chapters, digests, allNotes) {
            val base = if (chapters.isNotEmpty()) chapters else {
                digests.map { ChapterMeta(it.chapterIndex, it.title, it.startPage, it.endPage) }
            }
            base.mapNotNull { ch ->
                val digest = digests.firstOrNull { it.chapterIndex == ch.index }
                val notes = allNotes.filter { it.chapterIndex == ch.index }
                if (digest == null && notes.isEmpty()) null else Triple(ch, digest, notes)
            }
        }
        ModalBottomSheet(onDismissRequest = { showNotes = false }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("TA 的笔记", style = MaterialTheme.typography.titleMedium, color = theme.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "《${book.title}》· 已读 ${digests.size} / ${chapters.size.coerceAtLeast(digests.size)} 章",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.sub,
                )
                if (studyState.running && studyState.bookId == book.id) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = theme.name,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = studyState.message.ifBlank { "TA 正在读…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { studyService.cancel() }) { Text("停止") }
                    }
                }
                val errorText = (if (studyState.bookId == book.id) studyState.error else null) ?: noteError
                errorText?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                proposals.forEach { proposal ->
                    item(key = "proposal-${proposal.id}") {
                        SkillProposalCard(
                            theme = theme,
                            proposal = proposal,
                            onCreate = { skillConfirm = proposal },
                            onDismiss = {
                                repo.updateSkillProposalStatus(book.id, proposal.id, "dismissed")
                                noteRefresh++
                            },
                        )
                    }
                }
                if (bookSummary != null) {
                    item(key = "book-summary") {
                        Surface(shape = RoundedCornerShape(10.dp), color = theme.cellBg) {
                            Column(Modifier.padding(10.dp)) {
                                Text("全书总览", style = MaterialTheme.typography.titleSmall, color = theme.text)
                                Spacer(Modifier.height(4.dp))
                                Text(bookSummary.content, style = MaterialTheme.typography.bodySmall, color = theme.text)
                            }
                        }
                    }
                }
                itemsIndexed(chapterRows, key = { _, row -> "chapter-${row.first.index}" }) { _, row ->
                    DigestCard(theme = theme, chapter = row.first, digest = row.second, notes = row.third)
                }
                if (proposals.isEmpty() && chapterRows.isEmpty() && bookSummary == null) {
                    item {
                        Text(
                            "还没有笔记:点右上角 ✨ 让 TA 读这一章或整本;TA 会把剧情摘要、读书笔记和技能提案留在这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.sub,
                        )
                    }
                }
            }
        }
    }

    // 技能确认:确认后写入技能库并加入当前助手启用列表
    skillConfirm?.let { proposal ->
        AlertDialog(
            onDismissRequest = { skillConfirm = null },
            title = { Text("TA 想把这个学成技能") },
            text = {
                Column {
                    Text("确认后写入技能库,并加入当前助手的启用列表", style = MaterialTheme.typography.bodySmall, color = theme.sub)
                    Spacer(Modifier.height(8.dp))
                    Text(proposal.name, style = MaterialTheme.typography.labelMedium, color = theme.name)
                    if (proposal.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(proposal.description, style = MaterialTheme.typography.bodySmall, color = theme.text)
                    }
                    if (proposal.whenToUse.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("什么时候用:${proposal.whenToUse}", style = MaterialTheme.typography.bodySmall, color = theme.sub)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pending = proposal
                    skillConfirm = null
                    scope.launch {
                        val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            skillManager.saveSkill(pending.name, pending.skillMd)
                        }
                        if (saved == null) {
                            noteError = "技能保存失败:名字或格式不合法"
                            return@launch
                        }
                        settingsStore.update { settings ->
                            settings.copy(
                                assistants = settings.assistants.map { a ->
                                    if (a.id == assistant.id) {
                                        a.copy(enabledSkills = a.enabledSkills + saved.name)
                                    } else a
                                },
                            )
                        }
                        repo.updateSkillProposalStatus(book.id, pending.id, "created")
                        noteError = null
                        noteRefresh++
                    }
                }) { Text("创建并启用") }
            },
            dismissButton = {
                TextButton(onClick = {
                    repo.updateSkillProposalStatus(book.id, proposal.id, "dismissed")
                    skillConfirm = null
                    noteRefresh++
                }) { Text("先不建") }
            },
        )
    }
}

/** 「让 TA 读」面板里的一行选项 */
@Composable
private fun StudyOptionRow(
    theme: DiscoverTheme,
    icon: String,
    title: String,
    desc: String,
    tag: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = theme.chipBg, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) theme.text else theme.sub,
                )
                Text(desc, style = MaterialTheme.typography.bodySmall, color = theme.sub)
            }
            Spacer(Modifier.width(8.dp))
            Text(tag, style = MaterialTheme.typography.labelSmall, color = theme.sub)
        }
    }
}

/** 章节摘要卡:剧情 + 人物/伏笔/基调 + 金句 + TA 笔记 */
@Composable
private fun DigestCard(
    theme: DiscoverTheme,
    chapter: ChapterMeta,
    digest: me.rerere.rikkahub.data.readtogether.ChapterDigest?,
    notes: List<me.rerere.rikkahub.data.readtogether.ReadNote>,
) {
    Surface(shape = RoundedCornerShape(10.dp), color = theme.cellBg) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 ${chapter.index + 1} 章 ${chapter.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "第 ${chapter.startPage}–${chapter.endPage} 页",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.sub,
                )
            }
            if (digest == null) {
                Spacer(Modifier.height(4.dp))
                Text("这一章还没让 TA 读过,下面是 TA 随手记的笔记", style = MaterialTheme.typography.labelSmall, color = theme.sub)
            }
            if (digest != null && digest.plot.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(digest.plot, style = MaterialTheme.typography.bodySmall, color = theme.text)
            }
            val facts = buildList {
                if (digest != null) {
                    if (digest.characters.isNotEmpty()) add("人物:${digest.characters.joinToString("、")}")
                    if (digest.threads.isNotEmpty()) add("伏笔:${digest.threads.joinToString("；")}")
                    if (digest.tone.isNotBlank()) add("基调:${digest.tone}")
                }
            }
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = theme.sub)
            }
            digest?.quotes?.take(2)?.forEach { quote ->
                Spacer(Modifier.height(5.dp))
                Text("「${quote}」", style = MaterialTheme.typography.bodySmall, color = theme.name)
            }
            notes.forEach { note ->
                Spacer(Modifier.height(7.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = theme.chipBg) {
                    Column(Modifier.padding(8.dp)) {
                        Text(note.title, style = MaterialTheme.typography.labelMedium, color = theme.text)
                        if (note.content.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(note.content, style = MaterialTheme.typography.bodySmall, color = theme.sub)
                        }
                    }
                }
            }
        }
    }
}

/** 技能提案卡:确认后才创建并启用 */
@Composable
private fun SkillProposalCard(
    theme: DiscoverTheme,
    proposal: SkillProposal,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = theme.chipBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.btnBg.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(
                "技能提案 · 第 ${proposal.chapterIndex + 1} 章",
                style = MaterialTheme.typography.labelSmall,
                color = theme.name,
            )
            Spacer(Modifier.height(3.dp))
            Text(proposal.name, style = MaterialTheme.typography.titleSmall, color = theme.text)
            if (proposal.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(proposal.description, style = MaterialTheme.typography.bodySmall, color = theme.text)
            }
            if (proposal.whenToUse.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text("什么时候用:${proposal.whenToUse}", style = MaterialTheme.typography.labelSmall, color = theme.sub)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("先不建") }
                Button(onClick = onCreate) { Text("创建并启用") }
            }
        }
    }
}

/** 正文段落渲染:点=选句面板;长按=手选划线(拖两端手柄字符级调整);整段/句子/手选三种标注各自渲染 */
@Composable
private fun ParagraphText(
    para: String,
    paraIdx: Int,
    annotations: List<ReadTogetherRepository.ReadAnnotation>,
    theme: DiscoverTheme,
    palette: ReadPalette,
    font: FontFamily,
    selActive: Boolean,
    onTap: () -> Unit,
    onSelectStart: () -> Unit,
    onClearSelection: () -> Unit,
    onMarkCustom: (Int, Int) -> Unit,
    onMarkParagraph: () -> Unit,
) {
    val paraAnnos = annotations.filter {
        it.paragraphIndex == paraIdx ||
            (it.excerpt.length > 3 && para.contains(it.excerpt))
    }
    val wholeAnnotated = paraAnnos.any { it.kind == "paragraph" }
    val sentenceAnnos = paraAnnos.filter { it.kind == "sentence" }
    val customAnnos = paraAnnos.filter { it.kind == "custom" }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textOrigin by remember { mutableStateOf(Offset.Zero) }
    var boxW by remember { mutableStateOf(0) }
    var selStart by remember { mutableIntStateOf(-1) }
    var selEnd by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selActive) {
        if (!selActive) {
            selStart = -1
            selEnd = -1
        }
    }

    fun enterSelection(charOffset: Int) {
        selStart = (charOffset - 2).coerceIn(0, para.length - 1)
        selEnd = (charOffset + 9).coerceIn(0, para.length - 1)
        onSelectStart()
    }

    val selLo = if (selStart >= 0 && selEnd >= 0) minOf(selStart, selEnd) else -1
    val selHi = if (selStart >= 0 && selEnd >= 0) maxOf(selStart, selEnd) else -1

    val text = buildAnnotatedString {
        append(para)
        if (wholeAnnotated) {
            addStyle(
                SpanStyle(background = theme.chipBg),
                0,
                para.length,
            )
        }
        sentenceAnnos.forEach { a ->
            val idx = para.indexOf(a.excerpt)
            if (idx >= 0) {
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline),
                    idx,
                    (idx + a.excerpt.length).coerceAtMost(para.length),
                )
            }
        }
        customAnnos.forEach { a ->
            if (a.sOffset in 0 until para.length && a.eOffset >= a.sOffset) {
                addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline, background = palette.accentSoft),
                    a.sOffset,
                    (a.eOffset + 1).coerceAtMost(para.length),
                )
            }
        }
        if (selActive && selLo >= 0) {
            addStyle(
                SpanStyle(textDecoration = TextDecoration.Underline, background = palette.accentSoft),
                selLo,
                (selHi + 1).coerceAtMost(para.length),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { boxW = it.width },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
                fontFamily = font,
            ),
            color = palette.text,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .onGloballyPositioned { textOrigin = it.positionInRoot() }
                .pointerInput(paraIdx) {
                    detectTapGestures(
                        onTap = {
                            if (selActive) onClearSelection() else onTap()
                        },
                        onLongPress = { pos ->
                            val l = layout
                            if (l != null) {
                                enterSelection(l.getOffsetForPosition(pos))
                            }
                        },
                    )
                },
        )
        if (selActive && selLo >= 0) {
            val l = layout
            if (l != null) {
                val rectS = l.getCursorRect(selLo)
                val rectE = l.getCursorRect(selHi)
                val handleOff = with(LocalDensity.current) { 7.dp.toPx() }
                SelectionHandle(
                    anchorX = rectS.left - handleOff,
                    anchorY = rectS.top,
                    palette = palette,
                    isStart = true,
                    layout = l,
                    textOrigin = textOrigin,
                    maxOffset = para.length - 1,
                    onOffsetChange = { selStart = it },
                )
                SelectionHandle(
                    anchorX = rectE.right - handleOff,
                    anchorY = rectE.top,
                    palette = palette,
                    isStart = false,
                    layout = l,
                    textOrigin = textOrigin,
                    maxOffset = para.length - 1,
                    onOffsetChange = { selEnd = it },
                )
                // 浮动操作条:划线 / 整段 / 取消
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.solid,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.line),
                    modifier = Modifier
                        .offset {
                            val barW = 218.dp.toPx().roundToInt()
                            val x = (rectS.left - 70.dp.toPx()).roundToInt().coerceIn(0, (boxW - barW).coerceAtLeast(0))
                            val y = (rectS.top - 46.dp.toPx()).roundToInt().coerceAtLeast(0)
                            IntOffset(x, y)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "划线",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.accent)
                                .clickable { onMarkCustom(selLo, selHi) }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "整段",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.text,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onMarkParagraph()
                                    onClearSelection()
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                        )
                        Text(
                            "取消",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.sub,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClearSelection() }
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 手选划线的起/终手柄:竖杆+圆点,拖动按字符偏移吸附 */
@Composable
private fun SelectionHandle(
    anchorX: Float,
    anchorY: Float,
    palette: ReadPalette,
    isStart: Boolean,
    layout: TextLayoutResult,
    textOrigin: Offset,
    maxOffset: Int,
    onOffsetChange: (Int) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .offset { IntOffset(anchorX.roundToInt(), anchorY.roundToInt()) }
            .onGloballyPositioned { origin = it.positionInRoot() }
            .size(width = 14.dp, height = 28.dp)
            .pointerInput(isStart) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position + origin - textOrigin
                        onOffsetChange(layout.getOffsetForPosition(pos).coerceIn(0, maxOffset))
                    },
                )
            }
            .drawBehind {
                val cx = size.width / 2
                val stickTop = if (isStart) 0f else size.height * 0.35f
                val stickBottom = if (isStart) size.height * 0.65f else size.height
                drawLine(palette.accent, Offset(cx, stickTop), Offset(cx, stickBottom), 2.dp.toPx())
                drawCircle(
                    palette.accent,
                    radius = 5.5.dp.toPx(),
                    center = if (isStart) Offset(cx, size.height - 5.5.dp.toPx()) else Offset(cx, 5.5.dp.toPx()),
                )
            },
    )
}
