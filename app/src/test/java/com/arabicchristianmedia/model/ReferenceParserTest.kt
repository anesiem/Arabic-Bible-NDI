package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.8: ReferenceParser tests — English/Arabic/abbreviation parsing
 * for the /api/trigger remote API.
 */
class ReferenceParserTest {

    private val books = listOf(
        BibleBook(
            id = "gen", arabicName = "التكوين", englishName = "Genesis",
            arabicAbbreviation = "تك", englishAbbreviation = "Gen",
            testament = Testament.OLD_TESTAMENT, totalChapters = 50
        ),
        BibleBook(
            id = "jhn", arabicName = "يوحنا", englishName = "John",
            arabicAbbreviation = "يو", englishAbbreviation = "Jn",
            testament = Testament.NEW_TESTAMENT, totalChapters = 21
        ),
        BibleBook(
            id = "1jn", arabicName = "يوحنا الأولى", englishName = "1 John",
            arabicAbbreviation = "1يو", englishAbbreviation = "1Jn",
            testament = Testament.NEW_TESTAMENT, totalChapters = 5
        ),
        BibleBook(
            id = "psa", arabicName = "المزامير", englishName = "Psalms",
            arabicAbbreviation = "مز", englishAbbreviation = "Ps",
            testament = Testament.OLD_TESTAMENT, totalChapters = 150
        )
    )

    @Test
    fun parseEnglishName() {
        val result = ReferenceParser.parse("John 3:16", books)
        assertNotNull(result)
        assertEquals("jhn", result!!.book.id)
        assertEquals(3, result.chapter)
        assertEquals(16, result.verse)
    }

    @Test
    fun parseEnglishNameCaseInsensitive() {
        val result = ReferenceParser.parse("john 3:16", books)
        assertNotNull(result)
        assertEquals("jhn", result!!.book.id)
    }

    @Test
    fun parseEnglishAbbreviation() {
        val result = ReferenceParser.parse("Jn 3:16", books)
        assertNotNull(result)
        assertEquals("jhn", result!!.book.id)
        assertEquals(3, result.chapter)
        assertEquals(16, result.verse)
    }

    @Test
    fun parseNumberedBook() {
        val result = ReferenceParser.parse("1 John 1:9", books)
        assertNotNull(result)
        assertEquals("1jn", result!!.book.id)
        assertEquals(1, result.chapter)
        assertEquals(9, result.verse)
    }

    @Test
    fun parseNumberedAbbreviation() {
        val result = ReferenceParser.parse("1Jn 1:9", books)
        assertNotNull(result)
        assertEquals("1jn", result!!.book.id)
    }

    @Test
    fun parseArabicName() {
        val result = ReferenceParser.parse("يوحنا 3:16", books)
        assertNotNull(result)
        assertEquals("jhn", result!!.book.id)
        assertEquals(3, result.chapter)
        assertEquals(16, result.verse)
    }

    @Test
    fun parseEasternArabicNumerals() {
        val result = ReferenceParser.parse("يوحنا ٣:١٦", books)
        assertNotNull(result)
        assertEquals("jhn", result!!.book.id)
        assertEquals(3, result.chapter)
        assertEquals(16, result.verse)
    }

    @Test
    fun parseArabicAbbreviation() {
        val result = ReferenceParser.parse("يو 3:16", books)
        assertNotNull(result)
        assertEquals("jhn", result!!.book.id)
    }

    @Test
    fun parseInvalidReference() {
        assertNull(ReferenceParser.parse("", books))
        assertNull(ReferenceParser.parse("John", books))
        assertNull(ReferenceParser.parse("John 3", books))
        assertNull(ReferenceParser.parse("NotABook 3:16", books))
    }

    @Test
    fun parseChapterOutOfRange() {
        // John has 21 chapters; chapter 99 is invalid.
        assertNull(ReferenceParser.parse("John 99:1", books))
    }

    @Test
    fun normalizeDigits() {
        assertEquals("123", ReferenceParser.normalizeDigits("١٢٣"))
        assertEquals("3:16", ReferenceParser.normalizeDigits("٣:١٦"))
        assertEquals("abc", ReferenceParser.normalizeDigits("abc"))
    }

    @Test
    fun findBookByEnglishName() {
        val book = ReferenceParser.findBook("Genesis", books)
        assertNotNull(book)
        assertEquals("gen", book!!.id)
    }

    @Test
    fun findBookByAbbreviation() {
        val book = ReferenceParser.findBook("Ps", books)
        assertNotNull(book)
        assertEquals("psa", book!!.id)
    }
}
