package me.rerere.rikkahub.ui.pages.read

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.rikkahub.data.readtogether.ReadTogetherRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.discover.DiscoverCard
import me.rerere.rikkahub.ui.pages.discover.DiscoverTheme
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

/**
 * 共同阅读(发现页风格,复用 7 套主题):
 * 书架(导入 txt/epub/pdf) → 阅读器(段落正文,长按段落标注评论)。
 * 进度人机共享(共享 currentPage,助手经 read_together 工具感知/翻页/标注)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadTogetherPage() {
    val theme = rememberDiscoverTheme()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val repo = koinInject<ReadTogetherRepository>()
    val scope = rememberCoroutineScope()

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
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("共同阅读") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = discoverTopBarColors(theme),
                actions = { DiscoverThemeMenuButton() },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .discoverPageBackground(theme)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                                text = "导入 txt / epub / pdf 自动解析成页;你和 TA 共享同一阅读进度,读到哪里 TA 就知道哪里;双方都能在页面上写标注评论。",
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
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = theme.btnBg,
                                        contentColor = theme.btnFg,
                                    ),
                                ) {
                                    Icon(FileImport, contentDescription = null, modifier = Modifier.size(18.dp))
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
                itemsIndexed(books, key = { i, b -> b.id }) { _, book ->
                    DiscoverCard(
                        theme,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { openBookId = book.id },
                                onLongClick = {
                                    // 长按删除(二次确认走 dialog)
                                    pendingDeleteId = book.id
                                },
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
                                BookOpen01,
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

// 长按删除的待确认 bookId 见 ReadTogetherPage 内的局部状态

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookReader(
    theme: DiscoverTheme,
    book: ReadTogetherRepository.BookMeta,
    repo: ReadTogetherRepository,
    contentPadding: PaddingValues,
    onBackToShelf: () -> Unit,
    onProgress: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable(book.id) { mutableIntStateOf(book.currentPage.coerceIn(1, book.totalPages)) }
    var paragraphs by remember(page) { mutableStateOf(repo.getPageParagraphs(book.id, page)) }
    var showAnnotations by remember { mutableStateOf(false) }
    var annotateTarget by remember { mutableStateOf<Pair<Int, String>?>(null) } // (段落序号, 原文)

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        // 顶行:返回书架 / 书名+页码 / 标注列表
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToShelf) {
                Icon(ArrowLeft01, contentDescription = "返回书架", tint = theme.text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "第 $page / ${book.totalPages} 页 · 进度人机同步",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.sub,
                )
            }
            IconButton(onClick = { showAnnotations = true }) {
                Icon(PencilEdit01, contentDescription = "标注列表", tint = theme.text)
            }
        }
        Spacer(Modifier.height(4.dp))
        // 正文:长按段落标注
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(paragraphs) { idx, para ->
                Text(
                    text = para,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35f,
                    ),
                    color = theme.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { annotateTarget = idx to para },
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            if (paragraphs.isEmpty()) {
                item {
                    Text("(本页无内容)", style = MaterialTheme.typography.bodySmall, color = theme.sub)
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
                Icon(ArrowLeft01, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("上一页", color = if (page > 1) theme.text else theme.sub)
            }
            Text(
                text = "${(page * 100 / book.totalPages)}%",
                style = MaterialTheme.typography.labelMedium,
                color = theme.sub,
            )
            TextButton(onClick = { turnTo(page + 1) }, enabled = page < book.totalPages) {
                Text("下一页", color = if (page < book.totalPages) theme.text else theme.sub)
                Spacer(Modifier.width(4.dp))
                Icon(ArrowRight01, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }

    // 标注对话框(长按段落)
    annotateTarget?.let { (idx, para) ->
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { annotateTarget = null },
            title = { Text("标注这段") },
            text = {
                Column {
                    Text(
                        text = para.take(120) + if (para.length > 120) "…" else "",
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
                                paragraphIndex = idx,
                                excerpt = para,
                                comment = comment,
                                author = "user",
                            )
                            annotateTarget = null
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { annotateTarget = null }) { Text("取消") }
            },
        )
    }

    // 标注列表(双方共享)
    if (showAnnotations) {
        val annotations = remember(page) { repo.listAnnotations(book.id) }
        ModalBottomSheet(onDismissRequest = { showAnnotations = false }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "双方标注 · ${annotations.size} 条",
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "你和 TA 的评论都在这里;点某条可跳到对应页(长按删除自己的)",
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
                        Text("还没有标注,长按正文段落即可添加", style = MaterialTheme.typography.bodySmall, color = theme.sub)
                    }
                }
                itemsIndexed(annotations, key = { i, a -> a.id }) { _, ann ->
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
                                        repo.deleteAnnotation(book.id, ann.id)
                                        showAnnotations = false
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
                                            text = if (isAi) "AI" else "我",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = theme.btnFg,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "第 ${ann.page} 页",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.sub,
                                )
                                Spacer(Modifier.weight(1f))
                                if (!isAi) {
                                    Icon(
                                        Delete02,
                                        contentDescription = "长按删除",
                                        tint = theme.sub,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (ann.excerpt.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "「${ann.excerpt.take(60)}${if (ann.excerpt.length > 60) "…" else ""}」",
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
}
