package me.rerere.rikkahub.data.readtogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyResultParserTest {

    @Test
    fun `parses plain json payload`() {
        val raw = """
            {"recap":{"plot":"主角出发","characters":["阿青"],"threads":["旧信"],"quotes":["走吧"],"tone":"沉静"},
             "notes":[{"title":"留白","content":"没说完的话才是重点","tags":["写作"]}],
             "memory":{"title":"叙事","content":"留白比解释更有力"},
             "skill":{"name":"scene-break","description":"用留白收束场景","whenToUse":"收尾","steps":["停在动作前"],"pitfalls":["别解释"]}}
        """.trimIndent()
        val payload = StudyResultParser.parse(raw)

        assertNotNull(payload)
        assertEquals("主角出发", payload!!.recap?.plot)
        assertEquals(1, payload.notes.size)
        assertEquals("scene-break", payload.skill?.name)
        assertFalse(StudyResultParser.isEmpty(payload))
    }

    @Test
    fun `strips markdown fence and surrounding chatter`() {
        val raw = "好的，这是结果：\n```json\n{\"recap\":{\"plot\":\"雨夜\"},\"notes\":[]}\n```\n希望有用。"
        val payload = StudyResultParser.parse(raw)

        assertNotNull(payload)
        assertEquals("雨夜", payload!!.recap?.plot)
    }

    @Test
    fun `tolerates trailing comma`() {
        val raw = "{\"recap\":{\"plot\":\"结局\",},\"notes\":[],}"
        val payload = StudyResultParser.parse(raw)

        assertNotNull(payload)
        assertEquals("结局", payload!!.recap?.plot)
    }

    @Test
    fun `returns null on garbage`() {
        assertNull(StudyResultParser.parse("模型什么也没说"))
    }

    @Test
    fun `detects empty payload`() {
        val payload = StudyResultParser.parse("{\"recap\":null,\"notes\":[],\"memory\":null,\"skill\":null}")
        assertNotNull(payload)
        assertTrue(StudyResultParser.isEmpty(payload!!))
    }

    @Test
    fun `parses merge payload`() {
        val payload = StudyResultParser.parseMerge("{\"summary\":\"全书主线\",\"memory\":{\"title\":\"主题\",\"content\":\"记住这点\"}}")
        assertNotNull(payload)
        assertEquals("全书主线", payload!!.summary)
    }
}
