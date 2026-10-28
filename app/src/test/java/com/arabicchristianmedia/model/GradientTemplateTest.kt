package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-28 design batch: design-gradient card and auto-fit
 * citation floor / ratio lock defaults. Off by default so every
 * existing style renders exactly as before.
 */
class GradientTemplateTest {

    @Test
    fun gradientDefaultsToOffWithTwoColorsAndAngle() {
        val tpl = LowerThirdTemplate(id = "test", name = "Test")
        assertFalse(tpl.gradientEnabled)
        assertEquals("#0A1128", tpl.gradientColorHex2)
        assertEquals(90, tpl.gradientAngleDeg)
        assertEquals(GradientAnimationType.NONE, tpl.gradientAnimation)
        assertEquals(1.0f, tpl.gradientSpeed, 0.001f)
    }

    @Test
    fun gradientAnimationFromIdFallsBackToNone() {
        assertEquals(GradientAnimationType.SLIDE, GradientAnimationType.fromId("slide"))
        assertEquals(GradientAnimationType.ROTATE, GradientAnimationType.fromId("rotate"))
        assertEquals(GradientAnimationType.PULSE, GradientAnimationType.fromId("pulse"))
        assertEquals(GradientAnimationType.NONE, GradientAnimationType.fromId("unknown"))
    }

    @Test
    fun autoFitCitationFloorAndRatioLockDefaults() {
        val tpl = LowerThirdTemplate(id = "test", name = "Test")
        assertEquals(10, tpl.autoFitMinCitationSp)
        assertTrue(tpl.autoFitRatioLock)
    }
}
