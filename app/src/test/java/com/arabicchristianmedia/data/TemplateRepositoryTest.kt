package com.arabicchristianmedia.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arabicchristianmedia.model.LowerThirdTemplate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.6 style-system persistence: per-tab working templates, highlight ids,
 * dirty flags, partition by isFullScreen, and legacy shadow migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TemplateRepositoryTest {

    private lateinit var repo: TemplateRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bible_ndi_templates_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repo = TemplateRepository(context)
    }

    private fun baseTemplate(id: String, isFullScreen: Boolean): LowerThirdTemplate =
        TemplateRepository.DEFAULT_TEMPLATES[0].copy(id = id, name = "Style $id", isFullScreen = isFullScreen)

    @Test
    fun `styles are strictly partitioned by isFullScreen`() {
        repo.saveTemplates(
            listOf(
                baseTemplate("lower_1", false),
                baseTemplate("show_1", true),
                baseTemplate("lower_2", false)
            )
        )
        val all = repo.getAllTemplates()
        assertEquals(3, all.size)
        assertEquals(setOf("lower_1", "lower_2"), all.filter { !it.isFullScreen }.map { it.id }.toSet())
        assertEquals(listOf("show_1"), all.filter { it.isFullScreen }.map { it.id })
    }

    @Test
    fun `working templates persist independently per tab`() {
        val lower = baseTemplate("lower_w", false).copy(verseFontSize = 42)
        val show = baseTemplate("show_w", true).copy(verseFontSize = 77)
        repo.saveWorkingTemplate(lower)
        repo.saveWorkingTemplate(show)

        assertEquals(42, repo.getWorkingTemplate(false)!!.verseFontSize)
        assertEquals(77, repo.getWorkingTemplate(true)!!.verseFontSize)
    }

    @Test
    fun `dirty flags persist independently per tab`() {
        assertFalse(repo.isWorkingDirty(false))
        assertFalse(repo.isWorkingDirty(true))

        repo.setWorkingDirty(false, true)
        assertTrue(repo.isWorkingDirty(false))
        assertFalse(repo.isWorkingDirty(true))

        repo.setWorkingDirty(true, true)
        assertTrue(repo.isWorkingDirty(false))
        assertTrue(repo.isWorkingDirty(true))
    }

    @Test
    fun `cleared highlight stays null across restart`() {
        repo.setActiveTemplateId("some_style")
        assertEquals("some_style", repo.getActiveTemplateId())

        // User edited without saving -> highlight cleared.
        repo.setActiveTemplateId(null)
        assertNull(repo.getActiveTemplateId())

        // Simulate restart with a fresh repository instance.
        val restarted = TemplateRepository(context)
        assertNull(restarted.getActiveTemplateId())
    }

    @Test
    fun `show highlight round-trips including null`() {
        assertNull(repo.getActiveShowTemplateId())
        repo.setActiveShowTemplateId("show_style_1")
        assertEquals("show_style_1", repo.getActiveShowTemplateId())
        repo.setActiveShowTemplateId(null)
        assertNull(repo.getActiveShowTemplateId())
    }

    @Test
    fun `legacy tpl_modern_glass migrates to transparent default`() {
        repo.setActiveTemplateId("tpl_modern_glass")
        assertEquals("tpl_transparent_alpha", repo.getActiveTemplateId())
    }

    @Test
    fun `legacy showDropShadow migrates to both new flags`() {
        // A v1.5-era JSON: has showDropShadow, lacks the new shadow/glow keys.
        val legacy = JSONObject()
            .put("id", "legacy_1")
            .put("name", "Legacy")
            .put("showDropShadow", true)
        val arr = JSONArray().put(legacy)
        context.getSharedPreferences("bible_ndi_templates_prefs", Context.MODE_PRIVATE)
            .edit().putString("custom_templates", arr.toString()).commit()

        val restored = repo.getAllTemplates().first { it.id == "legacy_1" }
        assertTrue(restored.textShadowEnabled)
        assertEquals("#000000", restored.textShadowColorHex)
        assertTrue(restored.cardGlowEnabled)
        assertEquals("#000000", restored.cardGlowColorHex)
    }

    @Test
    fun `legacy transparent style migrates card glow to off`() {
        // A v1.5-era transparent style: legacy shadow on, but no card to glow —
        // the migration must not add a halo to a transparent feed.
        val legacy = JSONObject()
            .put("id", "legacy_transparent")
            .put("name", "Legacy Transparent")
            .put("style", "TRANSPARENT_OUTLINE")
            .put("isPureTransparentBackground", true)
            .put("showDropShadow", true)
        val arr = JSONArray().put(legacy)
        context.getSharedPreferences("bible_ndi_templates_prefs", Context.MODE_PRIVATE)
            .edit().putString("custom_templates", arr.toString()).commit()

        val restored = repo.getAllTemplates().first { it.id == "legacy_transparent" }
        assertTrue(restored.textShadowEnabled)
        assertFalse(restored.cardGlowEnabled)
    }

    @Test
    fun `factory styles carry explicit safe shadow and glow values`() {
        val defaults = TemplateRepository.DEFAULT_TEMPLATES
        assertEquals(38, defaults.size)
        defaults.forEach { tpl ->
            assertTrue(tpl.textShadowEnabled)
            assertTrue(tpl.textShadowColorHex.isNotBlank())
            assertTrue(tpl.cardGlowColorHex.isNotBlank())
        }
        // The two transparent styles must not glow: there is no card, and the
        // feeds promise 100% transparency. The card styles keep the glow.
        assertFalse(defaults.first { it.id == "tpl_transparent_alpha" }.cardGlowEnabled)
        assertFalse(defaults.first { it.id == "tpl_transparent_animated_video" }.cardGlowEnabled)
        assertFalse(defaults.first { it.id == "tpl_transparent_alpha_full" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_transparent_alpha_full" }.isFullScreen)
        assertFalse(defaults.first { it.id == "tpl_upper_room" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_modern_glass" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_animated_gold" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_classic_banner" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_cathedral_gold" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_bilingual_dual" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_sanctuary_gold" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_morning_mercy" }.cardGlowEnabled)
    }

    @Test
    fun `legacy showDropShadow=false migrates to both flags off`() {
        val legacy = JSONObject()
            .put("id", "legacy_2")
            .put("name", "Legacy 2")
            .put("showDropShadow", false)
        val arr = JSONArray().put(legacy)
        context.getSharedPreferences("bible_ndi_templates_prefs", Context.MODE_PRIVATE)
            .edit().putString("custom_templates", arr.toString()).commit()

        val restored = repo.getAllTemplates().first { it.id == "legacy_2" }
        assertFalse(restored.textShadowEnabled)
        assertFalse(restored.cardGlowEnabled)
    }

    @Test
    fun `new shadow and glow fields round-trip`() {
        val tpl = baseTemplate("shadow_1", false).copy(
            textShadowEnabled = false,
            textShadowColorHex = "#FF0000",
            cardGlowEnabled = true,
            cardGlowColorHex = "#00FF00"
        )
        repo.saveTemplates(listOf(tpl))

        val restored = repo.getAllTemplates().first { it.id == "shadow_1" }
        assertFalse(restored.textShadowEnabled)
        assertEquals("#FF0000", restored.textShadowColorHex)
        assertTrue(restored.cardGlowEnabled)
        assertEquals("#00FF00", restored.cardGlowColorHex)
    }

    @Test
    fun `style export json round-trips through import`() {
        val tpl = baseTemplate("export_1", false).copy(
            name = "My Exported Style",
            fontFamily = "Reem Kufi",
            verseFontSize = 64,
            textShadowEnabled = true
        )
        val json = repo.templateToJsonString(tpl)
        assertTrue(json.contains("My Exported Style"))

        val imported = repo.templateFromJsonString(json)
        assertNotNull(imported)
        assertEquals("My Exported Style", imported!!.name)
        assertEquals("Reem Kufi", imported.fontFamily)
        assertEquals(64, imported.verseFontSize)
        assertTrue(imported.textShadowEnabled)
    }

    @Test
    fun `import rejects garbage json`() {
        assertNull(repo.templateFromJsonString("not json at all"))
        assertNull(repo.templateFromJsonString("{}"))
        assertNull(repo.templateFromJsonString("{\"foo\": 1}"))
        assertNull(repo.templateFromJsonString("{\"name\": \"   \"}"))
    }

    @Test
    fun `emblem round-trips through json`() {
        val tpl = baseTemplate("emblem_1", false).copy(emblem = "🕊️")
        val json = repo.templateToJsonString(tpl)
        assertTrue(json.contains("emblem"))

        val imported = repo.templateFromJsonString(json)
        assertNotNull(imported)
        assertEquals("🕊️", imported!!.emblem)

        // Empty emblem stays empty (no emblem rendered).
        val empty = repo.templateFromJsonString(repo.templateToJsonString(tpl.copy(emblem = "")))
        assertNotNull(empty)
        assertEquals("", empty!!.emblem)
    }

    @Test
    fun `legacy showCrossEmblem migrates to cross glyph`() {
        // Old JSON with the boolean flag and no emblem key.
        val legacyTrue = repo.templateFromJsonString(
            "{\"id\":\"leg1\",\"name\":\"Legacy\",\"showCrossEmblem\":true}"
        )
        assertNotNull(legacyTrue)
        assertEquals("✝", legacyTrue!!.emblem)

        val legacyFalse = repo.templateFromJsonString(
            "{\"id\":\"leg2\",\"name\":\"Legacy\",\"showCrossEmblem\":false}"
        )
        assertNotNull(legacyFalse)
        assertEquals("", legacyFalse!!.emblem)
    }

    @Test
    fun `v1_9 padding and auto-fit fields round-trip through JSON`() {
        val tpl = baseTemplate("pad1", false).copy(
            cardPaddingTopDp = 10f,
            cardPaddingBottomDp = 20f,
            cardPaddingLeftDp = 30f,
            cardPaddingRightDp = 40f,
            autoFitText = false,
            autoFitMinVerseSp = 36
        )
        val imported = repo.templateFromJsonString(repo.templateToJsonString(tpl))
        assertNotNull(imported)
        assertEquals(10f, imported!!.cardPaddingTopDp, 0.001f)
        assertEquals(20f, imported.cardPaddingBottomDp, 0.001f)
        assertEquals(30f, imported.cardPaddingLeftDp, 0.001f)
        assertEquals(40f, imported.cardPaddingRightDp, 0.001f)
        assertFalse(imported.autoFitText)
        assertEquals(36, imported.autoFitMinVerseSp)
    }

    @Test
    fun `v1_9 missing keys migrate to current-behavior defaults`() {
        // Old JSON without the v1.9 keys: padding matches the old hardcoded
        // 36/32 look, auto-fit defaults on.
        val imported = repo.templateFromJsonString("{\"id\":\"old1\",\"name\":\"Old\",\"style\":\"CARD_OVERLAY\"}")
        assertNotNull(imported)
        assertEquals(32f, imported!!.cardPaddingTopDp, 0.001f)
        assertEquals(32f, imported.cardPaddingBottomDp, 0.001f)
        assertEquals(36f, imported.cardPaddingLeftDp, 0.001f)
        assertEquals(36f, imported.cardPaddingRightDp, 0.001f)
        assertTrue(imported.autoFitText)
        assertEquals(28, imported.autoFitMinVerseSp)
    }

    @Test
    fun `v1_9 factory templates carry explicit padding and auto-fit values`() {
        for (tpl in TemplateRepository.DEFAULT_TEMPLATES) {
            assertEquals(tpl.id, 32f, tpl.cardPaddingTopDp, 0.001f)
            assertEquals(tpl.id, 32f, tpl.cardPaddingBottomDp, 0.001f)
            assertEquals(tpl.id, 36f, tpl.cardPaddingLeftDp, 0.001f)
            assertEquals(tpl.id, 36f, tpl.cardPaddingRightDp, 0.001f)
            assertTrue(tpl.id, tpl.autoFitText)
            assertEquals(tpl.id, 28, tpl.autoFitMinVerseSp)
        }
    }

    @Test
    fun `v1_9 bold italic fields round-trip through JSON`() {
        val tpl = baseTemplate("bi1", false).copy(
            verseIsBold = false,
            verseIsItalic = true,
            referenceIsBold = false,
            referenceIsItalic = true,
            secondaryVerseIsBold = true,
            secondaryVerseIsItalic = true,
            secondaryReferenceIsBold = false,
            secondaryReferenceIsItalic = false
        )
        val imported = repo.templateFromJsonString(repo.templateToJsonString(tpl))
        assertNotNull(imported)
        assertFalse(imported!!.verseIsBold)
        assertTrue(imported.verseIsItalic)
        assertFalse(imported.referenceIsBold)
        assertTrue(imported.referenceIsItalic)
        assertTrue(imported.secondaryVerseIsBold)
        assertTrue(imported.secondaryVerseIsItalic)
        assertFalse(imported.secondaryReferenceIsBold)
        assertFalse(imported.secondaryReferenceIsItalic)
    }

    @Test
    fun `v1_9 seven vivid presets exist with animated transparent backgrounds`() {        val vivid = TemplateRepository.DEFAULT_TEMPLATES.filter { it.id.startsWith("tpl_vivid_") }
        assertEquals(7, vivid.size)
        val expectedAnims = mapOf(
            "tpl_vivid_neon_night" to com.arabicchristianmedia.model.AnimatedBackgroundType.PARTICLE_STARS,
            "tpl_vivid_sunset_glory" to com.arabicchristianmedia.model.AnimatedBackgroundType.GOLDEN_PARTICLES,
            "tpl_vivid_ocean_depth" to com.arabicchristianmedia.model.AnimatedBackgroundType.ETHEREAL_BLUE_WAVES,
            "tpl_vivid_royal_amethyst" to com.arabicchristianmedia.model.AnimatedBackgroundType.ROYAL_PURPLE_SILK,
            "tpl_vivid_emerald_oasis" to com.arabicchristianmedia.model.AnimatedBackgroundType.EMERALD_GARDEN_WAVES,
            "tpl_vivid_golden_sanctuary" to com.arabicchristianmedia.model.AnimatedBackgroundType.GOLDEN_DIVINE_RAYS,
            "tpl_vivid_crimson_dawn" to com.arabicchristianmedia.model.AnimatedBackgroundType.ROSE_DAWN_GLOW
        )
        for (tpl in vivid) {
            assertEquals(tpl.id, expectedAnims[tpl.id], tpl.animatedBackground)
            assertTrue(tpl.id, tpl.isPureTransparentBackground)
            assertFalse(tpl.id, tpl.isFullScreen)
            assertTrue(tpl.id, tpl.textShadowEnabled)
        }
    }

    @Test
    fun `v1_9 twenty new presets cover both feeds with language mix`() {
        val lt = TemplateRepository.DEFAULT_TEMPLATES.filter { it.id.startsWith("tpl_lt_") }
        val fs = TemplateRepository.DEFAULT_TEMPLATES.filter { it.id.startsWith("tpl_fs_") }
        assertEquals(10, lt.size)
        assertEquals(10, fs.size)
        for (tpl in lt) assertFalse(tpl.id, tpl.isFullScreen)
        for (tpl in fs) assertTrue(tpl.id, tpl.isFullScreen)
        // Language mix per feed: 4 Arabic-only, 3 English-only, 3 Both.
        for ((group, label) in listOf(lt to "lt", fs to "fs")) {
            val langs = group.groupingBy { it.languageMode }.eachCount()
            assertEquals(label, 4, langs[com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY])
            assertEquals(label, 3, langs[com.arabicchristianmedia.model.LanguageMode.ENGLISH_ONLY])
            assertEquals(label, 3, langs[com.arabicchristianmedia.model.LanguageMode.BOTH])
        }
        // Full Show presets use large centered text.
        for (tpl in fs) {
            assertTrue(tpl.id, tpl.verseFontSize >= 56)
            assertEquals(tpl.id, com.arabicchristianmedia.model.BroadcastTextAlignment.CENTER, tpl.alignment)
        }
    }
}
