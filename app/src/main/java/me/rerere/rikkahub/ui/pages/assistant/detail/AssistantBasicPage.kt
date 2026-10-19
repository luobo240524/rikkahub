package me.rerere.rikkahub.ui.pages.assistant.detail

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ChatFontFamily
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
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
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_glass))
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("none" to R.string.assistant_page_bubble_glass_none,
                            "frost" to R.string.assistant_page_bubble_glass_frost,
                            "liquid" to R.string.assistant_page_bubble_glass_liquid).forEach { (mode, label) ->
                            TextButton(
                                onClick = { onUpdate(assistant.copy(bubbleGlassStyle = mode)) },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = if (assistant.bubbleGlassStyle == mode)
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                            ) {
                                Text(stringResource(label))
                            }
                        }
                    }
                }
                FormItem(
                    modifier = Modifier.padding(8.dp),
                    label = {
                        Text(stringResource(R.string.assistant_page_bubble_material))
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("" to R.string.assistant_page_bubble_material_default,
                            "frost" to R.string.assistant_page_bubble_material_frost,
                            "glass" to R.string.assistant_page_bubble_material_glass,
                            "droplet" to R.string.assistant_page_bubble_material_droplet,
                            "frosted" to R.string.assistant_page_bubble_material_frosted).forEach { (mat, label) ->
                            TextButton(
                                onClick = { onUpdate(assistant.copy(bubbleMaterial = mat)) },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = if (assistant.bubbleMaterial == mat)
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                            ) {
                                Text(stringResource(label))
                            }
                        }
                    }
                }
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
