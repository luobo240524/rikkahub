package me.rerere.rikkahub.utils

/**
 * 移除字符串中的Markdown格式
 * @return 移除Markdown格式后的纯文本
 */
fun String.stripMarkdown(): String {
    return this
        // 移除代码块 (```...``` 和 `...`)
        .replace(Regex("```[\\s\\S]*?```|`[^`]*?`"), "")
        // 移除图片和链接，但保留其文本内容
        .replace(Regex("!?\\[([^\\]]+)\\]\\([^\\)]*\\)"), "$1")
        // 移除加粗和斜体 (先处理两个星号的)
        .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+?)\\*"), "$1")
        // 移除下划线
        .replace(Regex("__([^_]+?)__"), "$1")
        .replace(Regex("_([^_]+?)_"), "$1")
        // 移除删除线
        .replace(Regex("~~([^~]+?)~~"), "$1")
        // 移除标题标记 (多行模式)
        .replace(Regex("(?m)^#+\\s*"), "")
        // 移除列表标记 (多行模式)
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        // 移除引用标记 (多行模式)
        .replace(Regex("(?m)^>\\s*"), "")
        // 移除水平分割线
        .replace(Regex("(?m)^(\\s*[-*_]){3,}\\s*$"), "")
        // 将多个换行符压缩，以保留段落
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/** 动作/神态旁白特征词:括号段内容命中才剥,防误伤真实内容(如「(下周三)见」) */
private val STAGE_DIRECTION_PATTERN = Regex(
    "笑|叹|喘|咳|嗓|呸|停顿|沉默|温柔|轻声|小声|低声|耳语|摇头|点头|低头|抬头|耸肩|眨眼|挑眉|皱眉|脸红|抿|咬|捂|挥|凑近|靠近|起身|坐下|抱|牵|递|倒水|敲|推门|脚步|哭|哽咽|抽泣|啜泣|语塞|深吸|屏住|语气|眼神|张嘴|闭嘴|吸溜|打响指|歪头|托腮|挠头|摊手|耸了耸|顿了顿|笑了笑|叹了口气"
)

/**
 * 通话朗读保险:剥动作/神态旁白(r274验收:提示词已禁,模型偶尔仍写,星号段/短括号注记会被念出声)。
 * 保守规则:*…*/**…** 短段(≤20字)整段去掉;括号段只在内容命中旁白词表时去掉;其余原样保留。
 */
fun String.stripVoiceStageDirections(): String {
    return this
        .replace(Regex("\\*\\*([^*\\n]{1,24}?)\\*\\*"), "$1") // **强调** 保留内容只去标记
        .replace(Regex("\\*([^*\\n]{1,20}?)\\*"), "") // *动作* 短单星段按旁白整段去掉
        .replace(Regex("\\*+"), "") // 残余星号只去标记留内容
        .replace(Regex("[（(]([^（）()\\n]{1,16})[)）]")) { m ->
            if (STAGE_DIRECTION_PATTERN.containsMatchIn(m.groupValues[1])) "" else m.value
        }
        .replace(Regex("[【〔]([^【】〔〕\\n]{1,16})[】〕]")) { m ->
            if (STAGE_DIRECTION_PATTERN.containsMatchIn(m.groupValues[1])) "" else m.value
        }
        .replace(Regex("\\s{3,}"), "  ")
        .trim()
}

fun String.extractThinkingTitle(): String? {
    // 按行分割文本
    val lines = this.lines()

    // 从后往前查找最后一个符合条件的加粗文本行
    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()

        // 检查是否为加粗格式且独占一整行
        val boldPattern = Regex("^\\*\\*(.+?)\\*\\*$")
        val match = boldPattern.find(line)

        if (match != null) {
            // 返回加粗标记内的文本内容
            return match.groupValues[1].trim().takeUnless { it.isBlank() }
        }
    }

    return null
}
