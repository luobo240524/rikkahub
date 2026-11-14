package me.rerere.tts.provider

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class TTSProviderSettingDeepSeekTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testDeepSeekSerialization() {
        val setting = TTSProviderSetting.DeepSeek(
            id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            name = "Test DeepSeek",
            apiKey = "test-key",
            baseUrl = "https://api.deepseek.com",
            voiceId = "tide",
            format = "pcm",
        )

        val encoded = json.encodeToString(TTSProviderSetting.serializer(), setting)
        assertTrue(encoded.contains("\"deepseek\""))
        assertTrue(encoded.contains("\"test-key\""))
        assertTrue(encoded.contains("\"tide\""))
        assertTrue(encoded.contains("\"pcm\""))

        val decoded = json.decodeFromString(
            TTSProviderSetting.serializer(),
            encoded
        ) as TTSProviderSetting.DeepSeek

        assertEquals(setting.id, decoded.id)
        assertEquals(setting.apiKey, decoded.apiKey)
        assertEquals(setting.baseUrl, decoded.baseUrl)
        assertEquals(setting.voiceId, decoded.voiceId)
        assertEquals(setting.format, decoded.format)
    }
}
