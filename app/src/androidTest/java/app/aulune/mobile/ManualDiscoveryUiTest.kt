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
    fun profileGuidedExploreRequiresExplicitClickAndReadyPlan() {
        var clicked = false
        val readyPlan = ProfileGuidedExplorePlan(
            focusThemes = listOf("技术 · AI"),
            platforms = listOf(ContentPlatform.BILIBILI),
            summary = "点击执行前不会联网。",
        )
        composeRule.setContent {
            MaterialTheme {
                ProfileGuidedExploreCard(
                    plan = readyPlan,
                    isRunning = false,
                    onRun = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("按我的画像探索").assertIsDisplayed()
        composeRule.onNodeWithText("只会导入计划列出的公开来源；不会后台执行，不读取账号 Cookie。").assertIsDisplayed()
        composeRule.onNodeWithText("确认并按画像探索").performClick()
        assertTrue(clicked)
    }

    @Test
    fun profileGuidedExploreIsDisabledWithoutPlanOrWhileRunning() {
        composeRule.setContent {
            MaterialTheme {
                ProfileGuidedExploreCard(
                    plan = ProfileGuidedExplorePlan(),
                    isRunning = false,
                    onRun = {},
                )
            }
        }
        composeRule.onNodeWithText("确认并按画像探索").assertIsNotEnabled()

        composeRule.setContent {
            MaterialTheme {
                ProfileGuidedExploreCard(
                    plan = ProfileGuidedExplorePlan(
                        focusThemes = listOf("技术 · AI"),
                        platforms = listOf(ContentPlatform.BILIBILI),
                    ),
                    isRunning = true,
                    onRun = {},
                )
            }
        }
        composeRule.onNodeWithText("正在按画像探索…").assertIsNotEnabled()
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
