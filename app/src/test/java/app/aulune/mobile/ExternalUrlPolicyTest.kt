package app.aulune.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalUrlPolicyTest {
    @Test
    fun `allows http and https links with a host`() {
        assertTrue(ExternalUrlPolicy.isAllowed("https://www.example.com/article?id=1"))
        assertTrue(ExternalUrlPolicy.isAllowed("  http://example.com/path  "))
    }

    @Test
    fun `normalizes only safe web image addresses`() {
        assertTrue(ExternalUrlPolicy.normalizedHttpUrlOrEmpty("  https://cdn.example.com/cover.webp ").startsWith("https://"))
        assertTrue(ExternalUrlPolicy.normalizedHttpUrlOrEmpty("file:///sdcard/cover.webp").isEmpty())
    }

    @Test
    fun `rejects blank malformed and non web links`() {
        assertFalse(ExternalUrlPolicy.isAllowed(""))
        assertFalse(ExternalUrlPolicy.isAllowed("example.com/no-scheme"))
        assertFalse(ExternalUrlPolicy.isAllowed("file:///sdcard/private.txt"))
        assertFalse(ExternalUrlPolicy.isAllowed("intent://unsafe"))
        assertFalse(ExternalUrlPolicy.isAllowed("mailto:user@example.com"))
    }
}
