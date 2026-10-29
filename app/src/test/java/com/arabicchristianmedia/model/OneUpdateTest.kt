package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One-update batch (2026-10-29) unit tests:
 * - chapter-band alternation logic
 * - ClearModeDecision (off-air frame)
 * - auto-fit ratio freeze math (AutoFitRatio)
 * - book-group mapping (66 books -> 10 groups)
 * - recent-colors list logic (dedupe, cap, seed)
 */
class OneUpdateTest {

    // ---- Chapter-band alternation (BibleBookGroups.chapterBands) ----

    private fun verse(bookId: String, chapter: Int, verseNum: Int) = BibleVerse(
        id = "$bookId-$chapter-$verseNum",
        bookId = bookId, bookArabicName = bookId, bookEnglishName = bookId,
        chapter = chapter, verse = verseNum,
        arabicText = "نص", englishText = "text"
    )

    @Test
    fun bandsFlipOnChapterChangeNotPerRow() {
        val verses = listOf(
            verse("gen", 1, 1), verse("gen", 1, 2), verse("gen", 1, 3),
            verse("gen", 2, 1), verse("gen", 2, 2),
            verse("exo", 1, 1)
        )
        val bands = BibleBookGroups.chapterBands(verses)
        // Same chapter -> same band; chapter change -> flip.
        assertEquals(listOf(true, true, true, false, false, true), bands)
    }

    @Test
    fun bandsEmptyList() {
        assertEquals(emptyList<Boolean>(), BibleBookGroups.chapterBands(emptyList()))
    }

    @Test
    fun bandsSingleVerse() {
        assertEquals(listOf(true), BibleBookGroups.chapterBands(listOf(verse("gen", 1, 1))))
    }

    // ---- ClearModeDecision ----

    @Test
    fun clearDecisionLiveStaysLive() {
        assertEquals(
            ClearModeDecision.OffAirFrame.LIVE,
            ClearModeDecision.decide(true, ClearMode.CLEAR_ALL, true)
        )
        assertEquals(
            ClearModeDecision.OffAirFrame.LIVE,
            ClearModeDecision.decide(true, ClearMode.CLEAR_VERSE_ONLY, true)
        )
    }

    @Test
    fun clearDecisionNoVerseIsTransparent() {
        // Fresh launch, no verse ever selected -> transparent even in verse-only.
        assertEquals(
            ClearModeDecision.OffAirFrame.CLEAR_ALL_TRANSPARENT,
            ClearModeDecision.decide(false, ClearMode.CLEAR_VERSE_ONLY, false)
        )
    }

    @Test
    fun clearDecisionModes() {
        assertEquals(
            ClearModeDecision.OffAirFrame.CLEAR_VERSE_BACKGROUND,
            ClearModeDecision.decide(false, ClearMode.CLEAR_VERSE_ONLY, true)
        )
        assertEquals(
            ClearModeDecision.OffAirFrame.CLEAR_ALL_TRANSPARENT,
            ClearModeDecision.decide(false, ClearMode.CLEAR_ALL, true)
        )
    }

    // ---- AutoFitRatio (item 8 freeze math) ----

    @Test
    fun ratioRecordAndScale() {
        val ratio = AutoFitRatio.record(30, 21)
        assertTrue(ratio != null && ratio > 0f)
        // 30sp verse -> citation preserves 30/21.
        assertEquals(21, AutoFitRatio.scaledCitation(30, ratio!!))
        // 60sp verse -> 42sp citation (same ratio).
        assertEquals(42, AutoFitRatio.scaledCitation(60, ratio))
    }

    @Test
    fun ratioRecordZeroIsNull() {
        assertNull(AutoFitRatio.record(0, 21))
        assertNull(AutoFitRatio.record(30, 0))
    }

    @Test
    fun ratioPercent() {
        // 30/21 = 142.857% -> 143%.
        assertEquals(143, AutoFitRatio.percent(30f / 21f))
    }

    @Test
    fun ratioScaledCitationMinOne() {
        assertEquals(1, AutoFitRatio.scaledCitation(1, 100f))
    }

    // ---- Book-group mapping (66 -> 10) ----

    @Test
    fun allBooksMappedToTenGroups() {
        val groups = BibleBookGroups.ALL
        assertEquals(10, groups.size)
        val allIds = groups.flatMap { it.bookIds }
        assertEquals(66, allIds.size)
        assertEquals(66, allIds.toSet().size) // no duplicates
    }

    @Test
    fun groupCounts() {
        val counts = BibleBookGroups.ALL.associate { it.id to it.bookIds.size }
        assertEquals(5, counts["pentateuch"])
        assertEquals(12, counts["historical"])
        assertEquals(5, counts["poetry"])
        assertEquals(5, counts["major_prophets"])
        assertEquals(12, counts["minor_prophets"])
        assertEquals(4, counts["gospels"])
        assertEquals(1, counts["acts"])
        assertEquals(13, counts["pauline"])
        assertEquals(8, counts["general"])
        assertEquals(1, counts["revelation"])
    }

    @Test
    fun everyBookHasAGroup() {
        // All ids resolve; spot-check boundaries.
        assertEquals("pentateuch", BibleBookGroups.groupOf("gen")?.id)
        assertEquals("pentateuch", BibleBookGroups.groupOf("deu")?.id)
        assertEquals("historical", BibleBookGroups.groupOf("est")?.id)
        assertEquals("poetry", BibleBookGroups.groupOf("psa")?.id)
        assertEquals("major_prophets", BibleBookGroups.groupOf("dan")?.id)
        assertEquals("minor_prophets", BibleBookGroups.groupOf("mal")?.id)
        assertEquals("gospels", BibleBookGroups.groupOf("jhn")?.id)
        assertEquals("acts", BibleBookGroups.groupOf("act")?.id)
        assertEquals("pauline", BibleBookGroups.groupOf("phm")?.id)
        assertEquals("general", BibleBookGroups.groupOf("jud")?.id)
        assertEquals("revelation", BibleBookGroups.groupOf("rev")?.id)
    }

    @Test
    fun shortDisplayNameStripsPrefix() {
        assertEquals("التكوين", BibleBookGroups.shortDisplayName("سفر التكوين"))
        assertEquals("متى", BibleBookGroups.shortDisplayName("إنجيل متى"))
        assertEquals("رومية", BibleBookGroups.shortDisplayName("رسالة رومية"))
        assertEquals("أعمال الرسل", BibleBookGroups.shortDisplayName("سفر أعمال الرسل"))
        // Citations keep full names — this is display-only.
        assertEquals("المزامير", BibleBookGroups.shortDisplayName("سفر المزامير"))
    }

    // ---- Recent-colors list logic (item 9) ----

    /** Pure logic mirroring the UI's onAddRecentColor: dedupe + prepend + cap 12. */
    private fun addRecent(list: List<String>, hex: String): List<String> {
        if (list.any { it.equals(hex, ignoreCase = true) }) return list
        return (listOf(hex) + list).take(12)
    }

    @Test
    fun recentColorsSeedDefaults() {
        val seed = listOf("#FFFFFF", "#FBBF24", "#F59E0B", "#EF4444", "#10B981", "#3B82F6", "#8B5CF6", "#000000", "#94A3B8")
        assertEquals(9, seed.size)
    }

    @Test
    fun recentColorsDedupeCaseInsensitive() {
        val list = listOf("#FFFFFF", "#000000")
        assertEquals(list, addRecent(list, "#ffffff"))
        assertEquals(listOf("#FF0000", "#FFFFFF", "#000000"), addRecent(list, "#FF0000"))
    }

    @Test
    fun recentColorsCapTwelve() {
        var list = (1..12).map { "#%06X".format(it) }
        list = addRecent(list, "#FFFFFF")
        assertEquals(12, list.size)
        assertEquals("#FFFFFF", list.first())
    }

    // ---- New template defaults (items 7/8/14) ----

    @Test
    fun lineSpacingDefaults() {
        val tpl = LowerThirdTemplate(id = "t", name = "T")
        assertEquals(1.5f, tpl.lineSpacingArabic, 0.001f)
        assertEquals(1.5f, tpl.lineSpacingEnglish, 0.001f)
    }

    @Test
    fun autoFitFrozenRatioDefaultsNull() {
        val tpl = LowerThirdTemplate(id = "t", name = "T")
        assertNull(tpl.autoFitFrozenRatio)
    }
}
