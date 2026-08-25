package app.aulune.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun fromNameReturnsMatchingMode() {
        assertEquals(ThemeMode.System, ThemeMode.fromName("System"))
        assertEquals(ThemeMode.Light, ThemeMode.fromName("Light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromName("Dark"))
    }

    @Test
    fun fromNameFallsBackToSystemForUnknownValues() {
        assertEquals(ThemeMode.System, ThemeMode.fromName(""))
        assertEquals(ThemeMode.System, ThemeMode.fromName("unknown"))
    }
}
