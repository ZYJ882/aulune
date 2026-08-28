package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentClosureTest {
    @Test
    fun synthesizesTheFiveMemoryLayersWithoutNetworkState() {
        val snapshot = RankingSnapshot(
            content = emptyList(),
            savedCount = 0,
            interests = listOf(
                InterestEntity(
                    theme = "技术 · AI",
                    weight = 4.0,
                    evidenceCount = 6,
                    lifecycle = InterestLifecycle.Active.name,
                    firstSeenAt = 1L,
                    lastEvidenceAt = 2L,
                    updatedAt = 2L,
                ),
            ),
            events = listOf(BehaviorEventEntity("e1", "c1", "open", "技术 · AI", 2L)),
            feedback = emptyList(),
            hypotheses = listOf(
                InterestHypothesisEntity(
                    id = "h1",
                    candidateTheme = "创造 · 设计",
                    sourceTheme = "技术 · AI",
                    origin = "behavior",
                    reason = "bridge",
                    evidenceCount = 6,
                    status = InterestHypothesisStatus.Pending.name,
                    createdAt = 2L,
                    expiresAt = 100L,
                ),
            ),
        )

        val cognitive = AuluneAgentPolicy.synthesize(snapshot, now = 3L)

        assertEquals(5, cognitive.layers.size)
        assertEquals(1, cognitive.pendingConfirmations)
        assertTrue(cognitive.layers.any { it.name.startsWith("Awareness") && it.requiresConfirmation })
        assertTrue(cognitive.layers.any { it.name.startsWith("Soul") && it.requiresConfirmation })
    }

    @Test
    fun candidateEvaluationExplainsNoveltyAndTopicMatch() {
        val item = LocalContentEntity(
            contentKey = "c1",
            source = "公开来源",
            channel = "web",
            title = "AI 工具实践",
            readTime = "5 分钟",
            summary = "一个完整的 AI 实践案例",
            theme = "技术 · AI",
            url = "https://example.com/c1",
            gradientStart = 0L,
            gradientEnd = 0L,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val snapshot = RankingSnapshot(
            content = listOf(item), savedCount = 0,
            interests = listOf(InterestEntity("技术 · AI", 3.0, 5, InterestLifecycle.Active.name, 1L, 1L, 1L)),
            events = emptyList(), feedback = emptyList(),
        )

        val result = AuluneAgentPolicy.evaluate(item, snapshot)

        assertEquals("c1", result.contentKey)
        assertTrue(result.reasons.any { it.contains("匹配本机主题") })
        assertTrue(result.reasons.contains("来源新颖"))
    }
}
