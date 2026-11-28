package me.rerere.rikkahub.data.readtogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadTogetherChapteringTest {

    @Test
    fun `chunkIntoPages maps every paragraph to the page it starts on`() {
        val paragraphs = listOf("a".repeat(400), "b".repeat(400), "c".repeat(400))
        val paged = ReadTogetherChaptering.chunkIntoPages(paragraphs, target = 900)

        assertEquals(2, paged.pages.size)
        assertEquals(listOf(1, 1, 2), paged.paragraphPage)
        assertTrue(paged.pages[0].startsWith("a"))
    }

    @Test
    fun `txt headings split chapters and keep preface`() {
        val paragraphs = listOf(
            "这本书献给所有夜行的人。",
            "第一章 起点",
            "正文一",
            "正文二",
            "第二章 远方",
            "正文三",
        )
        val spans = ReadTogetherChaptering.txtSpans(paragraphs)

        assertEquals(3, spans.size)
        assertEquals("开篇", spans[0].title)
        assertEquals("第一章 起点", spans[1].title)
        assertEquals(1, spans[1].startParagraph)
        assertEquals(4, spans[1].endParagraph)
        assertEquals("第二章 远方", spans[2].title)
    }

    @Test
    fun `txt without headings returns empty so caller can fall back to page blocks`() {
        val paragraphs = listOf("普通正文", "没有章节标题")
        assertTrue(ReadTogetherChaptering.txtSpans(paragraphs).isEmpty())
    }

    @Test
    fun `epub docs become chapters with title fallback`() {
        val docs = listOf(
            "第一章 春" to listOf("p1", "p2"),
            "" to listOf("p3"),
            "空章" to emptyList(),
        )
        val spans = ReadTogetherChaptering.epubSpans(docs)

        assertEquals(2, spans.size)
        assertEquals("第一章 春", spans[0].title)
        assertEquals(0, spans[0].startParagraph)
        assertEquals(2, spans[0].endParagraph)
        assertEquals("第2节", spans[1].title)
        assertEquals(2, spans[1].startParagraph)
        assertEquals(3, spans[1].endParagraph)
    }

    @Test
    fun `page block chapters cover all pages`() {
        val chapters = ReadTogetherChaptering.pageBlockChapters(25)

        assertEquals(3, chapters.size)
        assertEquals(1, chapters[0].startPage)
        assertEquals(10, chapters[0].endPage)
        assertEquals(21, chapters[2].startPage)
        assertEquals(25, chapters[2].endPage)
    }

    @Test
    fun `infer chapters from stored pages detects headings and preface`() {
        val pages = listOf(
            "前言\n一些说明",
            "第一章 开始\n正文",
            "继续正文",
            "第二章 结束\n正文",
        )
        val chapters = ReadTogetherChaptering.inferChaptersFromPages(pages)

        assertEquals(3, chapters.size)
        assertEquals("开篇", chapters[0].title)
        assertEquals(1, chapters[0].startPage)
        assertEquals(1, chapters[0].endPage)
        assertEquals("第一章 开始", chapters[1].title)
        assertEquals(2, chapters[1].startPage)
        assertEquals(3, chapters[1].endPage)
        assertEquals("第二章 结束", chapters[2].title)
        assertEquals(4, chapters[2].startPage)
    }

    @Test
    fun `pdf visual lines merge until sentence-ending punctuation`() {
        val lines = listOf(
            "在人口密聚的城市里，有这样一个宁静的去处，像是上",
            "帝的苦心安排。",
        )
        val result = ReadTogetherChaptering.joinPdfVisualLines(lines)

        assertEquals(
            listOf("在人口密聚的城市里，有这样一个宁静的去处，像是上帝的苦心安排。"),
            result,
        )
    }

    @Test
    fun `pdf short lines like headings stay separate paragraphs`() {
        val lines = listOf(
            "我与地坛",
            "自从那个下午我无意中进了这园子，就再没长久地离",
            "开过它。",
        )
        val result = ReadTogetherChaptering.joinPdfVisualLines(lines)

        assertEquals(
            listOf("我与地坛", "自从那个下午我无意中进了这园子，就再没长久地离开过它。"),
            result,
        )
    }

    @Test
    fun `pdf lines ending with comma continue into next line`() {
        val lines = listOf(
            "两条腿残废后的最初几年，我找不到工作，找不到去路，",
            "忽然间几乎什么都找不到了。",
        )
        val result = ReadTogetherChaptering.joinPdfVisualLines(lines)

        assertEquals(
            listOf("两条腿残废后的最初几年，我找不到工作，找不到去路，忽然间几乎什么都找不到了。"),
            result,
        )
    }

    @Test
    fun `pdf ascii words joined with space across lines`() {
        val lines = listOf(
            "The quick brown fox jumps over the lazy",
            "dog near the river bank yesterday.",
        )
        val result = ReadTogetherChaptering.joinPdfVisualLines(lines)

        assertEquals(listOf("The quick brown fox jumps over the lazy dog near the river bank yesterday."), result)
    }

    @Test
    fun `pdf single line returned as is`() {
        assertEquals(listOf("只有一行"), ReadTogetherChaptering.joinPdfVisualLines(listOf("只有一行")))
    }
}
