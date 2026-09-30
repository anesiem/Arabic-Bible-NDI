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
        assertEquals(7, defaults.size)
        defaults.forEach { tpl ->
            assertTrue(tpl.textShadowEnabled)
            assertEquals("#000000", tpl.textShadowColorHex)
            assertEquals("#000000", tpl.cardGlowColorHex)
        }
        // The two transparent styles must not glow: there is no card, and the
        // feeds promise 100% transparency. The five card styles keep the glow
        // (matches the legacy drop-shadow look).
        assertFalse(defaults.first { it.id == "tpl_transparent_alpha" }.cardGlowEnabled)
        assertFalse(defaults.first { it.id == "tpl_transparent_animated_video" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_modern_glass" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_animated_gold" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_classic_banner" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_cathedral_gold" }.cardGlowEnabled)
        assertTrue(defaults.first { it.id == "tpl_bilingual_dual" }.cardGlowEnabled)
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
}
