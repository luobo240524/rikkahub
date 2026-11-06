package me.rerere.rikkahub.ui.pages.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.YearMonth

/**
 * 每日心情打卡：月历视图(用户实心点/助手圆环) + emoji 选择 + 短语 + 历史列表。
 */
private val MOOD_EMOJIS = listOf("😊", "😴", "🤩", "🥰", "😎", "😢", "😡", "🤒", "🫠", "😐")

@Composable
fun MoodCheckinPage(
    vm: MoodCheckinVM = koinViewModel(),
) {
    val month by vm.month.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedMood by remember { mutableStateOf(MOOD_EMOJIS.first()) }
    var phrase by remember { mutableStateOf("") }

    val today = LocalDate.now().toString()
    val todayUserChecked = records.any { it.date == today && it.subject == "user" }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.mood_title)) },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 月历视图
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 月份翻页
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { vm.prevMonth() }) { Text("‹") }
                            Text(
                                text = "${month.year}-${month.monthValue.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.nextMonth() }) { Text("›") }
                        }
                        // 星期表头
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                                Text(
                                    text = d,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        // 日期网格：用户=实心点,助手=圆环
                        val firstDay = month.atDay(1)
                        val leading = (firstDay.dayOfWeek.value + 6) % 7 // 周一=0
                        val days = month.lengthOfMonth()
                        val cells = leading + days
                        val rows = (cells + 6) / 7
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val cell = row * 7 + col
                                    val day = cell - leading + 1
                                    Box(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                        if (day in 1..days) {
                                            val date = month.atDay(day).toString()
                                            val userChecked = records.any { it.date == date && it.subject == "user" }
                                            val assistantChecked = records.any { it.date == date && it.subject == "assistant" }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.align(Alignment.Center),
                                            ) {
                                                Text(
                                                    text = day.toString(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(
                                                                if (userChecked) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                                CircleShape,
                                                            )
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .border(
                                                                width = 1.5.dp,
                                                                color = if (assistantChecked) MaterialTheme.colorScheme.tertiary
                                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                                shape = CircleShape,
                                                            )
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
            // 今日打卡表单
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (todayUserChecked) stringResource(R.string.mood_checked)
                            else stringResource(R.string.mood_today),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        // emoji 选择器
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            MOOD_EMOJIS.take(5).forEach { e ->
                                MoodDot(e, selectedMood == e) { selectedMood = e }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            MOOD_EMOJIS.drop(5).forEach { e ->
                                MoodDot(e, selectedMood == e) { selectedMood = e }
                            }
                        }
                        OutlinedTextField(
                            value = phrase,
                            onValueChange = { phrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.mood_phrase_hint)) },
                            singleLine = true,
                        )
                        TextButton(
                            onClick = {
                                vm.checkin(selectedMood, phrase.trim().ifBlank { selectedMood })
                                phrase = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.mood_checkin_btn))
                        }
                    }
                }
            }
            // 历史列表
            item {
                Text(
                    text = stringResource(R.string.mood_history),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (records.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.mood_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(records.size, key = { records[it].id }) { index ->
                val record = records[index]
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = record.mood,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (record.subject == "user") {
                                    stringResource(R.string.mood_user)
                                } else {
                                    stringResource(R.string.mood_assistant)
                                }) + " · " + record.date,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(text = record.phrase, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = stringResource(R.string.moments_delete),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(onClick = { vm.delete(record.id) }),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodDot(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = emoji,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .size(40.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp),
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        textAlign = TextAlign.Center,
    )
}
