package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.model.MemoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRecallTest {

    private fun file(path: String, body: String) = MemoryFile(
        path = path,
        content = "---\nname: test\ntitle: 测试\n---\n\n$body",
        updatedAt = 0L,
    )

    private val threeBody = file(
        "/notes/books/三体-1234.md",
        """
        ## 主要人物
        - [stated] 叶文洁是个外冷内热的人,经历过背叛,对人类文明失望
        - [stated] 罗辑是面壁者,后来成了执剑人
        ## 情节
        - [stated] 黑暗森林法则由罗辑悟出
        """.trimIndent(),
    )

    @Test
    fun `提到条目里的名字时只带回那一条,不返回整篇`() {
        val hits = MemoryRecall.recall(listOf(threeBody), "你还记得叶文洁吗")

        assertEquals(1, hits.size)
        assertEquals("/notes/books/三体-1234.md", hits[0].path)
        assertEquals("主要人物", hits[0].section)
        assertTrue(hits[0].text.contains("叶文洁"))
        // 同文件的其它条目不应被带出来
        assertTrue(hits.none { it.text.contains("罗辑") })
    }

    @Test
    fun `没提到关键词时不召回`() {
        val hits = MemoryRecall.recall(listOf(threeBody), "今天天气怎么样")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `纯虚词命中不算相关`() {
        val noisy = file("/topics/misc.md", "- [stated] 什么都可以,随你安排")
        val hits = MemoryRecall.recall(listOf(noisy), "这是什么")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `profile 与 preferences 已经全文注入,不再重复召回`() {
        val profile = file(MemoryFile.PROFILE_PATH, "- [stated] 用户喜欢叶文洁这个角色")
        val hits = MemoryRecall.recall(listOf(profile), "聊聊叶文洁")
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `多个文件命中时按条目分别返回`() {
        val other = file("/people/laowang.md", "- [stated] 老王也喜欢叶文洁,借过书")
        val hits = MemoryRecall.recall(listOf(threeBody, other), "叶文洁")

        assertEquals(2, hits.size)
        assertEquals(
            setOf("/notes/books/三体-1234.md", "/people/laowang.md"),
            hits.map { it.path }.toSet(),
        )
    }

    @Test
    fun `英文条目按词匹配,少于四字符的巧合不算`() {
        val en = file("/topics/work.md", "- [stated] works at Anthropic on model safety")
        assertTrue(MemoryRecall.recall(listOf(en), "How is Anthropic doing?").isNotEmpty())
        assertTrue(MemoryRecall.recall(listOf(en), "ok").isEmpty())
    }

    @Test
    fun `条目数量与字符预算有上限`() {
        val body = (1..40).joinToString("\n") { i ->
            "- [stated] 关键词命中条目编号 $i ,后面补一段足够长的说明文字让它占用预算空间"
        }
        val many = file("/topics/keyword.md", body)
        val hits = MemoryRecall.recall(listOf(many), "关键词")

        assertTrue(hits.size <= MemoryRecall.MAX_ENTRIES)
        assertTrue(hits.sumOf { it.text.length } <= MemoryRecall.MAX_CHARS + 200)
    }

    @Test
    fun `列表符号与 stated 标记不影响匹配`() {
        val hits = MemoryRecall.recall(listOf(threeBody), "叶文洁")
        assertTrue(hits.isNotEmpty())
        // 返回的是去掉前缀的纯正文,再由注入层统一加标签
        assertTrue(hits[0].text.startsWith("叶文洁"))
    }
}
