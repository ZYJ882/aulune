package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiConfigurationTest {
    @Test
    fun blankKeyNeverEnablesCloudAiOrInheritsAnotherProviderKey() {
        val previous = CloudAiConfig(
            provider = AiProvider.OpenAI,
            apiKey = "openai-secret",
            enabled = true,
        )

        val switched = normalizedCloudAiConfig(
            provider = AiProvider.Gemini,
            apiKey = "   ",
            model = "",
            baseUrl = "",
            enable = true,
        )

        assertTrue(previous.isUsable)
        assertEquals(AiProvider.Gemini, switched.provider)
        assertEquals("", switched.apiKey)
        assertFalse(switched.enabled)
        assertFalse(switched.isUsable)
        assertEquals(AiProvider.Gemini.defaultModel, switched.model)
        assertEquals(AiProvider.Gemini.defaultBaseUrl, switched.baseUrl)
    }

    @Test
    fun validKeyEnablesOnlyTheCurrentProviderAndNormalizesFields() {
        val config = normalizedCloudAiConfig(
            provider = AiProvider.OpenRouter,
            apiKey = "  router-secret  ",
            model = "  openai/gpt-4o-mini  ",
            baseUrl = " https://openrouter.ai/api/v1/ ",
            protocol = ProviderProtocol.AnthropicMessages,
            enable = true,
        )

        assertEquals(AiProvider.OpenRouter, config.provider)
        assertEquals("router-secret", config.apiKey)
        assertEquals("openai/gpt-4o-mini", config.model)
        assertEquals("https://openrouter.ai/api/v1/", config.baseUrl)
        assertEquals(ProviderProtocol.AnthropicMessages, config.protocol)
        assertTrue(config.enabled)
        assertTrue(config.isUsable)
    }
}
