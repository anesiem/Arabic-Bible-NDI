package com.example

import com.example.data.ArabicTextFormatter
import com.example.data.BibleRepository
import com.example.data.TemplateRepository
import com.example.model.BibleVerse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testEasternArabicDigitsConversion() {
        val result = ArabicTextFormatter.toEasternArabicDigits("John 3:16")
        assertEquals("John ٣:١٦", result)

        val pureNumbers = ArabicTextFormatter.toEasternArabicDigits(2024)
        assertEquals("٢٠٢٤", pureNumbers)
    }

    @Test
    fun testArabicPunctuationFormatting() {
        val input = "الْحَقَّ،الْحَقَّ أَقُولُ لَكُمْ؛إِنَّهُ حَقّ."
        val formatted = ArabicTextFormatter.formatArabicPunctuation(input)
        assertTrue(formatted.contains("،"))
        assertTrue(formatted.contains("؛"))

        // Western comma should be replaced by Arabic comma
        val westernInput = "نعم,هذا صحيح"
        val normalized = ArabicTextFormatter.formatArabicPunctuation(westernInput)
        assertEquals("نعم، هذا صحيح", normalized)
    }

    @Test
    fun testArabicCitation() {
        val verse = BibleRepository.getVerses("jhn", 3).first { it.verse == 16 }
        val citation = verse.getFormattedArabicCitation(useEasternNumbers = true)
        assertTrue(citation.contains("إنجيل يوحنا"))
        assertTrue(citation.contains("٣"))
        assertTrue(citation.contains("١٦"))
    }

    @Test
    fun testPreloadedVersesCoverage() {
        val johnVerses = BibleRepository.getVerses("jhn", 3)
        assertTrue("John 3 should have verses", johnVerses.isNotEmpty())
        val jhn316 = johnVerses.firstOrNull { it.verse == 16 }
        assertNotNull("John 3:16 should be present", jhn316)
        assertTrue(jhn316!!.arabicText.contains("أَحَبَّ اللهُ الْعَالَمَ"))

        val psalm23 = BibleRepository.getVerses("psa", 23)
        assertTrue("Psalm 23 should have verses", psalm23.isNotEmpty())
        assertTrue(psalm23.first().arabicText.contains("رَاعِيَّ"))
    }

    @Test
    fun testDefaultTemplatesIncludeTransparentAlpha() {
        val templates = TemplateRepository.DEFAULT_TEMPLATES
        val transparentTpl = templates.firstOrNull { it.bgOpacity == 0.0f }
        assertNotNull("A pure 100% transparent template must exist for OBS/vMix", transparentTpl)
    }
}
