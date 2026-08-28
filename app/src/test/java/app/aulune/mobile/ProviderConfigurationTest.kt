package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun presetProvidersIgnoreStoredProtocolOverrides() {
        val wrongOpenAiFormat = ProviderSettings(protocol = ProviderProtocol.GeminiGenerateContent)
        val wrongClaudeFormat = ProviderSettings(protocol = ProviderProtocol.OpenAiCompatible)

        assertEquals(ProviderProtocol.OpenAiCompatible, wrongOpenAiFormat.effectiveProtocol(AiProvider.Kimi))
        assertEquals(ProviderProtocol.OpenAiCompatible, wrongOpenAiFormat.effectiveProtocol(AiProvider.OpenRouter))
        assertEquals(ProviderProtocol.AnthropicMessages, wrongClaudeFormat.effectiveProtocol(AiProvider.Claude))
        assertEquals(ProviderProtocol.GeminiGenerateContent, wrongClaudeFormat.effectiveProtocol(AiProvider.Gemini))
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
    fun cloudConfigFallbackRestoresMissingProviderProfileAfterRestart() {
        val cloud = CloudAiConfig(
            provider = AiProvider.OpenRouter,
            apiKey = "saved-key",
            model = "openai/gpt-4o-mini",
            baseUrl = "https://openrouter.ai/api/v1",
            protocol = ProviderProtocol.OpenAiCompatible,
            enabled = true,
        )

        val restored = ProviderProfilesSnapshot().withCloudConfigFallback(cloud)

        assertEquals(AiProvider.OpenRouter, restored.selectedProvider)
        assertEquals("saved-key", restored.profiles.getValue(AiProvider.OpenRouter).apiKey)
        assertEquals("openai/gpt-4o-mini", restored.profiles.getValue(AiProvider.OpenRouter).model)
    }

    @Test
    fun emptyCloudConfigDoesNotChangeExistingProfiles() {
        val stored = ProviderProfilesSnapshot(
            selectedProvider = AiProvider.Kimi,
            profiles = mapOf(AiProvider.Kimi to ProviderSettings(apiKey = "profile-key", model = "kimi-k2")),
        )

        assertEquals(stored, stored.withCloudConfigFallback(CloudAiConfig()))
    }

    @Test
    fun cloudConfigFallbackNeverOverwritesAnExistingProviderProfile() {
        val stored = ProviderProfilesSnapshot(
            selectedProvider = AiProvider.Kimi,
            profiles = mapOf(AiProvider.OpenRouter to ProviderSettings(apiKey = "profile-key", model = "stored-model")),
        )
        val cloud = CloudAiConfig(provider = AiProvider.OpenRouter, apiKey = "cloud-key", model = "cloud-model")

        val restored = stored.withCloudConfigFallback(cloud)

        assertEquals(stored, restored)
    }

    @Test
    fun openAiCompatibleParserReadsOpenRouterChoices() {
        val text = LlmResponseParser.openAiText("""{"choices":[{"message":{"content":"hello"}}]}""")
        assertEquals("hello", text)
    }

    @Test
    fun openAiCompatibleParserExplainsErrorPayloadWithoutChoices() {
        val error = runCatching {
            LlmResponseParser.openAiText("""{"error":{"code":"rate_limit_exceeded","message":"额度已用尽"}}""")
        }.exceptionOrNull()
        assertTrue(error?.message?.contains("rate_limit_exceeded") == true)
        assertTrue(error?.message?.contains("额度已用尽") == true)
    }

    @Test
    fun openAiCompatibleParserRejectsEmptyChoicesAndMalformedBodyClearly() {
        val empty = runCatching { LlmResponseParser.openAiText("""{"choices":[]}""") }.exceptionOrNull()
        val malformed = runCatching { LlmResponseParser.openAiText("not-json") }.exceptionOrNull()
        assertTrue(empty?.message?.contains("空 choices") == true)
        assertTrue(malformed?.message?.contains("无法解析") == true)
    }

    @Test
    fun cloudJsonParserAcceptsFencedObjectAndRejectsInvalidJson() {
        val parsed = CloudJsonResponseParser.extractObject("```json\n{\"valuesCandidate\":\"长期方向候选\"}\n```")
        assertNotNull(parsed)
        val invalid = runCatching { CloudJsonResponseParser.extractObject("{\"x\":}") }.exceptionOrNull()
        assertNotNull(invalid)
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
