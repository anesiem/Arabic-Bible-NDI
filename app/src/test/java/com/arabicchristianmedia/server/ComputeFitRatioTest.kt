package com.arabicchristianmedia.server

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.9 auto-fit math: [computeFitRatio] shrinks a too-tall text block to fit
 * its height budget, never below the user's minimum-size floor.
 */
class ComputeFitRatioTest {

    @Test
    fun `no shrink when content fits`() {
        assertEquals(1f, computeFitRatio(100f, 200f, 0.4f))
        assertEquals(1f, computeFitRatio(200f, 200f, 0.4f))
    }

    @Test
    fun `shrinks proportionally when overflowing`() {
        // Half the budget -> half the text size.
        assertEquals(0.5f, computeFitRatio(400f, 200f, 0.2f), 0.001f)
    }

    @Test
    fun `never shrinks below the minimum ratio`() {
        // Needs 0.1x but the floor is 0.4x.
        assertEquals(0.4f, computeFitRatio(2000f, 200f, 0.4f), 0.001f)
    }

    @Test
    fun `non-positive budget means no shrink`() {
        assertEquals(1f, computeFitRatio(500f, 0f, 0.4f))
        assertEquals(1f, computeFitRatio(500f, -10f, 0.4f))
    }

    @Test
    fun `min ratio above 1 clamps to 1`() {
        // Selected size already below the minimum: never shrink.
        assertEquals(1f, computeFitRatio(500f, 100f, 1f))
    }
}
