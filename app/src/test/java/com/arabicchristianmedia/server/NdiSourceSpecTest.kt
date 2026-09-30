package com.arabicchristianmedia.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.6: exactly two genuine standard NDI senders (Lower Third + Full Show).
 * No HX tiers, no discovery beacon.
 */
class NdiSourceSpecTest {

    @Test
    fun `exactly two genuine NDI feeds`() {
        assertEquals(
            listOf(NdiNativeSender.FEED_LOWER, NdiNativeSender.FEED_FULL),
            NdiNativeSender.ALL_FEEDS
        )
    }

    @Test
    fun `resolution options match the agreed list up to 1080p`() {
        assertEquals(
            listOf(
                1920 to 1080,
                1600 to 900,
                1360 to 768,
                1280 to 720,
                960 to 540,
                854 to 480,
                640 to 360
            ),
            NdiNativeSender.RESOLUTION_OPTIONS
        )
    }

    @Test
    fun `fps options are the agreed integer rates`() {
        assertEquals(
            listOf(60, 50, 30, 25, 24, 15, 10, 5),
            NdiNativeSender.FPS_OPTIONS
        )
    }

    @Test
    fun `default spec is 1080p30 with motion off`() {
        val lower = NdiNativeSender.defaultSpec(NdiNativeSender.FEED_LOWER)
        assertEquals(NdiNativeSender.FEED_LOWER, lower.feedKey)
        assertEquals(1920, lower.width)
        assertEquals(1080, lower.height)
        assertEquals(30, lower.fps)
        assertFalse(lower.motionEnabled)

        val full = NdiNativeSender.defaultSpec(NdiNativeSender.FEED_FULL)
        assertEquals(1920, full.width)
        assertEquals(1080, full.height)
        assertEquals(30, full.fps)
        assertFalse(full.motionEnabled)
    }

    @Test
    fun `display name ends with tablet model and feed key`() {
        // "<TabletModel> - <FeedName>", never localhost/IP.
        assertTrue(
            NdiNativeSender.displayName(NdiNativeSender.FEED_LOWER).endsWith(" - Bible-NDI-Lower")
        )
        assertTrue(
            NdiNativeSender.displayName(NdiNativeSender.FEED_FULL).endsWith(" - Bible-NDI-Full")
        )
    }
}
