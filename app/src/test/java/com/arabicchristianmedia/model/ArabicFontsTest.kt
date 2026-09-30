package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bundled Arabic Google Fonts registry: structural integrity plus a check that
 * every referenced TTF actually exists under src/main/assets/fonts.
 * (Gradle runs module unit tests with the working directory set to app/.)
 */
class ArabicFontsTest {

    @Test
    fun `registry holds the full arabic catalog`() {
        assertTrue("expected dozens of bundled fonts", ArabicFonts.all.size >= 50)
    }

    @Test
    fun `no duplicate family names`() {
        val families = ArabicFonts.all.map { it.family }
        assertEquals(families.size, families.toSet().size)
    }

    @Test
    fun `display names start with System then every family`() {
        assertEquals(ArabicFonts.SYSTEM, ArabicFonts.displayNames.first())
        assertEquals(ArabicFonts.all.size + 1, ArabicFonts.displayNames.size)
        for (f in ArabicFonts.all) {
            assertTrue("missing display name: ${f.family}", f.family in ArabicFonts.displayNames)
        }
    }

    @Test
    fun `find resolves known families and null for System or unknown`() {
        val amiri = ArabicFonts.find("Amiri")
        assertNotNull(amiri)
        assertEquals("fonts/amiri-400.ttf", amiri!!.asset400)
        assertEquals("fonts/amiri-700.ttf", amiri.asset700)
        assertNull(ArabicFonts.find(ArabicFonts.SYSTEM))
        assertNull(ArabicFonts.find("No Such Font"))
    }

    @Test
    fun `asset paths are well formed`() {
        for (f in ArabicFonts.all) {
            assertTrue("${f.family}: 400 path malformed", f.asset400.startsWith("fonts/") && f.asset400.endsWith(".ttf"))
            assertNotNull("${f.family}: missing 400 file", f.asset400)
            val w700 = f.asset700
            if (w700 != null) {
                assertTrue("${f.family}: 700 path malformed", w700.startsWith("fonts/") && w700.endsWith(".ttf"))
            }
        }
    }

    @Test
    fun `every referenced ttf exists in assets`() {
        val fontsDir = File("src/main/assets/fonts")
        assertTrue("assets/fonts dir not found at ${fontsDir.absolutePath}", fontsDir.isDirectory)
        val missing = ArabicFonts.all.flatMap {
            listOfNotNull(it.asset400.substringAfterLast('/'), it.asset700?.substringAfterLast('/'))
        }.filter { !File(fontsDir, it).isFile }
        assertTrue("missing font files: $missing", missing.isEmpty())
    }
}
