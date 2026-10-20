package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.0: ReferenceScanner tests — references inside shared sermon notes
 * (Google Keep), in order. The key cases mirror Ashraf's real note
 * format: short Arabic book names, Eastern digits, verse ranges, and
 * verse text carrying its own embedded verse numbers.
 */
class ReferenceScannerTest {

    private val books = listOf(
        BibleBook(
            id = "jhn", arabicName = "إنجيل يوحنا", englishName = "John",
            arabicAbbreviation = "يو", englishAbbreviation = "Jn",
            testament = Testament.NEW_TESTAMENT, totalChapters = 21
        ),
        BibleBook(
            id = "heb", arabicName = "رسالة العبرانيين", englishName = "Hebrews",
            arabicAbbreviation = "عب", englishAbbreviation = "Heb",
            testament = Testament.NEW_TESTAMENT, totalChapters = 13
        ),
        BibleBook(
            id = "psa", arabicName = "سفر المزامير", englishName = "Psalms",
            arabicAbbreviation = "مز", englishAbbreviation = "Ps",
            testament = Testament.OLD_TESTAMENT, totalChapters = 150
        )
    )

    @Test
    fun scanHisRangeNoteFindsOneRangeAndIgnoresEmbeddedNumbers() {
        // Structurally identical to Ashraf's Keep note: the shared text
        // itself contains verse numbers ٧ ٨ ٩ with NO book name — they
        // must not produce extra hits.
        val note = "العبرانيين ٩: ٧-٩ ٧ وَأَمَّا إِلَى الثَّانِي فَرَئِيسُ الْكَهَنَةِ فَقَطْ " +
            "٨ مُعْلِنًا الرُّوحُ الْقُدُسُ بِهذَا ٩ الَّذِي هُوَ رَمْزٌ لِلْوَقْتِ الْحَاضِرِ"
        val hits = ReferenceScanner.scan(note, books)
        assertEquals(1, hits.size)
        assertEquals("heb", hits[0].book.id)
        assertEquals(9, hits[0].chapter)
        assertEquals(7, hits[0].verse)
        assertEquals(9, hits[0].endVerse)
    }

    @Test
    fun scanSingleVerseWithText() {
        val note = "العبرانيين ٩: ١١ وَأَمَّا الْمَسِيحُ، وَهُوَ قَدْ جَاءَ رَئِيسَ كَهَنَةٍ"
        val hits = ReferenceScanner.scan(note, books)
        assertEquals(1, hits.size)
        assertEquals("heb", hits[0].book.id)
        assertEquals(9, hits[0].chapter)
        assertEquals(11, hits[0].verse)
        assertEquals(null, hits[0].endVerse)
    }

    @Test
    fun scanTwoReferencesInOrder() {
        val note = "• يوحنا ٣:١٦ لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ\n" +
            "• العبرانيين ٩: ٧-٩ نص الآية هنا"
        val hits = ReferenceScanner.scan(note, books)
        assertEquals(2, hits.size)
        assertEquals("jhn", hits[0].book.id)
        assertEquals(3, hits[0].chapter)
        assertEquals(16, hits[0].verse)
        assertEquals("heb", hits[1].book.id)
        assertEquals(9, hits[1].chapter)
        assertEquals(7, hits[1].verse)
        assertEquals(9, hits[1].endVerse)
    }

    @Test
    fun scanWholeChapterBullet() {
        val hits = ReferenceScanner.scan("المزامير ٢٣", books)
        assertEquals(1, hits.size)
        assertEquals("psa", hits[0].book.id)
        assertEquals(23, hits[0].chapter)
        assertTrue(hits[0].wholeChapter)
    }

    @Test
    fun scanNoReferenceReturnsEmpty() {
        assertTrue(ReferenceScanner.scan("ملاحظات الخدمة لهذا الأحد بدون مراجع", books).isEmpty())
        assertTrue(ReferenceScanner.scan("", books).isEmpty())
    }

    @Test
    fun scanAbbreviationInsideWordDoesNotMatch() {
        // "عب" (Hebrews abbreviation) inside "الشعب" is not a reference,
        // even with a chapter:verse pattern right after the word.
        assertTrue(ReferenceScanner.scan("الشعب ٩:٥ كان حاضراً", books).isEmpty())
    }

    @Test
    fun scanFullPrefixedName() {
        val hits = ReferenceScanner.scan("رسالة العبرانيين ٩:١١", books)
        assertEquals(1, hits.size)
        assertEquals("heb", hits[0].book.id)
        assertEquals(11, hits[0].verse)
    }
}
