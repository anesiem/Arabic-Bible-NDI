package com.arabicchristianmedia.model

import org.json.JSONObject

/**
 * App color themes (theme system): a named theme is a dark + light token
 * set for app chrome + list colors. Broadcast style templates are NOT part
 * of themes (they keep their own per-feed colors). ON AIR red/grey and the
 * green action button are FIXED across all themes (standing rulings, never
 * themed). Tokens not carried here (onPrimaryContainer, secondary,
 * onSecondary, sermon-live navy/yellow, verse-live-highlight) stay at
 * Classic values; onPrimary is derived from the primary (see
 * [onPrimaryFor]) so vivid primaries keep readable button text.
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
    val warning: Long
)

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
            bandA = 0xFF3B3052, bandB = 0xFF2B3A42, recents = 0xFF0D9488, warning = 0xFFFBBF24
        ),
        light = ColorThemeTokens(
            bg = 0xFFFEF7FF, card = 0xFFFFFFFF, surfaceContainer = 0xFFF3EDF7,
            surfaceVariant = 0xFFEADDFF, border = 0xFFCAC4D0, borderSubtle = 0xFFE7E0EC,
            textPrimary = 0xFF1D1B20, textSecondary = 0xFF49454F,
            primary = 0xFF6750A4, primaryContainer = 0xFFEADDFF,
            bandA = 0xFFEAE3F6, bandB = 0xFFDFE9F3, recents = 0xFF0D9488, warning = 0xFFB45309
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
            bandA = 0xFF3A3226, bandB = 0xFF2E3A2E, recents = 0xFFF59E0B, warning = 0xFFFBBF24
        ),
        light = ColorThemeTokens(
            bg = 0xFFFDF8F0, card = 0xFFFFFFFF, surfaceContainer = 0xFFF5EFE2,
            surfaceVariant = 0xFFE8DCC8, border = 0xFFC9BFA8, borderSubtle = 0xFFEDE6D6,
            textPrimary = 0xFF000000, textSecondary = 0xFF333333,
            primary = 0xFF1E40AF, primaryContainer = 0xFFDCE6F5,
            bandA = 0xFFF5E6C8, bandB = 0xFFDDE8D0, recents = 0xFFB45309, warning = 0xFFB45309
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
            bandA = 0xFF2D1B4E, bandB = 0xFF123B3F, recents = 0xFF22D3EE, warning = 0xFFFBBF24
        ),
        light = ColorThemeTokens(
            bg = 0xFFF5F3FF, card = 0xFFFFFFFF, surfaceContainer = 0xFFF0EDFB,
            surfaceVariant = 0xFFDDD6FE, border = 0xFFC9C2E0, borderSubtle = 0xFFE9E5F7,
            textPrimary = 0xFF1E1B4B, textSecondary = 0xFF4C4A6B,
            primary = 0xFF7C3AED, primaryContainer = 0xFFE6DFF5,
            bandA = 0xFFEDE9FE, bandB = 0xFFCFFAFE, recents = 0xFF0891B2, warning = 0xFFB45309
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
            bandA = 0xFF14352C, bandB = 0xFF3B2A12, recents = 0xFFFBBF24, warning = 0xFFFBBF24
        ),
        light = ColorThemeTokens(
            bg = 0xFFF0FDF9, card = 0xFFFFFFFF, surfaceContainer = 0xFFEAF9F1,
            surfaceVariant = 0xFFCFF5E0, border = 0xFFB9D8C6, borderSubtle = 0xFFE2F2E8,
            textPrimary = 0xFF0F172A, textSecondary = 0xFF475569,
            primary = 0xFF047857, primaryContainer = 0xFFC9EBD9,
            bandA = 0xFFD1FAE5, bandB = 0xFFFEF3C7, recents = 0xFFB45309, warning = 0xFFB45309
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
            bandA = 0xFF3B332B, bandB = 0xFF2E352E, recents = 0xFFE8A33D, warning = 0xFFFBBF24
        ),
        light = ColorThemeTokens(
            bg = 0xFFFDF6E3, card = 0xFFFFFBEB, surfaceContainer = 0xFFF7ECD2,
            surfaceVariant = 0xFFEFE0BC, border = 0xFFD8CBA8, borderSubtle = 0xFFEFE7D0,
            textPrimary = 0xFF292524, textSecondary = 0xFF57534E,
            primary = 0xFF92400E, primaryContainer = 0xFFF0DCB8,
            bandA = 0xFFF5E6C8, bandB = 0xFFE7E5D4, recents = 0xFFB45309, warning = 0xFFB45309
        )
    )

    val BUILT_INS: List<ColorTheme> = listOf(CLASSIC, CLEAR, PULSE, FRESH, GENTLE)

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
                warning = argb("warning", if (isDark) "#FBBF24" else "#B45309")
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
