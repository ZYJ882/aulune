package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {
    @Test
    fun persistedValueFallsBackToSystemMode() {
        assertEquals(AppearanceMode.System, AppearanceMode.fromPersisted(null))
        assertEquals(AppearanceMode.System, AppearanceMode.fromPersisted("unknown"))
        assertEquals(AppearanceMode.Dark, AppearanceMode.fromPersisted("Dark"))
    }

    @Test
    fun modeResolvesImmediatelyAgainstSystemTheme() {
        assertTrue(AppearanceMode.System.resolvesToDark(systemDark = true))
        assertFalse(AppearanceMode.System.resolvesToDark(systemDark = false))
        assertFalse(AppearanceMode.Light.resolvesToDark(systemDark = true))
        assertTrue(AppearanceMode.Dark.resolvesToDark(systemDark = false))
    }
}
