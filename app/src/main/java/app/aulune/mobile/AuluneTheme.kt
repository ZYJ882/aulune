package app.aulune.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════
//  主题模式
// ═══════════════════════════════════════════════════════════════

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色");

    companion object {
        fun fromName(name: String): ThemeMode = entries.firstOrNull { it.name == name } ?: System
    }
}

// ═══════════════════════════════════════════════════════════════
//  品牌色（紫色）
// ═══════════════════════════════════════════════════════════════

val AulunePurple = Color(0xFF7857FF)
val AulunePurpleDark = Color(0xFF5C3FD9)
val AuluneCyan = Color(0xFF1CA4D8)
val AuluneInk = Color(0xFF171427)
val AuluneMuted = Color(0xFF716C84)
val AuluneCanvas = Color(0xFFF8F7FF)
val AuluneSoftViolet = Color(0xFFEDE9FF)

// 浅色主题
private val LightColorScheme = lightColorScheme(
    primary = AulunePurple,
    onPrimary = Color.White,
    primaryContainer = AuluneSoftViolet,
    onPrimaryContainer = AuluneInk,
    secondary = AuluneCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F4FA),
    onSecondaryContainer = Color(0xFF0D4A5E),
    tertiary = Color(0xFFE07093),
    onTertiary = Color.White,
    background = AuluneCanvas,
    onBackground = AuluneInk,
    surface = Color.White,
    onSurface = AuluneInk,
    surfaceVariant = Color(0xFFF1F0F5),
    onSurfaceVariant = AuluneMuted,
    outline = Color(0xFFD9D6E3),
    outlineVariant = Color(0xFFE9E8EF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

// 深色主题
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB4A3FF),
    onPrimary = Color(0xFF2A1570),
    primaryContainer = Color(0xFF422E9E),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF7DD0EA),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D63),
    onSecondaryContainer = Color(0xFFB7EAFF),
    tertiary = Color(0xFFFFB1C8),
    onTertiary = Color(0xFF5E1133),
    background = Color(0xFF14121E),
    onBackground = Color(0xFFE6E1EC),
    surface = Color(0xFF1C1A28),
    onSurface = Color(0xFFE6E1EC),
    surfaceVariant = Color(0xFF2A2736),
    onSurfaceVariant = Color(0xFFC9C4D4),
    outline = Color(0xFF938FA0),
    outlineVariant = Color(0xFF44414F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

// ═══════════════════════════════════════════════════════════════
//  主题管理器
// ═══════════════════════════════════════════════════════════════

object ThemeManager {
    private const val PREFS_NAME = "aulune_theme"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_DYNAMIC = "dynamic_color"

    fun getMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ThemeMode.fromName(prefs.getString(KEY_MODE, ThemeMode.System.name) ?: ThemeMode.System.name)
    }

    fun setMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
    }

    fun isDynamicColorEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DYNAMIC, true)
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

// ═══════════════════════════════════════════════════════════════
//  Aulune 主题
// ═══════════════════════════════════════════════════════════════

@Composable
fun AuluneTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuluneTypography,
        shapes = AuluneShapes,
        content = content,
    )
}

// ═══════════════════════════════════════════════════════════════
//  字体层级
// ═══════════════════════════════════════════════════════════════

private val AuluneTypography = androidx.compose.material3.Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 40.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 32.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        lineHeight = 40.sp,
    ),
    headlineLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 28.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        lineHeight = 36.sp,
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        lineHeight = 32.sp,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 20.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        lineHeight = 28.sp,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        lineHeight = 24.sp,
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        lineHeight = 24.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        lineHeight = 16.sp,
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        lineHeight = 16.sp,
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        lineHeight = 14.sp,
    ),
)

// ═══════════════════════════════════════════════════════════════
//  形状
// ═══════════════════════════════════════════════════════════════

private val AuluneShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

// ═══════════════════════════════════════════════════════════════
//  动画常量
// ═══════════════════════════════════════════════════════════════

object AuluneMotion {
    const val ButtonDuration = 150
    const val CardScale = 0.98f
    const val PageTransitionDuration = 220
    const val DialogDuration = 240
    const val BottomSheetDuration = 250
}

// ═══════════════════════════════════════════════════════════════
//  尺寸常量
// ═══════════════════════════════════════════════════════════════

object AuluneDimensions {
    val PageHorizontalPadding = 24.dp
    val CardCornerRadius = 28.dp
    val PrimaryButtonHeight = 56.dp
    val InputCornerRadius = 28.dp
    val CardElevation = 1.dp
}
