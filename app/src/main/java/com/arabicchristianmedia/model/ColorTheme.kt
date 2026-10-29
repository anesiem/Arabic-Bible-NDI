package com.arabicchristianmedia.model

import org.json.JSONObject

/**
 * App color themes (theme system): a named theme is a dark + light token
 * set for app chrome + list colors. Broadcast style templates are NOT part
 * of themes (they keep their own per-feed colors). ON AIR red/grey and the
 * green action button are FIXED across all themes (standing rulings, never
 * themed). Tokens not carried here (onPrimaryContainer, secondary,
 * onSecondary) stay at Classic values; onPrimary is derived from the
 * primary (see [onPrimaryFor]) so vivid primaries keep readable button
 * text.
 *
 * One-update item 17: liveHighlight (live-verse row highlight in
 * Library/recents/sermon), recents (recents marker + side line) and
 * sermonTint (muted band colour for sermon verses) are theme variables —
 * every surface reads them from the active theme, including the remote
 * page tokens. Item 6: bandA/bandB are the alternate Editor-section-card
 * backgrounds (user-controllable here).
 *
 * Contrast, verified by relative-luminance calculation (not eyeballed):
 * body text on card >= 7:1, secondary on card >= 4.5:1, recents marker on
 * card and on both bands >= 3:1, for every theme except Classic light's
 * recents-on-bandA (2.997, the pre-existing shipping value, kept verbatim
 * per the Classic contract below).
 */
data class ColorThemeTokens(
    val bg: Long,
    val card: Long,
    val surfaceContainer: Long,
    val surfaceVariant: Long,
    val border: Long,
    val borderSubtle: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val primary: Long,
    val primaryContainer: Long,
    val bandA: Long,
    val bandB: Long,
    val recents: Long,
    val warning: Long,
    val liveHighlight: Long,
    val sermonTint: Long,
    val groupPentateuch: Long,
    val groupHistorical: Long,
    val groupPoetry: Long,
    val groupMajorProphets: Long,
    val groupMinorProphets: Long,
    val groupGospels: Long,
    val groupActs: Long,
    val groupPauline: Long,
    val groupGeneral: Long,
    val groupRevelation: Long
)

/**
 * One-update item 21: book-group color accessors by group id
 * (pentateuch … revelation), so the theme editor and the picker can read
 * and write them without reflecting over the token fields.
 */
fun ColorThemeTokens.groupColor(groupId: String): Long = when (groupId) {
    "pentateuch" -> groupPentateuch
    "historical" -> groupHistorical
    "poetry" -> groupPoetry
    "major_prophets" -> groupMajorProphets
    "minor_prophets" -> groupMinorProphets
    "gospels" -> groupGospels
    "acts" -> groupActs
    "pauline" -> groupPauline
    "general" -> groupGeneral
    "revelation" -> groupRevelation
    else -> 0xFF94A3B8
}

fun ColorThemeTokens.withGroupColor(groupId: String, argb: Long): ColorThemeTokens = when (groupId) {
    "pentateuch" -> copy(groupPentateuch = argb)
    "historical" -> copy(groupHistorical = argb)
    "poetry" -> copy(groupPoetry = argb)
    "major_prophets" -> copy(groupMajorProphets = argb)
    "minor_prophets" -> copy(groupMinorProphets = argb)
    "gospels" -> copy(groupGospels = argb)
    "acts" -> copy(groupActs = argb)
    "pauline" -> copy(groupPauline = argb)
    "general" -> copy(groupGeneral = argb)
    "revelation" -> copy(groupRevelation = argb)
    else -> this
}

data class ColorTheme(
    val id: String,
    val name: String,
    val dark: ColorThemeTokens,
    val light: ColorThemeTokens
) {
    fun tokens(isDark: Boolean): ColorThemeTokens = if (isDark) dark else light
}

/** Button text on a primary fill: whichever of white / Classic dark-purple reads better. */
fun onPrimaryFor(primaryArgb: Long): Long {
    val white = contrastRatio(0xFFFFFFFF, primaryArgb)
    val dark = contrastRatio(0xFF381E72, primaryArgb)
    return if (white >= dark) 0xFFFFFFFF else 0xFF381E72
}

/** WCAG relative-luminance contrast ratio between two ARGB colors. */
fun contrastRatio(aArgb: Long, bArgb: Long): Double {
    fun lum(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
    val la = lum(aArgb)
    val lb = lum(bArgb)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

object ColorThemes {
    /** DEFAULT, selected on fresh install: exactly today's colors, verbatim. */
    val CLASSIC = ColorTheme(
        id = "classic",
        name = "Classic",
        dark = ColorThemeTokens(
            bg = 0xFF141218, card = 0xFF1D1B20, surfaceContainer = 0xFF2B2930,
            surfaceVariant = 0xFF4A4458, border = 0xFF49454F, borderSubtle = 0xFF36343B,
            textPrimary = 0xFFE6E1E5, textSecondary = 0xFFCAC4D0,
            primary = 0xFFD0BCFF, primaryContainer = 0xFF4F378B,
            bandA = 0xFF3B3052, bandB = 0xFF2B3A42, recents = 0xFF0D9488, warning = 0xFFFBBF24,
            liveHighlight = 0xFF1E293B, sermonTint = 0xFF2B3A42,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFFEF7FF, card = 0xFFFFFFFF, surfaceContainer = 0xFFF3EDF7,
            surfaceVariant = 0xFFEADDFF, border = 0xFFCAC4D0, borderSubtle = 0xFFE7E0EC,
            textPrimary = 0xFF1D1B20, textSecondary = 0xFF49454F,
            primary = 0xFF6750A4, primaryContainer = 0xFFEADDFF,
            bandA = 0xFFEAE3F6, bandB = 0xFFDFE9F3, recents = 0xFF0D9488, warning = 0xFFB45309,
            liveHighlight = 0xFFE0F2FE, sermonTint = 0xFFDFE9F3,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    /** Seniors: warm hues (the aging lens keeps warm distinctions; blue fades),
     * warm off-white bg (less glare than pure white), strongest recents luminance. */
    val CLEAR = ColorTheme(
        id = "clear",
        name = "Clear",
        dark = ColorThemeTokens(
            bg = 0xFF1A1712, card = 0xFF26221C, surfaceContainer = 0xFF2E2820,
            surfaceVariant = 0xFF4A3F2A, border = 0xFF57503F, borderSubtle = 0xFF3A332A,
            textPrimary = 0xFFFFFFFF, textSecondary = 0xFFD6D0C4,
            primary = 0xFFEAB308, primaryContainer = 0xFF5A4A1A,
            bandA = 0xFF3A3226, bandB = 0xFF2E3A2E, recents = 0xFFF59E0B, warning = 0xFFFBBF24,
            liveHighlight = 0xFF1E293B, sermonTint = 0xFF2E3A2E,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFFDF8F0, card = 0xFFFFFFFF, surfaceContainer = 0xFFF5EFE2,
            surfaceVariant = 0xFFE8DCC8, border = 0xFFC9BFA8, borderSubtle = 0xFFEDE6D6,
            textPrimary = 0xFF000000, textSecondary = 0xFF333333,
            primary = 0xFF1E40AF, primaryContainer = 0xFFDCE6F5,
            bandA = 0xFFF5E6C8, bandB = 0xFFDDE8D0, recents = 0xFFB45309, warning = 0xFFB45309,
            liveHighlight = 0xFFE0F2FE, sermonTint = 0xFFDDE8D0,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    /** Teens, dark-first: vivid chrome, but color lives only in chrome/bands/
     * markers — body text stays near-white on dark (15.8:1). Note: Pulse dark
     * primary is darkened violet, so white button text on it clears 4.5:1
     * (5.70:1); onPrimaryFor picks white. */
    val PULSE = ColorTheme(
        id = "pulse",
        name = "Pulse",
        dark = ColorThemeTokens(
            bg = 0xFF0F0A1E, card = 0xFF1E1533, surfaceContainer = 0xFF271B40,
            surfaceVariant = 0xFF3D2A5E, border = 0xFF4A3A6B, borderSubtle = 0xFF2E2347,
            textPrimary = 0xFFF5F3FF, textSecondary = 0xFFC4B5FD,
            primary = 0xFF7C3AED, primaryContainer = 0xFF4C2E8B,
            bandA = 0xFF2D1B4E, bandB = 0xFF123B3F, recents = 0xFF22D3EE, warning = 0xFFFBBF24,
            liveHighlight = 0xFF1E293B, sermonTint = 0xFF123B3F,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFF5F3FF, card = 0xFFFFFFFF, surfaceContainer = 0xFFF0EDFB,
            surfaceVariant = 0xFFDDD6FE, border = 0xFFC9C2E0, borderSubtle = 0xFFE9E5F7,
            textPrimary = 0xFF1E1B4B, textSecondary = 0xFF4C4A6B,
            primary = 0xFF7C3AED, primaryContainer = 0xFFE6DFF5,
            bandA = 0xFFEDE9FE, bandB = 0xFFCFFAFE, recents = 0xFF0891B2, warning = 0xFFB45309,
            liveHighlight = 0xFFE0F2FE, sermonTint = 0xFFCFFAFE,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    /** Youth / young adults: bright, clean, daytime. Fresh light recents is
     * deep amber #B45309 (not #D97706: the lighter amber fails band contrast). */
    val FRESH = ColorTheme(
        id = "fresh",
        name = "Fresh",
        dark = ColorThemeTokens(
            bg = 0xFF0C1A17, card = 0xFF142A25, surfaceContainer = 0xFF1C332C,
            surfaceVariant = 0xFF2A4A3E, border = 0xFF3D5A4E, borderSubtle = 0xFF24382F,
            textPrimary = 0xFFECFDF5, textSecondary = 0xFFA7F3D0,
            primary = 0xFF34D399, primaryContainer = 0xFF14532D,
            bandA = 0xFF14352C, bandB = 0xFF3B2A12, recents = 0xFFFBBF24, warning = 0xFFFBBF24,
            liveHighlight = 0xFF1E293B, sermonTint = 0xFF3B2A12,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFF0FDF9, card = 0xFFFFFFFF, surfaceContainer = 0xFFEAF9F1,
            surfaceVariant = 0xFFCFF5E0, border = 0xFFB9D8C6, borderSubtle = 0xFFE2F2E8,
            textPrimary = 0xFF0F172A, textSecondary = 0xFF475569,
            primary = 0xFF047857, primaryContainer = 0xFFC9EBD9,
            bandA = 0xFFD1FAE5, bandB = 0xFFFEF3C7, recents = 0xFFB45309, warning = 0xFFB45309,
            liveHighlight = 0xFFE0F2FE, sermonTint = 0xFFFEF3C7,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    /** Long reading: sepia/warm, low saturation — warm paper tones cut blue
     * light and glare over a three-hour service. */
    val GENTLE = ColorTheme(
        id = "gentle",
        name = "Gentle",
        dark = ColorThemeTokens(
            bg = 0xFF1C1917, card = 0xFF292524, surfaceContainer = 0xFF322C26,
            surfaceVariant = 0xFF4A3F31, border = 0xFF57503F, borderSubtle = 0xFF3A332B,
            textPrimary = 0xFFF5F5F4, textSecondary = 0xFFD6D3D1,
            primary = 0xFFC89B6A, primaryContainer = 0xFF5C4626,
            bandA = 0xFF3B332B, bandB = 0xFF2E352E, recents = 0xFFE8A33D, warning = 0xFFFBBF24,
            liveHighlight = 0xFF1E293B, sermonTint = 0xFF2E352E,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFFDF6E3, card = 0xFFFFFBEB, surfaceContainer = 0xFFF7ECD2,
            surfaceVariant = 0xFFEFE0BC, border = 0xFFD8CBA8, borderSubtle = 0xFFEFE7D0,
            textPrimary = 0xFF292524, textSecondary = 0xFF57534E,
            primary = 0xFF92400E, primaryContainer = 0xFFF0DCB8,
            bandA = 0xFFF5E6C8, bandB = 0xFFE7E5D4, recents = 0xFFB45309, warning = 0xFFB45309,
            liveHighlight = 0xFFE0F2FE, sermonTint = 0xFFE7E5D4,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    /** One-update item 18: Bento Blue — blue shades primary, yellow
     * live/highlight, grey surfaces. Designed separately for dark and
     * light. */
    val BENTO_BLUE = ColorTheme(
        id = "bento_blue",
        name = "Bento Blue",
        dark = ColorThemeTokens(
            bg = 0xFF101418, card = 0xFF1A2027, surfaceContainer = 0xFF222B34,
            surfaceVariant = 0xFF2E3947, border = 0xFF3A4552, borderSubtle = 0xFF28303A,
            textPrimary = 0xFFF1F5F9, textSecondary = 0xFF94A3B8,
            primary = 0xFF3B82F6, primaryContainer = 0xFF1E3A5F,
            bandA = 0xFF1E293B, bandB = 0xFF1C2B3A, recents = 0xFFF59E0B, warning = 0xFFFBBF24,
            liveHighlight = 0xFFFACC15, sermonTint = 0xFF243447,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFF8FAFC, card = 0xFFFFFFFF, surfaceContainer = 0xFFF1F5F9,
            surfaceVariant = 0xFFE2E8F0, border = 0xFFCBD5E1, borderSubtle = 0xFFE2E8F0,
            textPrimary = 0xFF0F172A, textSecondary = 0xFF475569,
            primary = 0xFF1D4ED8, primaryContainer = 0xFFDBEAFE,
            bandA = 0xFFDBEAFE, bandB = 0xFFFEF3C7, recents = 0xFFB45309, warning = 0xFFB45309,
            liveHighlight = 0xFFFEF08A, sermonTint = 0xFFE0EAF5,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    /** One-update item 18: Desert Dawn — warm, low-glare, recommended for
     * long daytime use. */
    val DESERT_DAWN = ColorTheme(
        id = "desert_dawn",
        name = "Desert Dawn",
        dark = ColorThemeTokens(
            bg = 0xFF1C1712, card = 0xFF262019, surfaceContainer = 0xFF2E2820,
            surfaceVariant = 0xFF453A2A, border = 0xFF57493A, borderSubtle = 0xFF38302A,
            textPrimary = 0xFFFFF7ED, textSecondary = 0xFFD6C4A8,
            primary = 0xFFF59E0B, primaryContainer = 0xFF5A3D14,
            bandA = 0xFF33291D, bandB = 0xFF3A2E1E, recents = 0xFFFB923C, warning = 0xFFFBBF24,
            liveHighlight = 0xFF7C2D12, sermonTint = 0xFF3A2F22,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFFDF8F0, card = 0xFFFFFFFF, surfaceContainer = 0xFFFAF3E8,
            surfaceVariant = 0xFFF0E4D0, border = 0xFFD8C9A8, borderSubtle = 0xFFEDE4D0,
            textPrimary = 0xFF292524, textSecondary = 0xFF57534E,
            primary = 0xFFB45309, primaryContainer = 0xFFF5E0C0,
            bandA = 0xFFF5E6C8, bandB = 0xFFEDE0C8, recents = 0xFFB45309, warning = 0xFFB45309,
            liveHighlight = 0xFFFEF3C7, sermonTint = 0xFFF0E4CE,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    /** One-update item 18: Midnight — deep navy-black dark-first theme. */
    val MIDNIGHT = ColorTheme(
        id = "midnight",
        name = "Midnight",
        dark = ColorThemeTokens(
            bg = 0xFF060913, card = 0xFF0C1220, surfaceContainer = 0xFF111A2E,
            surfaceVariant = 0xFF1B2540, border = 0xFF2A3654, borderSubtle = 0xFF182036,
            textPrimary = 0xFFE2E8F0, textSecondary = 0xFF94A3B8,
            primary = 0xFF60A5FA, primaryContainer = 0xFF1E3A8A,
            bandA = 0xFF101A30, bandB = 0xFF0F2033, recents = 0xFF22D3EE, warning = 0xFFFBBF24,
            liveHighlight = 0xFF1E3A8A, sermonTint = 0xFF14243D,
            groupPentateuch = 0xFFF59E0B,
            groupHistorical = 0xFF38BDF8,
            groupPoetry = 0xFFA78BFA,
            groupMajorProphets = 0xFFF472B6,
            groupMinorProphets = 0xFFFB923C,
            groupGospels = 0xFF34D399,
            groupActs = 0xFF22D3EE,
            groupPauline = 0xFF60A5FA,
            groupGeneral = 0xFFA3E635,
            groupRevelation = 0xFFF87171
        ),
        light = ColorThemeTokens(
            bg = 0xFFF0F4FA, card = 0xFFFFFFFF, surfaceContainer = 0xFFE8EEF7,
            surfaceVariant = 0xFFD6E2F2, border = 0xFFB8C9E2, borderSubtle = 0xFFDDE6F2,
            textPrimary = 0xFF0F1B2D, textSecondary = 0xFF475569,
            primary = 0xFF1E40AF, primaryContainer = 0xFFDBEAFE,
            bandA = 0xFFDBEAFE, bandB = 0xFFCFFAFE, recents = 0xFF0891B2, warning = 0xFFB45309,
            liveHighlight = 0xFFFEF08A, sermonTint = 0xFFD9E6F5,
            groupPentateuch = 0xFFB45309,
            groupHistorical = 0xFF0284C7,
            groupPoetry = 0xFF7C3AED,
            groupMajorProphets = 0xFFDB2777,
            groupMinorProphets = 0xFFEA580C,
            groupGospels = 0xFF047857,
            groupActs = 0xFF0891B2,
            groupPauline = 0xFF1D4ED8,
            groupGeneral = 0xFF4D7C0F,
            groupRevelation = 0xFFDC2626
        )
    )

    val BUILT_INS: List<ColorTheme> = listOf(CLASSIC, CLEAR, PULSE, FRESH, GENTLE, BENTO_BLUE, DESERT_DAWN, MIDNIGHT)

    fun isBuiltIn(id: String?): Boolean = BUILT_INS.any { it.id == id }

    /** Resolve a selected id (built-ins first, then customs) — unknown ids fall back to Classic. */
    fun byId(id: String?, customs: List<ColorTheme> = emptyList()): ColorTheme {
        if (id != null) {
            BUILT_INS.firstOrNull { it.id == id }?.let { return it }
            customs.firstOrNull { it.id == id }?.let { return it }
        }
        return CLASSIC
    }
}

/** JSON codec for custom themes (SharedPreferences, app_theme_prefs). Hex strings keep the file readable. */
fun ColorTheme.toJson(): JSONObject {
    fun tokensJson(t: ColorThemeTokens): JSONObject {
        fun hex(v: Long): String = "#%06X".format(v and 0xFFFFFF)
        return JSONObject().apply {
            put("bg", hex(t.bg)); put("card", hex(t.card))
            put("surface", hex(t.surfaceContainer)); put("variant", hex(t.surfaceVariant))
            put("border", hex(t.border)); put("borderSub", hex(t.borderSubtle))
            put("text", hex(t.textPrimary)); put("textSec", hex(t.textSecondary))
            put("primary", hex(t.primary)); put("primaryCont", hex(t.primaryContainer))
            put("bandA", hex(t.bandA)); put("bandB", hex(t.bandB)); put("recents", hex(t.recents)); put("warning", hex(t.warning))
            put("liveHighlight", hex(t.liveHighlight)); put("sermonTint", hex(t.sermonTint))
            put("gPent", hex(t.groupPentateuch)); put("gHist", hex(t.groupHistorical)); put("gPoet", hex(t.groupPoetry))
            put("gMajP", hex(t.groupMajorProphets)); put("gMinP", hex(t.groupMinorProphets)); put("gGosp", hex(t.groupGospels))
            put("gActs", hex(t.groupActs)); put("gPaul", hex(t.groupPauline)); put("gGen", hex(t.groupGeneral)); put("gRev", hex(t.groupRevelation))
        }
    }
    return JSONObject().apply {
        put("id", id); put("name", name)
        put("dark", tokensJson(dark)); put("light", tokensJson(light))
    }
}

fun colorThemeFromJson(obj: JSONObject): ColorTheme? {
    return try {
        fun parseTokens(o: JSONObject, isDark: Boolean): ColorThemeTokens {
            fun argb(key: String, default: String = "#000000"): Long {
                val raw = o.optString(key, default).removePrefix("#")
                return 0xFF000000 or raw.toLong(16)
            }
            return ColorThemeTokens(
                bg = argb("bg"), card = argb("card"),
                surfaceContainer = argb("surface"), surfaceVariant = argb("variant"),
                border = argb("border"), borderSubtle = argb("borderSub"),
                textPrimary = argb("text"), textSecondary = argb("textSec"),
                primary = argb("primary"), primaryContainer = argb("primaryCont"),
                bandA = argb("bandA"), bandB = argb("bandB"), recents = argb("recents"),
                // Pre-warning custom themes (saved before this field existed)
                // fall back to the per-mode amber, not black.
                warning = argb("warning", if (isDark) "#FBBF24" else "#B45309"),
                // One-update item 17: pre-update custom themes fall back to
                // the old fixed values (dark slate / light ice-blue for
                // live highlight, bandB for sermon tint).
                liveHighlight = argb("liveHighlight", if (isDark) "#1E293B" else "#E0F2FE"),
                sermonTint = argb("sermonTint", if (isDark) "#2B3A42" else "#DFE9F3"),
                groupPentateuch = argb("gPent", if (isDark) "#F59E0B" else "#B45309"),
                groupHistorical = argb("gHist", if (isDark) "#38BDF8" else "#0284C7"),
                groupPoetry = argb("gPoet", if (isDark) "#A78BFA" else "#7C3AED"),
                groupMajorProphets = argb("gMajP", if (isDark) "#F472B6" else "#DB2777"),
                groupMinorProphets = argb("gMinP", if (isDark) "#FB923C" else "#EA580C"),
                groupGospels = argb("gGosp", if (isDark) "#34D399" else "#047857"),
                groupActs = argb("gActs", if (isDark) "#22D3EE" else "#0891B2"),
                groupPauline = argb("gPaul", if (isDark) "#60A5FA" else "#1D4ED8"),
                groupGeneral = argb("gGen", if (isDark) "#A3E635" else "#4D7C0F"),
                groupRevelation = argb("gRev", if (isDark) "#F87171" else "#DC2626")
            )
        }
        ColorTheme(
            id = obj.getString("id"),
            name = obj.optString("name", "Custom"),
            dark = parseTokens(obj.getJSONObject("dark"), true),
            light = parseTokens(obj.getJSONObject("light"), false)
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * One-time migration (v1): a user with non-default sermon pair picks
 * keeps their exact look as a custom theme — Classic chrome + picked
 * bands + Classic (teal) recents. Returns null when both picks are the
 * defaults (d1/l1), meaning the user lands on Classic unchanged.
 * Pure function (no prefs) so the migration is unit-testable.
 */
fun migratedThemeFromPairs(darkPairId: String?, lightPairId: String?): ColorTheme? {
    val darkId = darkPairId ?: "d1"
    val lightId = lightPairId ?: "l1"
    if (darkId == "d1" && lightId == "l1") return null
    val classic = ColorThemes.CLASSIC
    val darkPair = SermonShadePairs.dark(darkId)
    val lightPair = SermonShadePairs.light(lightId)
    return ColorTheme(
        id = "custom_migrated",
        name = "My colors",
        dark = classic.dark.copy(bandA = darkPair.shadeA, bandB = darkPair.shadeB),
        light = classic.light.copy(bandA = lightPair.shadeA, bandB = lightPair.shadeB)
    )
}
