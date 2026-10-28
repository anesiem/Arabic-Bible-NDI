package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Combined batch, 2026-10-28: new gradient motions (Flow, Shimmer,
 * Wave), Play Once variants, and pure = opacity 0.
 *
 * Periodicity is the whole point of the new effects: value(t) must
 * equal value(t + period) where period = 1/speed, so the loop can
 * never show a first-frame/last-frame jump like a recorded video.
 */
class GradientMotionPlayOnceTest {

    @Test
    fun newGradientIdsRoundTrip() {
        assertEquals(GradientAnimationType.FLOW, GradientAnimationType.fromId("flow"))
        assertEquals(GradientAnimationType.SHIMMER, GradientAnimationType.fromId("shimmer"))
        assertEquals(GradientAnimationType.WAVE, GradientAnimationType.fromId("wave"))
        // Old ids unchanged.
        assertEquals(GradientAnimationType.SLIDE, GradientAnimationType.fromId("slide"))
        assertEquals(GradientAnimationType.PULSE, GradientAnimationType.fromId("pulse"))
    }

    @Test
    fun flowOffsetIsPeriodic() {
        val speed = 1.5f
        val period = 1.0 / speed
        for (t in listOf(0.0, 0.37, 1.23, 5.0)) {
            val a = GradientMotion.flowOffset(GradientMotion.phase(t, speed))
            val b = GradientMotion.flowOffset(GradientMotion.phase(t + period, speed))
            assertTrue("flow at $t vs $t+period", abs(a - b) < 1e-4f)
        }
    }

    @Test
    fun shimmerMultiplierIsPeriodicAndSubtle() {
        val speed = 2.0f
        val period = 1.0 / speed
        for (t in listOf(0.0, 0.11, 2.5)) {
            val a = GradientMotion.shimmerMultiplier(GradientMotion.phase(t, speed))
            val b = GradientMotion.shimmerMultiplier(GradientMotion.phase(t + period, speed))
            assertTrue(abs(a - b) < 1e-4f)
            assertTrue("shimmer $a in 0.76..1.0", a in 0.76f..1.0f)
        }
    }

    @Test
    fun waveAngleIsPeriodicAndBounded() {
        val speed = 1.0f
        val period = 1.0 / speed
        for (t in listOf(0.0, 0.5, 3.3)) {
            val a = GradientMotion.waveAngle(90f, GradientMotion.phase(t, speed))
            val b = GradientMotion.waveAngle(90f, GradientMotion.phase(t + period, speed))
            assertTrue(abs(a - b) < 1e-3f)
            assertTrue("wave $a in 70..110", a in 70f..110f)
        }
    }

    @Test
    fun playOnceStateMachine() {
        // Per Verse and Per Go Live share the end state: layer off.
        var s = PlayOnceState.State.IDLE
        s = PlayOnceState.startVerse(s)
        assertTrue(PlayOnceState.isLayerVisible(s))
        s = PlayOnceState.onEnded(s)
        assertFalse(PlayOnceState.isLayerVisible(s))
        assertEquals(PlayOnceState.State.FINISHED, s)
        // Same session, Per Go Live must not restart from FINISHED.
        assertEquals(PlayOnceState.State.FINISHED, PlayOnceState.startGoLive(s))
        // OFF AIR clears, next live session plays again.
        s = PlayOnceState.clear(s)
        assertEquals(PlayOnceState.State.IDLE, s)
        s = PlayOnceState.startGoLive(s)
        assertTrue(PlayOnceState.isLayerVisible(s))
    }

    @Test
    fun playOnceModeIds() {
        assertEquals(VideoLoopMode.PLAY_ONCE_PER_VERSE, VideoLoopMode.fromId("once_verse"))
        assertEquals(VideoLoopMode.PLAY_ONCE_PER_GOLIVE, VideoLoopMode.fromId("once_golive"))
        // Legacy "once" (unpushed branch only) maps to Per Verse.
        assertEquals(VideoLoopMode.PLAY_ONCE_PER_VERSE, VideoLoopMode.fromId("once"))
        assertTrue(VideoLoopMode.PLAY_ONCE_PER_VERSE.isPlayOnce())
        assertTrue(VideoLoopMode.PLAY_ONCE_PER_GOLIVE.isPlayOnce())
        assertFalse(VideoLoopMode.FADE.isPlayOnce())
        assertFalse(VideoLoopMode.CUT.isPlayOnce())
    }

    @Test
    fun pureIsOpacityZero() {
        val pure = LowerThirdTemplate(id = "t", name = "T", bgOpacity = 0f, isPureTransparentBackground = false)
        assertTrue(pure.isPureDerived)
        val card = LowerThirdTemplate(id = "t", name = "T", bgOpacity = 1f, isPureTransparentBackground = false)
        assertFalse(card.isPureDerived)
        // Legacy flag still counts as pure until migration clears it.
        val legacy = LowerThirdTemplate(id = "t", name = "T", bgOpacity = 0.85f, isPureTransparentBackground = true)
        assertTrue(legacy.isPureDerived)
    }

    // Split transitions + continuous box effects (combined batch).
    @Test
    fun boxTransitionDefaultsMirrorVerse() {
        val t = LowerThirdTemplate(id = "t", name = "T")
        // Fresh template: box == verse, so it moves together until the
        // user isolates one (same migration as fromJson for old saves).
        assertEquals(t.transition, t.boxTransition)
        assertEquals(t.transitionDurationMs, t.boxTransitionDurationMs)
        assertFalse(t.boxZoomEnabled)
        assertFalse(t.boxFloatEnabled)
        assertFalse(t.boxSwayEnabled)
        assertFalse(t.boxGlowPulseEnabled)
        assertEquals(1.0f, t.boxMotionSpeed)
        assertEquals(TransitionType.NONE, TransitionType.valueOf("NONE"))
    }

    @Test
    fun boxZoomRangeAndPeriodicity() {
        // 1.0 at phase 0 (neutral), 1.05 at PI, back to 1.0 at 2*PI.
        assertEquals(1.0f, BoxMotion.zoomScale(0f, 1f), 0.0001f)
        assertEquals(1.05f, BoxMotion.zoomScale(Math.PI.toFloat(), 1f), 0.0001f)
        assertEquals(1.0f, BoxMotion.zoomScale((2 * Math.PI).toFloat(), 1f), 0.0001f)
        // First frame == last frame at the period boundary.
        val ph = 0.7f
        assertEquals(BoxMotion.zoomScale(ph, 1f), BoxMotion.zoomScale(ph + (2 * Math.PI).toFloat(), 1f), 0.0001f)
        // Ramp 0 = neutral regardless of phase (during transition).
        assertEquals(1.0f, BoxMotion.zoomScale(Math.PI.toFloat(), 0f), 0.0001f)
    }

    @Test
    fun boxFloatSwayGlowRanges() {
        assertEquals(0f, BoxMotion.floatDp(0f, 1f), 0.0001f)
        assertEquals(8f, BoxMotion.floatDp((Math.PI / 2).toFloat(), 1f), 0.0001f)
        assertEquals(-8f, BoxMotion.floatDp((3 * Math.PI / 2).toFloat(), 1f), 0.0001f)
        assertEquals(0f, BoxMotion.floatDp((2 * Math.PI).toFloat(), 1f), 0.0001f)
        assertEquals(8f, BoxMotion.swayDp((Math.PI / 2).toFloat(), 1f), 0.0001f)
        assertEquals(1.0f, BoxMotion.glowFactor(0f, 1f), 0.0001f)
        assertEquals(1.3f, BoxMotion.glowFactor((Math.PI / 2).toFloat(), 1f), 0.0001f)
        assertEquals(0.7f, BoxMotion.glowFactor((3 * Math.PI / 2).toFloat(), 1f), 0.0001f)
        assertEquals(0f, BoxMotion.floatDp((Math.PI / 2).toFloat(), 0f), 0.0001f)
    }

    @Test
    fun boxRampStaysNeutralDuringTransitionThenGrows() {
        assertEquals(0f, BoxMotion.ramp(0L, 1000L), 0.0001f)
        assertEquals(0f, BoxMotion.ramp(1000L, 1000L), 0.0001f)
        val mid = BoxMotion.ramp(1300L, 1000L)
        assertTrue(mid > 0f && mid < 1f)
        assertEquals(1f, BoxMotion.ramp(1600L, 1000L), 0.0001f)
        assertEquals(1f, BoxMotion.ramp(5000L, 1000L), 0.0001f)
    }

    @Test
    fun boxPhaseIsPeriodicAtOneOverSpeed() {
        // phase(speed, t) and phase(speed, t + 1/speed) differ by 2*PI,
        // so every sin/cos effect repeats exactly there.
        val a = BoxMotion.phase(1.5f, 0.4f)
        val b = BoxMotion.phase(1.5f, 0.4f + 1f / 1.5f)
        assertEquals(2 * Math.PI.toFloat(), b - a, 0.0001f)
    }
}
