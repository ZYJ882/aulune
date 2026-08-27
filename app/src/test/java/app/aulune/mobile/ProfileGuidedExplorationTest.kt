package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileGuidedExplorationTest {
    private fun activeInterest(theme: String, evidence: Int = 5, weight: Double = 4.0) = InterestEntity(
        theme = theme,
        weight = weight,
        evidenceCount = evidence,
        lifecycle = InterestLifecycle.Active.name,
        firstSeenAt = 1L,
        lastEvidenceAt = 2L,
        updatedAt = 2L,
    )

    @Test
    fun activeBehaviorInterestProducesAuditableBridgeHypothesis() {
        val proposals = ProfileGuidedExplorationPolicy.propose(
            interests = listOf(activeInterest("技术 · AI")),
            existing = emptyList(),
            now = 1_000L,
        )

        assertEquals(1, proposals.size)
        assertEquals("创造 · 设计", proposals.single().candidateTheme)
        assertEquals("技术 · AI", proposals.single().sourceTheme)
        assertEquals("behavior", proposals.single().origin)
        assertEquals(InterestHypothesisStatus.Pending.name, proposals.single().status)
        assertTrue(proposals.single().expiresAt > proposals.single().createdAt)
    }

    @Test
    fun existingPendingHypothesisIsNotDuplicated() {
        val existing = InterestHypothesisEntity(
            id = "existing",
            candidateTheme = "创造 · 设计",
            sourceTheme = "技术 · AI",
            origin = "behavior",
            reason = "existing",
            evidenceCount = 5,
            createdAt = 1L,
            expiresAt = Long.MAX_VALUE,
        )

        val proposals = ProfileGuidedExplorationPolicy.propose(
            interests = listOf(activeInterest("技术 · AI")),
            existing = listOf(existing),
            now = 2_000L,
        )

        assertTrue(proposals.none { it.candidateTheme == "创造 · 设计" })
    }

    @Test
    fun expiredPendingHypothesisChangesStateWithoutPromotingInterest() {
        val pending = InterestHypothesisEntity(
            id = "expired",
            candidateTheme = "创造 · 设计",
            sourceTheme = "技术 · AI",
            origin = "behavior",
            reason = "test",
            evidenceCount = 5,
            createdAt = 1L,
            expiresAt = 10L,
        )

        val expired = ProfileGuidedExplorationPolicy.expire(listOf(pending), now = 10L).single()

        assertEquals(InterestHypothesisStatus.Expired.name, expired.status)
        assertEquals(10L, expired.decidedAt)
    }

    @Test
    fun confirmedHypothesisIsIncludedInProfileGuidedPlan() {
        val confirmed = InterestHypothesisEntity(
            id = "confirmed",
            candidateTheme = "创造 · 设计",
            sourceTheme = "技术 · AI",
            origin = "behavior",
            reason = "test",
            evidenceCount = 5,
            status = InterestHypothesisStatus.Confirmed.name,
            createdAt = 1L,
            expiresAt = Long.MAX_VALUE,
            decidedAt = 5L,
        )

        val plan = ProfileGuidedExplorationPolicy.plan(
            interests = listOf(activeInterest("技术 · AI")),
            hypotheses = listOf(confirmed),
            intent = SessionIntent.Explore,
        )

        assertTrue(plan.isReady)
        assertEquals("创造 · 设计", plan.focusThemes.first())
        assertTrue(plan.platforms.contains(ContentPlatform.XIAOHONGSHU))
        assertTrue(plan.summary.contains("点击执行前不会联网"))
    }

    @Test
    fun noEvidenceLeavesProfileGuidedPlanDisabled() {
        val plan = ProfileGuidedExplorationPolicy.plan(
            interests = emptyList(),
            hypotheses = emptyList(),
            intent = SessionIntent.Balanced,
        )

        assertFalse(plan.isReady)
        assertTrue(plan.summary.contains("先在信息流"))
    }

    @Test
    fun dialogueExtractionUsesRulesAndStoresNoOriginalConversation() {
        val proposals = ProfileGuidedExplorationPolicy.proposeFromDialogue(
            messages = listOf("我正在做 Android Kotlin 开源项目，也想更系统地学习代码架构。"),
            existing = emptyList(),
            now = 100L,
        )

        assertTrue(proposals.any { it.candidateTheme == "技术 · 编程" })
        assertTrue(proposals.all { it.origin == "dialogue" })
        assertTrue(proposals.all { !it.reason.contains("Android Kotlin") })
    }
}
