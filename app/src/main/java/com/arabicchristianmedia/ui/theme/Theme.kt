package com.arabicchristianmedia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.arabicchristianmedia.model.AppThemeMode
import com.arabicchristianmedia.model.ColorTheme
import com.arabicchristianmedia.model.ColorThemeTokens
import com.arabicchristianmedia.model.ColorThemes
import com.arabicchristianmedia.model.onPrimaryFor

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
    val green: Color,
    val warning: Color
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
        green = BentoGreen,
        warning = Color(0xFFB45309)
    )
}

/**
 * List colors: chapter bands + recents marker + live-row treatments, all
 * resolved from the selected app theme for the current mode. Every list
 * (Library, Sermon, recents sheet, remote page tokens) reads these — never
 * a scattered hex, and never the old per-screen pair lookup.
 * One-update item 17: verseLiveHighlight (live-verse row highlight),
 * recentLine (thin side line for a recent verse — the theme recents
 * variable, so a theme change recolours it on every surface) and
 * sermonTint (muted band colour for sermon verses) are theme variables
 * too: Library, Sermon, recents and the remote tokens all read them.
 * 2026-10-28: focusedBorder (yellow border for the focused verse) is the
 * remaining fixed accent (ON AIR red/grey are fixed by standing ruling).
 */
data class ListColors(
    val bandA: Color,
    val bandB: Color,
    val recents: Color,
    val sermonLiveNavy: Color,
    val sermonLiveYellow: Color,
    val sermonLivePaleYellow: Color,
    val verseLiveHighlight: Color,
    val focusedBorder: Color,
    val recentLine: Color,
    val sermonTint: Color
)

val LocalListColors = staticCompositionLocalOf {
    ListColors(
        bandA = Color(ColorThemes.CLASSIC.light.bandA),
        bandB = Color(ColorThemes.CLASSIC.light.bandB),
        recents = Color(0xFF0D9488),
        sermonLiveNavy = SermonLiveNavy,
        sermonLiveYellow = SermonLiveYellow,
        sermonLivePaleYellow = SermonLivePaleYellow,
        verseLiveHighlight = VerseLiveHighlightLight,
        focusedBorder = SermonLiveYellow,
        recentLine = Color(0xFF0D9488),
        sermonTint = Color(ColorThemes.CLASSIC.light.bandB)
    )
}

/** Bento chrome + Material scheme, both built from one theme token set. */
private fun bentoColorsFromTheme(tokens: ColorThemeTokens, isDark: Boolean): BentoColors {
    return BentoColors(
        bg = Color(tokens.bg),
        card = Color(tokens.card),
        surfaceContainer = Color(tokens.surfaceContainer),
        surfaceVariant = Color(tokens.surfaceVariant),
        border = Color(tokens.border),
        borderSubtle = Color(tokens.borderSubtle),
        textPrimary = Color(tokens.textPrimary),
        textSecondary = Color(tokens.textSecondary),
        primary = Color(tokens.primary),
        onPrimary = Color(onPrimaryFor(tokens.primary)),
        primaryContainer = Color(tokens.primaryContainer),
        onPrimaryContainer = if (isDark) BentoOnPrimaryContainerDark else BentoOnPrimaryContainerLight,
        secondary = if (isDark) BentoSecondaryDark else BentoSecondaryLight,
        onSecondary = if (isDark) BentoOnSecondaryDark else BentoOnSecondaryLight,
        liveRed = BentoLiveRed,
        liveRedBright = BentoLiveRedBright,
        green = BentoGreen,
        warning = Color(tokens.warning)
    )
}

private fun colorSchemeFromTheme(bento: BentoColors, isDark: Boolean): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = bento.primary,
        onPrimary = bento.onPrimary,
        primaryContainer = bento.primaryContainer,
        onPrimaryContainer = bento.onPrimaryContainer,
        secondary = bento.secondary,
        onSecondary = bento.onSecondary,
        tertiary = bento.green,
        background = bento.bg,
        onBackground = bento.textPrimary,
        surface = bento.card,
        onSurface = bento.textPrimary,
        surfaceVariant = bento.surfaceContainer,
        onSurfaceVariant = bento.textSecondary,
        outline = bento.border,
        outlineVariant = bento.borderSubtle,
        error = bento.liveRed,
        onError = Color.White
    )
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorThemes.CLASSIC,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val tokens = colorTheme.tokens(isDark)
    val bentoColors = bentoColorsFromTheme(tokens, isDark)
    val colorScheme = colorSchemeFromTheme(bentoColors, isDark)
    val listColors = ListColors(
        bandA = Color(tokens.bandA),
        bandB = Color(tokens.bandB),
        recents = Color(tokens.recents),
        sermonLiveNavy = SermonLiveNavy,
        sermonLiveYellow = SermonLiveYellow,
        sermonLivePaleYellow = SermonLivePaleYellow,
        // One-update item 17: live highlight + sermon tint are theme
        // variables now (were fixed Classic values).
        verseLiveHighlight = Color(tokens.liveHighlight),
        focusedBorder = SermonLiveYellow,
        recentLine = Color(tokens.recents),
        sermonTint = Color(tokens.sermonTint)
    )

    CompositionLocalProvider(
        LocalBentoColors provides bentoColors,
        LocalListColors provides listColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

