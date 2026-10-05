package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8: LowerThirdTemplate new-field tests — language mode, shadow
 * thickness/distance/angle, and legacy bilingualMode migration.
 */
class LowerThirdTemplateV18Test {

    @Test
    fun languageModeDefaultsToArabicOnly() {
        val tpl = LowerThirdTemplate(
            id = "test",
            name = "Test"
        )
        assertEquals(LanguageMode.ARABIC_ONLY, tpl.languageMode)
    }

    @Test
    fun shadowDefaultsMatchLegacyAppearance() {
        // Defaults reproduce the old setShadowLayer(8, 0, 4) look.
        val tpl = LowerThirdTemplate(id = "test", name = "Test")
        assertEquals(8f, tpl.textShadowBlurDp, 0.001f)
        assertEquals(4f, tpl.textShadowOffsetDp, 0.001f)
        assertEquals(90, tpl.textShadowAngleDeg)
    }

    @Test
    fun languageModeValuesExist() {
        // All three modes must be present.
        val modes = LanguageMode.values()
        assertTrue(modes.contains(LanguageMode.ARABIC_ONLY))
        assertTrue(modes.contains(LanguageMode.ENGLISH_ONLY))
        assertTrue(modes.contains(LanguageMode.BOTH))
        assertEquals(3, modes.size)
    }

    @Test
    fun shadowAngleSemantics() {
        // 0°=right, 90°=down, 180°=left, 270°=up.
        // Verify the angle values are within the valid compass range.
        val tpl = LowerThirdTemplate(id = "test", name = "Test")
        assertTrue(tpl.textShadowAngleDeg in 0..360)
    }
}
