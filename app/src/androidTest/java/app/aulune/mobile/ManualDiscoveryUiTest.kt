package app.aulune.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManualDiscoveryUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun otherPublicSourcesConsoleOnlyOffersExplicitClickTrigger() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                BackgroundDiscoveryCard(
                    state = BackgroundDiscoveryUiState(),
                    onRunNow = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithText("其他公开来源").assertIsDisplayed()
        composeRule.onNodeWithText("仅在你点击后联网 · B 站热门由上方单独导入").assertIsDisplayed()
        composeRule.onNodeWithText("探索其他公开来源").performClick()
        assertTrue(clicked)
    }

    @Test
    fun otherPublicSourcesButtonIsDisabledWhileTaskIsRunning() {
        composeRule.setContent {
            MaterialTheme {
                BackgroundDiscoveryCard(
                    state = BackgroundDiscoveryUiState(isRunning = true),
                    onRunNow = {}
                )
            }
        }

        composeRule.onNodeWithText("探索其他公开来源").assertIsNotEnabled()
    }
}
