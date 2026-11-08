package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import android.content.Context
import android.net.Uri
import me.rerere.rikkahub.data.files.FileUtils
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.io.File
import me.rerere.rikkahub.data.files.FileFolders
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.BubbleBeautify
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.message.liquidGlassShadow
import me.rerere.rikkahub.ui.components.message.neumorphShadow
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_basic))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantBasicContent(
            innerPadding = innerPadding,
            assistant = assistant,
            providers = providers,
            tags = tags,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            vm = vm
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM
) {
    val settings by vm.settings.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier
                    .size(80.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_name))
                },
                modifier = Modifier.padding(8.dp),

                ) {
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                name = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_tags))
                },
                modifier = Modifier.padding(8.dp),
            ) {
                TagsInput(
                    value = assistant.tags,
                    tags = tags,
                    onValueChange = { tagIds, tagList ->
                        vm.updateTags(tagIds, tagList)
                    },
                )
            }

            HorizontalDivider()

            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_workspace))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_workspace_desc))
                },
                modifier = Modifier.padding(8.dp),
            ) {
                val selectedWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
                Select(
                    options = listOf<WorkspaceEntity?>(null) + workspaces,
                    selectedOption = selectedWorkspace,
                    onOptionSelected = { workspace ->
                        onUpdate(
                            assistant.copy(
                                workspaceId = workspace?.id?.let { Uuid.parse(it) }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { workspace ->
                        workspace?.name ?: stringResource(R.string.workspace_no_binding)
                    },
                )
            }

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.useAssistantAvatar,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useAssistantAvatar = it
                                )
                            )
                        }
                    )
                }
            )

            HorizontalDivider()
        }

        // 社交与通话:三个每助手开关集中一卡(原来「语音通话」藏在上一张卡最底部,验收⑥反馈找不到)
        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("社交与通话") },
                description = { Text("以下开关均为每助手独立,控制 TA 在发现体系与语音通话中的能力") },
            )

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("发现页入口") },
                description = {
                    Text("开启后聊天页顶栏显示地球下拉(发现/朋友圈/心里话/打卡/时间轴/语音通话);关闭 = 顶栏不显示地球")
                },
                tail = {
                    Switch(
                        checked = assistant.discoverEnabled,
                        onCheckedChange = { onUpdate(assistant.copy(discoverEnabled = it)) }
                    )
                }
            )

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("时间轴读写") },
                description = {
                    Text("开启后 TA 能查看时间轴、记录事件与设定提醒(默认关,选开)")
                },
                tail = {
                    Switch(
                        checked = assistant.timelineEnabled,
                        onCheckedChange = { onUpdate(assistant.copy(timelineEnabled = it)) }
                    )
                }
            )

            HorizontalDivider()

            FormItem(
                modifier = Modifier.padding(8.dp),
                label = { Text("语音通话与主动来电") },
                description = {
                    Text("开启后可在发现页 / 侧边栏与 TA 像打电话一样实时对话;TA 也可能主动来电(一天至多一两次)")
                },
                tail = {
                    Switch(
                        checked = assistant.voiceCallEnabled,
                        onCheckedChange = { onUpdate(assistant.copy(voiceCallEnabled = it)) }
                    )
                }
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_chat_model))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_chat_model_desc))
                },
                content = {
                    ModelSelector(
                        modelId = assistant.chatModelId,
                        providers = providers,
                        type = ModelType.CHAT,
                        onSelect = {
                            onUpdate(
                                assistant.copy(
                                    chatModelId = it.id
                                )
                            )
                        },
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_temperature))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_temperature_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    temperature = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                if (assistant.temperature != null) {
                    var temperatureInput by remember(assistant.id) {
                        mutableStateOf(assistant.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = temperature
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = temperatureValue == null || temperatureValue !in 0f..2f,
                        supportingText = {
                            Text("0 - 2")
                        }
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_top_p))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_top_p_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topP = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                assistant.topP?.let { topP ->
                    var topPInput by remember(assistant.id) {
                        mutableStateOf(topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                onUpdate(
                                    assistant.copy(
                                        topP = nextTopP
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = topPValue == null || topPValue !in 0f..1f,
                        supportingText = {
                            Text("0 - 1")
                        }
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_context_message_limit))
                },
                description = {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_limit_desc),
                    )
                }
            ) {
                var contextMessageLimitInput by remember(
                    assistant.id,
                    assistant.contextMessageLimit
                ) {
                    mutableStateOf(assistant.contextMessageLimit.toString())
                }
                var contextMessageLimitFocused by remember(assistant.id) {
                    mutableStateOf(false)
                }
                val focusManager = LocalFocusManager.current

                fun commitContextMessageLimit() {
                    val value = contextMessageLimitInput.toIntOrNull()
                    if (value == null) {
                        contextMessageLimitInput = assistant.contextMessageLimit.toString()
                        return
                    }

                    contextMessageLimitInput = value.toString()
                    if (value != assistant.contextMessageLimit) {
                        onUpdate(assistant.copy(contextMessageLimit = value))
                    }
                }

                OutlinedTextField(
                    value = contextMessageLimitInput,
                    onValueChange = { input ->
                        if (input.all(Char::isDigit) &&
                            (input.isEmpty() || input.toIntOrNull() != null)
                        ) {
                            contextMessageLimitInput = input
                            input.toIntOrNull()
                                ?.takeIf { it != assistant.contextMessageLimit }
                                ?.let { onUpdate(assistant.copy(contextMessageLimit = it)) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (contextMessageLimitFocused && !focusState.isFocused) {
                                commitContextMessageLimit()
                            }
                            contextMessageLimitFocused = focusState.isFocused
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.assistant_page_context_message_limit_hint)
                        )
                    }
                )

                if (assistant.contextMessageLimit > 0) {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_limit_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_stream_output))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_stream_output_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.streamOutput,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    streamOutput = it
                                )
                            )
                        }
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_split_messages))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_split_messages_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.splitMessages,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    splitMessages = it
                                )
                            )
                        }
                    )
                }
            )
            if (assistant.splitMessages) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_split_mode))
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onUpdate(assistant.copy(splitMode = "auto")) },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = if (assistant.splitMode == "auto")
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                        ) {
                            Text(stringResource(R.string.assistant_page_split_mode_auto))
                        }
                        TextButton(
                            onClick = { onUpdate(assistant.copy(splitMode = "force")) },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = if (assistant.splitMode == "force")
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                        ) {
                            Text(stringResource(R.string.assistant_page_split_mode_force))
                        }
                    }
                }
                HorizontalDivider()
                if (assistant.splitMode != "force") FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_split_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_custom_prompt_hint))
                    },
                ) {
                    OutlinedTextField(
                        value = assistant.splitPrompt ?: "",
                        onValueChange = { text ->
                            onUpdate(assistant.copy(splitPrompt = text))
                        },
                        placeholder = {
                            Text(stringResource(R.string.assistant_page_split_prompt_hint))
                        },
                        minLines = 4,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        onUpdate(assistant.copy(splitPrompt = me.rerere.rikkahub.service.SPLIT_MESSAGES_PROMPT))
                    }) {
                        Text(stringResource(R.string.assistant_page_prompt_reset))
                    }
                }
                HorizontalDivider()
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_autonomous_wake))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_autonomous_wake_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.autonomousWake,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    autonomousWake = it
                                )
                            )
                        }
                    )
                }
            )
            if (assistant.autonomousWake) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_autonomous_wake_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_custom_prompt_hint))
                    },
                ) {
                    OutlinedTextField(
                        value = assistant.wakePrompt ?: "",
                        onValueChange = { text ->
                            onUpdate(assistant.copy(wakePrompt = text))
                        },
                        placeholder = {
                            Text(stringResource(R.string.assistant_page_autonomous_wake_prompt_hint))
                        },
                        minLines = 4,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        onUpdate(assistant.copy(wakePrompt = me.rerere.rikkahub.service.AUTONOMOUS_WAKE_PROMPT))
                    }) {
                        Text(stringResource(R.string.assistant_page_prompt_reset))
                    }
                }
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_wake_daily_limit_title))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_wake_daily_limit_desc))
                    },
                ) {
                    OutlinedTextField(
                        value = if (assistant.wakeDailyLimit == 0) "" else assistant.wakeDailyLimit.toString(),
                        onValueChange = { text ->
                            val limit = text.filter { it.isDigit() }.toIntOrNull() ?: 0
                            onUpdate(assistant.copy(wakeDailyLimit = limit.coerceIn(0, 500)))
                        },
                        placeholder = { Text("20") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_show_header_info))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_show_header_info_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.showHeaderInfo,
                        onCheckedChange = {
                            onUpdate(assistant.copy(showHeaderInfo = it))
                        }
                    )
                }
            )
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_show_footer_model_info))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_show_footer_model_info_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.showFooterModelInfo,
                        onCheckedChange = {
                            onUpdate(assistant.copy(showFooterModelInfo = it))
                        }
                    )
                }
            )
            HorizontalDivider()

            // 用户头像与昵称（本助手独立）：与上方「使用助手头像」「显示头部信息」两个开关相互独立
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_user_identity_title))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_user_identity_desc))
                },
            )
            // 用户昵称
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_user_nickname_label))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_user_nickname_hint))
                },
                content = {
                    OutlinedTextField(
                        value = assistant.userNickname ?: "",
                        onValueChange = { text ->
                            onUpdate(assistant.copy(userNickname = text.ifBlank { null }))
                        },
                        placeholder = {
                            Text(
                                settings.displaySetting.userNickname.ifBlank {
                                    stringResource(R.string.user_default_name)
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            )
            HorizontalDivider()
            // 用户头像
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_user_avatar_label))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_user_identity_desc))
                },
                content = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        UIAvatar(
                            value = assistant.userAvatar ?: settings.displaySetting.userAvatar,
                            name = assistant.userNickname
                                ?: settings.displaySetting.userNickname.ifBlank {
                                    stringResource(R.string.user_default_name)
                                },
                            onUpdate = { avatar ->
                                onUpdate(assistant.copy(userAvatar = avatar))
                            },
                            modifier = Modifier.size(56.dp)
                        )
                        if (assistant.userAvatar != null || assistant.userNickname != null) {
                            TextButton(onClick = {
                                onUpdate(assistant.copy(userAvatar = null, userNickname = null))
                            }) {
                                Text(stringResource(R.string.assistant_page_user_identity_reset))
                            }
                        }
                    }
                }
            )
            HorizontalDivider()

            // 引号字体高亮（按助手独立）
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_quote_highlight_title))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_quote_highlight_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.quoteHighlight,
                        onCheckedChange = {
                            onUpdate(assistant.copy(quoteHighlight = it))
                        }
                    )
                }
            )
            if (assistant.quoteHighlight) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_quote_highlight_color))
                    },
                    content = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val colorScheme = MaterialTheme.colorScheme
                            val options: List<Triple<String, Int, androidx.compose.ui.graphics.Color>> = listOf(
                                Triple("theme", R.string.quote_color_theme, colorScheme.tertiary),
                                Triple("red", R.string.quote_color_red, Color(0xFFE57373)),
                                Triple("yellow", R.string.quote_color_yellow, Color(0xFFFFB74D)),
                                Triple("blue", R.string.quote_color_blue, Color(0xFF64B5F6)),
                                Triple("green", R.string.quote_color_green, Color(0xFF81C784)),
                            )
                            options.forEach { (key, labelRes, color) ->
                                val selected = assistant.quoteHighlightColor == key
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            onUpdate(assistant.copy(quoteHighlightColor = key))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = HugeIcons.Tick01,
                                            contentDescription = stringResource(labelRes),
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_bubble_width_title))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_bubble_width_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.bubbleMaxWidth > 0,
                        onCheckedChange = { enable ->
                            onUpdate(
                                assistant.copy(
                                    bubbleMaxWidth = if (enable) {
                                        if (assistant.bubbleMaxWidth > 0) assistant.bubbleMaxWidth else 300
                                    } else 0
                                )
                            )
                        }
                    )
                }
            )
            if (assistant.bubbleMaxWidth > 0) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_width_value, assistant.bubbleMaxWidth))
                    },
                ) {
                    Slider(
                        value = assistant.bubbleMaxWidth.toFloat(),
                        onValueChange = {
                            onUpdate(assistant.copy(bubbleMaxWidth = it.toInt().coerceIn(200, 420)))
                        },
                        valueRange = 200f..420f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
            }
            // 对话字体（每助手独立）
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text("对话字体")
                },
                description = {
                    Text("仅作用于该助手的聊天消息；自定义=导入字体文件(.ttf/.otf/.ttc)")
                },
            ) {
                val context = LocalContext.current
                val fontPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    runCatching {
                        importAssistantFontInternal(context, uri)
                    }.onSuccess { (path, name) ->
                        onUpdate(
                            assistant.copy(
                                chatFontFamily = ChatFontFamily.CUSTOM,
                                chatCustomFontPath = path,
                            )
                        )
                        android.widget.Toast.makeText(context, "字体已导入：$name", android.widget.Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        android.widget.Toast.makeText(context, "导入失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                val fontMenuExpanded = remember { androidx.compose.runtime.mutableStateOf(false) }
                val fontOptions = listOf(
                    null to "跟随全局",
                    ChatFontFamily.DEFAULT to "默认",
                    ChatFontFamily.SERIF to "衬线",
                    ChatFontFamily.MONOSPACE to "等宽",
                    ChatFontFamily.CUSTOM to "自定义",
                )
                // 每个选项用对应字体渲染，方便预览
                val customPreviewFont = assistant.chatCustomFontPath?.let { path ->
                    remember(path) {
                        runCatching {
                            androidx.compose.ui.text.font.FontFamily(
                                android.graphics.Typeface.createFromFile(
                                    java.io.File(context.filesDir, path)
                                )
                            )
                        }.getOrNull()
                    }
                }
                fun previewFontFamily(family: ChatFontFamily?): androidx.compose.ui.text.font.FontFamily? =
                    when (family) {
                        ChatFontFamily.DEFAULT -> androidx.compose.ui.text.font.FontFamily.Default
                        ChatFontFamily.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
                        ChatFontFamily.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
                        ChatFontFamily.CUSTOM -> customPreviewFont
                        null -> null
                    }
                val currentFontLabel = fontOptions.firstOrNull { it.first == assistant.chatFontFamily }?.second
                    ?: "跟随全局"
                val currentPreviewStyle = previewFontFamily(assistant.chatFontFamily)?.let {
                    androidx.compose.ui.text.TextStyle(fontFamily = it)
                }
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { fontMenuExpanded.value = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            currentFontLabel + "  ▾",
                            style = currentPreviewStyle
                                ?: androidx.compose.ui.text.TextStyle.Default,
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = fontMenuExpanded.value,
                        onDismissRequest = { fontMenuExpanded.value = false },
                    ) {
                        fontOptions.forEach { (family, label) ->
                            val previewStyle = previewFontFamily(family)?.let {
                                androidx.compose.ui.text.TextStyle(fontFamily = it)
                            }
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    val suffix = if (family == ChatFontFamily.CUSTOM &&
                                        assistant.chatCustomFontPath != null
                                    ) {
                                        "（" + assistant.chatCustomFontPath!!.substringAfterLast('/')
                                            .substringBeforeLast('.') + "）"
                                    } else ""
                                    Text(
                                        label + suffix,
                                        style = (previewStyle
                                            ?: androidx.compose.ui.text.TextStyle.Default).merge(
                                            androidx.compose.material3.MaterialTheme.typography.bodyLarge
                                        ),
                                    )
                                },
                                trailingIcon = {
                                    if (assistant.chatFontFamily == family) {
                                        Text("✓")
                                    }
                                },
                                onClick = {
                                    fontMenuExpanded.value = false
                                    if (family == ChatFontFamily.CUSTOM) {
                                        fontPickerLauncher.launch(AssistantFontMimeTypes)
                                    } else {
                                        onUpdate(assistant.copy(chatFontFamily = family, chatCustomFontPath = null))
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // 对话字体大小（每助手独立）
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text("对话字体大小")
                },
                description = {
                    Text(
                        assistant.fontSizeRatio?.let { "当前覆盖：${(it * 100).toInt()}%" }
                            ?: "跟随全局设置（拖动滑杆即覆盖，重置恢复跟随）"
                    )
                },
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Slider(
                        value = assistant.fontSizeRatio ?: 1f,
                        onValueChange = { onUpdate(assistant.copy(fontSizeRatio = it)) },
                        valueRange = 0.5f..2f,
                        steps = 11,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "${((assistant.fontSizeRatio ?: 1f) * 100).toInt()}%")
                    TextButton(
                        onClick = { onUpdate(assistant.copy(fontSizeRatio = null)) },
                        enabled = assistant.fontSizeRatio != null,
                    ) {
                        Text("重置")
                    }
                }
            }
            // 气泡样式覆盖（每助手独立）
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_bubble_override))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_bubble_override_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.bubbleOverride,
                        onCheckedChange = {
                            onUpdate(assistant.copy(bubbleOverride = it))
                        }
                    )
                }
            )
            if (assistant.bubbleOverride) {
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_assistant_color))
                    },
                ) {
                    BubbleColorPalette(
                        selected = assistant.bubbleAssistantColor,
                        onSelect = { onUpdate(assistant.copy(bubbleAssistantColor = it)) }
                    )
                }
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_user_color))
                    },
                ) {
                    BubbleColorPalette(
                        selected = assistant.bubbleUserColor,
                        onSelect = { onUpdate(assistant.copy(bubbleUserColor = it)) }
                    )
                }
                // mini 实时预览：两条气泡随下方设置即时变化
                MiniBubblePreview(assistant = assistant)
                // 气泡材质（折叠下拉）
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_material))
                    },
                ) {
                    Select(
                        options = listOf("", "frost", "glass", "droplet", "neumorph"),
                        selectedOption = assistant.bubbleMaterial,
                        onOptionSelected = { onUpdate(assistant.copy(bubbleMaterial = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        optionToString = {
                            when (it) {
                                "frost" -> stringResource(R.string.assistant_page_bubble_material_frost)
                                "glass" -> stringResource(R.string.assistant_page_bubble_material_glass)
                                "droplet" -> stringResource(R.string.assistant_page_bubble_material_droplet)
                                "neumorph" -> "实心立体"
                                else -> stringResource(R.string.assistant_page_bubble_material_default)
                            }
                        },
                    )
                }
                // 实心立体效果(仅 neumorph 材质显示):凸起=浮雕/按压前,凹陷=内刻/按压后
                if (assistant.bubbleMaterial == "neumorph") {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text("立体效果") },
                    ) {
                        Select(
                            options = listOf("raised", "sunken"),
                            selectedOption = assistant.bubbleNeuEffect,
                            onOptionSelected = { onUpdate(assistant.copy(bubbleNeuEffect = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            optionToString = {
                                when (it) {
                                    "sunken" -> "凹陷 · 按压后"
                                    else -> "凸起 · 按压前"
                                }
                            },
                        )
                    }
                }
                // 液态玻璃质感(仅 droplet 材质显示):清透=高光近透体,烟灰=吸光深体(参考 liquid-glass-note 两预设)
                if (assistant.bubbleMaterial == "droplet") {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = { Text("玻璃质感") },
                    ) {
                        Select(
                            options = listOf("clear", "smoked"),
                            selectedOption = assistant.bubbleLiquidStyle,
                            onOptionSelected = { onUpdate(assistant.copy(bubbleLiquidStyle = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            optionToString = {
                                when (it) {
                                    "smoked" -> "烟灰 · 吸光深体"
                                    else -> "清透 · 高光"
                                }
                            },
                        )
                    }
                }
                // 气泡形状（折叠下拉，含贴角圆角）
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_shape))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_bubble_shape_desc))
                    },
                ) {
                    Select(
                        options = listOf("", "rounded", "pill", "wechat", "large"),
                        selectedOption = assistant.bubbleShape,
                        onOptionSelected = { onUpdate(assistant.copy(bubbleShape = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        optionToString = {
                            when (it) {
                                "rounded" -> stringResource(R.string.setting_display_page_bubble_shape_rounded)
                                "pill" -> stringResource(R.string.setting_display_page_bubble_shape_pill)
                                "wechat" -> stringResource(R.string.setting_display_page_bubble_shape_wechat)
                                "large" -> stringResource(R.string.assistant_page_bubble_shape_large)
                                else -> stringResource(R.string.assistant_page_bubble_shape_follow_global)
                            }
                        },
                    )
                }
                // 不透明度（仅默认材质显示并生效；null = 跟随全局）
                if (assistant.bubbleMaterial.isBlank()) {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = {
                            Text(stringResource(R.string.assistant_page_bubble_opacity))
                        },
                        description = {
                            Text(stringResource(R.string.assistant_page_bubble_opacity_desc))
                        },
                    ) {
                        val globalOpacity = LocalSettings.current.displaySetting.bubbleOpacity
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Slider(
                                value = assistant.bubbleOpacity ?: globalOpacity,
                                onValueChange = { onUpdate(assistant.copy(bubbleOpacity = it)) },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.weight(1f),
                            )
                            Text(text = "${((assistant.bubbleOpacity ?: globalOpacity) * 100).toInt()}%")
                            TextButton(
                                onClick = { onUpdate(assistant.copy(bubbleOpacity = null)) },
                                enabled = assistant.bubbleOpacity != null,
                            ) {
                                Text(stringResource(R.string.assistant_page_bubble_follow_global))
                            }
                        }
                    }
                }
                // 清透度（仅玻璃材质显示并生效）
                if (assistant.bubbleMaterial == "glass") {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = {
                            Text(
                                stringResource(
                                    R.string.assistant_page_bubble_alpha,
                                    (assistant.bubbleAlpha * 100).toInt()
                                )
                            )
                        },
                        description = {
                            Text(stringResource(R.string.assistant_page_bubble_alpha_desc))
                        },
                    ) {
                        Slider(
                            value = assistant.bubbleAlpha,
                            onValueChange = {
                                onUpdate(assistant.copy(bubbleAlpha = it))
                            },
                            valueRange = 0.1f..1f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // 磨砂程度（仅磨砂材质显示并生效）
                if (assistant.bubbleMaterial == "frost") {
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = {
                            Text(stringResource(R.string.assistant_page_bubble_frost_level))
                        },
                        description = {
                            Text(stringResource(R.string.assistant_page_bubble_frost_level_desc))
                        },
                    ) {
                        Slider(
                            value = assistant.bubbleFrostLevel,
                            onValueChange = {
                                onUpdate(assistant.copy(bubbleFrostLevel = it))
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // 边框美化（助手/用户/两侧同时）
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_beautify))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_bubble_beautify_desc))
                    },
                    tail = {
                        Switch(
                            checked = assistant.bubbleBeautifyEnabled,
                            onCheckedChange = {
                                onUpdate(assistant.copy(bubbleBeautifyEnabled = it))
                            }
                        )
                    }
                )
                if (assistant.bubbleBeautifyEnabled) {
                    // 应用范围直接持久化到助手配置(assistant/user/both)
                    val beautifySide = assistant.bubbleBeautifyScope.ifBlank { "both" }
                    val beautifyCfg =
                        if (beautifySide == "user") assistant.bubbleBeautifyUser
                        else assistant.bubbleBeautifyAssistant
                    fun updateBeautify(cfg: BubbleBeautify) {
                        onUpdate(
                            when (beautifySide) {
                                "user" -> assistant.copy(bubbleBeautifyUser = cfg)
                                "both" -> assistant.copy(bubbleBeautifyAssistant = cfg, bubbleBeautifyUser = cfg)
                                else -> assistant.copy(bubbleBeautifyAssistant = cfg)
                            }
                        )
                    }
                    FormItem(
                        modifier = Modifier.padding(8.dp),
                        label = {
                            Text(stringResource(R.string.assistant_page_bubble_beautify_scope))
                        },
                    ) {
                        Select(
                            options = listOf("assistant", "user", "both"),
                            selectedOption = beautifySide,
                            onOptionSelected = { onUpdate(assistant.copy(bubbleBeautifyScope = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            optionToString = {
                                when (it) {
                                    "assistant" -> stringResource(R.string.assistant_page_bubble_beautify_scope_assistant)
                                    "user" -> stringResource(R.string.assistant_page_bubble_beautify_scope_user)
                                    else -> stringResource(R.string.assistant_page_bubble_beautify_scope_both)
                                }
                            },
                        )
                    }
                        // 贴纸（预设收纳进下拉；自定义时显示输入框）
                        val stickerPresets = listOf(
                            "=U-U=", "(=^･ω･^=)", "♪(´▽｀)", "♡", "✦", "✿",
                            "🐱", "🐶", "🐰", "🦊", "🐻", "❄️", "🍃", "⭐", "🍋",
                        )
                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = {
                                Text(stringResource(R.string.assistant_page_bubble_sticker))
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Select(
                                    options = stickerPresets + listOf("", "custom"),
                                    selectedOption = when {
                                        beautifyCfg.sticker.isBlank() -> ""
                                        beautifyCfg.sticker in stickerPresets -> beautifyCfg.sticker
                                        else -> "custom"
                                    },
                                    onOptionSelected = { sel ->
                                        updateBeautify(
                                            beautifyCfg.copy(
                                                sticker = when (sel) {
                                                    "" -> ""
                                                    // 进入自定义时给个种子值,避免输入框拿到空串无法聚焦
                                                    "custom" -> if (beautifyCfg.sticker.isBlank() || beautifyCfg.sticker in stickerPresets) "😀" else beautifyCfg.sticker
                                                    else -> sel
                                                }
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    optionToString = {
                                        when (it) {
                                            "" -> stringResource(R.string.assistant_page_bubble_sticker_none)
                                            "custom" -> stringResource(R.string.assistant_page_bubble_sticker_custom)
                                            else -> it
                                        }
                                    },
                                )
                                if (beautifyCfg.sticker.isNotBlank() && beautifyCfg.sticker !in stickerPresets) {
                                    OutlinedTextField(
                                        value = beautifyCfg.sticker,
                                        onValueChange = { updateBeautify(beautifyCfg.copy(sticker = it.take(12))) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text(stringResource(R.string.assistant_page_bubble_sticker_custom)) },
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                        // 贴纸位置（四角可选，同侧统一）
                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = {
                                Text(stringResource(R.string.assistant_page_bubble_sticker_position))
                            },
                        ) {
                            Select(
                                options = listOf("tl", "tr", "bl", "br"),
                                selectedOption = beautifyCfg.position,
                                onOptionSelected = { updateBeautify(beautifyCfg.copy(position = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                optionToString = {
                                    when (it) {
                                        "tl" -> stringResource(R.string.assistant_page_bubble_sticker_pos_tl)
                                        "bl" -> stringResource(R.string.assistant_page_bubble_sticker_pos_bl)
                                        "br" -> stringResource(R.string.assistant_page_bubble_sticker_pos_br)
                                        else -> stringResource(R.string.assistant_page_bubble_sticker_pos_tr)
                                    }
                                },
                            )
                        }
                        // 贴纸倾斜角度（有贴纸时显示）
                        if (beautifyCfg.sticker.isNotBlank()) {
                            FormItem(
                                modifier = Modifier.padding(8.dp),
                                label = {
                                    Text(
                                        stringResource(R.string.assistant_page_bubble_sticker_angle) +
                                            " ${beautifyCfg.stickerAngle.roundToInt()}°"
                                    )
                                },
                            ) {
                                Slider(
                                    value = beautifyCfg.stickerAngle,
                                    onValueChange = { updateBeautify(beautifyCfg.copy(stickerAngle = it)) },
                                    valueRange = -45f..45f,
                                    steps = 17,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        // 边框颜色（预设收纳进下拉；自定义显示取色器）
                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = {
                                Text(stringResource(R.string.assistant_page_bubble_border_color))
                            },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Select(
                                    options = listOf("", "#35312D", "#8B5E3C", "#4A69A5", "#D96A8B", "#3E7C4F", "custom"),
                                    selectedOption = if (beautifyCfg.borderColor.isBlank()) "" else "custom",
                                    onOptionSelected = {
                                        updateBeautify(
                                            beautifyCfg.copy(
                                                borderColor = when (it) {
                                                    "" -> ""
                                                    "custom" -> if (beautifyCfg.borderColor.isBlank()) "#35312D" else beautifyCfg.borderColor
                                                    else -> it
                                                }
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    optionToString = {
                                        when (it) {
                                            "" -> stringResource(R.string.assistant_page_bubble_border_color_theme)
                                            "#35312D" -> stringResource(R.string.assistant_page_bubble_border_color_ink)
                                            "#8B5E3C" -> stringResource(R.string.assistant_page_bubble_border_color_brown)
                                            "#4A69A5" -> stringResource(R.string.assistant_page_bubble_border_color_blue)
                                            "#D96A8B" -> stringResource(R.string.assistant_page_bubble_border_color_pink)
                                            "#3E7C4F" -> stringResource(R.string.assistant_page_bubble_border_color_green)
                                            else -> stringResource(R.string.assistant_page_bubble_border_color_custom)
                                        }
                                    },
                                )
                                if (beautifyCfg.borderColor.isNotBlank()) {
                                    BorderColorEditor(
                                        color = runCatching {
                                            Color(android.graphics.Color.parseColor(beautifyCfg.borderColor))
                                        }.getOrDefault(Color(0xFF35312D)),
                                        onColorChange = { c ->
                                            updateBeautify(
                                                beautifyCfg.copy(
                                                    borderColor = String.format("#%06X", 0xFFFFFF and c.toArgb())
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        // 边框颜色深浅（50%=原色，向白变浅/向黑加深）
                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = {
                                Text(
                                    stringResource(R.string.assistant_page_bubble_border_shade) +
                                        " ${(beautifyCfg.borderColorShade * 100).roundToInt()}%"
                                )
                            },
                        ) {
                            Slider(
                                value = beautifyCfg.borderColorShade,
                                onValueChange = { updateBeautify(beautifyCfg.copy(borderColorShade = it)) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // 边框粗细
                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = {
                                Text(stringResource(R.string.assistant_page_bubble_border_width))
                            },
                        ) {
                            Slider(
                                value = beautifyCfg.borderWidth,
                                onValueChange = { updateBeautify(beautifyCfg.copy(borderWidth = it)) },
                                valueRange = 0.5f..3f,
                                steps = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // 边框样式（全包/半包）
                        FormItem(
                            modifier = Modifier.padding(8.dp),
                            label = {
                                Text(stringResource(R.string.assistant_page_bubble_border_style))
                            },
                        ) {
                            Select(
                                options = listOf("full", "half-top", "half-bottom"),
                                selectedOption = beautifyCfg.borderStyle,
                                onOptionSelected = { updateBeautify(beautifyCfg.copy(borderStyle = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                optionToString = {
                                    when (it) {
                                        "half-top" -> stringResource(R.string.assistant_page_bubble_border_style_half_top)
                                        "half-bottom" -> stringResource(R.string.assistant_page_bubble_border_style_half_bottom)
                                        else -> stringResource(R.string.assistant_page_bubble_border_style_full)
                                    }
                                },
                            )
                        }
                    }
                HorizontalDivider()
            }
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_thinking_budget))
                },
            ) {
                ReasoningButton(
                    reasoningLevel = assistant.reasoningLevel,
                    onUpdateReasoningLevel = { level ->
                        onUpdate(assistant.copy(reasoningLevel = level))
                    }
                )
            }
            HorizontalDivider()
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_max_tokens))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_max_tokens_desc))
                }
            ) {
                OutlinedTextField(
                    value = assistant.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) {
                            null
                        } else {
                            text.toIntOrNull()?.takeIf { it > 0 }
                        }
                        onUpdate(
                            assistant.copy(
                                maxTokens = tokens
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                    },
                    supportingText = {
                        if (assistant.maxTokens != null) {
                            Text(stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens))
                        } else {
                            Text(stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                        }
                    }
                )
            }
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer
        ) {
            FormItem(
                modifier = Modifier.padding(8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_gradient_background))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_gradient_background_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.useGradientBackground,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useGradientBackground = it
                                )
                            )
                        }
                    )
                }
            )

            if (!assistant.useGradientBackground) {
                HorizontalDivider()

                BackgroundPicker(
                    modifier = Modifier.padding(8.dp),
                    background = assistant.background,
                    backgroundOpacity = assistant.backgroundOpacity,
                    onUpdate = { background ->
                        onUpdate(
                            assistant.copy(
                                background = background
                            )
                        )
                    }
                )
            }

            if (!assistant.useGradientBackground && assistant.background != null) {
                val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
                HorizontalDivider()
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_background_opacity))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_background_opacity_desc))
                    }
                ) {
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    backgroundOpacity = it.toFixed(2).toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
                                )
                            )
                        },
                        valueRange = 0f..1f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.assistant_page_background_opacity_value,
                            (backgroundOpacity * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

private val BubbleColorPresets: List<Pair<Long?, String>> = listOf(
    null to "主题",
    0xFFBCBCB9L to "雾灰",
    0xFFB5C09AL to "抹茶",
    0xFFA7B4C2L to "雾蓝",
    0xFF3A4048L to "墨夜",
    0xFFBBDEFBL to "蓝",
    0xFFC8E6C9L to "绿",
    0xFFFFE0B2L to "橙",
    0xFFF8BBD0L to "粉",
    0xFFE1BEE7L to "紫",
    0xFFB2EBF2L to "青",
    0xFFFFCDD2L to "红",
    0xFFD7CCC8L to "棕",
    0xFFCFD8DCL to "灰",
    0xFF1565C0L to "深蓝",
    0xFF2E7D32L to "深绿",
    // 官方主题自带气泡色（各预设主题浅色方案的助手气泡底色）
    0xFFF6E4E5L to "樱",
    0xFFE4E9ECL to "海",
    0xFFE8E9DEL to "春",
    0xFFF0E7D9L to "秋",
    0xFFEBEBEBL to "黑",
    0xFFF6F5F0L to "瓷白",
    0xFFF1F2F4L to "简",
    0xFFEDEAE0L to "Claude",
)

@Composable
private fun BubbleColorPalette(
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        BubbleColorPresets.forEach { (color, label) ->
            val bg = color?.let { androidx.compose.ui.graphics.Color(it.toLong()) }
                ?: androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(bg)
                    .border(
                        width = 2.dp,
                        color = if (selected == color) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .clickable { onSelect(color) },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (color == null) {
                    Text("T", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private val AssistantFontMimeTypes = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/octet-stream",
    "*/*",
)

private fun importAssistantFontInternal(context: Context, uri: Uri): Pair<String, String> {
    val displayName = FileUtils.getFileNameFromUri(context, uri)?.takeIf { it.isNotBlank() } ?: "assistant_font"
    val extension = displayName.substringAfterLast('.', "").lowercase()
        .takeIf { it in setOf("ttf", "otf", "ttc") } ?: "ttf"
    val fontDir = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
    val targetFile = File(fontDir, "assistant_font.${java.util.UUID.randomUUID()}.$extension")
    context.contentResolver.openInputStream(uri)?.use { input ->
        targetFile.outputStream().use { output -> input.copyTo(output) }
    } ?: error("无法读取所选字体文件")
    runCatching { Typeface.createFromFile(targetFile) }.onFailure { e ->
        targetFile.delete()
        throw IllegalArgumentException(e.message ?: "无效的字体文件", e)
    }
    val relativePath = FileUtils.getRelativePathInFilesDir(context.filesDir, targetFile)
        ?: "${FileFolders.FONTS}/${targetFile.name}"
    return relativePath to displayName
}

// ---- 气泡 mini 实时预览与边框取色器 ----

/**
 * 两条 mini 气泡（助手/用户），随上方气泡材质/形状/颜色/美化设置即时变化。
 * 静态近似渲染（无背景模糊），用于设置时直观看效果。
 */
@androidx.compose.runtime.Composable
private fun MiniBubblePreview(assistant: Assistant) {
    val shapeName = assistant.bubbleShape.ifBlank { "rounded" }
    val shape = when (shapeName) {
        "wechat" -> androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
        "pill" -> androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
        "large" -> androidx.compose.foundation.shape.RoundedCornerShape(22.dp, 5.dp, 22.dp, 22.dp)
        else -> androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    }
    val assistantColor = assistant.bubbleAssistantColor?.let { Color(it) }
        ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val userColor = assistant.bubbleUserColor?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primaryContainer
    val globalOpacity = LocalSettings.current.displaySetting.bubbleOpacity
    // r216:预览与聊天页渲染同口径(neumorph 双向柔影 / 液态玻璃边缘光学 / 深体翻白)
    fun previewFill(base: Color): Color = when (assistant.bubbleMaterial) {
        "droplet" -> if (assistant.bubbleLiquidStyle == "smoked") Color(0xFF141A26).copy(alpha = 0.30f)
        else Color.White.copy(alpha = 0.25f)
        "frost" -> base.copy(alpha = (0.40f + 0.38f * assistant.bubbleFrostLevel).coerceAtMost(0.95f))
        "glass" -> base.copy(alpha = (0.06f + 0.64f * assistant.bubbleAlpha).coerceIn(0.08f, 0.85f))
        "neumorph" -> base.copy(alpha = 1f)
        else -> base.copy(alpha = assistant.bubbleOpacity ?: globalOpacity)
    }
    @androidx.compose.runtime.Composable
    fun previewOptics(sideShape: androidx.compose.foundation.shape.RoundedCornerShape): Modifier =
        when (assistant.bubbleMaterial) {
            "neumorph" -> Modifier.neumorphShadow(
                shape = sideShape,
                raised = assistant.bubbleNeuEffect != "sunken",
            )
            "droplet" -> Modifier.liquidGlassShadow(shape = sideShape)
            else -> Modifier
        }
    fun bodyIsDark(fill: Color): Boolean = when (assistant.bubbleMaterial) {
        "droplet" -> assistant.bubbleLiquidStyle == "smoked"
        "neumorph" -> fill.luminance() < 0.45f
        else -> false
    }
    fun beautifyOf(side: String): BubbleBeautify? =
        if (!assistant.bubbleBeautifyEnabled) null
        else if (side == "assistant") assistant.bubbleBeautifyAssistant
        else assistant.bubbleBeautifyUser

    val assistantSideShape = if (shapeName == "large") {
        // 助手侧贴角圆角：左上收小(与 ChatMessage 渲染一致)
        androidx.compose.foundation.shape.RoundedCornerShape(5.dp, 22.dp, 22.dp, 22.dp)
    } else shape
    val userSideShape = if (shapeName == "large") {
        androidx.compose.foundation.shape.RoundedCornerShape(22.dp, 5.dp, 22.dp, 22.dp)
    } else shape
    val assistantFill = previewFill(assistantColor)
    val userFill = previewFill(userColor)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MiniBubbleRow(
            text = "你好，这是一条收到的消息。",
            alignStart = true,
            shape = assistantSideShape,
            color = assistantFill,
            previewModifier = previewOptics(assistantSideShape),
            darkBody = bodyIsDark(assistantFill),
            beautify = beautifyOf("assistant"),
        )
        MiniBubbleRow(
            text = "你好，这是一条发送的消息。",
            alignStart = false,
            shape = userSideShape,
            color = userFill,
            previewModifier = previewOptics(userSideShape),
            darkBody = bodyIsDark(userFill),
            beautify = beautifyOf("user"),
        )
    }
}

@androidx.compose.runtime.Composable
private fun MiniBubbleRow(
    text: String,
    alignStart: Boolean,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    color: Color,
    previewModifier: Modifier = Modifier,
    darkBody: Boolean = false,
    beautify: BubbleBeautify?,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (alignStart) Arrangement.Start else Arrangement.End,
        ) {
            androidx.compose.material3.Surface(
                shape = shape,
                color = color,
                contentColor = if (darkBody) Color(0xFFF2F5F8)
                else androidx.compose.material3.LocalContentColor.current,
                // 阴影/边缘光学画在 Surface 之前(drawBehind 在内层生效),深体翻白与聊天页一致
                modifier = previewModifier.then(
                    if (beautify != null) {
                        val c = runCatching { Color(android.graphics.Color.parseColor(beautify.borderColor)) }
                            .getOrDefault(MaterialTheme.colorScheme.outline)
                        // 与聊天渲染同规则：深浅滑杆混白/混黑
                        val shaded = when {
                            beautify.borderColorShade < 0.5f ->
                                androidx.compose.ui.graphics.lerp(c, Color.White, (0.5f - beautify.borderColorShade) * 2f)
                            beautify.borderColorShade > 0.5f ->
                                androidx.compose.ui.graphics.lerp(c, Color.Black, (beautify.borderColorShade - 0.5f) * 2f)
                            else -> c
                        }
                        Modifier.border(beautify.borderWidth.dp, shaded, shape)
                    } else Modifier
                ),
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        beautify?.takeIf { it.sticker.isNotBlank() }?.let { cfg ->
            val stickerColor = runCatching { Color(android.graphics.Color.parseColor(cfg.borderColor)) }
                .getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)
            val isEmoji = cfg.sticker.any {
                it.code >= 0x1F000 || it.code == 0x2B50 || it.code == 0x2728 || it.code in 0x2744..0x2749
            }
            val align = when (cfg.position) {
                "tl" -> androidx.compose.ui.Alignment.TopStart
                "bl" -> androidx.compose.ui.Alignment.BottomStart
                "br" -> androidx.compose.ui.Alignment.BottomEnd
                else -> androidx.compose.ui.Alignment.TopEnd
            }
            androidx.compose.material3.Text(
                text = cfg.sticker,
                fontSize = if (isEmoji) 20.sp else 11.sp,
                fontWeight = if (isEmoji) androidx.compose.ui.text.font.FontWeight.Normal
                else androidx.compose.ui.text.font.FontWeight.Bold,
                color = if (cfg.borderColor.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else stickerColor,
                modifier = Modifier
                    .align(align)
                    .graphicsLayer { rotationZ = cfg.stickerAngle }
                    .offset(
                        x = if (cfg.position.endsWith("l")) 8.dp else (-8).dp,
                        y = if (cfg.position.startsWith("t")) (if (isEmoji) (-15).dp else (-12).dp)
                        else (if (isEmoji) 13.dp else 10.dp),
                    ),
            )
        }
    }
}

/**
 * 边框取色器：色相/饱和度/亮度三滑杆 + 预览圆点（参考 SettingThemePage 的 HSL 方案）
 */
@androidx.compose.runtime.Composable
private fun BorderColorEditor(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    val hsl = remember(color) {
        FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }
    }
    var hue by remember(color) { mutableFloatStateOf(hsl[0]) }
    var saturation by remember(color) { mutableFloatStateOf(hsl[1]) }
    var lightness by remember(color) { mutableFloatStateOf(hsl[2]) }

    fun update(h: Float, s: Float, l: Float) {
        onColorChange(Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l))))
    }

    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("H", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                Slider(
                    value = hue,
                    onValueChange = { hue = it; update(hue, saturation, lightness) },
                    valueRange = 0f..360f,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("S", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it; update(hue, saturation, lightness) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("L", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(16.dp))
                Slider(
                    value = lightness,
                    onValueChange = { lightness = it; update(hue, saturation, lightness) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
