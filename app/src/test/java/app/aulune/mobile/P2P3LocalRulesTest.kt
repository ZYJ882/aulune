package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2P3LocalRulesTest {
    private fun content(title: String, sourceKey: String = "bilibili") = LocalContentEntity(
        contentKey = "bilibili:test-$sourceKey",
        source = "B 站 · 测试作者",
        channel = SourceChannel.Video.name,
        title = title,
        readTime = "08:00",
        summary = "人工智能与产品实践",
        theme = "",
        url = "https://www.bilibili.com/video/test",
        gradientStart = 0L,
        gradientEnd = 0L,
        createdAt = 1L,
        updatedAt = 1L,
        sourceKey = sourceKey
    )

    @Test
    fun classifierCanonicalizesChineseAiAliases() {
        val result = RuleTopicClassifier.canonicalize(content("大模型与人工智能产品实践"))
        assertEquals("技术 · AI", result.theme)
        assertEquals("技术", result.topicGroup)
        assertTrue(result.seriesKey.isNotBlank())
    }

    @Test
    fun seriesKeyDropsEpisodeNumbersAndSuffix() {
        val key = RuleTopicClassifier.inferSeriesKey("【深度学习】AI 产品拆解 第 12 期：从模型到体验")
        assertFalse(key.contains("12"))
        assertFalse(key.contains("从模型到体验"))
    }

    @Test
    fun exploreIntentRewardsUnseenSource() {
        val candidate = RuleTopicClassifier.canonicalize(content("AI 产品实践", sourceKey = "new-source"))
        val trial = InterestEntity("技术 · AI", 1.0, 1, InterestLifecycle.Trial.name, 1L, 1L, 1L)
        val balanced = LocalAdaptiveCore.score(candidate, listOf(trial), emptyList(), emptyList(), 0, SessionIntent.Balanced)
        val explore = LocalAdaptiveCore.score(candidate, listOf(trial), emptyList(), emptyList(), 0, SessionIntent.Explore)
        assertTrue(explore > balanced)
    }

    @Test
    fun valuesLayerStaysPendingUntilUserConfirms() {
        val interests = listOf(
            InterestEntity("技术 · AI", 6.0, 7, InterestLifecycle.Active.name, 1L, 2L, 2L),
            InterestEntity("创造 · 设计", 5.0, 6, InterestLifecycle.Active.name, 1L, 2L, 2L)
        )
        val profiles = LocalProfileBuilder.build(interests, 14, SessionIntent.Balanced, emptyMap(), 100L)
        val values = profiles.first { it.layer == ProfileLayer.Values.name }
        assertEquals("pending", values.confirmationState)
        assertEquals("等待你的确认", values.summary)
        assertTrue(values.candidate.isNotBlank())

        val confirmed = LocalProfileBuilder.confirm(values, 101L)
        assertEquals("confirmed", confirmed.confirmationState)
        assertEquals("", confirmed.candidate)
        assertTrue(confirmed.summary.isNotBlank())
    }
}
