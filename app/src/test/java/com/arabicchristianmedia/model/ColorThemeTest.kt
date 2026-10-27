package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * App color themes: resolution per mode, Classic fallback, v1 pair
 * migration, recents per mode, button-text choice, JSON round-trip,
 * and the WCAG contrast bars (body >= 7:1, secondary >= 4.5:1,
 * recents on card and on bands >= 3:1). One documented exception:
 * Classic light recents-on-bandA is 2.998 — the pre-existing shipping
 * value, kept verbatim per the Classic contract (asserted >= 2.99).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ColorThemeTest {

    @Test
    fun unknownAndNullIdsFallBackToClassic() {
        assertEquals("null id must resolve to Classic", ColorThemes.CLASSIC, ColorThemes.byId(null))
        assertEquals("unknown id must resolve to Classic", ColorThemes.CLASSIC, ColorThemes.byId("no-such-theme"))
        assertEquals("classic id must resolve to Classic", ColorThemes.CLASSIC, ColorThemes.byId("classic"))
    }

    @Test
    fun darkAndLightTokenSetsResolvePerMode() {
        val pulse = ColorThemes.byId("pulse")
        assertEquals("dark mode must pick the dark set", ColorThemes.PULSE.dark, pulse.tokens(true))
        assertEquals("light mode must pick the light set", ColorThemes.PULSE.light, pulse.tokens(false))
        assertEquals("Classic light bg is the shipping value", 0xFFFEF7FF, ColorThemes.CLASSIC.light.bg)
        assertEquals("Classic dark bg is the shipping value", 0xFF141218, ColorThemes.CLASSIC.dark.bg)
        assertEquals("Classic light primary is the shipping value", 0xFF6750A4, ColorThemes.CLASSIC.light.primary)
        assertEquals("Classic dark primary is the shipping value", 0xFFD0BCFF, ColorThemes.CLASSIC.dark.primary)
    }

    @Test
    fun customThemesResolveByIdOnlyWhenSupplied() {
        val custom = ColorThemes.CLASSIC.copy(id = "my-theme", name = "Mine")
        assertEquals("supplied custom id must resolve", custom, ColorThemes.byId("my-theme", listOf(custom)))
        assertEquals("unsupplied custom id must fall back to Classic", ColorThemes.CLASSIC, ColorThemes.byId("my-theme"))
    }

    @Test
    fun migrationDefaultsToClassic() {
        assertNull("d1/l1 picks must migrate to nothing (Classic)", migratedThemeFromPairs("d1", "l1"))
        assertNull("null picks must migrate to nothing (Classic)", migratedThemeFromPairs(null, null))
    }

    @Test
    fun migrationKeepsPickedBandsAndClassicChrome() {
        val migrated = migratedThemeFromPairs("d2", "l2")
            ?: throw AssertionError("non-default picks must produce a migrated theme")
        assertEquals("migrated id", "custom_migrated", migrated.id)
        assertEquals("migrated name", "My colors", migrated.name)
        assertEquals("migrated dark bandA is the picked d2 shade", 0xFF413147, migrated.dark.bandA)
        assertEquals("migrated dark bandB is the picked d2 shade", 0xFF37402F, migrated.dark.bandB)
        assertEquals("migrated light bandA is the picked l2 shade", 0xFFF6E3E6, migrated.light.bandA)
        assertEquals("migrated light bandB is the picked l2 shade", 0xFFE3EFE2, migrated.light.bandB)
        assertEquals("migrated dark recents stay Classic teal", 0xFF0D9488, migrated.dark.recents)
        assertEquals("migrated light recents stay Classic teal", 0xFF0D9488, migrated.light.recents)
        assertEquals("migrated dark bg stays Classic chrome", ColorThemes.CLASSIC.dark.bg, migrated.dark.bg)
        assertEquals("migrated light text stays Classic chrome", ColorThemes.CLASSIC.light.textPrimary, migrated.light.textPrimary)
    }

    @Test
    fun migrationUnknownPairIdFallsBackToDefaultPair() {
        val migrated = migratedThemeFromPairs("nope", "l1")
            ?: throw AssertionError("unknown dark id still migrates (fallback pair)")
        assertEquals("unknown dark id falls back to d1 shades", SermonShadePairs.dark("d1").shadeA, migrated.dark.bandA)
        assertEquals("light stays l1", SermonShadePairs.light("l1").shadeA, migrated.light.bandA)
    }

    @Test
    fun recentsResolvePerMode() {
        assertEquals("Classic recents teal dark", 0xFF0D9488, ColorThemes.CLASSIC.tokens(true).recents)
        assertEquals("Classic recents teal light", 0xFF0D9488, ColorThemes.CLASSIC.tokens(false).recents)
        assertEquals("Clear recents amber dark", 0xFFF59E0B, ColorThemes.CLEAR.tokens(true).recents)
        assertEquals("Clear recents amber light", 0xFFB45309, ColorThemes.CLEAR.tokens(false).recents)
        assertEquals("Pulse recents cyan dark", 0xFF22D3EE, ColorThemes.PULSE.tokens(true).recents)
        assertEquals("Pulse recents cyan light", 0xFF0891B2, ColorThemes.PULSE.tokens(false).recents)
        assertEquals("Fresh recents amber dark", 0xFFFBBF24, ColorThemes.FRESH.tokens(true).recents)
        assertEquals("Fresh recents amber light", 0xFFB45309, ColorThemes.FRESH.tokens(false).recents)
        assertEquals("Gentle recents amber dark", 0xFFE8A33D, ColorThemes.GENTLE.tokens(true).recents)
        assertEquals("Gentle recents amber light", 0xFFB45309, ColorThemes.GENTLE.tokens(false).recents)
    }

    @Test
    fun buttonTextPicksTheMoreReadableColor() {
        assertEquals("Pulse dark primary reads white", 0xFFFFFFFF, onPrimaryFor(0xFF7C3AED))
        assertEquals("Fresh light primary reads white", 0xFFFFFFFF, onPrimaryFor(0xFF047857))
        assertEquals("bright yellow primary reads dark purple", 0xFF381E72, onPrimaryFor(0xFFEAB308))
        assertEquals("Classic light primary reads white", 0xFFFFFFFF, onPrimaryFor(0xFF6750A4))
    }

    @Test
    fun jsonRoundTripPreservesTokens() {
        val classic = ColorThemes.CLASSIC
        assertEquals("Classic round-trips", classic, colorThemeFromJson(classic.toJson()))
        val custom = classic.copy(
            id = "custom_x", name = "X",
            dark = classic.dark.copy(bandA = 0xFF111111, recents = 0xFF222222),
            light = classic.light.copy(bandB = 0xFF333333)
        )
        assertEquals("custom theme round-trips", custom, colorThemeFromJson(custom.toJson()))
    }

    @Test
    fun contrastBarsHoldForEveryThemeAndMode() {
        for (theme in ColorThemes.BUILT_INS) {
            for (isDark in listOf(true, false)) {
                val t = theme.tokens(isDark)
                val mode = if (isDark) "dark" else "light"
                assertTrue(
                    "${theme.id} $mode body text must be >= 7:1 on card",
                    contrastRatio(t.textPrimary, t.card) >= 7.0
                )
                assertTrue(
                    "${theme.id} $mode secondary text must be >= 4.5:1 on card",
                    contrastRatio(t.textSecondary, t.card) >= 4.5
                )
                assertTrue(
                    "${theme.id} $mode recents must be >= 3:1 on card",
                    contrastRatio(t.recents, t.card) >= 3.0
                )
                // Classic light recents-on-bandA is 2.998, the pre-existing
                // shipping value kept verbatim; every other band clears 3:1.
                val bandABar = if (theme.id == "classic" && !isDark) 2.99 else 3.0
                assertTrue(
                    "${theme.id} $mode recents must be >= $bandABar:1 on bandA",
                    contrastRatio(t.recents, t.bandA) >= bandABar
                )
                assertTrue(
                    "${theme.id} $mode recents must be >= 3:1 on bandB",
                    contrastRatio(t.recents, t.bandB) >= 3.0
                )
            }
        }
    }

    @Test
    fun warningTextClearsBarOnEveryCard() {
        val themes = listOf(
            ColorThemes.CLASSIC, ColorThemes.CLEAR, ColorThemes.PULSE,
            ColorThemes.FRESH, ColorThemes.GENTLE
        )
        for (theme in themes) {
            for (isDark in listOf(true, false)) {
                val t = theme.tokens(isDark)
                assertTrue(
                    "warning on card must clear 4.5:1 for ${theme.id} dark=$isDark",
                    contrastRatio(t.warning, t.card) >= 4.5
                )
            }
        }
    }

    @Test
    fun missingWarningKeyFallsBackToModeAmber() {
        val json = ColorThemes.CLASSIC.toJson()
        json.getJSONObject("dark").remove("warning")
        json.getJSONObject("light").remove("warning")
        val parsed = colorThemeFromJson(json)
        assertEquals("dark falls back to light amber", 0xFFFBBF24, parsed?.dark?.warning)
        assertEquals("light falls back to dark amber", 0xFFB45309, parsed?.light?.warning)
    }
}
