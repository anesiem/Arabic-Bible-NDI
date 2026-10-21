package com.arabicchristianmedia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.9.1: sermon group shade assignment. Rules: shades alternate per
 * reference-group in display order; a batch prepended (or merged) above an
 * existing list must end on the opposite shade of the current top group;
 * existing groups are never recolored.
 */
class SermonGroupingTest {

    @Test
    fun emptyBatchHasNoShades() {
        assertEquals("empty batch must produce no shades", emptyList<Int>(), SermonGrouping.shadesForNewBatch(null, 0))
        assertEquals("empty batch must produce no shades", emptyList<Int>(), SermonGrouping.shadesForNewBatch(1, 0))
    }

    @Test
    fun freshListStartsAtZeroAndAlternates() {
        assertEquals("single group on empty list", listOf(0), SermonGrouping.shadesForNewBatch(null, 1))
        assertEquals("three groups on empty list alternate from 0", listOf(0, 1, 0), SermonGrouping.shadesForNewBatch(null, 3))
    }

    @Test
    fun prependedBatchEndsOppositeToCurrentTop() {
        // Property: for any top shade and batch size, the LAST batch shade
        // differs from the top shade, and the batch alternates internally.
        for (top in 0..1) {
            for (n in 1..6) {
                val shades = SermonGrouping.shadesForNewBatch(top, n)
                assertTrue("batch of $n over top $top must end on the opposite shade", shades.last() != top)
                for (i in 1 until n) {
                    assertTrue("batch of $n must alternate at index $i", shades[i] != shades[i - 1])
                }
            }
        }
    }

    @Test
    fun mergeKeepsStoredPatternWhenListEmpty() {
        assertEquals(
            "merge into an empty list keeps the stored shades untouched",
            listOf(1, 0, 1),
            SermonGrouping.reshadeForMerge(null, listOf(1, 0, 1))
        )
    }

    @Test
    fun mergeFlipsOnlyWhenSeamWouldCollide() {
        // Stored [0,1] over top shade 1: seam is 1 vs 1 — must flip to [1,0].
        assertEquals(
            "colliding seam flips the merged shades",
            listOf(1, 0),
            SermonGrouping.reshadeForMerge(1, listOf(0, 1))
        )
        // Stored [0,1] over top shade 0: seam is 1 vs 0 — already fine.
        assertEquals(
            "non-colliding seam keeps the stored shades",
            listOf(0, 1),
            SermonGrouping.reshadeForMerge(0, listOf(0, 1))
        )
    }

    @Test
    fun mergePreservesRelativeAlternation() {
        val stored = listOf(1, 0, 1, 0)
        val result = SermonGrouping.reshadeForMerge(0, stored)
        for (i in 1 until stored.size) {
            assertTrue("merge must preserve alternation at index $i", result[i] != result[i - 1])
        }
        assertTrue("merged batch must end opposite the current top", result.last() != 0)
    }
}
