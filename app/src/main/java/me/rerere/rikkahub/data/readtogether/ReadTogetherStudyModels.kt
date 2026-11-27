package me.rerere.rikkahub.data.readtogether

import kotlinx.serialization.Serializable

/**
 * 「让 TA 读一章/整本」的学习产物模型。
 * 全部存 <bookId>/ 下的 JSON,与 annotations.json 同目录,删书自动清理。
 */

/** 章节摘要(每章一条,按 chapterIndex 覆盖) */
@Serializable
data class ChapterDigest(
    val chapterIndex: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int,
    val sourceHash: String = "",
    val plot: String = "",
    val characters: List<String> = emptyList(),
    val threads: List<String> = emptyList(),
    val quotes: List<String> = emptyList(),
    val tone: String = "",
    val createdAt: Long = 0,
    val model: String = "",
)

/** TA/用户的一条读书笔记(kind: knowledge / insight / book_summary) */
@Serializable
data class ReadNote(
    val id: String = "",
    val chapterIndex: Int = 0,
    val page: Int = 0,
    val kind: String = "insight",
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val author: String = "ai",
    val createdAt: Long = 0,
)

/** TA 起草、等用户确认的技能提案 */
@Serializable
data class SkillProposal(
    val id: String = "",
    val chapterIndex: Int = 0,
    val name: String,
    val description: String,
    val whenToUse: String = "",
    val skillMd: String = "",
    val status: String = "pending", // pending / created / dismissed
    val createdAt: Long = 0,
)

// ---------- 模型输出 DTO(解析容错,字段全给默认值) ----------

@Serializable
data class StudyRecap(
    val plot: String = "",
    val characters: List<String> = emptyList(),
    val threads: List<String> = emptyList(),
    val quotes: List<String> = emptyList(),
    val tone: String = "",
)

@Serializable
data class StudyNote(
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class StudyMemory(
    val title: String = "",
    val content: String = "",
)

@Serializable
data class StudySkill(
    val name: String = "",
    val description: String = "",
    val whenToUse: String = "",
    val steps: List<String> = emptyList(),
    val pitfalls: List<String> = emptyList(),
)

/** 单章分析结果;sections 都可空,空则不产出对应产物 */
@Serializable
data class StudyPayload(
    val recap: StudyRecap? = null,
    val notes: List<StudyNote> = emptyList(),
    val memory: StudyMemory? = null,
    val skill: StudySkill? = null,
)

/** 整本合并结果 */
@Serializable
data class BookMergePayload(
    val summary: String = "",
    val memory: StudyMemory? = null,
)
