package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.9.27 (item 1): the per-theme, per-mode list-treatment defaults —
 * Ashraf's review picks (widget v155, confirmed 2026-11-20). Pure
 * Kotlin under test, so plain JUnit (no Robolectric).
 */
class ListTreatmentTest {

    @Test
    fun recentsLightDefaultsMatchReviewPicks() {
        // Tint except Clear/Gentle/Pulse (border).
        for (id in listOf("bento_blue", "classic", "desert_dawn", "fresh", "midnight")) {
            assertEquals("$id light recents are tint", ListTreatment.TINT, defaultListTreatment(id, false, ListKind.RECENT))
        }
        for (id in listOf("clear", "gentle", "pulse")) {
            assertEquals("$id light recents are border", ListTreatment.BORDER, defaultListTreatment(id, false, ListKind.RECENT))
        }
    }

    @Test
    fun recentsDarkDefaultsMatchReviewPicks() {
        // Border except Classic/Midnight (soft) and Fresh (line).
        for (id in listOf("bento_blue", "clear", "desert_dawn", "gentle", "pulse")) {
            assertEquals("$id dark recents are border", ListTreatment.BORDER, defaultListTreatment(id, true, ListKind.RECENT))
        }
        for (id in listOf("classic", "midnight")) {
            assertEquals("$id dark recents are soft", ListTreatment.SOFT, defaultListTreatment(id, true, ListKind.RECENT))
        }
        assertEquals("fresh dark recents are line", ListTreatment.LINE, defaultListTreatment("fresh", true, ListKind.RECENT))
    }

    @Test
    fun sermonDefaultsMatchReviewPicks() {
        // Solid everywhere except Desert Dawn light (soft).
        val ids = listOf("classic", "clear", "pulse", "fresh", "gentle", "bento_blue", "desert_dawn", "midnight")
        for (id in ids) {
            for (isDark in listOf(true, false)) {
                val expected = if (!isDark && id == "desert_dawn") ListTreatment.SOFT else ListTreatment.SOLID
                assertEquals("$id ${if (isDark) "dark" else "light"} sermon", expected, defaultListTreatment(id, isDark, ListKind.SERMON))
            }
        }
    }

    @Test
    fun unknownThemeFallsBackToMajorityPick() {
        assertEquals("unknown light recents fall back to tint", ListTreatment.TINT, defaultListTreatment("no-such-theme", false, ListKind.RECENT))
        assertEquals("unknown dark recents fall back to border", ListTreatment.BORDER, defaultListTreatment("no-such-theme", true, ListKind.RECENT))
        assertEquals("unknown sermon falls back to solid", ListTreatment.SOLID, defaultListTreatment("no-such-theme", false, ListKind.SERMON))
    }

    @Test
    fun listTreatmentFromNameParsesSafely() {
        assertEquals("name parses", ListTreatment.TINT, listTreatmentFromName("TINT"))
        assertNull("null stays null", listTreatmentFromName(null))
        assertNull("unknown name falls back to null", listTreatmentFromName("nope"))
    }
}
