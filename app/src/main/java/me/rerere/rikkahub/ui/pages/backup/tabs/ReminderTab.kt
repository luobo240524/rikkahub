package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackupReminderConfig
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.utils.toLocalDateTime
import java.time.Instant

@Composable
fun ReminderTab(vm: BackupVM) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val config = settings.backupReminderConfig

    fun updateConfig(update: BackupReminderConfig) {
        vm.updateSettings(settings.copy(backupReminderConfig = update))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup(
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(
                trailingContent = {
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { updateConfig(config.copy(enabled = it)) },
                    )
                },
                headlineContent = { Text(stringResource(R.string.backup_page_reminder_enable)) },
            )

            if (config.enabled) {
                item(
                    headlineContent = { Text(stringResource(R.string.backup_page_reminder_interval)) },
                    supportingContent = {
                        val intervals = listOf(1, 3, 7, 14, 30)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            intervals.forEachIndexed { index, days ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = intervals.size,
                                    ),
                                    onClick = { updateConfig(config.copy(intervalDays = days)) },
                                    selected = config.intervalDays == days,
                                ) {
                                    Text(stringResource(R.string.backup_page_reminder_interval_days, days))
                                }
                            }
                        }
                    },
                )

                item(
                    headlineContent = {
                        Text(
                            if (config.lastBackupTime == 0L) {
                                stringResource(R.string.backup_page_reminder_no_record)
                            } else {
                                stringResource(
                                    R.string.backup_page_reminder_last_time,
                                    Instant.ofEpochMilli(config.lastBackupTime).toLocalDateTime()
                                )
                            }
                        )
                    },
                )
            }
        }

        // 自动备份:每天一次把设置+数据库推到 WebDAV,云端保留最近 N 份(实现在 AutoBackupController)
        CardGroup(
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(
                trailingContent = {
                    Switch(
                        checked = config.autoBackupEnabled,
                        onCheckedChange = { updateConfig(config.copy(autoBackupEnabled = it)) },
                    )
                },
                headlineContent = { Text("自动备份") },
                supportingContent = { Text("每天一次,把「设置+数据库」备份到 WebDAV(不含文件);未配置 WebDAV 时不会执行") },
            )

            if (config.autoBackupEnabled) {
                item(
                    headlineContent = { Text("保留份数") },
                    supportingContent = {
                        val counts = listOf(5, 7, 10, 15)
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            counts.forEachIndexed { index, count ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = counts.size,
                                    ),
                                    onClick = { updateConfig(config.copy(autoBackupKeepCount = count)) },
                                    selected = config.autoBackupKeepCount == count,
                                ) {
                                    Text("${count} 份")
                                }
                            }
                        }
                    },
                )

                item(
                    headlineContent = {
                        Text(
                            if (config.lastAutoBackupTime == 0L) {
                                "上次自动备份:从未"
                            } else {
                                "上次自动备份:" + Instant.ofEpochMilli(config.lastAutoBackupTime)
                                    .toLocalDateTime().toString().replace('T', ' ')
                            }
                        )
                    },
                )
            }
        }
    }
}
