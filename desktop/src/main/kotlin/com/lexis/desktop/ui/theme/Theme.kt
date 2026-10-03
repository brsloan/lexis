package com.lexis.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lexis.desktop.data.ThemeMode

// Same palettes as the Android app's non-dynamic themes.
private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFF2F4F4F),
    tertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFFFCFBFF),
    surface = Color(0xFFFCFBFF),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLow = Color(0xFFF5F4FA),
    surfaceContainer = Color(0xFFEFEEF5),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0B1F8F),
    primaryContainer = Color(0xFF2A3AA5),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF434559),
    tertiary = Color(0xFFA9C7C7),
    tertiaryContainer = Color(0xFF6B5A1E),
    background = Color(0xFF131316),
    surface = Color(0xFF131316),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1F1F23),
)

/** Colours for the semantic roles of dictionary text. */
data class ArticleColors(
    val quote: Color,
    val citation: Color,
    val senseLabel: Color,
    val etymology: Color,
    val pronunciation: Color,
    val link: Color,
    val marker: Color,
)

val LocalArticleColors = staticCompositionLocalOf {
    ArticleColors(
        quote = Color(0xFF6A1B6A),
        citation = Color(0xFF5A5A5A),
        senseLabel = Color(0xFF4B0082),
        etymology = Color(0xFF5E5E66),
        pronunciation = Color(0xFF2F4F4F),
        link = Color(0xFF3F51B5),
        marker = Color(0xFF8A2D2D),
    )
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LexisTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val scheme = if (dark) DarkScheme else LightScheme
    val articleColors = if (dark) ArticleColors(
        quote = Color(0xFFE2A9E2),
        citation = Color(0xFFB0B0B8),
        senseLabel = Color(0xFFC3B1FF),
        etymology = Color(0xFFA8A8B3),
        pronunciation = Color(0xFF9FD3D3),
        link = scheme.primary,
        marker = Color(0xFFE59A9A),
    ) else ArticleColors(
        quote = Color(0xFF6A1B6A),
        citation = Color(0xFF5A5A5A),
        senseLabel = Color(0xFF4B0082),
        etymology = Color(0xFF5E5E66),
        pronunciation = Color(0xFF2F4F4F),
        link = scheme.primary,
        marker = Color(0xFF8A2D2D),
    )
    CompositionLocalProvider(LocalArticleColors provides articleColors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
