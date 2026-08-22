package app.aulune.mobile

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformReliabilityTest {
    @Test
    fun bilibiliCapabilityIsAdvertisedAsSupported() {
        val capability = PlatformCapabilityMatrix.forPlatform(ContentPlatform.BILIBILI)

        assertEquals(PlatformCapabilityLevel.Supported, capability.publicImport)
        assertEquals(PlatformCapabilityLevel.Supported, capability.accountProfile)
        assertEquals(PlatformCapabilityLevel.Supported, capability.accountContent)
    }

    @Test
    fun directPlatformCapabilityIsClearlyExperimental() {
        val capability = PlatformCapabilityMatrix.forPlatform(ContentPlatform.XIAOHONGSHU)

        assertEquals(PlatformCapabilityLevel.Experimental, capability.publicImport)
        assertEquals(PlatformCapabilityLevel.Experimental, capability.accountProfile)
        assertEquals(PlatformCapabilityLevel.Limited, capability.accountContent)
    }

    @Test
    fun classifierRecognizesRateLimitAuthenticationAndNetworkFailures() {
        val rate = PlatformFailureClassifier.classify(IllegalStateException("HTTP 429 too many requests"))
        val authentication = PlatformFailureClassifier.classify(IllegalStateException("401 cookie expired"))
        val network = PlatformFailureClassifier.classify(IOException("connection timeout"))

        assertEquals(PlatformFailureKind.RateLimited, rate.kind)
        assertTrue(rate.kind.retryable)
        assertEquals(PlatformFailureKind.Authentication, authentication.kind)
        assertFalse(authentication.kind.retryable)
        assertEquals(PlatformFailureKind.Network, network.kind)
        assertTrue(network.kind.retryable)
    }

    @Test
    fun classifierUnwrapsConnectorFailureCause() {
        val failure = PlatformFailureClassifier.classify(
            PlatformConnectorException(ContentPlatform.REDDIT, IOException("connection timeout"))
        )

        assertEquals(PlatformFailureKind.Network, failure.kind)
        assertTrue(failure.kind.retryable)
    }

    @Test
    fun retryPolicyRetriesTransientFailureAndReturnsValue() = runTest {
        var attempts = 0
        val outcome = PlatformRetryPolicy.run {
            attempts += 1
            if (attempts < 3) throw IOException("timeout")
            "ok"
        }

        assertEquals("ok", outcome.value)
        assertEquals(3, outcome.attempts)
        assertEquals(3, attempts)
    }

    @Test
    fun connectorFactoriesProvideMatchingPlatformAdapters() {
        ContentPlatform.entries.forEach { platform ->
            assertEquals(platform, PlatformConnectorFactory.getPublic(platform).platform)
            assertEquals(platform, PlatformAccountConnectorFactory.get(platform).platform)
        }
    }

    @Test
    fun retryPolicyStopsForAuthenticationFailure() = runTest {
        var attempts = 0
        val outcome = PlatformRetryPolicy.run {
            attempts += 1
            throw IllegalStateException("403 authorization failed")
        }

        assertEquals(1, attempts)
        assertEquals(1, outcome.attempts)
        assertNotNull(outcome.failure)
        assertEquals(PlatformFailureKind.Authentication, outcome.failure?.kind)
    }
}
