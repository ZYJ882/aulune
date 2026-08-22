package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigurationTest {
    @Test
    fun providerDefaultsCoverRequestedCloudServices() {
        assertEquals("https://api.z.ai/api/paas/v4", AiProvider.Zhipu.defaultBaseUrl)
        assertEquals("https://api.moonshot.ai/v1", AiProvider.Kimi.defaultBaseUrl)
        assertEquals("https://openrouter.ai/api/v1", AiProvider.OpenRouter.defaultBaseUrl)
        assertEquals(ProviderProtocol.OpenAiCompatible, AiProvider.Zhipu.protocol)
        assertEquals(ProviderProtocol.OpenAiCompatible, AiProvider.Custom.protocol)
    }

    @Test
    fun customProviderCanOverrideProtocolEndpointAndModel() {
        val settings = ProviderSettings(
            apiKey = "test-key",
            model = "company-model",
            baseUrl = "https://gateway.example/v1",
            protocol = ProviderProtocol.GeminiGenerateContent
        )
        assertEquals("company-model", settings.effectiveModel(AiProvider.Custom))
        assertEquals("https://gateway.example/v1", settings.effectiveBaseUrl(AiProvider.Custom))
        assertEquals(ProviderProtocol.GeminiGenerateContent, settings.effectiveProtocol(AiProvider.Custom))
    }

    @Test
    fun normalizesChatAndModelCatalogEndpoints() {
        assertEquals("https://api.example/v1/chat/completions", LlmProtocolSupport.endpointUrl("https://api.example/v1/", "chat/completions"))
        assertEquals("https://api.example/v1/chat/completions", LlmProtocolSupport.endpointUrl("https://api.example/v1/chat/completions", "chat/completions"))
        assertEquals("https://api.example/v1/models", LlmProtocolSupport.modelsUrl("https://api.example/v1/chat/completions"))
        assertEquals("https://api.example/v1/models", LlmProtocolSupport.modelsUrl("https://api.example/v1/messages"))
    }

    @Test
    fun parsesCommonAndGeminiModelCatalogResponses() {
        val common = LlmProtocolSupport.dataModelIds("""{"data":[{"id":"b-model"},{"id":"a-model"},{"id":"a-model"},{"id":""}]}""")
        assertEquals(listOf("a-model", "b-model"), common.map { it.id })

        val gemini = LlmProtocolSupport.geminiModelIds("""{"models":[{"name":"models/gemini-2.5-flash"},{"name":"models/gemini-2.5-pro"}]}""")
        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-pro"), gemini.map { it.id })
    }

    @Test
    fun profileCodecRoundTripsProviderSettings() {
        val snapshot = ProviderProfilesSnapshot(
            selectedProvider = AiProvider.OpenRouter,
            profiles = mapOf(
                AiProvider.OpenRouter to ProviderSettings(
                    apiKey = " test-key ",
                    model = " openai/gpt-4o-mini ",
                    baseUrl = " https://gateway.example/api/v1 ",
                    protocol = ProviderProtocol.OpenAiCompatible
                )
            )
        )
        val decoded = ProviderProfilesCodec.decode(ProviderProfilesCodec.encode(snapshot), "OpenRouter")
        assertEquals(AiProvider.OpenRouter, decoded.selectedProvider)
        assertEquals("test-key", decoded.profiles.getValue(AiProvider.OpenRouter).apiKey)
        assertEquals("openai/gpt-4o-mini", decoded.profiles.getValue(AiProvider.OpenRouter).model)
        assertEquals("https://gateway.example/api/v1", decoded.profiles.getValue(AiProvider.OpenRouter).baseUrl)
        assertTrue(decoded.profiles.getValue(AiProvider.OpenRouter).protocol == ProviderProtocol.OpenAiCompatible)
    }
}
