package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Clear Mode (2026-10-28 hotfix): what OFF AIR / Clear does.
 * CLEAR_ALL = today's behaviour (everything goes away).
 * CLEAR_VERSE_ONLY = text, citation and emblem blanked, every
 * layer below them stays. Defaults are by feed, not one global
 * default: Full Show -> verse only, Lower Third -> all.
 */
class ClearModeTest {

    @Test
    fun fullDefaultsToVerseOnly() {
        val tpl = LowerThirdTemplate(id = "t1", name = "Full", isFullScreen = true)
        assertEquals(ClearMode.CLEAR_VERSE_ONLY, tpl.clearMode)
    }

    @Test
    fun lowerDefaultsToAll() {
        val tpl = LowerThirdTemplate(id = "t2", name = "Lower", isFullScreen = false)
        assertEquals(ClearMode.CLEAR_ALL, tpl.clearMode)
    }

    @Test
    fun explicitValueSurvivesCopy() {
        val tpl = LowerThirdTemplate(id = "t3", name = "Lower", isFullScreen = false)
            .copy(clearMode = ClearMode.CLEAR_VERSE_ONLY)
        assertEquals(ClearMode.CLEAR_VERSE_ONLY, tpl.clearMode)
    }

    @Test
    fun decisionLiveIsLive() {
        assertEquals(
            ClearModeDecision.OffAirFrame.LIVE,
            ClearModeDecision.decide(true, ClearMode.CLEAR_ALL, true)
        )
    }

    @Test
    fun decisionVerseOnlyKeepsBackground() {
        assertEquals(
            ClearModeDecision.OffAirFrame.CLEAR_VERSE_BACKGROUND,
            ClearModeDecision.decide(false, ClearMode.CLEAR_VERSE_ONLY, true)
        )
    }

    @Test
    fun decisionAllGoesTransparent() {
        assertEquals(
            ClearModeDecision.OffAirFrame.CLEAR_ALL_TRANSPARENT,
            ClearModeDecision.decide(false, ClearMode.CLEAR_ALL, true)
        )
    }

    @Test
    fun decisionNoVerseEverStaysBlankEvenInVerseOnly() {
        // Fresh launch, no verse ever live: no empty card on launch.
        assertEquals(
            ClearModeDecision.OffAirFrame.CLEAR_ALL_TRANSPARENT,
            ClearModeDecision.decide(false, ClearMode.CLEAR_VERSE_ONLY, false)
        )
    }
}
