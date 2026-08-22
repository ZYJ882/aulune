package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRankingTest {
    private fun content(
        key: String = "bilibili:BV1test",
        theme: String = "技术 · AI",
        source: String = "bilibili",
        author: String = "creator"
    ) = LocalContentEntity(
        contentKey = key,
        source = "B 站 · $source",
        channel = SourceChannel.Video.name,
        title = "测试内容",
        readTime = "10:00",
        summary = "测试摘要",
        theme = theme,
        url = "https://www.bilibili.com/video/BV1test",
        gradientStart = 0L,
        gradientEnd = 0L,
        createdAt = 1L,
        updatedAt = 1L,
        sourceKey = source,
        authorKey = author,
        topicGroup = theme.substringBefore("·").trim()
    )

    @Test
    fun repeatedPositiveEvidencePromotesTrialInterest() {
        var interest: InterestEntity? = null
        repeat(5) { index ->
            interest = LocalAdaptiveCore.updateInterest(
                current = interest,
                theme = "技术 · AI",
                delta = 1.0,
                now = 1_000L + index,
            )
        }
        assertEquals(InterestLifecycle.Active.name, interest?.lifecycle)
        assertEquals(5, interest?.evidenceCount)
    }

    @Test
    fun negativeAuthorFeedbackExcludesMatchingCandidate() {
        val candidate = content(author = "blocked-up")
        val feedback = LocalFeedbackEntity(
            id = "negative-author",
            contentKey = candidate.contentKey,
            feedbackType = FeedbackType.Negative.name,
            targetType = FeedbackTarget.Author.name,
            targetKey = "blocked-up",
            occurredAt = 100L
        )
        assertTrue(LocalAdaptiveCore.shouldExclude(candidate, listOf(feedback)))
        assertFalse(LocalAdaptiveCore.shouldExclude(candidate, emptyList()))
    }

    @Test
    fun repeatedThemeEventsCauseFatiguePenalty() {
        val candidate = content()
        val interest = InterestEntity(
            theme = candidate.theme,
            weight = 4.0,
            evidenceCount = 5,
            lifecycle = InterestLifecycle.Active.name,
            firstSeenAt = 1L,
            lastEvidenceAt = 10L,
            updatedAt = 10L
        )
        val calmScore = LocalAdaptiveCore.score(candidate, listOf(interest), emptyList(), emptyList(), 0)
        val repeatedEvents = (1..8).map { index ->
            BehaviorEventEntity(
                id = "event-$index",
                contentKey = "other-$index",
                eventType = "open",
                theme = candidate.theme,
                occurredAt = index.toLong(),
                sourceKey = candidate.sourceKey,
                topicGroup = candidate.topicGroup
            )
        }
        val fatiguedScore = LocalAdaptiveCore.score(candidate, listOf(interest), repeatedEvents, emptyList(), 0)
        assertTrue(fatiguedScore < calmScore)
    }
}
