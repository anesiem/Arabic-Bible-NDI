package com.arabicchristianmedia.data

import android.content.Context
import android.content.SharedPreferences
import com.arabicchristianmedia.model.AnimatedBackgroundType
import com.arabicchristianmedia.model.BroadcastTextAlignment
import com.arabicchristianmedia.model.LanguageMode
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.TemplateStyle
import com.arabicchristianmedia.model.TransitionType
import androidx.core.content.edit
import com.arabicchristianmedia.model.StreamBackgroundMode
import org.json.JSONArray
import org.json.JSONObject

class TemplateRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bible_ndi_templates_prefs", Context.MODE_PRIVATE)

    /** Template IDs whose legacy video URL migration was already attempted this process. */
    private val videoMigrationAttempted = mutableSetOf<String>()

    companion object {
        val DEFAULT_TEMPLATES = listOf(
            LowerThirdTemplate(
                id = "tpl_transparent_alpha",
                name = "100% Transparent Alpha (OBS/vMix/NDI)",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.0f, // PURE 100% TRANSPARENT BACKGROUND
                accentColorHex = "#F59E0B",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FBBF24",
                verseFontSize = 30,
                referenceFontSize = 21,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 8,
                horizontalMarginPercent = 5,
                cornerRadiusDp = 12,
                transition = TransitionType.SLIDE_UP,
                transitionDurationMs = 400,
                showAccentBorder = false,
                showDropShadow = true,
                textShadowEnabled = true, // safe default: white text over video needs a shadow
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8f,
                textShadowOffsetDp = 4f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = false, // safe default: no card on transparent styles
                cardGlowColorHex = "#000000",
                emblem = "✝",
                bilingualMode = false,
                languageMode = LanguageMode.ARABIC_ONLY,
                isPureTransparentBackground = true,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
            ),
            LowerThirdTemplate(
                id = "tpl_modern_glass",
                name = "Modern Broadcast Glass",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#0B132B",
                bgOpacity = 0.82f,
                accentColorHex = "#E5A93C",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F4D06F",
                verseFontSize = 26,
                referenceFontSize = 19,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 6,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 18,
                transition = TransitionType.FADE,
                transitionDurationMs = 350,
                showAccentBorder = true,
                showDropShadow = true,
                textShadowEnabled = true, // safe default: white text over video needs a shadow
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8f,
                textShadowOffsetDp = 4f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = true, // safe default: matches legacy drop-shadow look
                cardGlowColorHex = "#000000",
                emblem = "✝",
                bilingualMode = false,
                languageMode = LanguageMode.ARABIC_ONLY,
                streamBackgroundMode = com.arabicchristianmedia.model.StreamBackgroundMode.TRANSPARENT_ALPHA
            ),
            LowerThirdTemplate(
                id = "tpl_transparent_animated_video",
                name = "Transparent + Animated Motion Video (فيديو متحرك شفاف)",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.0f, // 100% Transparent Background
                accentColorHex = "#F59E0B",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FBBF24",
                verseFontSize = 29,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 8,
                horizontalMarginPercent = 5,
                cornerRadiusDp = 14,
                transition = TransitionType.FADE,
                transitionDurationMs = 400,
                showAccentBorder = false,
                showDropShadow = true,
                textShadowEnabled = true, // safe default: white text over video needs a shadow
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8f,
                textShadowOffsetDp = 4f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = false, // safe default: no card on transparent styles
                cardGlowColorHex = "#000000",
                emblem = "✝",
                bilingualMode = false,
                languageMode = LanguageMode.ARABIC_ONLY,
                isPureTransparentBackground = true,
                animatedBackground = com.arabicchristianmedia.model.AnimatedBackgroundType.GOLDEN_DIVINE_RAYS,
                animatedBackgroundOpacity = 0.80f
            ),
            LowerThirdTemplate(
                id = "tpl_animated_gold",
                name = "Animated Golden Divine Rays (فيديو متحرك)",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#0B132B",
                bgOpacity = 0.40f,
                accentColorHex = "#F59E0B",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE68A",
                verseFontSize = 27,
                referenceFontSize = 19,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 6,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 18,
                transition = TransitionType.FADE,
                transitionDurationMs = 350,
                showAccentBorder = true,
                showDropShadow = true,
                textShadowEnabled = true, // safe default: white text over video needs a shadow
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8f,
                textShadowOffsetDp = 4f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = true, // safe default: matches legacy drop-shadow look
                cardGlowColorHex = "#000000",
                emblem = "✝",
                bilingualMode = false,
                languageMode = LanguageMode.ARABIC_ONLY,
                isPureTransparentBackground = false,
                animatedBackground = com.arabicchristianmedia.model.AnimatedBackgroundType.GOLDEN_DIVINE_RAYS,
                animatedBackgroundOpacity = 0.75f
            ),
            LowerThirdTemplate(
                id = "tpl_classic_banner",
                name = "Classic TV Broadcast Banner",
                style = TemplateStyle.CLASSIC_BANNER,
                bgColorHex = "#0D1F2D",
                bgOpacity = 0.92f,
                accentColorHex = "#00B4D8",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#90E0EF",
                verseFontSize = 25,
                referenceFontSize = 18,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 4,
                horizontalMarginPercent = 4,
                cornerRadiusDp = 10,
                transition = TransitionType.SLIDE_RIGHT,
                transitionDurationMs = 450,
                showAccentBorder = true,
                showDropShadow = true,
                textShadowEnabled = true, // safe default: white text over video needs a shadow
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8f,
                textShadowOffsetDp = 4f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = true, // safe default: matches legacy drop-shadow look
                cardGlowColorHex = "#000000",
                emblem = "",
                bilingualMode = false,
                languageMode = LanguageMode.ARABIC_ONLY
            ),
            LowerThirdTemplate(
                id = "tpl_cathedral_gold",
                name = "Cathedral Liturgical Gold",
                style = TemplateStyle.ROYAL_LITURGICAL,
                bgColorHex = "#161B33",
                bgOpacity = 0.88f,
                accentColorHex = "#D4AF37",
                textColorHex = "#FFFDF9",
                referenceColorHex = "#FFD700",
                verseFontSize = 27,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 7,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 20,
                transition = TransitionType.FADE,
                transitionDurationMs = 500,
                showAccentBorder = true,
                showDropShadow = true,
                textShadowEnabled = true, // safe default: white text over video needs a shadow
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8f,
                textShadowOffsetDp = 4f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = true, // safe default: matches legacy drop-shadow look
                cardGlowColorHex = "#000000",
                emblem = "✝",
                bilingualMode = false,
                languageMode = LanguageMode.ARABIC_ONLY
            ),
            LowerThirdTemplate(
                id = "tpl_bilingual_dual",
                name = "Bilingual Arabic & English",
                style = TemplateStyle.TWO_LINE_SPLIT,
                bgColorHex = "#0F172A",
                bgOpacity = 0.88f,
                accentColorHex = "#38BDF8",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#7DD3FC",
                verseFontSize = 23,
                referenceFontSize = 17,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 5,
                horizontalMarginPercent = 5,
                cornerRadiusDp = 14,
                transition = TransitionType.FADE,
                transitionDurationMs = 350,
                showAccentBorder = true,
                showDropShadow = true,
                textShadowEnabled = true, // safe default: white text over video needs a shadow
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8f,
                textShadowOffsetDp = 4f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = true, // safe default: matches legacy drop-shadow look
                cardGlowColorHex = "#000000",
                emblem = "✝",
                bilingualMode = true,
                languageMode = LanguageMode.BOTH
            ),
            // v1.8: Sanctuary Gold — charcoal card, gold citation, candle-glow motion.
            LowerThirdTemplate(
                id = "tpl_sanctuary_gold",
                name = "Sanctuary Gold",
                style = TemplateStyle.CLASSIC_BANNER,
                bgColorHex = "#1C1917",
                bgOpacity = 0.92f,
                accentColorHex = "#D4AF37",
                textColorHex = "#FEFCE8",
                referenceColorHex = "#FDE68A",
                verseFontSize = 28,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.CENTER,
                positionBottomPercent = 6,
                horizontalMarginPercent = 8,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 500,
                showAccentBorder = true,
                showDropShadow = true,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 10f,
                textShadowOffsetDp = 5f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = true,
                cardGlowColorHex = "#D4AF37",
                emblem = "✦",
                languageMode = LanguageMode.BOTH,
                animatedBackground = AnimatedBackgroundType.CANDLE_LITURGICAL_GLOW,
                animatedBackgroundOpacity = 0.35f,
                secondaryTextColorHex = "#FEF3C7",
                secondaryReferenceColorHex = "#FCD34D",
                secondaryVerseFontSize = 20,
                secondaryReferenceFontSize = 15,
                bilingualSpacing = 12
            ),
            // v1.8: Morning Mercy — warm off-white, soft, highly readable.
            LowerThirdTemplate(
                id = "tpl_morning_mercy",
                name = "Morning Mercy",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#FFFBEB",
                bgOpacity = 0.95f,
                accentColorHex = "#B45309",
                textColorHex = "#451A03",
                referenceColorHex = "#92400E",
                verseFontSize = 26,
                referenceFontSize = 19,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.RIGHT,
                positionBottomPercent = 6,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 18,
                transition = TransitionType.SLIDE_UP,
                transitionDurationMs = 450,
                showAccentBorder = true,
                showDropShadow = true,
                textShadowEnabled = true,
                textShadowColorHex = "#FFFFFF",
                textShadowBlurDp = 6f,
                textShadowOffsetDp = 3f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = true,
                cardGlowColorHex = "#FDE68A",
                emblem = "☀",
                languageMode = LanguageMode.BOTH,
                animatedBackground = AnimatedBackgroundType.GOLDEN_DIVINE_RAYS,
                animatedBackgroundOpacity = 0.25f,
                secondaryTextColorHex = "#78350F",
                secondaryReferenceColorHex = "#A16207",
                secondaryVerseFontSize = 19,
                secondaryReferenceFontSize = 14,
                bilingualSpacing = 10
            ),
            // v1.8: Upper Room — transparent, large centered text, minimal.
            LowerThirdTemplate(
                id = "tpl_upper_room",
                name = "Upper Room",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.0f,
                accentColorHex = "#E0E7FF",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#C7D2FE",
                verseFontSize = 42,
                referenceFontSize = 26,
                fontFamily = "Amiri",
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                alignment = BroadcastTextAlignment.CENTER,
                positionBottomPercent = 10,
                horizontalMarginPercent = 10,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 600,
                showAccentBorder = false,
                showDropShadow = false,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 12f,
                textShadowOffsetDp = 6f,
                textShadowAngleDeg = 90,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                emblem = "",
                languageMode = LanguageMode.BOTH,
                animatedBackground = AnimatedBackgroundType.PARTICLE_STARS,
                animatedBackgroundOpacity = 0.4f,
                secondaryTextColorHex = "#E0E7FF",
                secondaryReferenceColorHex = "#A5B4FC",
                secondaryVerseFontSize = 28,
                secondaryReferenceFontSize = 20,
                bilingualSpacing = 16
            )
        )
    }

    fun getAllTemplates(): List<LowerThirdTemplate> {
        val savedJson = prefs.getString("custom_templates", null) ?: return DEFAULT_TEMPLATES
        return try {
            val list = mutableListOf<LowerThirdTemplate>()
            var migratedAny = false
            val arr = JSONArray(savedJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val hadId = obj.optString("customVideoId", "").isNotEmpty()
                val tpl = fromJson(obj)
                // A one-time legacy migration produced a new video ID: persist
                // so it is not re-attempted on every launch.
                if (!hadId && tpl.customVideoId.isNotEmpty()) migratedAny = true
                list.add(tpl)
            }
            if (list.isEmpty()) return DEFAULT_TEMPLATES
            if (migratedAny) {
                try { saveTemplates(list) } catch (e: Exception) {}
            }
            list
        } catch (e: Exception) {
            DEFAULT_TEMPLATES
        }
    }

    fun saveTemplates(templates: List<LowerThirdTemplate>) {
        val arr = JSONArray()
        templates.forEach { arr.put(toJson(it)) }
        prefs.edit { putString("custom_templates", arr.toString()) }
    }

    /**
     * Highlighted (selected) style id for the Lower Third tab. Null = no style
     * highlighted (user edited without saving, or nothing selected yet).
     * Legacy installs that referenced the deleted "tpl_modern_glass" fall back
     * to the transparent default; a stored null is preserved so a cleared
     * highlight is never resurrected after restart.
     */
    fun getActiveTemplateId(): String? {
        val id = prefs.getString("active_template_id", null)
        if (id == "tpl_modern_glass") {
            return "tpl_transparent_alpha"
        }
        return id
    }

    fun setActiveTemplateId(id: String?) {
        prefs.edit().putString("active_template_id", id).apply()
    }

    /**
     * Highlighted (selected) style id for the Full Show tab. Null = no style
     * highlighted (user edited without saving, or nothing selected yet).
     */
    fun getActiveShowTemplateId(): String? =
        prefs.getString("active_show_template_id", null)

    fun setActiveShowTemplateId(id: String?) {
        prefs.edit().putString("active_show_template_id", id).apply()
    }

    /**
     * Working (live) template per tab: the exact values driving HTTP + NDI right
     * now. Saved styles in [getAllTemplates] are never modified by editing;
     * only explicit "save as" writes into the style list.
     */
    fun getWorkingTemplate(isFullScreen: Boolean): LowerThirdTemplate? {
        val key = if (isFullScreen) "working_show_template" else "working_lower_template"
        val json = prefs.getString(key, null) ?: return null
        return try { fromJson(JSONObject(json)) } catch (e: Exception) { null }
    }

    fun saveWorkingTemplate(template: LowerThirdTemplate) {
        val key = if (template.isFullScreen) "working_show_template" else "working_lower_template"
        prefs.edit().putString(key, toJson(template).toString()).apply()
    }

    /** True when the working template has unsaved edits (highlight cleared). */
    fun isWorkingDirty(isFullScreen: Boolean): Boolean {
        val key = if (isFullScreen) "working_show_dirty" else "working_lower_dirty"
        return prefs.getBoolean(key, false)
    }

    fun setWorkingDirty(isFullScreen: Boolean, dirty: Boolean) {
        val key = if (isFullScreen) "working_show_dirty" else "working_lower_dirty"
        prefs.edit().putBoolean(key, dirty).apply()
    }

    /**
     * Serializes one style to shareable JSON (pretty-printed) for export.
     */
    fun templateToJsonString(t: LowerThirdTemplate): String = toJson(t).toString(2)

    /**
     * Parses a style from shared/imported JSON. Returns null when the payload
     * is not a recognizable style (defensive: never throws).
     */
    fun templateFromJsonString(json: String): LowerThirdTemplate? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("name", "").trim().isEmpty()) return null
            // Sanity: a real style carries several known keys.
            val knownKeys = listOf("fontFamily", "bgColorHex", "textColorHex", "verseFontSize", "style", "showCrossEmblem", "emblem")
            if (knownKeys.none { obj.has(it) }) return null
            fromJson(obj)
        } catch (e: Exception) {
            null
        }
    }

    private fun toJson(t: LowerThirdTemplate): JSONObject {        return JSONObject().apply {
            put("id", t.id)
            put("name", t.name)
            put("style", t.style.name)
            put("bgColorHex", t.bgColorHex)
            put("bgOpacity", t.bgOpacity.toDouble())
            put("accentColorHex", t.accentColorHex)
            put("textColorHex", t.textColorHex)
            put("referenceColorHex", t.referenceColorHex)
            put("verseFontSize", t.verseFontSize)
            put("referenceFontSize", t.referenceFontSize)
            put("fontFamily", t.fontFamily)
            put("secondaryTextColorHex", t.secondaryTextColorHex)
            put("secondaryReferenceColorHex", t.secondaryReferenceColorHex)
            put("secondaryVerseFontSize", t.secondaryVerseFontSize)
            put("secondaryReferenceFontSize", t.secondaryReferenceFontSize)
            put("secondaryFontFamily", t.secondaryFontFamily)
            put("isFullScreen", t.isFullScreen)
            put("bilingualSpacing", t.bilingualSpacing)
            put("showBilingualSpacing", t.showBilingualSpacing)
            put("useEasternArabicNumerals", t.useEasternArabicNumerals)
            put("useArabicPunctuation", t.useArabicPunctuation)
            put("alignment", t.alignment.name)
            put("positionBottomPercent", t.positionBottomPercent)
            put("horizontalMarginPercent", t.horizontalMarginPercent)
            put("cornerRadiusDp", t.cornerRadiusDp)
            put("transition", t.transition.name)
            put("transitionDurationMs", t.transitionDurationMs)
            put("showAccentBorder", t.showAccentBorder)
            put("showDropShadow", t.showDropShadow)
            put("textShadowEnabled", t.textShadowEnabled)
            put("textShadowColorHex", t.textShadowColorHex)
            put("textShadowBlurDp", t.textShadowBlurDp.toDouble())
            put("textShadowOffsetDp", t.textShadowOffsetDp.toDouble())
            put("textShadowAngleDeg", t.textShadowAngleDeg)
            put("cardGlowEnabled", t.cardGlowEnabled)
            put("cardGlowColorHex", t.cardGlowColorHex)
            put("emblem", t.emblem)
            put("bilingualMode", t.bilingualMode)
            put("languageMode", t.languageMode.name)
            put("isPureTransparentBackground", t.isPureTransparentBackground)
            put("animatedBackground", t.animatedBackground.name)
            put("animatedBackgroundOpacity", t.animatedBackgroundOpacity.toDouble())
            put("customVideoUrl", t.customVideoUrl)
            put("customVideoId", t.customVideoId)
            put("customVideoMuted", t.customVideoMuted)
            put("motionSpeed", t.motionSpeed)
            put("streamBackgroundMode", t.streamBackgroundMode.name)
        }
    }

    private fun fromJson(obj: JSONObject): LowerThirdTemplate {
        // Safe per-style migration defaults: read style/transparency BEFORE the
        // object build so a legacy single shadow switch migrates to the new
        // flags correctly — transparent styles must NOT gain a card glow.
        val legacyShadow = obj.optBoolean("showDropShadow", true)
        val migratedStyle = try { TemplateStyle.valueOf(obj.optString("style")) }
            catch (e: Exception) { TemplateStyle.MODERN_GLASS } // unknown style: assume a card, keep legacy look
        val migratedTransparent = obj.optBoolean("isPureTransparentBackground", false) ||
                migratedStyle == TemplateStyle.TRANSPARENT_OUTLINE
        val safeGlowDefault = legacyShadow && !migratedTransparent
        return LowerThirdTemplate(
            id = obj.optString("id", "tpl_${System.currentTimeMillis()}"),
            name = obj.optString("name", "Custom Template"),
            style = try { TemplateStyle.valueOf(obj.optString("style")) } catch (e: Exception) { TemplateStyle.MODERN_GLASS },
            bgColorHex = obj.optString("bgColorHex", "#0A1128"),
            bgOpacity = obj.optDouble("bgOpacity", 0.85).toFloat(),
            accentColorHex = obj.optString("accentColorHex", "#E5A93C"),
            textColorHex = obj.optString("textColorHex", "#FFFFFF"),
            referenceColorHex = obj.optString("referenceColorHex", "#F4D06F"),
            verseFontSize = obj.optInt("verseFontSize", 26),
            referenceFontSize = obj.optInt("referenceFontSize", 18),
            fontFamily = obj.optString("fontFamily", "Amiri"),
            secondaryTextColorHex = obj.optString("secondaryTextColorHex", "#CBD5E1"),
            secondaryReferenceColorHex = obj.optString("secondaryReferenceColorHex", "#94A3B8"),
            secondaryVerseFontSize = obj.optInt("secondaryVerseFontSize", 18),
            secondaryReferenceFontSize = obj.optInt("secondaryReferenceFontSize", 14),
            secondaryFontFamily = obj.optString("secondaryFontFamily", "System"),
            isFullScreen = obj.optBoolean("isFullScreen", false),
            bilingualSpacing = obj.optInt("bilingualSpacing", 20),
            showBilingualSpacing = obj.optBoolean("showBilingualSpacing", true),
            useEasternArabicNumerals = obj.optBoolean("useEasternArabicNumerals", true),
            useArabicPunctuation = obj.optBoolean("useArabicPunctuation", true),
            alignment = try { BroadcastTextAlignment.valueOf(obj.optString("alignment")) } catch (e: Exception) { BroadcastTextAlignment.RIGHT },
            positionBottomPercent = obj.optInt("positionBottomPercent", 6),
            horizontalMarginPercent = obj.optInt("horizontalMarginPercent", 6),
            cornerRadiusDp = obj.optInt("cornerRadiusDp", 16),
            transition = try { TransitionType.valueOf(obj.optString("transition")) } catch (e: Exception) { TransitionType.FADE },
            transitionDurationMs = obj.optInt("transitionDurationMs", 350),
            showAccentBorder = obj.optBoolean("showAccentBorder", true),
            showDropShadow = obj.optBoolean("showDropShadow", true),
            // One-time migration: the legacy single switch becomes both new flags (on, black).
            // Transparent styles migrate glow to OFF (no card to glow); the rest keep the
            // legacy drop-shadow look. New saves always carry the explicit keys below.
            textShadowEnabled = if (obj.has("textShadowEnabled")) obj.optBoolean("textShadowEnabled", true)
                                else legacyShadow,
            textShadowColorHex = obj.optString("textShadowColorHex", "#000000"),
            textShadowBlurDp = obj.optDouble("textShadowBlurDp", 8.0).toFloat(),
            textShadowOffsetDp = obj.optDouble("textShadowOffsetDp", 4.0).toFloat(),
            textShadowAngleDeg = obj.optInt("textShadowAngleDeg", 90),
            cardGlowEnabled = if (obj.has("cardGlowEnabled")) obj.optBoolean("cardGlowEnabled", true)
                              else safeGlowDefault,
            cardGlowColorHex = obj.optString("cardGlowColorHex", "#000000"),
            // v1.7 migration: the legacy showCrossEmblem boolean becomes a
            // free emblem string; old "true" styles get the cross glyph.
            emblem = if (obj.has("emblem")) obj.optString("emblem", "")
                     else if (obj.optBoolean("showCrossEmblem", true)) "✝" else "",
            bilingualMode = obj.optBoolean("bilingualMode", false),
            // v1.8 migration: the legacy bilingualMode boolean becomes the
            // three-way languageMode; old "true" styles become BOTH.
            languageMode = if (obj.has("languageMode")) {
                try { com.arabicchristianmedia.model.LanguageMode.valueOf(obj.optString("languageMode")) }
                catch (e: Exception) { com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY }
            } else if (obj.optBoolean("bilingualMode", false)) {
                com.arabicchristianmedia.model.LanguageMode.BOTH
            } else {
                com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY
            },
            isPureTransparentBackground = obj.optBoolean("isPureTransparentBackground", false),
            animatedBackground = try { com.arabicchristianmedia.model.AnimatedBackgroundType.valueOf(obj.optString("animatedBackground")) } catch (e: Exception) { com.arabicchristianmedia.model.AnimatedBackgroundType.NONE },
            animatedBackgroundOpacity = obj.optDouble("animatedBackgroundOpacity", 0.65).toFloat(),
            customVideoUrl = obj.optString("customVideoUrl", ""),
            customVideoId = obj.optString("customVideoId", ""),
            customVideoMuted = obj.optBoolean("customVideoMuted", true),
            motionSpeed = obj.optDouble("motionSpeed", 1.0).toFloat().coerceIn(0.25f, 3.0f),
            streamBackgroundMode = try { com.arabicchristianmedia.model.StreamBackgroundMode.valueOf(obj.optString("streamBackgroundMode")) } catch (e: Exception) { com.arabicchristianmedia.model.StreamBackgroundMode.TRANSPARENT_ALPHA }
        ).let { migrateLegacyVideo(it) }
    }

    /**
     * v1.7 one-time migration: a legacy customVideoUrl (raw content:// URI or
     * file path, pre-private-storage) is imported into VideoStore; the
     * template keeps the new ID and drops the raw URL. Best-effort:
     * unresolvable URLs simply lose the video (the editor prompts to re-pick).
     */
    private fun migrateLegacyVideo(tpl: LowerThirdTemplate): LowerThirdTemplate {
        if (tpl.customVideoId.isNotEmpty() || tpl.customVideoUrl.isBlank()) return tpl
        synchronized(videoMigrationAttempted) {
            if (!videoMigrationAttempted.add(tpl.id)) return tpl
        }
        return try {
            val id = com.arabicchristianmedia.server.VideoStore(appContext).importLegacyUrl(tpl.customVideoUrl)
            if (id != null) tpl.copy(customVideoId = id, customVideoUrl = "") else tpl
        } catch (e: Exception) {
            tpl
        }
    }
}
