package com.arabicchristianmedia.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arabicchristianmedia.model.ClearMode
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.VerticalAlignment
import com.arabicchristianmedia.model.isPureDerived
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
        val custom = repo.getAllTemplates().filter { it.id in setOf("lower_1", "show_1", "lower_2") }
        assertEquals(3, custom.size)
        assertEquals(setOf("lower_1", "lower_2"), custom.filter { !it.isFullScreen }.map { it.id }.toSet())
        assertEquals(listOf("show_1"), custom.filter { it.isFullScreen }.map { it.id })
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
    fun `legacy tpl_modern_glass migrates to its redesigned successor`() {
        repo.setActiveTemplateId("tpl_modern_glass")
        assertEquals("tpl_lt_pure_word_v2", repo.getActiveTemplateId())
    }

    @Test
    fun `any other legacy factory id migrates to the pure-word default`() {
        repo.setActiveTemplateId("tpl_vivid_neon_night")
        assertEquals("tpl_lt_pure_word_v2", repo.getActiveTemplateId())
        // tpl_lt_golden_glory was deleted in the one-update redesign —
        // it is now a legacy id and migrates to pure word too.
        repo.setActiveTemplateId("tpl_lt_golden_glory")
        assertEquals("tpl_lt_pure_word_v2", repo.getActiveTemplateId())
        // tpl_lt_classic was deleted in the item-94 redesign (1.9.10) —
        // it is now a legacy id and migrates to pure word too.
        repo.setActiveTemplateId("tpl_lt_classic")
        assertEquals("tpl_lt_pure_word_v2", repo.getActiveTemplateId())
    }

    @Test
    fun `legacy show ids migrate to the pure-word full default`() {
        repo.setActiveShowTemplateId("tpl_fs_golden_light")
        assertEquals("tpl_fs_pure_word_v2", repo.getActiveShowTemplateId())
        repo.setActiveShowTemplateId("tpl_lt_classic")
        assertEquals("tpl_fs_pure_word_v2", repo.getActiveShowTemplateId())
    }

    @Test
    fun `retired 1_9_9 factory ids are legacy factory ids`() {
        listOf(
            "tpl_fs_pure_word", "tpl_fs_golden_light", "tpl_fs_midnight",
            "tpl_fs_desert", "tpl_fs_royal", "tpl_lt_classic", "tpl_lt_minimal",
            "tpl_lt_elegant", "tpl_lt_bold", "tpl_lt_soft"
        ).forEach { id ->
            assertTrue("$id must be a legacy factory id", TemplateRepository.isFactoryStyleId(id))
        }
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
        assertEquals(20, defaults.size)
        assertEquals(20, defaults.map { it.id }.toSet().size)
        assertEquals(10, defaults.count { !it.isFullScreen })
        assertEquals(10, defaults.count { it.isFullScreen })
        // Item 94 (1.9.10): fresh ids — none may collide with the legacy
        // set, or a user's Update to that style would be discarded on load.
        defaults.forEach { tpl ->
            assertFalse("${tpl.id} must not be a legacy id", tpl.id in TemplateRepository.LEGACY_FACTORY_IDS)
            assertTrue(tpl.textShadowEnabled)
            assertTrue(tpl.textShadowColorHex.isNotBlank())
            assertTrue(tpl.cardGlowColorHex.isNotBlank())
            assertTrue("logo stays off by default", tpl.logoUri.isEmpty())
        }
        // Item 94: only one transparent style per feed (L1 + F1); the
        // other 18 are fully opaque so the draft's contrast figures hold.
        val transparentIds = listOf("tpl_lt_pure_word_v2", "tpl_fs_pure_word_v2")
        transparentIds.forEach { id ->
            val tpl = defaults.first { it.id == id }
            assertFalse("$id should not glow", tpl.cardGlowEnabled)
            assertEquals("$id should be 100% transparent", 0.0f, tpl.bgOpacity, 0.001f)
        }
        defaults.filter { it.id !in transparentIds }.forEach { tpl ->
            assertEquals("${tpl.id} should be fully opaque", 1.0f, tpl.bgOpacity, 0.001f)
        }
        // Item 94 progression: transparent -> gradient -> gradient + glow
        // (Lower L7) -> gradient + glow + particles (Full F5).
        assertTrue("Golden Desert (Lower) should glow", defaults.first { it.id == "tpl_lt_golden_desert" }.cardGlowEnabled)
        assertFalse(
            "no other Lower style glows",
            defaults.filter { !it.isFullScreen && it.id != "tpl_lt_golden_desert" }.any { it.cardGlowEnabled }
        )
        // Item 95 part 2: every Full style carries the fullscreen macro
        // values, so card glow is baked OFF even on the draft's "glow on"
        // Full palettes (the renderer skips glow chrome in fullscreen).
        assertFalse("no Full style glows (item 95 macro)", defaults.filter { it.isFullScreen }.any { it.cardGlowEnabled })
        assertTrue("Royal Proclamation should have golden particles", defaults.first { it.id == "tpl_fs_royal_proclamation" }.particleEffect.name == "GOLDEN_PARTICLES")
        assertTrue("Royal video background is none (item 75 split)", defaults.first { it.id == "tpl_fs_royal_proclamation" }.animatedBackground.name == "NONE")
        assertFalse("no other style has particles", defaults.filter { it.id != "tpl_fs_royal_proclamation" }.any { it.particleEffect.name != "NONE" })
        // New designs: gradient second color is a real companion —
        // never equal to first, never a dropped-alpha black.
        defaults.filter { it.gradientEnabled }.forEach { tpl ->
            assertFalse(
                "${tpl.id} gradient colors must differ",
                tpl.bgColorHex.equals(tpl.gradientColorHex2, ignoreCase = true)
            )
        }
        assertEquals(16, defaults.count { it.gradientEnabled })
        // Draft palette spot-checks (bg / gradient / citation / accent).
        val midnightLower = defaults.first { it.id == "tpl_lt_midnight_navy" }
        assertEquals("#0A1128", midnightLower.bgColorHex)
        assertEquals("#1B3A5C", midnightLower.gradientColorHex2)
        assertEquals("#F5D76E", midnightLower.referenceColorHex)
        assertEquals("#38BDF8", midnightLower.accentColorHex)
        val royalFull = defaults.first { it.id == "tpl_fs_royal_proclamation" }
        assertEquals("#1E1B4B", royalFull.bgColorHex)
        assertEquals("#312E81", royalFull.gradientColorHex2)
        assertEquals("#FBBF24", royalFull.accentColorHex)
        val ivoryFull = defaults.first { it.id == "tpl_fs_soft_ivory" }
        assertEquals("#FEF3C7", ivoryFull.bgColorHex)
        assertEquals("#1C1917", ivoryFull.textColorHex)
        assertEquals("#92400E", ivoryFull.referenceColorHex)
        assertEquals("#334155", ivoryFull.secondaryTextColorHex)
        // English (secondary) text: light grey on dark, dark slate on light.
        defaults.filter { it.id !in listOf("tpl_lt_slate_sky", "tpl_fs_soft_ivory") }.forEach { tpl ->
            assertEquals("${tpl.id} English text", "#CBD5E1", tpl.secondaryTextColorHex)
        }
        listOf("tpl_lt_slate_sky", "tpl_fs_soft_ivory").forEach { id ->
            assertEquals("$id English text", "#334155", defaults.first { it.id == id }.secondaryTextColorHex)
        }
        // No retired 1.9.9 name survives (the draft reuses only Pure Word,
        // Golden Light, Midnight Clarity, Desert Dawn, Royal Proclamation).
        val retiredNames = setOf("Classic Banner", "Modern Minimal", "Elegant Serif", "Bold Contrast", "Soft Light")
        assertTrue(defaults.none { it.name in retiredNames })
        // Vertical alignment by feed (1.9.16: one field): Full TOP, Lower BOTTOM.
        defaults.filter { it.isFullScreen }.forEach { tpl ->
            assertEquals(VerticalAlignment.TOP, tpl.verticalAlignment)
        }
        defaults.filter { !it.isFullScreen }.forEach { tpl ->
            assertEquals(VerticalAlignment.BOTTOM, tpl.verticalAlignment)
        }
        // Item 95 part 2: every Full style bakes the macro values.
        defaults.filter { it.isFullScreen }.forEach { tpl ->
            assertTrue(tpl.id, tpl.trueFullscreen)
            assertEquals(tpl.id, 0, tpl.horizontalMarginPercent)
            assertEquals(tpl.id, 0.0f, tpl.cardPaddingTopDp, 0.001f)
            assertEquals(tpl.id, 0.0f, tpl.cardPaddingBottomDp, 0.001f)
            assertEquals(tpl.id, 0.0f, tpl.cardPaddingLeftDp, 0.001f)
            assertEquals(tpl.id, 0.0f, tpl.cardPaddingRightDp, 0.001f)
            assertFalse(tpl.id, tpl.showAccentBorder)
            assertFalse(tpl.id, tpl.boxSwayEnabled)
            assertFalse(tpl.id, tpl.boxFloatEnabled)
            assertFalse(tpl.id, tpl.boxZoomEnabled)
            assertFalse(tpl.id, tpl.boxGlowPulseEnabled)
            assertEquals(tpl.id, 0, tpl.cornerRadiusDp)
            assertEquals(tpl.id, 100, tpl.boxHeightPercent)
            assertEquals(tpl.id, 0, tpl.positionBottomPercent)
            assertEquals(tpl.id, ClearMode.CLEAR_VERSE_ONLY, tpl.clearMode)
        }
        defaults.filter { !it.isFullScreen }.forEach { tpl ->
            assertFalse(tpl.id, tpl.trueFullscreen)
            assertEquals(tpl.id, ClearMode.CLEAR_ALL, tpl.clearMode)
        }
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
            autoFitMaxVerseSp = 36,
            boxHeightPercent = 60
        )
        val imported = repo.templateFromJsonString(repo.templateToJsonString(tpl))
        assertNotNull(imported)
        assertEquals(10f, imported!!.cardPaddingTopDp, 0.001f)
        assertEquals(20f, imported.cardPaddingBottomDp, 0.001f)
        assertEquals(30f, imported.cardPaddingLeftDp, 0.001f)
        assertEquals(40f, imported.cardPaddingRightDp, 0.001f)
        assertFalse(imported.autoFitText)
        assertEquals(36, imported.autoFitMaxVerseSp)
        assertEquals(60, imported.boxHeightPercent)
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
        assertEquals(48, imported.autoFitMaxVerseSp)
        assertEquals(45, imported.boxHeightPercent)
    }

    @Test
    fun `v1_9_1 legacy autoFitMinVerseSp migrates as the cap`() {
        // v1.9 saves carry only the old floor key; its value becomes the cap
        // (the meaning changed on Ashraf's ruling, the number carries over).
        val imported = repo.templateFromJsonString("{\"id\":\"leg3\",\"name\":\"Legacy\",\"autoFitMinVerseSp\":42}")
        assertNotNull(imported)
        assertEquals(42, imported!!.autoFitMaxVerseSp)
    }

    @Test
    fun `v1_9_1 legacy floor below verse size migrates up to the verse size`() {
        // Round-2 guard: floor 16 with verse 58 was harmless as a floor;
        // as a cap it would crush every verse (Ashraf's Midnight Psalm).
        // The migrated cap rises to the style's own verse size.
        val imported = repo.templateFromJsonString("{\"id\":\"leg4\",\"name\":\"Legacy2\",\"verseFontSize\":58,\"autoFitMinVerseSp\":16}")
        assertNotNull(imported)
        assertEquals(58, imported!!.autoFitMaxVerseSp)
    }

    @Test
    fun `v1_9_1 box height defaults follow the screen kind`() {
        val lower = repo.templateFromJsonString("{\"id\":\"bh1\",\"name\":\"L\"}")
        val full = repo.templateFromJsonString("{\"id\":\"bh2\",\"name\":\"F\",\"isFullScreen\":true}")
        assertNotNull(lower)
        assertNotNull(full)
        assertEquals(45, lower!!.boxHeightPercent)
        assertEquals(100, full!!.boxHeightPercent)
    }

    @Test
    fun `v1_9_10 vertical alignment defaults follow the screen kind`() {
        // Old JSON without the item-91 keys: Full anchors TOP, Lower BOTTOM.
        val lower = repo.templateFromJsonString("{\"id\":\"va1\",\"name\":\"L\"}")
        val full = repo.templateFromJsonString("{\"id\":\"va2\",\"name\":\"F\",\"isFullScreen\":true}")
        assertNotNull(lower)
        assertNotNull(full)
        assertEquals(VerticalAlignment.BOTTOM, lower!!.verticalAlignment)
        assertEquals(VerticalAlignment.TOP, full!!.verticalAlignment)
    }

    @Test
    fun `v1_9_10 vertical alignment round-trips through JSON`() {
        val tpl = baseTemplate("va3", false).copy(
            verticalAlignment = VerticalAlignment.BOTTOM
        )
        val imported = repo.templateFromJsonString(repo.templateToJsonString(tpl))
        assertNotNull(imported)
        assertEquals(VerticalAlignment.BOTTOM, imported!!.verticalAlignment)
    }

    @Test
    fun `v1_9_16 old single ratio lock migrates to the two per-language locks`() {
        // Old save with the single key: both locks take its value.
        val off = repo.templateFromJsonString("{\"id\":\"rl1\",\"name\":\"R\",\"autoFitRatioLock\":false}")
        assertNotNull(off)
        assertFalse(off!!.autoFitRatioLockArabic)
        assertFalse(off.autoFitRatioLockEnglish)
        // New keys win over the old key when both are present.
        val mixed = repo.templateFromJsonString("{\"id\":\"rl2\",\"name\":\"M\",\"autoFitRatioLock\":false,\"autoFitRatioLockArabic\":true,\"autoFitRatioLockEnglish\":false}")
        assertNotNull(mixed)
        assertTrue(mixed!!.autoFitRatioLockArabic)
        assertFalse(mixed.autoFitRatioLockEnglish)
        // No key at all: both on (the old default).
        val def = repo.templateFromJsonString("{\"id\":\"rl3\",\"name\":\"D\"}")
        assertNotNull(def)
        assertTrue(def!!.autoFitRatioLockArabic)
        assertTrue(def.autoFitRatioLockEnglish)
        // Round-trip: the two locks survive JSON.
        val tpl = baseTemplate("rl4", false).copy(autoFitRatioLockArabic = false, autoFitRatioLockEnglish = true)
        val imported = repo.templateFromJsonString(repo.templateToJsonString(tpl))
        assertNotNull(imported)
        assertFalse(imported!!.autoFitRatioLockArabic)
        assertTrue(imported.autoFitRatioLockEnglish)
    }

    @Test
    fun `v1_9_16 old per-language alignment keys migrate to the single field`() {
        // Old save with the two per-language keys (split): Arabic wins
        // unless it is CENTER, when English wins — the old collapse rule.
        val split = repo.templateFromJsonString("{\"id\":\"va4\",\"name\":\"S\",\"verticalAlignmentArabic\":\"BOTTOM\",\"verticalAlignmentEnglish\":\"CENTER\"}")
        assertNotNull(split)
        assertEquals(VerticalAlignment.BOTTOM, split!!.verticalAlignment)
        val centerFirst = repo.templateFromJsonString("{\"id\":\"va5\",\"name\":\"C\",\"verticalAlignmentArabic\":\"CENTER\",\"verticalAlignmentEnglish\":\"BOTTOM\"}")
        assertNotNull(centerFirst)
        assertEquals(VerticalAlignment.BOTTOM, centerFirst!!.verticalAlignment)
        // New single key wins over the old keys when both are present.
        val both = repo.templateFromJsonString("{\"id\":\"va6\",\"name\":\"B\",\"verticalAlignment\":\"CENTER\",\"verticalAlignmentArabic\":\"BOTTOM\",\"verticalAlignmentEnglish\":\"BOTTOM\"}")
        assertNotNull(both)
        assertEquals(VerticalAlignment.CENTER, both!!.verticalAlignment)
    }

    @Test
    fun `v1_9 factory templates carry explicit padding and auto-fit values`() {
        for (tpl in TemplateRepository.DEFAULT_TEMPLATES) {
            assertTrue(tpl.id, tpl.cardPaddingTopDp >= 0f)
            assertTrue(tpl.id, tpl.cardPaddingBottomDp >= 0f)
            assertTrue(tpl.id, tpl.cardPaddingLeftDp >= 0f)
            assertTrue(tpl.id, tpl.cardPaddingRightDp >= 0f)
            assertTrue(tpl.id, tpl.autoFitMaxVerseSp > 0)
            assertTrue(tpl.id, tpl.boxHeightPercent in 10..100)
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

    // ── v1.9.1: update style in place ───────────────────────────────────

    @Test
    fun updatedTemplatesReplacesSameIdPreservingOrder() {
        val a = baseTemplate("tpl_100", false)
        val b = baseTemplate("tpl_200", false)
        val c = baseTemplate("tpl_300", true)
        val editedB = b.copy(verseFontSize = 99, textColorHex = "#123456")
        val result = TemplateRepository.updatedTemplates(listOf(a, b, c), editedB)
        assertEquals("list size must not change on in-place update", 3, result.size)
        assertEquals("order is preserved (first)", a, result[0])
        assertEquals("the same-id entry is replaced by the edited values", editedB, result[1])
        assertEquals("order is preserved (last)", c, result[2])
    }

    @Test
    fun updatedTemplatesWithUnknownIdChangesNothing() {
        val a = baseTemplate("tpl_100", false)
        val stranger = baseTemplate("tpl_999", false)
        val result = TemplateRepository.updatedTemplates(listOf(a), stranger)
        assertEquals("an unknown id must not be inserted by an update", listOf(a), result)
    }

    @Test
    fun factoryStyleIdsAreDetected() {
        val factoryId = TemplateRepository.DEFAULT_TEMPLATES[0].id
        assertTrue("a shipped style id is factory", TemplateRepository.isFactoryStyleId(factoryId))
        assertTrue(
            "a previous shipped set id is factory",
            TemplateRepository.isFactoryStyleId(TemplateRepository.LEGACY_FACTORY_IDS.first())
        )
        assertFalse("a user style id (tpl_<timestamp>) is not factory", TemplateRepository.isFactoryStyleId("tpl_1730000000000"))
        assertFalse("null is not factory", TemplateRepository.isFactoryStyleId(null))
    }

    @Test
    fun `bracket system-font flag round-trips through persistence`() {
        val saved = baseTemplate("brk_1", false).copy(useSystemFontForBrackets = false)
        repo.saveTemplates(listOf(saved))
        val loaded = repo.getAllTemplates().first { it.id == "brk_1" }
        assertFalse("an OFF bracket flag must survive save and load", loaded.useSystemFontForBrackets)
    }

    @Test
    fun `bracket system-font flag defaults to true when absent from JSON`() {
        val parsed = repo.templateFromJsonString("""{"name":"T","id":"brk_2"}""")
        assertNotNull("a minimal JSON with a known key must parse", parsed)
        assertTrue("a missing bracket flag must default to ON", parsed!!.useSystemFontForBrackets)
    }
}
