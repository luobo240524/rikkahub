package me.rerere.rikkahub.ui.pages.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.MoodCheckinEntity
import me.rerere.rikkahub.ui.components.social.SocialActionRow
import me.rerere.rikkahub.ui.components.social.SocialCommentDialog
import me.rerere.rikkahub.ui.components.social.SocialInteractionBox
import me.rerere.rikkahub.ui.components.social.socialAuthorAvatar
import me.rerere.rikkahub.ui.components.social.socialAuthorName
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.pages.discover.DiscoverTheme
import me.rerere.rikkahub.ui.pages.discover.DiscoverThemeMenuButton
import me.rerere.rikkahub.ui.pages.discover.discoverCardBorder
import me.rerere.rikkahub.ui.pages.discover.discoverForegroundOverlay
import me.rerere.rikkahub.ui.pages.discover.discoverPageBackground
import me.rerere.rikkahub.ui.pages.discover.discoverTopBarColors
import me.rerere.rikkahub.ui.pages.discover.rememberDiscoverTheme
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth

/**
 * 每日心情打卡:统计行 + 月历(emoji格) + 30天胶囊条 + 表单 + 历史卡(点赞评论)。
 * 主题化:七套发现页主题,顶栏 🎨 折叠下拉切换。
 */
private val MOOD_EMOJIS = listOf("😊", "😴", "🤩", "🥰", "😎", "😢", "😡", "🤒", "🫠", "😐")

@Composable
fun MoodCheckinPage(
    vm: MoodCheckinVM = koinViewModel(),
) {
    val month by vm.month.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val recentRecords by vm.recentRecords.collectAsStateWithLifecycle()
    val interactions by vm.interactions.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val dayRecords by vm.dayRecords.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedMood by remember { mutableStateOf(MOOD_EMOJIS.first()) }
    var customEmoji by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var commentTarget by remember { mutableStateOf<MoodCheckinEntity?>(null) }

    val settings = LocalSettings.current
    val assistant = settings.getCurrentAssistant()
    val theme = rememberDiscoverTheme()

    val checkinInteractions = remember(interactions) {
        interactions.filter { it.targetType == "checkin" }.groupBy { it.targetId }
    }

    val today = LocalDate.now().toString()
    val todayUserChecked = records.any { it.date == today && it.subject == "user" }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.mood_title)) },
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 统计行
                item(key = "stats") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = theme.cardBg,
                        border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(vertical = 12.dp)) {
                            MoodStat("连续打卡", "${calcStreak(recentRecords)} 天", theme, Modifier.weight(1f))
                            MoodStat("本月打卡", "${records.count { it.subject == "user" }} 次", theme, Modifier.weight(1f))
                            MoodStat("最常心情", topMood(records) ?: "—", theme, Modifier.weight(1f))
                        }
                    }
                }
                // 月历视图
                item(key = "calendar") {
                    Surface(
                        shape = RoundedCornerShape(theme.cardCorner),
                        color = theme.cardBg,
                        border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { vm.prevMonth() }) { Text("‹", color = theme.name) }
                                Text(
                                    text = "${month.year}-${month.monthValue.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.text,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.nextMonth() }) { Text("›", color = theme.name) }
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                                    Text(
                                        text = d,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.sub,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            val firstDay = month.atDay(1)
                            val leading = (firstDay.dayOfWeek.value + 6) % 7
                            val days = month.lengthOfMonth()
                            val cells = leading + days
                            val rows = (cells + 6) / 7
                            for (row in 0 until rows) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    for (col in 0 until 7) {
                                        val cell = row * 7 + col
                                        val day = cell - leading + 1
                                        Box(modifier = Modifier.weight(1f).padding(vertical = 3.dp)) {
                                            if (day in 1..days) {
                                                val date = month.atDay(day).toString()
                                                val userMood = records.firstOrNull { it.date == date && it.subject == "user" }?.mood
                                                val assistantChecked = records.any { it.date == date && it.subject == "assistant" }
                                                val isToday = date == today
                                                val isSelected = date == selectedDate
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .clickable { vm.selectDate(date) }
                                                        .then(
                                                            when {
                                                                isToday -> Modifier.border(
                                                                    width = 1.5.dp,
                                                                    color = theme.like,
                                                                    shape = RoundedCornerShape(10.dp),
                                                                )
                                                                isSelected -> Modifier.border(
                                                                    width = 1.5.dp,
                                                                    color = theme.name,
                                                                    shape = RoundedCornerShape(10.dp),
                                                                )
                                                                else -> Modifier
                                                            }
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        text = day.toString(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = theme.text,
                                                    )
                                                    if (userMood != null) {
                                                        Text(text = userMood, fontSize = 12.sp, lineHeight = 14.sp)
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(top = 4.dp)
                                                                .size(4.dp)
                                                                .background(theme.cellBg, CircleShape)
                                                        )
                                                    }
                                                    if (assistantChecked) {
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(top = 2.dp)
                                                                .size(5.dp)
                                                                .border(width = 1.5.dp, color = theme.name, shape = CircleShape)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 最近30天心情胶囊条
                if (recentRecords.isNotEmpty()) {
                    item(key = "recent_strip") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = theme.cardBg,
                            border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "最近30天", style = MaterialTheme.typography.titleSmall, color = theme.text)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    recentRecords.sortedBy { it.date }.forEach { r ->
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = theme.chipBg,
                                        ) {
                                            Text(
                                                text = "${r.mood} ${r.date.drop(5)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = theme.text,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 今日打卡表单
                item(key = "form") {
                    Surface(
                        shape = RoundedCornerShape(theme.cardCorner),
                        color = theme.cardBg,
                        border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (todayUserChecked) stringResource(R.string.mood_checked)
                                else stringResource(R.string.mood_today),
                                style = MaterialTheme.typography.titleSmall,
                                color = theme.text,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                MOOD_EMOJIS.take(5).forEach { e -> MoodDot(e, selectedMood == e, theme) { selectedMood = e } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                MOOD_EMOJIS.drop(5).forEach { e -> MoodDot(e, selectedMood == e, theme) { selectedMood = e } }
                            }
                            OutlinedTextField(
                                value = phrase,
                                onValueChange = { phrase = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.mood_phrase_hint), color = theme.sub) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.fldBg,
                                    unfocusedContainerColor = theme.fldBg,
                                    focusedTextColor = theme.text,
                                    unfocusedTextColor = theme.text,
                                    cursorColor = theme.like,
                                    focusedBorderColor = theme.like,
                                    unfocusedBorderColor = theme.sub,
                                ),
                            )
                            OutlinedTextField(
                                value = customEmoji,
                                onValueChange = { customEmoji = it.take(4) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("自定义 emoji(可选,填了优先用这个)", color = theme.sub) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = theme.fldBg,
                                    unfocusedContainerColor = theme.fldBg,
                                    focusedTextColor = theme.text,
                                    unfocusedTextColor = theme.text,
                                    cursorColor = theme.like,
                                    focusedBorderColor = theme.like,
                                    unfocusedBorderColor = theme.sub,
                                ),
                            )
                            TextButton(
                                onClick = {
                                    val mood = customEmoji.trim().ifBlank { selectedMood }
                                    vm.checkin(mood, phrase.trim().ifBlank { mood })
                                    phrase = ""
                                    customEmoji = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.mood_checkin_btn), color = theme.name)
                            }
                        }
                    }
                }
                // 当日打卡:点月历日期查看当天(替代一直累积的历史列表)
                item(key = "day_header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "当日打卡",
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = selectedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.sub,
                        )
                    }
                }
                if (dayRecords.isEmpty()) {
                    item(key = "day_empty") {
                        Text(
                            text = "这一天还没有打卡",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.sub,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                items(dayRecords.size, key = { dayRecords[it].id }) { index ->
                    val record = dayRecords[index]
                    MoodCheckinCard(
                        record = record,
                        assistant = assistant,
                        theme = theme,
                        interactions = checkinInteractions[record.id].orEmpty(),
                        onDelete = { vm.delete(record.id) },
                        onLike = { vm.toggleLike(record.id) },
                        onComment = { commentTarget = record },
                    )
                }
            }
            // 前景装饰层(绿色主题叶子)
            Box(modifier = Modifier.matchParentSize().discoverForegroundOverlay(theme))
        }
    }

    commentTarget?.let { target ->
        SocialCommentDialog(
            title = stringResource(R.string.mood_title),
            onDismiss = { commentTarget = null },
            onConfirm = { text ->
                vm.comment(target.id, text, target.subject)
                commentTarget = null
            },
        )
    }
}

/** 连续打卡天数 */
private fun calcStreak(records: List<MoodCheckinEntity>): Int {
    val dates = records.map { it.date }.toSortedSet(Comparator.reverseOrder())
    if (dates.isEmpty()) return 0
    var cursor = LocalDate.now()
    if (cursor.toString() !in dates) cursor = cursor.minusDays(1)
    if (cursor.toString() !in dates) return 0
    var streak = 0
    while (cursor.toString() in dates) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** 本月最常心情 */
private fun topMood(records: List<MoodCheckinEntity>): String? {
    return records.filter { it.subject == "user" }
        .groupingBy { it.mood }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
}

@Composable
private fun MoodStat(label: String, value: String, theme: DiscoverTheme, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = theme.name)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = theme.sub)
    }
}

@Composable
private fun MoodCheckinCard(
    record: MoodCheckinEntity,
    assistant: me.rerere.rikkahub.data.model.Assistant,
    theme: DiscoverTheme,
    interactions: List<me.rerere.rikkahub.data.db.entity.SocialInteractionEntity>,
    onDelete: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
) {
    val likes = interactions.count { it.type == "like" }
    val userLiked = interactions.any { it.type == "like" && it.actor == "user" }
    val commentCount = interactions.count { it.type == "comment" }

    Surface(
        shape = RoundedCornerShape(theme.cardCorner),
        color = theme.cardBg,
        border = theme.cardBorder?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UIAvatar(
                    name = socialAuthorName(record.subject, assistant),
                    value = socialAuthorAvatar(record.subject, assistant),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = socialAuthorName(record.subject, assistant),
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.name,
                    )
                    Text(
                        text = record.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.sub,
                    )
                }
                Icon(
                    imageVector = HugeIcons.Delete02,
                    contentDescription = stringResource(R.string.moments_delete),
                    tint = theme.sub,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onDelete),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = record.mood, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                Text(
                    text = record.phrase,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
            }
            SocialInteractionBox(interactions = interactions, assistant = assistant, theme = theme, author = record.subject)
            SocialActionRow(
                likeCount = likes,
                liked = userLiked,
                commentCount = commentCount,
                onLike = onLike,
                onComment = onComment,
                theme = theme,
            )
        }
    }
}

@Composable
private fun MoodDot(emoji: String, selected: Boolean, theme: DiscoverTheme, onClick: () -> Unit) {
    Text(
        text = emoji,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .size(40.dp)
            .background(
                if (selected) theme.like.copy(alpha = 0.15f) else theme.cellBg,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) theme.like else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        textAlign = TextAlign.Center,
    )
}
