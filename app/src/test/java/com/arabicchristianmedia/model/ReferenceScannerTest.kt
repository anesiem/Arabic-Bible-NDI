package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/**
 * v1.9.1 regression tests — the reported Rev 1 share bug (a verse
 * reference degrading into a whole chapter), the abbreviation-prefix
 * trap, and verse-list support.
 */
class ReferenceScannerFixTest {

    private val books = listOf(
        BibleBook("psa", "سفر المزامير", "Psalms", "مز", "Psa", Testament.OLD_TESTAMENT, 150),
        BibleBook("ezr", "سفر عزرا", "Ezra", "عز", "Ezr", Testament.OLD_TESTAMENT, 10),
        BibleBook("heb", "رسالة العبرانيين", "Hebrews", "عب", "Heb", Testament.NEW_TESTAMENT, 13),
        BibleBook("joh", "إنجيل يوحنا", "John", "يو", "Jn", Testament.NEW_TESTAMENT, 21),
        BibleBook("rev", "سفر الرؤيا", "Revelation", "رؤ", "Rev", Testament.NEW_TESTAMENT, 22)
    )

    @Test
    fun reportedShareAddsOnlyListedVerses() {
        val text = "رؤيا ١: ١٢، ١٣ ١٢ فَالْتَفَتُّ لأَنْظُرَ الصَّوْتَ الَّذِي تَكَلَّمَ مَعِي. " +
            "وَلَمَّا الْتَفَتُّ رَأَيْتُ سَبْعَ مَنَايِرَ مِنْ ذَهَبٍ، ١٣ وَفِي وَسْطِ السَّبْعِ الْمَنَايِرِ " +
            "شِبْهُ ابْنِ إِنْسَانٍ، رؤيا ١: ٢٠ سِرَّ السَّبْعَةِ الْكَوَاكِبِ"
        val refs = ReferenceScanner.scan(text, books)
        assertEquals("the share holds exactly two references", 2, refs.size)
        assertEquals("rev", refs[0].book.id)
        assertEquals(1, refs[0].chapter)
        assertEquals(listOf(12, 13), refs[0].verseList)
        assertFalse("a verse list must never become a whole chapter", refs[0].wholeChapter)
        assertEquals("rev", refs[1].book.id)
        assertEquals(20, refs[1].verse)
        assertNull(refs[1].verseList)
        assertFalse(refs[1].wholeChapter)
    }

    @Test
    fun bareChapterStillMeansWholeChapter() {
        val refs = ReferenceScanner.scan("سفر المزامير ٢٣", books)
        assertEquals(1, refs.size)
        assertTrue(refs[0].wholeChapter)
        assertEquals(23, refs[0].chapter)
    }

    @Test
    fun bareShortFormChapterStillMeansWholeChapter() {
        val refs = ReferenceScanner.scan("رؤيا ١", books)
        assertEquals(1, refs.size)
        assertTrue(refs[0].wholeChapter)
        assertEquals(1, refs[0].chapter)
    }

    @Test
    fun invalidVerseReferenceNeverDegradesToWholeChapter() {
        // Chapter 23 does not exist in Revelation (22 chapters): the
        // reference is invalid and must yield nothing.
        val refs = ReferenceScanner.scan("رؤيا ٢٣: ١", books)
        assertTrue("an invalid c:v reference must produce no reference", refs.isEmpty())
    }

    @Test
    fun rangeStillWorks() {
        val refs = ReferenceScanner.scan("العبرانيين ٩: ٧-٩", books)
        assertEquals(1, refs.size)
        assertEquals(7, refs[0].verse)
        assertEquals(9, refs[0].endVerse)
        assertNull(refs[0].verseList)
    }

    @Test
    fun englishVerseListParses() {
        val refs = ReferenceScanner.scan("John 3:16, 18 is the text", books)
        assertEquals(1, refs.size)
        assertEquals(listOf(16, 18), refs[0].verseList)
        assertFalse(refs[0].wholeChapter)
    }

    @Test
    fun singleVerseUnchanged() {
        val refs = ReferenceScanner.scan("يوحنا ٣: ١٦", books)
        assertEquals(1, refs.size)
        assertEquals(16, refs[0].verse)
        assertNull(refs[0].endVerse)
        assertNull(refs[0].verseList)
        assertFalse(refs[0].wholeChapter)
    }
}

/**
 * Minor fix (2026-10-28): comma-separated shares from other apps must not
 * lose digit books. "Jos5:11,2Ki23:21" used to parse as Joshua 5:[11,2]
 * and drop 2 Kings 23:21, because ",2" looked like a verse list.
 */
class ReferenceScannerCommaTest {

    private val books = listOf(
        BibleBook("jos", "سفر يشوع", "Joshua", "يش", "Jos", Testament.OLD_TESTAMENT, 24),
        BibleBook("2ki", "سفر الملوك الثاني", "2 Kings", "٢مل", "2Ki", Testament.OLD_TESTAMENT, 25),
        BibleBook("2ch", "سفر أخبار الأيام الثاني", "2 Chronicles", "٢أخ", "2Ch", Testament.OLD_TESTAMENT, 36),
        BibleBook("1co", "رسالة كورنثوس الأولى", "1 Corinthians", "١كو", "1Co", Testament.NEW_TESTAMENT, 16),
        BibleBook("jhn", "إنجيل يوحنا", "John", "يو", "Jhn", Testament.NEW_TESTAMENT, 21)
    )

    @Test
    fun commaNoSpaceDigitBookIsNotVerseList() {
        val refs = ReferenceScanner.scan("Jos5:11,2Ki23:21,2Ki23:22", books)
        assertEquals(3, refs.size)
        assertEquals("jos", refs[0].book.id)
        assertEquals(11, refs[0].verse)
        assertNull(refs[0].verseList)
        assertEquals("2ki", refs[1].book.id)
        assertEquals(21, refs[1].verse)
        assertEquals("2ki", refs[2].book.id)
        assertEquals(22, refs[2].verse)
    }

    @Test
    fun commaSpaceDigitBookIsNotVerseList() {
        val refs = ReferenceScanner.scan("John 3:16, 2 Kings 23:21", books)
        assertEquals(2, refs.size)
        assertEquals("jhn", refs[0].book.id)
        assertEquals(16, refs[0].verse)
        assertNull(refs[0].verseList)
        assertEquals("2ki", refs[1].book.id)
        assertEquals(21, refs[1].verse)
    }

    @Test
    fun commaPassoverFragmentKeepsAllVerses() {
        val refs = ReferenceScanner.scan(
            "Jos5:10,Jos5:11,2Ki23:21,2Ki23:22,2Ki23:23,2Ch30:1,2Ch30:2,2Ch30:5", books
        )
        assertEquals(8, refs.size)
        assertEquals(
            listOf(
                "jos" to 10, "jos" to 11, "2ki" to 21, "2ki" to 22,
                "2ki" to 23, "2ch" to 1, "2ch" to 2, "2ch" to 5
            ),
            refs.map { it.book.id to it.verse }
        )
    }

    @Test
    fun legitVerseListStillWorks() {
        val refs = ReferenceScanner.scan("John 3:16, 18 is the text", books)
        assertEquals(1, refs.size)
        assertEquals(listOf(16, 18), refs[0].verseList)
    }
}
