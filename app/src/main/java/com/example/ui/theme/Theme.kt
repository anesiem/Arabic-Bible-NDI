package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.model.AppThemeMode

data class BentoColors(
    val bg: Color,
    val card: Color,
    val surfaceContainer: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val liveRed: Color,
    val liveRedBright: Color,
    val green: Color
)

val LocalBentoColors = staticCompositionLocalOf {
    BentoColors(
        bg = BentoBgLight,
        card = BentoCardWhiteLight,
        surfaceContainer = BentoSurfaceContainerLight,
        surfaceVariant = BentoSurfaceVariantLight,
        border = BentoBorderLight,
        borderSubtle = BentoBorderSubtleLight,
        textPrimary = BentoTextPrimaryLight,
        textSecondary = BentoTextSecondaryLight,
        primary = BentoPrimaryLight,
        onPrimary = BentoOnPrimaryLight,
        primaryContainer = BentoPrimaryContainerLight,
        onPrimaryContainer = BentoOnPrimaryContainerLight,
        secondary = BentoSecondaryLight,
        onSecondary = BentoOnSecondaryLight,
        liveRed = BentoLiveRed,
        liveRedBright = BentoLiveRedBright,
        green = BentoGreen
    )
}

private val LightBentoColors = BentoColors(
    bg = BentoBgLight,
    card = BentoCardWhiteLight,
    surfaceContainer = BentoSurfaceContainerLight,
    surfaceVariant = BentoSurfaceVariantLight,
    border = BentoBorderLight,
    borderSubtle = BentoBorderSubtleLight,
    textPrimary = BentoTextPrimaryLight,
    textSecondary = BentoTextSecondaryLight,
    primary = BentoPrimaryLight,
    onPrimary = BentoOnPrimaryLight,
    primaryContainer = BentoPrimaryContainerLight,
    onPrimaryContainer = BentoOnPrimaryContainerLight,
    secondary = BentoSecondaryLight,
    onSecondary = BentoOnSecondaryLight,
    liveRed = BentoLiveRed,
    liveRedBright = BentoLiveRedBright,
    green = BentoGreen
)

private val DarkBentoColors = BentoColors(
    bg = BentoBgDark,
    card = BentoCardDark,
    surfaceContainer = BentoSurfaceContainerDark,
    surfaceVariant = BentoSurfaceVariantDark,
    border = BentoBorderDark,
    borderSubtle = BentoBorderSubtleDark,
    textPrimary = BentoTextPrimaryDark,
    textSecondary = BentoTextSecondaryDark,
    primary = BentoPrimaryDark,
    onPrimary = BentoOnPrimaryDark,
    primaryContainer = BentoPrimaryContainerDark,
    onPrimaryContainer = BentoOnPrimaryContainerDark,
    secondary = BentoSecondaryDark,
    onSecondary = BentoOnSecondaryDark,
    liveRed = BentoLiveRed,
    liveRedBright = BentoLiveRedBright,
    green = BentoGreen
)

private val BentoLightColorScheme = lightColorScheme(
    primary = BentoPrimaryLight,
    onPrimary = BentoOnPrimaryLight,
    primaryContainer = BentoPrimaryContainerLight,
    onPrimaryContainer = BentoOnPrimaryContainerLight,
    secondary = BentoSecondaryLight,
    onSecondary = BentoOnSecondaryLight,
    tertiary = BentoGreen,
    background = BentoBgLight,
    onBackground = BentoTextPrimaryLight,
    surface = BentoCardWhiteLight,
    onSurface = BentoTextPrimaryLight,
    surfaceVariant = BentoSurfaceContainerLight,
    onSurfaceVariant = BentoTextSecondaryLight,
    outline = BentoBorderLight,
    outlineVariant = BentoBorderSubtleLight,
    error = BentoLiveRed,
    onError = Color.White
)

private val BentoDarkColorScheme = darkColorScheme(
    primary = BentoPrimaryDark,
    onPrimary = BentoOnPrimaryDark,
    primaryContainer = BentoPrimaryContainerDark,
    onPrimaryContainer = BentoOnPrimaryContainerDark,
    secondary = BentoSecondaryDark,
    onSecondary = BentoOnSecondaryDark,
    tertiary = BentoGreen,
    background = BentoBgDark,
    onBackground = BentoTextPrimaryDark,
    surface = BentoCardDark,
    onSurface = BentoTextPrimaryDark,
    surfaceVariant = BentoSurfaceContainerDark,
    onSurfaceVariant = BentoTextSecondaryDark,
    outline = BentoBorderDark,
    outlineVariant = BentoBorderSubtleDark,
    error = BentoLiveRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) BentoDarkColorScheme else BentoLightColorScheme
    val bentoColors = if (isDark) DarkBentoColors else LightBentoColors

    CompositionLocalProvider(LocalBentoColors provides bentoColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

