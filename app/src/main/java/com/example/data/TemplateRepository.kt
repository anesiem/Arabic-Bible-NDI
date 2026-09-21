package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BroadcastTextAlignment
import com.example.model.LowerThirdTemplate
import com.example.model.TemplateStyle
import com.example.model.TransitionType
import androidx.core.content.edit
import com.example.model.StreamBackgroundMode
import org.json.JSONArray
import org.json.JSONObject

class TemplateRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bible_ndi_templates_prefs", Context.MODE_PRIVATE)

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
                showCrossEmblem = true,
                bilingualMode = false,
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
                showCrossEmblem = true,
                bilingualMode = false,
                streamBackgroundMode = com.example.model.StreamBackgroundMode.TRANSPARENT_ALPHA
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
                showCrossEmblem = true,
                bilingualMode = false,
                isPureTransparentBackground = true,
                animatedBackground = com.example.model.AnimatedBackgroundType.GOLDEN_DIVINE_RAYS,
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
                showCrossEmblem = true,
                bilingualMode = false,
                isPureTransparentBackground = false,
                animatedBackground = com.example.model.AnimatedBackgroundType.GOLDEN_DIVINE_RAYS,
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
                showCrossEmblem = false,
                bilingualMode = false
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
                showCrossEmblem = true,
                bilingualMode = false
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
                showCrossEmblem = true,
                bilingualMode = true
            )
        )
    }

    fun getAllTemplates(): List<LowerThirdTemplate> {
        val savedJson = prefs.getString("custom_templates", null) ?: return DEFAULT_TEMPLATES
        return try {
            val list = mutableListOf<LowerThirdTemplate>()
            val arr = JSONArray(savedJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(fromJson(obj))
            }
            if (list.isEmpty()) DEFAULT_TEMPLATES else list
        } catch (e: Exception) {
            DEFAULT_TEMPLATES
        }
    }

    fun saveTemplates(templates: List<LowerThirdTemplate>) {
        val arr = JSONArray()
        templates.forEach { arr.put(toJson(it)) }
        prefs.edit { putString("custom_templates", arr.toString()) }
    }

    fun getActiveTemplateId(): String {
        val id = prefs.getString("active_template_id", null)
        if ((id == null) || (id == "tpl_modern_glass")) {
            return "tpl_transparent_alpha"
        }
        return id
    }

    fun setActiveTemplateId(id: String) {
        prefs.edit().putString("active_template_id", id).apply()
    }

    private fun toJson(t: LowerThirdTemplate): JSONObject {
        return JSONObject().apply {
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
            put("showCrossEmblem", t.showCrossEmblem)
            put("bilingualMode", t.bilingualMode)
            put("isPureTransparentBackground", t.isPureTransparentBackground)
            put("animatedBackground", t.animatedBackground.name)
            put("animatedBackgroundOpacity", t.animatedBackgroundOpacity.toDouble())
            put("customVideoUrl", t.customVideoUrl)
            put("streamBackgroundMode", t.streamBackgroundMode.name)
        }
    }

    private fun fromJson(obj: JSONObject): LowerThirdTemplate {
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
            showCrossEmblem = obj.optBoolean("showCrossEmblem", true),
            bilingualMode = obj.optBoolean("bilingualMode", false),
            isPureTransparentBackground = obj.optBoolean("isPureTransparentBackground", false),
            animatedBackground = try { com.example.model.AnimatedBackgroundType.valueOf(obj.optString("animatedBackground")) } catch (e: Exception) { com.example.model.AnimatedBackgroundType.NONE },
            animatedBackgroundOpacity = obj.optDouble("animatedBackgroundOpacity", 0.65).toFloat(),
            customVideoUrl = obj.optString("customVideoUrl", ""),
            streamBackgroundMode = try { com.example.model.StreamBackgroundMode.valueOf(obj.optString("streamBackgroundMode")) } catch (e: Exception) { com.example.model.StreamBackgroundMode.TRANSPARENT_ALPHA }
        )
    }
}
