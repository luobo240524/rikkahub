package me.rerere.rikkahub.ui.hooks

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

class ChatInputState {
    val textContent = TextFieldState()
    var messageContent by mutableStateOf(listOf<UIMessagePart>())
    var editingMessage by mutableStateOf<Uuid?>(null)

    /** 编辑中的思考内容:被编辑消息恰好含一个无签名思考块时非空,输入栏横幅出「编辑思考内容」入口 */
    var editingReasoning by mutableStateOf<TextFieldState?>(null)
        private set
    private var editingParts: List<UIMessagePart>? = null
    private var editingAttachmentUrls: Set<String> = emptySet()

    /** 引用的消息（长按气泡设置，发送时转为引用文本） */
    var quotedMessage by mutableStateOf<UIMessage?>(null)

    fun setQuote(message: UIMessage?) {
        quotedMessage = message
    }

    private fun quoteText(): String? {
        val quote = quotedMessage ?: return null
        val content = quote.parts.filterIsInstance<UIMessagePart.Text>()
            .joinToString("\n") { it.text }
            .trim()
            .take(500)
        // Markdown 引用块形式并入，不添加文字标识
        return if (content.isBlank()) null else "> ${content.replace("\n", "\n> ")}\n\n"
    }

    fun clearInput() {
        textContent.setTextAndPlaceCursorAtEnd("")
        messageContent = emptyList()
        editingMessage = null
        editingReasoning = null
        editingParts = null
        editingAttachmentUrls = emptySet()
    }

    fun isEditing() = editingMessage != null

    fun setMessageText(text: String) {
        textContent.setTextAndPlaceCursorAtEnd(text)
    }

    fun appendText(content: String) {
        textContent.setTextAndPlaceCursorAtEnd(textContent.text.toString() + content)
    }

    fun setContents(contents: List<UIMessagePart>) {
        val lastTextIndex = contents.indexOfLast { it is UIMessagePart.Text }
        val text = if (lastTextIndex >= 0) {
            (contents[lastTextIndex] as UIMessagePart.Text).text
        } else {
            ""
        }
        textContent.setTextAndPlaceCursorAtEnd(text)
        val reasoning = contents.editableReasoningOrNull()
        editingReasoning = reasoning?.let { TextFieldState(it.reasoning) }
        messageContent = contents.filter {
            it !is UIMessagePart.Text && (reasoning == null || it !is UIMessagePart.Reasoning)
        }
        editingParts = contents
        editingAttachmentUrls = contents.mapNotNull { it.attachmentUrlOrNull() }.toSet()
    }

    fun getContents(): List<UIMessagePart> {
        val text = textContent.text.toString()
        if (isEditing()) {
            val originalParts = editingParts
            if (originalParts != null) {
                val editedTextIndex = originalParts.indexOfLast { it is UIMessagePart.Text }
                val remainingAttachments = messageContent.toMutableList()
                val merged = mutableListOf<UIMessagePart>()

                originalParts.forEachIndexed { index, part ->
                    when {
                        index == editedTextIndex -> {
                            if (text.isNotBlank()) {
                                merged.add(UIMessagePart.Text(text))
                            }
                        }

                        part is UIMessagePart.Reasoning && editingReasoning != null -> {
                            val edited = editingReasoning
                            if (edited != null) {
                                val reasoning = edited.text.toString()
                                if (reasoning.isNotBlank()) {
                                    // copy 保留 createdAt/finishedAt,展示列表 key 不变
                                    merged.add(part.copy(reasoning = reasoning))
                                }
                            }
                        }

                        part is UIMessagePart.Text -> {
                            if (part.text.isNotBlank()) {
                                merged.add(part)
                            }
                        }

                        else -> {
                            val currentIndex = remainingAttachments.indexOf(part)
                            if (currentIndex >= 0) {
                                merged.add(remainingAttachments.removeAt(currentIndex))
                            }
                        }
                    }
                }
                if (editedTextIndex < 0 && text.isNotBlank()) {
                    merged.add(0, UIMessagePart.Text(text))
                }
                // Newly added attachments are appended in insertion order.
                merged.addAll(remainingAttachments)
                return merged
            }
            return if (text.isBlank()) messageContent else listOf(UIMessagePart.Text(text)) + messageContent
        }
        // 引用：把引用内容作为前缀文本并入发送内容，发送后清除
        val quotePrefix = quoteText()
        val quoted = quotedMessage
        if (quotePrefix != null) {
            quotedMessage = null
            return if (text.isBlank()) {
                listOf(UIMessagePart.Text(quotePrefix.trimEnd())) + messageContent
            } else {
                listOf(UIMessagePart.Text(quotePrefix + text)) + messageContent
            }
        }
        return if (text.isBlank()) messageContent else listOf(UIMessagePart.Text(text)) + messageContent
    }

    fun isEmpty(): Boolean {
        return textContent.text.isBlank() && messageContent.isEmpty()
    }

    fun addImages(uris: List<Uri>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach { uri ->
            newMessage.add(UIMessagePart.Image(uri.toString()))
        }
        messageContent = newMessage
    }

    fun addVideos(uris: List<Uri>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach { uri ->
            newMessage.add(UIMessagePart.Video(uri.toString()))
        }
        messageContent = newMessage
    }

    fun addAudios(uris: List<Uri>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach { uri ->
            newMessage.add(UIMessagePart.Audio(uri.toString()))
        }
        messageContent = newMessage
    }

    fun addFiles(uris: List<UIMessagePart.Document>) {
        val newMessage = messageContent.toMutableList()
        uris.forEach {
            newMessage.add(it)
        }
        messageContent = newMessage
    }

    /** 添加贴纸到待发送附件（带 sticker 标记，渲染为大图） */
    fun addSticker(part: UIMessagePart.Image) {
        messageContent = messageContent + part
    }

    /**
     * 仅删除当前输入组件临时新增的本地文件。
     * 编辑历史消息时，原有附件不在这里删除，由会话层统一做差异清理。
     */
    fun shouldDeleteFileOnRemove(part: UIMessagePart): Boolean {
        val url = part.attachmentUrlOrNull() ?: return false
        if (!url.startsWith("file:")) return false
        return !isEditing() || url !in editingAttachmentUrls
    }

    /**
     * 放宽守卫:忽略工具类部件(Tool/ToolCall/ToolResult/ServerTool)后,剩余部件必须全是
     * Text/Reasoning 且恰好一个思考块,才允许编辑思考内容;带 metadata 的思考块
     * (Claude/Gemini 思考签名、OpenAI encrypted_content)不可编辑——改写后回传签名会失效。
     */
    private fun List<UIMessagePart>.editableReasoningOrNull(): UIMessagePart.Reasoning? {
        val visible = filter {
            it !is UIMessagePart.Tool && it !is UIMessagePart.ToolCall &&
                it !is UIMessagePart.ToolResult && it !is UIMessagePart.ServerTool
        }
        if (!visible.all { it is UIMessagePart.Text || it is UIMessagePart.Reasoning }) return null
        return visible.filterIsInstance<UIMessagePart.Reasoning>()
            .singleOrNull()
            ?.takeIf { it.metadata.isNullOrEmpty() }
    }

    private fun UIMessagePart.attachmentUrlOrNull(): String? {
        return when (this) {
            is UIMessagePart.Image -> this.url
            is UIMessagePart.Video -> this.url
            is UIMessagePart.Audio -> this.url
            is UIMessagePart.Document -> this.url
            else -> null
        }
    }
}
