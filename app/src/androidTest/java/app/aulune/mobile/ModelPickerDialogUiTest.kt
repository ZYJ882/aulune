package app.aulune.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelPickerDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modelPickerSearchesScrollableListAndReturnsSelectedModel() {
        var selected = ""
        composeRule.setContent {
            MaterialTheme {
                ModelPickerDialog(
                    models = listOf("openai/gpt-4o-mini", "qwen/qwen3-32b", "anthropic/claude-3-7-sonnet"),
                    selectedModel = "",
                    onDismiss = {},
                    onSelect = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("选择模型").assertIsDisplayed()
        composeRule.onNodeWithText("搜索模型").performTextInput("qwen")
        composeRule.onNodeWithText("qwen/qwen3-32b").performClick()
        assertEquals("qwen/qwen3-32b", selected)
    }
}
