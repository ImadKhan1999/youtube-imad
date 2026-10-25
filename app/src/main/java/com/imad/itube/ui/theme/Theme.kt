package com.imad.itube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.imad.itube.data.store.ThemeMode

val YtRed = Color(0xFFFF0033)

/** The handful of colours YouTube's Android app uses beyond the Material roles. */
@Immutable
data class YtColors(
    val background: Color,
    val surface: Color,
    val chip: Color,
    val chipSelected: Color,
    val chipSelectedText: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val subscribeBg: Color,
    val subscribeText: Color,
    val miniPlayer: Color,
)

private val DarkYt = YtColors(
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF212121),
    chip = Color(0xFF272727),
    chipSelected = Color(0xFFF1F1F1),
    chipSelectedText = Color(0xFF0F0F0F),
    textPrimary = Color(0xFFF1F1F1),
    textSecondary = Color(0xFFAAAAAA),
    divider = Color(0xFF3F3F3F),
    subscribeBg = Color(0xFFF1F1F1),
    subscribeText = Color(0xFF0F0F0F),
    miniPlayer = Color(0xFF212121),
)

private val LightYt = YtColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    chip = Color(0xFFF2F2F2),
    chipSelected = Color(0xFF0F0F0F),
    chipSelectedText = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F0F0F),
    textSecondary = Color(0xFF606060),
    divider = Color(0xFFE5E5E5),
    subscribeBg = Color(0xFF0F0F0F),
    subscribeText = Color(0xFFFFFFFF),
    miniPlayer = Color(0xFFF9F9F9),
)

val LocalYtColors = staticCompositionLocalOf { DarkYt }

object Yt {
    val colors: YtColors
        @Composable get() = LocalYtColors.current
}

private val YtTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ITubeTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val yt = if (dark) DarkYt else LightYt
    val scheme = if (dark) {
        darkColorScheme(
            primary = YtRed,
            onPrimary = Color.White,
            secondary = yt.textSecondary,
            background = yt.background,
            onBackground = yt.textPrimary,
            surface = yt.background,
            onSurface = yt.textPrimary,
            surfaceVariant = yt.chip,
            onSurfaceVariant = yt.textSecondary,
            surfaceContainer = yt.surface,
            surfaceContainerLow = yt.surface,
            surfaceContainerHigh = yt.surface,
            outline = yt.divider,
            outlineVariant = yt.divider,
        )
    } else {
        lightColorScheme(
            primary = YtRed,
            onPrimary = Color.White,
            secondary = yt.textSecondary,
            background = yt.background,
            onBackground = yt.textPrimary,
            surface = yt.background,
            onSurface = yt.textPrimary,
            surfaceVariant = yt.chip,
            onSurfaceVariant = yt.textSecondary,
            surfaceContainer = yt.surface,
            surfaceContainerLow = yt.surface,
            surfaceContainerHigh = yt.surface,
            outline = yt.divider,
            outlineVariant = yt.divider,
        )
    }
    CompositionLocalProvider(LocalYtColors provides yt) {
        MaterialTheme(colorScheme = scheme, typography = YtTypography) {
            // Text outside a Surface/Scaffold (the watch page, overlays) would otherwise default to black.
            CompositionLocalProvider(LocalContentColor provides yt.textPrimary, content = content)
        }
    }
}
