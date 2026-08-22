package app.aulune.mobile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 用户可控的外观选项；跟随系统是默认值并会实时响应系统主题变化。 */
enum class AppearanceMode(val label: String, val description: String) {
    System("跟随系统", "随系统浅色/深色设置即时变化"),
    Light("浅色模式", "始终使用明亮、低对比的浅色界面"),
    Dark("深色模式", "始终使用舒适的深色界面") ;

    fun resolvesToDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromPersisted(value: String?): AppearanceMode = entries.firstOrNull { it.name == value } ?: System
    }
}

/** 外观是非敏感偏好，使用同步本机偏好保证单击后无需等待即可更新界面。 */
class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aulune-appearance", Context.MODE_PRIVATE)

    fun load(): AppearanceMode = AppearanceMode.fromPersisted(preferences.getString("appearance_mode", null))

    fun save(mode: AppearanceMode) {
        preferences.edit().putString("appearance_mode", mode.name).apply()
    }
}

object AuluneLayout {
    const val MotionDuration = 220
}

/** 在 Compose 首帧前同步系统栏，避免已保存的手动主题在启动时闪回系统默认颜色。 */
fun Activity.applyStartupAppearance(mode: AppearanceMode) {
    val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val useDark = mode.resolvesToDark(systemDark)
    window.statusBarColor = if (useDark) android.graphics.Color.rgb(18, 18, 23) else android.graphics.Color.rgb(247, 247, 251)
    window.navigationBarColor = if (useDark) android.graphics.Color.rgb(26, 26, 32) else android.graphics.Color.rgb(252, 252, 254)
    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !useDark
    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !useDark
}

private val AuluneLightScheme = lightColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECE7FF),
    onPrimaryContainer = Color(0xFF24135E),
    secondary = Color(0xFF5A8DFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EBFF),
    onSecondaryContainer = Color(0xFF112D68),
    tertiary = Color(0xFF356A57),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD2F0DE),
    onTertiaryContainer = Color(0xFF102C20),
    background = Color(0xFFF7F7FB),
    onBackground = Color(0xFF111118),
    surface = Color(0xFFFCFCFE),
    onSurface = Color(0xFF111118),
    surfaceVariant = Color(0xFFF0F0F6),
    onSurfaceVariant = Color(0xFF6C7280),
    outline = Color(0xFFE7E8EF),
    outlineVariant = Color(0xFFE7E8EF),
    error = Color(0xFFB3261E)
)

private val AuluneDarkScheme = darkColorScheme(
    primary = Color(0xFFD1C7FF),
    onPrimary = Color(0xFF35217D),
    primaryContainer = Color(0xFF503CB0),
    onPrimaryContainer = Color(0xFFECE7FF),
    secondary = Color(0xFFB8C8FF),
    onSecondary = Color(0xFF17366F),
    secondaryContainer = Color(0xFF315796),
    onSecondaryContainer = Color(0xFFE2EBFF),
    tertiary = Color(0xFFB1D9C1),
    onTertiary = Color(0xFF1C4935),
    background = Color(0xFF121217),
    onBackground = Color(0xFFE5E2E9),
    surface = Color(0xFF1A1A20),
    onSurface = Color(0xFFE5E2E9),
    surfaceVariant = Color(0xFF272731),
    onSurfaceVariant = Color(0xFFC9C4D0),
    outline = Color(0xFF3A3945),
    outlineVariant = Color(0xFF3A3945),
    error = Color(0xFFFFB4AB)
)

@Composable
fun AuluneTheme(mode: AppearanceMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = mode.resolvesToDark(systemDark)
    val targetScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        useDark -> AuluneDarkScheme
        else -> AuluneLightScheme
    }
    val colorScheme = targetScheme.animated()
    val view = LocalView.current

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.surface.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun animated(target: Color): Color {
        val value by animateColorAsState(target, animationSpec = tween(AuluneLayout.MotionDuration), label = "theme-color")
        return value
    }
    return copy(
        primary = animated(primary), onPrimary = animated(onPrimary), primaryContainer = animated(primaryContainer), onPrimaryContainer = animated(onPrimaryContainer),
        inversePrimary = animated(inversePrimary), secondary = animated(secondary), onSecondary = animated(onSecondary), secondaryContainer = animated(secondaryContainer), onSecondaryContainer = animated(onSecondaryContainer),
        tertiary = animated(tertiary), onTertiary = animated(onTertiary), tertiaryContainer = animated(tertiaryContainer), onTertiaryContainer = animated(onTertiaryContainer),
        background = animated(background), onBackground = animated(onBackground), surface = animated(surface), onSurface = animated(onSurface),
        surfaceVariant = animated(surfaceVariant), onSurfaceVariant = animated(onSurfaceVariant), surfaceTint = animated(surfaceTint),
        inverseSurface = animated(inverseSurface), inverseOnSurface = animated(inverseOnSurface), error = animated(error), onError = animated(onError),
        errorContainer = animated(errorContainer), onErrorContainer = animated(onErrorContainer), outline = animated(outline), outlineVariant = animated(outlineVariant),
        scrim = animated(scrim)
    )
}
