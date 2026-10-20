package com.arabicchristianmedia.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v2.0: tashkeel stripping for search normalization.
 */
class BibleRepositoryTashkeelTest {

    @Test
    fun stripTashkeel_removesDiacritics() {
        assertEquals(
            "البدء",
            BibleRepository.stripTashkeel("الْبَدْءِ")
        )
    }

    @Test
    fun stripTashkeel_removesTatweel() {
        assertEquals(
            "الله",
            BibleRepository.stripTashkeel("اللـه")
        )
    }

    @Test
    fun stripTashkeel_leavesPlainTextUnchanged() {
        assertEquals(
            "في البدء خلق الله",
            BibleRepository.stripTashkeel("في البدء خلق الله")
        )
    }

    @Test
    fun stripTashkeel_handlesEmpty() {
        assertEquals("", BibleRepository.stripTashkeel(""))
    }

    @Test
    fun stripTashkeel_fullVerse() {
        assertEquals(
            "في البدء خلق الله السماوات والارض",
            BibleRepository.stripTashkeel("فِي الْبَدْءِ خَلَقَ اللهُ السَّمَاوَاتِ وَالارْضَ")
        )
    }
}
