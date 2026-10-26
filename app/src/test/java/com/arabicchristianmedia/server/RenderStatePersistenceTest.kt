package com.arabicchristianmedia.server

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.TransitionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.9: OS-kill auto-resume — the verse/live state persisted by
 * [NdiBroadcastServer.updateVerse]/[NdiBroadcastServer.setLiveState] must be
 * restorable by a fresh server instance, with safe fallbacks when the verse or
 * the styles no longer resolve. Also covers the [NdiService] sender holder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RenderStatePersistenceTest {

    private lateinit var context: Context
    private lateinit var repo: TemplateRepository

    private val verse = BibleVerse(
        id = "jhn-3-16",
        bookId = "jhn",
        bookArabicName = "يوحنا",
        bookEnglishName = "John",
        chapter = 3,
        verse = 16,
        arabicText = "لأنه هكذا أحب الله العالم",
        englishText = "For God so loved the world"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Isolate verse-state prefs per test run.
        context.getSharedPreferences("bible_ndi_render_state", Context.MODE_PRIVATE)
            .edit().clear().apply()
        repo = TemplateRepository(context)
    }

    @Test
    fun `updateVerse persists verse and live state across server instances`() {
        val first = NdiBroadcastServer(context, 8091)
        first.updateVerse(verse, live = true)

        val second = NdiBroadcastServer(context, 8091)
        val restored = second.restorePersistedRenderState(repo) { _, _ -> listOf(verse) }

        assertTrue(restored)
        assertEquals(verse, second.currentVerse)
        assertTrue(second.isLive)
    }

    @Test
    fun `clear to standby persists live=false and restores as standby`() {
        val first = NdiBroadcastServer(context, 8091)
        first.updateVerse(verse, live = true)
        first.setLiveState(false)

        val second = NdiBroadcastServer(context, 8091)
        val restored = second.restorePersistedRenderState(repo) { _, _ -> listOf(verse) }

        assertTrue(restored)
        assertEquals(verse, second.currentVerse)
        assertFalse(second.isLive)
    }

    @Test
    fun `unresolvable verse falls back to standby`() {
        val first = NdiBroadcastServer(context, 8091)
        first.updateVerse(verse, live = true)

        val second = NdiBroadcastServer(context, 8091)
        val restored = second.restorePersistedRenderState(repo) { _, _ -> emptyList() }

        assertFalse(restored)
        assertFalse(second.isLive)
    }

    @Test
    fun `no persisted verse restores nothing`() {
        val server = NdiBroadcastServer(context, 8091)
        val restored = server.restorePersistedRenderState(repo) { _, _ -> listOf(verse) }

        assertFalse(restored)
        assertNull(server.currentVerse)
        assertFalse(server.isLive)
    }

    @Test
    fun `restores the persisted active styles`() {
        repo.setActiveTemplateId("tpl_lt_pure_word")
        repo.setActiveShowTemplateId("tpl_fs_pure_word")

        val server = NdiBroadcastServer(context, 8091)
        server.restorePersistedRenderState(repo) { _, _ -> emptyList() }

        assertEquals("tpl_lt_pure_word", server.currentTemplate.id)
        assertEquals("tpl_fs_pure_word", server.currentShowTemplate.id)
    }

    @Test
    fun `deleted styles fall back to factory defaults`() {
        repo.setActiveTemplateId("style-that-no-longer-exists")
        repo.setActiveShowTemplateId("style-that-no-longer-exists")

        val server = NdiBroadcastServer(context, 8091)
        server.restorePersistedRenderState(repo) { _, _ -> emptyList() }

        // Must not crash, and must land on real factory styles for each feed.
        assertFalse(server.currentTemplate.isFullScreen)
        assertTrue(server.currentShowTemplate.isFullScreen)
    }

    @Test
    fun `NdiService sender holder returns the single shared instance`() {
        val a = NdiService.ensureSender(context)
        val b = NdiService.ensureSender(context)
        assertSame(a, b)
    }

    @Test
    fun `clear starts a clear transition on the outgoing verse`() {
        val server = NdiBroadcastServer(context, 8091)
        server.updateTemplate(TemplateRepository.DEFAULT_TEMPLATES[0].copy(transition = TransitionType.FADE, transitionDurationMs = 2000))
        server.updateVerse(verse, live = true)
        server.setLiveState(false)
        assertTrue("clear must animate while the window runs", server.isClearTransitionActiveFor(false))
        val p = server.clearProgressFor(false)
        assertTrue("clear progress must start in range", p >= 0f && p <= 1f)
    }

    @Test
    fun `go-live starts a go-live transition from transparent`() {
        val server = NdiBroadcastServer(context, 8091)
        server.updateTemplate(TemplateRepository.DEFAULT_TEMPLATES[0].copy(transition = TransitionType.FADE, transitionDurationMs = 2000))
        server.setLiveState(false)
        server.updateVerse(verse, live = false)
        server.setLiveState(true)
        assertTrue("go-live must animate while the window runs", server.isGoLiveTransitionActiveFor(false))
        val p = server.goLiveProgressFor(false)
        assertTrue("go-live progress must start in range", p >= 0f && p <= 1f)
    }

    @Test
    fun `CUT disables go-live and clear transitions`() {
        val server = NdiBroadcastServer(context, 8091)
        server.updateTemplate(TemplateRepository.DEFAULT_TEMPLATES[0].copy(transition = TransitionType.CUT, transitionDurationMs = 2000))
        server.updateVerse(verse, live = true)
        server.setLiveState(false)
        assertFalse("CUT clear must be instant", server.isClearTransitionActiveFor(false))
        server.setLiveState(true)
        assertFalse("CUT go-live must be instant", server.isGoLiveTransitionActiveFor(false))
    }
}
