package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerPolicyTest {
    @Test
    fun modelPickerDeduplicatesSortsAndSearchesCaseInsensitively() {
        val models = listOf("openai/gpt-4o-mini", "Qwen/Qwen3-32B", "openai/GPT-4O-mini", "anthropic/claude-3-7-sonnet")

        val all = filterRemoteModels(models, query = "")
        val qwen = filterRemoteModels(models, query = "qWeN")

        assertEquals(listOf("anthropic/claude-3-7-sonnet", "openai/gpt-4o-mini", "Qwen/Qwen3-32B"), all)
        assertEquals(listOf("Qwen/Qwen3-32B"), qwen)
    }

    @Test
    fun modelPickerTrimsBlankRowsAndReturnsEmptyForNoMatch() {
        val models = listOf(" ", "openai/gpt-4o-mini", "")

        assertTrue(filterRemoteModels(models, query = "claude").isEmpty())
        assertEquals(listOf("openai/gpt-4o-mini"), filterRemoteModels(models, query = ""))
    }
}
