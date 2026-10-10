package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.stickers.Sticker
import me.rerere.rikkahub.ui.components.richtext.ZoomableAsyncImage
import me.rerere.rikkahub.ui.context.LocalToaster

private val PASTE_URL_TOKEN = Regex("""https?://\S+""")

/**
 * 解析批量粘贴文本：每行抓取 http 开头 token 作为直链，
 * 删除直链后的剩余文字清理分隔符后整体作为含义描述（位置不限前后）。
 */
fun parseStickerPasteLines(text: String): List<Pair<String, String>> {
    return text.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isBlank()) return@mapNotNull null
        val url = PASTE_URL_TOKEN.find(line)?.value ?: return@mapNotNull null
        var desc = line.replace(url, " ")
        desc = desc.replace(Regex("""[\s|｜,，;；\-—、]+"""), " ").trim()
        url to desc
    }
}

/**
 * 贴纸面板：展示贴纸库、点击加入输入框待发送、相册导入（先选图再填描述）、批量直链导入、长按删除。
 */
@Composable
fun StickerPanel(
    stickers: List<Sticker>,
    stickerUrl: (Sticker) -> String,
    onPick: (Sticker) -> Unit,
    onImportUrls: (entries: List<Pair<String, String>>) -> Unit,
    onImportUri: (uri: android.net.Uri, description: String) -> Unit,
    onRemove: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var pasteText by remember { mutableStateOf("") }
    // 相册选图后待确认的图片，弹出含义描述编辑框
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingDesc by remember { mutableStateOf("") }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            pendingDesc = ""
        }
    }

    // 相册导入确认对话框：预览 + 必填含义描述
    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text(stringResource(R.string.sticker_panel_desc_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoomableAsyncImage(
                        model = uri.toString(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    OutlinedTextField(
                        value = pendingDesc,
                        onValueChange = { pendingDesc = it },
                        placeholder = { Text(stringResource(R.string.sticker_panel_desc_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pendingDesc.isNotBlank(),
                    onClick = {
                        onImportUri(uri, pendingDesc)
                        pendingUri = null
                        pendingDesc = ""
                    },
                ) {
                    Text(stringResource(R.string.sticker_panel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUri = null }) {
                    Text(stringResource(R.string.sticker_panel_cancel))
                }
            },
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                placeholder = {
                    Text(
                        stringResource(R.string.sticker_panel_paste_hint),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                textStyle = MaterialTheme.typography.labelMedium,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                enabled = pasteText.isNotBlank(),
                onClick = {
                    val entries = parseStickerPasteLines(pasteText)
                    pasteText = ""
                    if (entries.isEmpty()) {
                        toaster.show(
                            message = context.getString(R.string.sticker_panel_no_url_found),
                            type = ToastType.Error,
                        )
                    } else {
                        onImportUrls(entries)
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.sticker_panel_add))
            }

            TextButton(
                onClick = {
                    pickMedia.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
            ) {
                Icon(
                    imageVector = HugeIcons.Add01,
                    contentDescription = stringResource(R.string.sticker_panel_import_gallery),
                    modifier = Modifier.size(18.dp),
                )
                Text(stringResource(R.string.sticker_panel_import_gallery))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = {
                                    onPick(sticker)
                                },
                                onLongClick = {
                                    onRemove(sticker)
                                    toaster.show(
                                        message = context.getString(R.string.sticker_panel_removed),
                                        type = ToastType.Success,
                                    )
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        ZoomableAsyncImage(
                            model = stickerUrl(sticker),
                            contentDescription = sticker.description,
                            modifier = Modifier.size(60.dp),
                        )
                    }
                }
            }
        }
    }
}
