package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sermon APIs: next/prev index logic (presenter + pedal). The live
 * contract is: step past either end does NOT wrap — it returns an
 * error and leaves the live verse unchanged. A verse not in the list
 * enters the list (next = first, prev = last).
 */
class SermonNavigatorTest {

    private fun verse(bookId: String, chapter: Int, verse: Int) = BibleVerse(
        id = "$bookId:$chapter:$verse",
        bookId = bookId,
        bookArabicName = bookId,
        bookEnglishName = bookId,
        chapter = chapter,
        verse = verse,
        arabicText = "text",
        englishText = "text"
    )

    private fun entries() = listOf(
        SermonEntry(verse("jhn", 3, 16), 1L, 0),
        SermonEntry(verse("jhn", 3, 17), 2L, 1),
        SermonEntry(verse("jhn", 3, 18), 3L, 0)
    )

    @Test
    fun nextFromMiddleGoesToNextVerse() {
        val outcome = SermonNavigator.step(entries(), verse("jhn", 3, 16), +1)
        assertEquals(1, outcome.index)
        assertEquals(17, outcome.entry?.verse?.verse)
        assertNull(outcome.error)
    }

    @Test
    fun prevFromMiddleGoesToPrevVerse() {
        val outcome = SermonNavigator.step(entries(), verse("jhn", 3, 17), -1)
        assertEquals(0, outcome.index)
        assertEquals(16, outcome.entry?.verse?.verse)
    }

    @Test
    fun nextAtEndDoesNotWrap() {
        val outcome = SermonNavigator.step(entries(), verse("jhn", 3, 18), +1)
        assertNull(outcome.entry)
        assertEquals("End of sermon list", outcome.error)
        assertEquals(2, outcome.index)
    }

    @Test
    fun prevAtStartDoesNotWrap() {
        val outcome = SermonNavigator.step(entries(), verse("jhn", 3, 16), -1)
        assertNull(outcome.entry)
        assertTrue(outcome.error!!.contains("first verse"))
    }

    @Test
    fun emptyListIsAnError() {
        val outcome = SermonNavigator.step(emptyList(), verse("jhn", 3, 16), +1)
        assertNull(outcome.entry)
        assertEquals("Sermon list is empty", outcome.error)
    }

    @Test
    fun verseNotInListEntersTheList() {
        val next = SermonNavigator.step(entries(), verse("exo", 12, 11), +1)
        assertEquals(0, next.index)
        assertEquals(16, next.entry?.verse?.verse)
        val prev = SermonNavigator.step(entries(), verse("exo", 12, 11), -1)
        assertEquals(2, prev.index)
        assertEquals(18, prev.entry?.verse?.verse)
    }

    @Test
    fun noLiveVerseStartsAtFirstForNext() {
        val outcome = SermonNavigator.step(entries(), null, +1)
        assertEquals(0, outcome.index)
    }
}
