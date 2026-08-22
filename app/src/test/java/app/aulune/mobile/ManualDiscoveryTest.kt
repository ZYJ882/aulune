package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualDiscoveryTest {
    @Test
    fun probeOutcomeUsesExistingFailureSemantics() {
        val available = SourceProbeOutcome(ContentPlatform.BILIBILI, discoveredCount = 3, attempts = 1)
        val degraded = SourceProbeOutcome(
            ContentPlatform.ZHIHU,
            discoveredCount = 0,
            attempts = 3,
            failure = PlatformFailure(PlatformFailureKind.Network, "timeout")
        )
        val unavailable = SourceProbeOutcome(
            ContentPlatform.XIAOHONGSHU,
            discoveredCount = 0,
            attempts = 1,
            failure = PlatformFailure(PlatformFailureKind.Authentication, "401")
        )

        assertEquals(SourceAvailabilityState.Available, available.state)
        assertEquals(SourceAvailabilityState.Degraded, degraded.state)
        assertEquals(SourceAvailabilityState.Unavailable, unavailable.state)
        assertTrue(available.detail().contains("3 条"))
    }

    @Test
    fun taskClassifierDistinguishesCompletedPartialAndFailedRuns() {
        val complete = DiscoveryRunResult(listOf(SourceProbeOutcome(ContentPlatform.BILIBILI, 1, 1)))
        val partial = DiscoveryRunResult(listOf(
            SourceProbeOutcome(ContentPlatform.BILIBILI, 1, 1),
            SourceProbeOutcome(ContentPlatform.ZHIHU, 0, 3, PlatformFailure(PlatformFailureKind.Network))
        ))
        val failed = DiscoveryRunResult(listOf(
            SourceProbeOutcome(ContentPlatform.BILIBILI, 0, 1, PlatformFailure(PlatformFailureKind.Authentication))
        ))

        assertEquals(DiscoveryTaskStatus.Completed, DiscoveryTaskClassifier.status(complete))
        assertEquals(DiscoveryTaskStatus.Partial, DiscoveryTaskClassifier.status(partial))
        assertEquals(DiscoveryTaskStatus.Failed, DiscoveryTaskClassifier.status(failed))
        assertTrue(DiscoveryTaskClassifier.detail(partial).contains("部分来源"))
    }

    @Test
    fun persistedRowsFallBackSafelyForUnknownLegacyValues() {
        val source = SourceAvailabilityEntity("missing", "other", "unknown", 100L).toUi()
        val task = DiscoveryTaskEntity("task", "other", "other", 100L).toUi()

        assertEquals(ContentPlatform.BILIBILI, source.platform)
        assertEquals(SourceAvailabilityState.Unavailable, source.state)
        assertEquals(DiscoveryTaskKind.Manual, task.kind)
        assertEquals(DiscoveryTaskStatus.Failed, task.status)
    }
}
