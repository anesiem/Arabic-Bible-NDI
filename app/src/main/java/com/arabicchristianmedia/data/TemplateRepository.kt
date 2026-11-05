package com.arabicchristianmedia.data

import android.content.Context
import android.content.SharedPreferences
import com.arabicchristianmedia.model.AnimatedBackgroundType
import com.arabicchristianmedia.model.BroadcastTextAlignment
import com.arabicchristianmedia.model.ClearMode
import com.arabicchristianmedia.model.LanguageMode
import com.arabicchristianmedia.model.GradientAnimationType
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.TemplateStyle
import com.arabicchristianmedia.model.TextEffectType
import com.arabicchristianmedia.model.TransitionType
import com.arabicchristianmedia.model.VideoLoopMode
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
        /** IDs of the previous shipped style set (pre-redesign). Entries with
         * these IDs in a saved list are replaced by the current shipped styles;
         * anything else (user styles, legacy imports) is never touched. */
        val LEGACY_FACTORY_IDS = setOf(
            "tpl_transparent_alpha", "tpl_modern_glass", "tpl_transparent_animated_video",
            "tpl_animated_gold", "tpl_classic_banner", "tpl_cathedral_gold",
            "tpl_bilingual_dual", "tpl_sanctuary_gold", "tpl_morning_mercy", "tpl_upper_room",
            "tpl_transparent_alpha_full", "tpl_vivid_neon_night", "tpl_vivid_sunset_glory",
            "tpl_vivid_ocean_depth", "tpl_vivid_royal_amethyst", "tpl_vivid_emerald_oasis",
            "tpl_vivid_golden_sanctuary", "tpl_vivid_crimson_dawn", "tpl_lt_pure_word",
            "tpl_lt_clear_light", "tpl_lt_crimson", "tpl_lt_azure", "tpl_lt_trumpet",
            "tpl_lt_coral", "tpl_lt_midnight", "tpl_lt_pearl", "tpl_lt_sage",
            "tpl_lt_velvet", "tpl_fs_pure_word", "tpl_fs_clear_light", "tpl_fs_dawn",
            "tpl_fs_ocean", "tpl_fs_royal", "tpl_fs_fire", "tpl_fs_sanctuary",
            "tpl_fs_starlit", "tpl_fs_garden", "tpl_fs_candle",
            // One-update batch (2026-10-29): the entire 39-style v1.9 set is
            // replaced by the 20 new designs. Saved entries with these IDs
            // are replaced by the new DEFAULT_TEMPLATES on load.
            "tpl_lt_golden_glory", "tpl_lt_sanctuary_gold", "tpl_lt_golden_trumpet",
            "tpl_lt_pentecost_flame", "tpl_lt_ocean_grace", "tpl_lt_azure_heaven",
            "tpl_lt_neon_night", "tpl_lt_starlit_worship", "tpl_lt_midnight_pill",
            "tpl_lt_royal_amethyst", "tpl_lt_velvet_worship", "tpl_lt_crown_glory",
            "tpl_lt_emerald_oasis", "tpl_lt_garden_peace", "tpl_lt_sage_serenity",
            "tpl_lt_crimson_dawn", "tpl_lt_rose_sharon", "tpl_lt_morning_mercy",
            "tpl_lt_candlelight", "tpl_lt_coral_joy", "tpl_lt_broadcast_glass",
            "tpl_lt_classic_banner", "tpl_lt_cathedral_gold", "tpl_lt_bilingual_harmony",
            "tpl_lt_floating_pill", "tpl_lt_crystal_english",
            "tpl_fs_glory_revealed", "tpl_fs_ocean_depths", "tpl_fs_starlit_heaven",
            "tpl_fs_royal_celebration", "tpl_fs_garden_prayer", "tpl_fs_dawn_chorus",
            "tpl_fs_candlelight_vigil", "tpl_fs_pentecost_fire", "tpl_fs_morning_light",
            "tpl_fs_midnight_psalm", "tpl_pure_transparent"
        )

        /** v1.9.1: shipped styles (the current set, or any previous shipped
         * set) can never be overwritten in place — the loader re-supplies
         * them from code on every launch. */
        fun isFactoryStyleId(id: String?): Boolean =
            id != null && (DEFAULT_TEMPLATES.any { it.id == id } || id in LEGACY_FACTORY_IDS)

        /** v1.9.1: the style list with the same-id entry replaced by
         * [updated], order preserved. Pure — unit-tested. */
        fun updatedTemplates(
            list: List<LowerThirdTemplate>,
            updated: LowerThirdTemplate
        ): List<LowerThirdTemplate> =
            list.map { if (it.id == updated.id) updated else it }

        val DEFAULT_TEMPLATES = listOf(
            LowerThirdTemplate(
                id = "tpl_fs_pure_word",
                name = "Pure Word",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.0f,
                accentColorHex = "#F4D06F",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F4D06F",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 0,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = false,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 45,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_golden_light",
                name = "Golden Light",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.15f,
                accentColorHex = "#FFB84D",
                textColorHex = "#FFF8E7",
                referenceColorHex = "#FFB84D",
                verseFontSize = 46,
                referenceFontSize = 26,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 37,
                secondaryReferenceFontSize = 20,
                secondaryFontFamily = "System",
                isFullScreen = true,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 0,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = false,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 58,
                boxHeightPercent = 45,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_midnight",
                name = "Midnight Clarity",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.35f,
                accentColorHex = "#F4D06F",
                textColorHex = "#E8F0FE",
                referenceColorHex = "#F4D06F",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 0,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = false,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 45,
                gradientEnabled = true,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.SLIDE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_desert",
                name = "Desert Dawn",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.5f,
                accentColorHex = "#E07A5F",
                textColorHex = "#FFF5E1",
                referenceColorHex = "#E07A5F",
                verseFontSize = 46,
                referenceFontSize = 26,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 37,
                secondaryReferenceFontSize = 20,
                secondaryFontFamily = "System",
                isFullScreen = true,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 0,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = false,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = true,
                cardGlowColorHex = "#E07A5F",
                cardGlowOpacity = 0.6f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 58,
                boxHeightPercent = 45,
                gradientEnabled = true,
                gradientColorHex2 = "#3E2723",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.SLIDE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_royal",
                name = "Royal Proclamation",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.6f,
                accentColorHex = "#D4AF37",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#D4AF37",
                verseFontSize = 50,
                referenceFontSize = 30,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 41,
                secondaryReferenceFontSize = 24,
                secondaryFontFamily = "System",
                isFullScreen = true,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = true,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 0,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = false,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = true,
                cardGlowColorHex = "#D4AF37",
                cardGlowOpacity = 0.8f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.GOLDEN_PARTICLES,
                animatedBackgroundOpacity = 0.5f,
                particleOpacity = 0.5f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 62,
                boxHeightPercent = 45,
                gradientEnabled = true,
                gradientColorHex2 = "#1A1A2E",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.5f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_classic",
                name = "Classic Banner",
                style = TemplateStyle.CLASSIC_BANNER,
                bgColorHex = "#1A2332",
                bgOpacity = 0.85f,
                accentColorHex = "#F4D06F",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F4D06F",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 8,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = true,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = false,
                autoFitText = true,
                autoFitMaxVerseSp = 44,
                boxHeightPercent = 45,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_minimal",
                name = "Modern Minimal",
                style = TemplateStyle.MINIMALIST_PILL,
                bgColorHex = "#2D2D2D",
                bgOpacity = 0.8f,
                accentColorHex = "#B0BEC5",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#B0BEC5",
                verseFontSize = 30,
                referenceFontSize = 18,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 21,
                secondaryReferenceFontSize = 12,
                secondaryFontFamily = "System",
                isFullScreen = false,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 8,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = false,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = false,
                autoFitText = true,
                autoFitMaxVerseSp = 42,
                boxHeightPercent = 45,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_elegant",
                name = "Elegant Serif",
                style = TemplateStyle.ROYAL_LITURGICAL,
                bgColorHex = "#3E2723",
                bgOpacity = 0.85f,
                accentColorHex = "#D4AF37",
                textColorHex = "#FFF8E1",
                referenceColorHex = "#D4AF37",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 8,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = true,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = false,
                autoFitText = true,
                autoFitMaxVerseSp = 44,
                boxHeightPercent = 45,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_bold",
                name = "Bold Contrast",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#000000",
                bgOpacity = 0.9f,
                accentColorHex = "#FFEB3B",
                textColorHex = "#FFEB3B",
                referenceColorHex = "#FFFFFF",
                verseFontSize = 34,
                referenceFontSize = 22,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 25,
                secondaryReferenceFontSize = 16,
                secondaryFontFamily = "System",
                isFullScreen = false,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = true,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 8,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = false,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = false,
                autoFitText = true,
                autoFitMaxVerseSp = 46,
                boxHeightPercent = 45,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_soft",
                name = "Soft Light",
                style = TemplateStyle.TWO_LINE_SPLIT,
                bgColorHex = "#FFFFFF",
                bgOpacity = 0.85f,
                accentColorHex = "#5D4037",
                textColorHex = "#212121",
                referenceColorHex = "#5D4037",
                verseFontSize = 30,
                referenceFontSize = 18,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 21,
                secondaryReferenceFontSize = 12,
                secondaryFontFamily = "System",
                isFullScreen = false,
                bilingualSpacing = 18,
                showBilingualSpacing = true,
                verseIsBold = false,
                verseIsItalic = false,
                referenceIsBold = true,
                referenceIsItalic = false,
                secondaryVerseIsBold = false,
                secondaryVerseIsItalic = false,
                secondaryReferenceIsBold = true,
                secondaryReferenceIsItalic = false,
                useEasternArabicNumerals = true,
                useArabicPunctuation = true,
                useSystemFontForBrackets = true,
                alignment = BroadcastTextAlignment.RIGHT,
                citationAlignmentArabic = BroadcastTextAlignment.RIGHT,
                citationAlignmentEnglish = BroadcastTextAlignment.LEFT,
                verseAlignmentEnglish = BroadcastTextAlignment.LEFT,
                positionBottomPercent = 8,
                horizontalMarginPercent = 6,
                cornerRadiusDp = 16,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_ALL,
                showAccentBorder = true,
                showAccentBorderWidthDp = 2f,
                accentOpacity = 1.0f,
                textShadowEnabled = true,
                textShadowColorHex = "#000000",
                textShadowBlurDp = 8.0f,
                textShadowOffsetDp = 5.0f,
                textShadowAngleDeg = 90,
                textEffect = TextEffectType.NONE,
                textEffectDepthDp = 3f,
                cardGlowEnabled = false,
                cardGlowColorHex = "#000000",
                cardGlowOpacity = 1.0f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                logoUri = "",
                logoPosition = "top_right",
                logoBoxSizePercent = 15f,
                logoOffsetXPercent = 0f,
                logoOffsetYPercent = 0f,
                logoOpacity = 1.0f,
                customVideoUrl = "",
                customVideoId = "",
                customVideoMuted = true,
                motionSpeed = 1.0f,
                videoLoopMode = VideoLoopMode.FADE,
                streamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
                cardPaddingTopDp = 32.0f,
                cardPaddingBottomDp = 32.0f,
                cardPaddingLeftDp = 36.0f,
                cardPaddingRightDp = 36.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = false,
                autoFitText = true,
                autoFitMaxVerseSp = 42,
                boxHeightPercent = 45,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLock = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
        )
    }

    /** Combined batch: pure-flag factory/user templates normalize
     * to opacity 0 on read, so the Editor slider and the renderers
     * (derived pure = opacity 0) can never disagree. Colors kept. */
    fun normalizePure(t: LowerThirdTemplate): LowerThirdTemplate =
        if (t.isPureTransparentBackground) t.copy(bgOpacity = 0f, isPureTransparentBackground = false) else t

    fun getAllTemplates(): List<LowerThirdTemplate> {
        val savedJson = prefs.getString("custom_templates", null) ?: return DEFAULT_TEMPLATES.map { normalizePure(it) }
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
            // v1.9: merge in any factory presets added since the user last saved.
            // Batch-2 fix: preserve user-modified factory templates. Previously,
            // ANY saved template with a current factory ID was discarded and
            // replaced with the factory default, losing the user's Update.
            // Now: if the saved list has a version of a factory template, keep
            // the saved (user-modified) version. Only LEGACY_FACTORY_IDS are
            // replaced (true redesigns).
            val currentIds = DEFAULT_TEMPLATES.map { it.id }.toSet()
            val kept = list.filter { it.id !in LEGACY_FACTORY_IDS }
            val savedById = kept.associateBy { it.id }
            val refreshed = DEFAULT_TEMPLATES.map { default ->
                normalizePure(savedById[default.id] ?: default)
            }.toMutableList()
            // Add user templates (non-factory IDs)
            refreshed.addAll(kept.filter { it.id !in currentIds })
            if (refreshed.map { it.id } != list.map { it.id } ||
                refreshed != list
            ) {
                migratedAny = true
            }
            list.clear()
            list.addAll(refreshed)
            if (migratedAny) {
                try { saveTemplates(list) } catch (e: Exception) {}
            }
            list
        } catch (e: Exception) {
            DEFAULT_TEMPLATES.map { normalizePure(it) }
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
     * to Royal Gold; other legacy shipped IDs fall back to the transparent
     * default; a stored null is preserved so a cleared
     * highlight is never resurrected after restart.
     */
    fun getActiveTemplateId(): String? {
        val id = prefs.getString("active_template_id", null)
        // A stored highlight from a previous shipped set maps to the new
        // designs. Unknown IDs (user styles) pass through unchanged.
        if (id != null && id in LEGACY_FACTORY_IDS) {
            return "tpl_lt_classic"
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
    fun getActiveShowTemplateId(): String? {
        val id = prefs.getString("active_show_template_id", null)
        if (id != null && id in LEGACY_FACTORY_IDS) {
            return "tpl_fs_pure_word"
        }
        return id
    }

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
        // Item 33: log parse failures instead of silently returning null —
        // a corrupt working template was silently falling back to defaults.
        return try { fromJson(JSONObject(json)) } catch (e: Exception) {
            android.util.Log.w("TemplateRepository", "Working template parse failed for key $key, falling back", e)
            null
        }
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
     * Rec 3 (1.9.8): embeds the logo bytes as "logoData" (Base64, NO_WRAP)
     * so a shared style keeps its logo — the logo file itself lives in
     * private storage and never travels with plain JSON. Best-effort: any
     * failure just omits logoData. Only the export path embeds it; toJson
     * (prefs storage) stays lean.
     */
    fun templateToJsonString(t: LowerThirdTemplate): String {
        val obj = toJson(t)
        if (t.logoUri.isNotEmpty()) {
            try {
                com.arabicchristianmedia.server.LogoStore(appContext).fileFor(t.logoUri)?.let { file ->
                    if (file.isFile && file.length() > 0) {
                        obj.put("logoData", android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("TemplateRepository", "Logo embed failed, exporting without logo", e)
            }
        }
        return obj.toString(2)
    }

    /**
     * Parses a style from shared/imported JSON. Returns null when the payload
     * is not a recognizable style (defensive: never throws).
     */
    fun templateFromJsonString(json: String): LowerThirdTemplate? {
        return try {
            val obj = JSONObject(json)
            if (obj.optString("name", "").trim().isEmpty()) return null
            // Sanity: a real style carries several known keys.
            val knownKeys = listOf("fontFamily", "bgColorHex", "textColorHex", "verseFontSize", "style", "showCrossEmblem", "emblem", "autoFitMinVerseSp", "autoFitMaxVerseSp", "boxHeightPercent", "isFullScreen", "id", "useSystemFontForBrackets", "gradientColorHex2", "gradientAngleDeg", "gradientAnimation", "autoFitMinCitationSp", "autoFitRatioLock", "clearMode", "lineSpacingArabic", "lineSpacingEnglish", "autoFitFrozenRatio", "verseCitationGapArabicDp", "verseCitationGapEnglishDp", "boxLoopContinuous", "trueFullscreen", "citationAlignmentArabic", "citationAlignmentEnglish", "verseAlignmentEnglish", "textEffect", "textEffectDepthDp", "cardGlowRadiusDp", "showAccentBorderWidthDp", "cardGlowOpacity", "accentOpacity", "showArabic", "showEnglish", "particleOpacity", "logoUri", "logoPosition", "logoBoxSizePercent", "logoOffsetXPercent", "logoOffsetYPercent", "logoOpacity", "logoData")
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
            put("verseIsBold", t.verseIsBold)
            put("verseIsItalic", t.verseIsItalic)
            put("referenceIsBold", t.referenceIsBold)
            put("referenceIsItalic", t.referenceIsItalic)
            put("secondaryVerseIsBold", t.secondaryVerseIsBold)
            put("secondaryVerseIsItalic", t.secondaryVerseIsItalic)
            put("secondaryReferenceIsBold", t.secondaryReferenceIsBold)
            put("secondaryReferenceIsItalic", t.secondaryReferenceIsItalic)
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
            put("useSystemFontForBrackets", t.useSystemFontForBrackets)
            put("alignment", t.alignment.name)
            put("citationAlignmentArabic", t.citationAlignmentArabic.name)
            put("citationAlignmentEnglish", t.citationAlignmentEnglish.name)
            put("verseAlignmentEnglish", t.verseAlignmentEnglish.name)
            put("positionBottomPercent", t.positionBottomPercent)
            put("horizontalMarginPercent", t.horizontalMarginPercent)
            put("cornerRadiusDp", t.cornerRadiusDp)
            put("transition", t.transition.name)
            put("transitionDurationMs", t.transitionDurationMs)
            put("boxTransition", t.boxTransition.name)
            put("boxTransitionDurationMs", t.boxTransitionDurationMs)
            put("boxZoomEnabled", t.boxZoomEnabled)
            put("boxFloatEnabled", t.boxFloatEnabled)
            put("boxSwayEnabled", t.boxSwayEnabled)
            put("boxGlowPulseEnabled", t.boxGlowPulseEnabled)
            put("boxMotionSpeed", t.boxMotionSpeed.toDouble())
            put("clearMode", t.clearMode.name)
            put("showAccentBorder", t.showAccentBorder)
            put("textShadowEnabled", t.textShadowEnabled)
            put("textShadowColorHex", t.textShadowColorHex)
            put("textShadowBlurDp", t.textShadowBlurDp.toDouble())
            put("textShadowOffsetDp", t.textShadowOffsetDp.toDouble())
            put("textShadowAngleDeg", t.textShadowAngleDeg)
            put("textEffect", t.textEffect.id)
            put("textEffectDepthDp", t.textEffectDepthDp.toDouble())
            put("cardGlowEnabled", t.cardGlowEnabled)
            put("cardGlowColorHex", t.cardGlowColorHex)
            put("cardGlowOpacity", t.cardGlowOpacity.toDouble())
            put("accentOpacity", t.accentOpacity.toDouble())
            put("emblem", t.emblem)
            put("bilingualMode", t.bilingualMode)
            put("languageMode", t.languageMode.name)
            put("showArabic", t.showArabic)
            put("showEnglish", t.showEnglish)
            put("isPureTransparentBackground", t.isPureTransparentBackground)
            put("animatedBackground", t.animatedBackground.name)
            put("animatedBackgroundOpacity", t.animatedBackgroundOpacity.toDouble())
            put("particleOpacity", t.particleOpacity.toDouble())
            put("logoUri", t.logoUri)
            put("logoPosition", t.logoPosition)
            put("logoBoxSizePercent", t.logoBoxSizePercent.toDouble())
            put("logoOffsetXPercent", t.logoOffsetXPercent.toDouble())
            put("logoOffsetYPercent", t.logoOffsetYPercent.toDouble())
            put("logoOpacity", t.logoOpacity.toDouble())
            put("customVideoUrl", t.customVideoUrl)
            put("customVideoId", t.customVideoId)
            put("customVideoMuted", t.customVideoMuted)
            put("motionSpeed", t.motionSpeed)
            put("videoLoopMode", t.videoLoopMode.id)
            put("streamBackgroundMode", t.streamBackgroundMode.name)
            put("cardPaddingTopDp", t.cardPaddingTopDp.toDouble())
            put("cardPaddingBottomDp", t.cardPaddingBottomDp.toDouble())
            put("cardPaddingLeftDp", t.cardPaddingLeftDp.toDouble())
            put("cardPaddingRightDp", t.cardPaddingRightDp.toDouble())
            put("verseCitationGapArabicDp", t.verseCitationGapArabicDp.toDouble())
            put("verseCitationGapEnglishDp", t.verseCitationGapEnglishDp.toDouble())
            put("boxLoopContinuous", t.boxLoopContinuous)
            put("trueFullscreen", t.trueFullscreen)
            put("autoFitText", t.autoFitText)
            put("autoFitMaxVerseSp", t.autoFitMaxVerseSp)
            put("boxHeightPercent", t.boxHeightPercent)
            put("gradientEnabled", t.gradientEnabled)
            put("gradientColorHex2", t.gradientColorHex2)
            put("gradientAngleDeg", t.gradientAngleDeg)
            put("gradientAnimation", t.gradientAnimation.id)
            put("gradientSpeed", t.gradientSpeed)
            put("autoFitMinCitationSp", t.autoFitMinCitationSp)
            put("autoFitRatioLock", t.autoFitRatioLock)
            put("lineSpacingArabic", t.lineSpacingArabic.toDouble())
            put("lineSpacingEnglish", t.lineSpacingEnglish.toDouble())
            if (t.autoFitFrozenRatio != null) put("autoFitFrozenRatio", t.autoFitFrozenRatio.toDouble())
            put("cardGlowRadiusDp", t.cardGlowRadiusDp.toDouble())
            put("showAccentBorderWidthDp", t.showAccentBorderWidthDp.toDouble())
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
        // Split-transition migration: the old single transition value is
        // read once here and reused for the verse fields below AND for
        // the box fields when the saved JSON predates them (no
        // "boxTransition" key), so old templates look exactly as before.
        val parsedTransition = try { TransitionType.valueOf(obj.optString("transition")) }
            catch (e: Exception) { TransitionType.FADE }
        val parsedTransitionDurationMs = obj.optInt("transitionDurationMs", 350)
        val parsedIsFullScreen = obj.optBoolean("isFullScreen", false)
        return LowerThirdTemplate(
            id = obj.optString("id", "tpl_${System.currentTimeMillis()}"),
            name = obj.optString("name", "Custom Template"),
            style = try { TemplateStyle.valueOf(obj.optString("style")) } catch (e: Exception) { TemplateStyle.MODERN_GLASS },
            bgColorHex = obj.optString("bgColorHex", "#0A1128"),
            // Combined batch: 100% Alpha toggle removed. Pure is now
            // simply bgOpacity == 0. A saved pure flag migrates to
            // opacity 0 and the flag is cleared; every other field
            // (including bgColorHex) is preserved, so returning the
            // slider to 1 restores the exact old card.
            bgOpacity = if (obj.optBoolean("isPureTransparentBackground", false)) 0f
                        else obj.optDouble("bgOpacity", 0.85).toFloat(),
            accentColorHex = obj.optString("accentColorHex", "#E5A93C"),
            textColorHex = obj.optString("textColorHex", "#FFFFFF"),
            referenceColorHex = obj.optString("referenceColorHex", "#F4D06F"),
            verseFontSize = obj.optInt("verseFontSize", 26),
            referenceFontSize = obj.optInt("referenceFontSize", 18),
            verseIsBold = obj.optBoolean("verseIsBold", true),
            verseIsItalic = obj.optBoolean("verseIsItalic", false),
            referenceIsBold = obj.optBoolean("referenceIsBold", true),
            referenceIsItalic = obj.optBoolean("referenceIsItalic", false),
            secondaryVerseIsBold = obj.optBoolean("secondaryVerseIsBold", false),
            secondaryVerseIsItalic = obj.optBoolean("secondaryVerseIsItalic", false),
            secondaryReferenceIsBold = obj.optBoolean("secondaryReferenceIsBold", true),
            secondaryReferenceIsItalic = obj.optBoolean("secondaryReferenceIsItalic", true),
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
            useSystemFontForBrackets = obj.optBoolean("useSystemFontForBrackets", true),
            alignment = try { BroadcastTextAlignment.valueOf(obj.optString("alignment")) } catch (e: Exception) { BroadcastTextAlignment.RIGHT },
            citationAlignmentArabic = try { BroadcastTextAlignment.valueOf(obj.optString("citationAlignmentArabic", "RIGHT")) } catch (e: Exception) { BroadcastTextAlignment.RIGHT },
            citationAlignmentEnglish = try { BroadcastTextAlignment.valueOf(obj.optString("citationAlignmentEnglish", "RIGHT")) } catch (e: Exception) { BroadcastTextAlignment.RIGHT },
            verseAlignmentEnglish = try { BroadcastTextAlignment.valueOf(obj.optString("verseAlignmentEnglish", "LEFT")) } catch (e: Exception) { BroadcastTextAlignment.LEFT },
            positionBottomPercent = obj.optInt("positionBottomPercent", 6),
            horizontalMarginPercent = obj.optInt("horizontalMarginPercent", 6),
            cornerRadiusDp = obj.optInt("cornerRadiusDp", 16),
            transition = parsedTransition,
            transitionDurationMs = parsedTransitionDurationMs,
            boxTransition = if (obj.has("boxTransition")) {
                try { TransitionType.valueOf(obj.optString("boxTransition")) } catch (e: Exception) { parsedTransition }
            } else parsedTransition,
            boxTransitionDurationMs = if (obj.has("boxTransitionDurationMs")) obj.optInt("boxTransitionDurationMs", parsedTransitionDurationMs) else parsedTransitionDurationMs,
            boxZoomEnabled = obj.optBoolean("boxZoomEnabled", false),
            boxFloatEnabled = obj.optBoolean("boxFloatEnabled", false),
            boxSwayEnabled = obj.optBoolean("boxSwayEnabled", false),
            boxGlowPulseEnabled = obj.optBoolean("boxGlowPulseEnabled", false),
            boxMotionSpeed = obj.optDouble("boxMotionSpeed", 1.0).toFloat(),
            // Clear Mode migration: an explicit key wins; an old save
            // without the key defaults by feed — Full -> verse-only,
            // Lower -> all — so old Lower templates stay Clear All as
            // today and old Full templates become Clear Verse Only.
            clearMode = if (obj.has("clearMode")) {
                try { com.arabicchristianmedia.model.ClearMode.valueOf(obj.optString("clearMode")) } catch (e: Exception) { if (parsedIsFullScreen) com.arabicchristianmedia.model.ClearMode.CLEAR_VERSE_ONLY else com.arabicchristianmedia.model.ClearMode.CLEAR_ALL }
            } else if (parsedIsFullScreen) com.arabicchristianmedia.model.ClearMode.CLEAR_VERSE_ONLY else com.arabicchristianmedia.model.ClearMode.CLEAR_ALL,
            showAccentBorder = obj.optBoolean("showAccentBorder", true),
            // One-time migration: old styles without the new keys gain text shadow
            // (on, black). Transparent styles migrate glow to OFF (no card to glow).
            // New saves always carry the explicit keys below.
            textShadowEnabled = if (obj.has("textShadowEnabled")) obj.optBoolean("textShadowEnabled", true)
                                else legacyShadow,
            textShadowColorHex = obj.optString("textShadowColorHex", "#000000"),
            textShadowBlurDp = obj.optDouble("textShadowBlurDp", 8.0).toFloat(),
            textShadowOffsetDp = obj.optDouble("textShadowOffsetDp", 4.0).toFloat(),
            textShadowAngleDeg = obj.optInt("textShadowAngleDeg", 90),
            textEffect = com.arabicchristianmedia.model.TextEffectType.fromId(obj.optString("textEffect", "none")),
            textEffectDepthDp = obj.optDouble("textEffectDepthDp", 3.0).toFloat(),
            cardGlowEnabled = if (obj.has("cardGlowEnabled")) obj.optBoolean("cardGlowEnabled", true)
                              else safeGlowDefault,
            cardGlowColorHex = obj.optString("cardGlowColorHex", "#000000"),
            cardGlowOpacity = obj.optDouble("cardGlowOpacity", 1.0).toFloat(),
            accentOpacity = obj.optDouble("accentOpacity", 1.0).toFloat(),
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
            // Item 27: per-language toggles. New keys win; otherwise migrate
            // from languageMode (ARABIC_ONLY -> Ar on/En off, etc.), then
            // legacy bilingualMode (true -> both on).
            showArabic = if (obj.has("showArabic")) obj.optBoolean("showArabic", true) else run {
                val lm = if (obj.has("languageMode")) {
                    try { com.arabicchristianmedia.model.LanguageMode.valueOf(obj.optString("languageMode")) }
                    catch (e: Exception) { com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY }
                } else if (obj.optBoolean("bilingualMode", false)) {
                    com.arabicchristianmedia.model.LanguageMode.BOTH
                } else {
                    com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY
                }
                lm != com.arabicchristianmedia.model.LanguageMode.ENGLISH_ONLY
            },
            showEnglish = if (obj.has("showEnglish")) obj.optBoolean("showEnglish", true) else run {
                val lm = if (obj.has("languageMode")) {
                    try { com.arabicchristianmedia.model.LanguageMode.valueOf(obj.optString("languageMode")) }
                    catch (e: Exception) { com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY }
                } else if (obj.optBoolean("bilingualMode", false)) {
                    com.arabicchristianmedia.model.LanguageMode.BOTH
                } else {
                    com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY
                }
                lm != com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY
            },
            isPureTransparentBackground = false, // migrated above; flag no longer a user mode
            animatedBackground = try { com.arabicchristianmedia.model.AnimatedBackgroundType.valueOf(obj.optString("animatedBackground")) } catch (e: Exception) { com.arabicchristianmedia.model.AnimatedBackgroundType.NONE },
            animatedBackgroundOpacity = obj.optDouble("animatedBackgroundOpacity", 0.65).toFloat(),
            particleOpacity = obj.optDouble("particleOpacity", 0.65).toFloat(),
            logoUri = obj.optString("logoUri", ""),
            logoPosition = obj.optString("logoPosition", "top_right"),
            logoBoxSizePercent = obj.optDouble("logoBoxSizePercent", 15.0).toFloat(),
            logoOffsetXPercent = obj.optDouble("logoOffsetXPercent", 0.0).toFloat(),
            logoOffsetYPercent = obj.optDouble("logoOffsetYPercent", 0.0).toFloat(),
            logoOpacity = obj.optDouble("logoOpacity", 1.0).toFloat(),
            customVideoUrl = obj.optString("customVideoUrl", ""),
            customVideoId = obj.optString("customVideoId", ""),
            customVideoMuted = obj.optBoolean("customVideoMuted", true),
            motionSpeed = obj.optDouble("motionSpeed", 1.0).toFloat().coerceIn(0f, 3.0f),
            videoLoopMode = VideoLoopMode.fromId(obj.optString("videoLoopMode", "fade")),
            streamBackgroundMode = try { com.arabicchristianmedia.model.StreamBackgroundMode.valueOf(obj.optString("streamBackgroundMode")) } catch (e: Exception) { com.arabicchristianmedia.model.StreamBackgroundMode.TRANSPARENT_ALPHA },
            cardPaddingTopDp = obj.optDouble("cardPaddingTopDp", 32.0).toFloat().coerceIn(0f, 200f),
            cardPaddingBottomDp = obj.optDouble("cardPaddingBottomDp", 32.0).toFloat().coerceIn(0f, 200f),
            cardPaddingLeftDp = obj.optDouble("cardPaddingLeftDp", 36.0).toFloat().coerceIn(0f, 300f),
            cardPaddingRightDp = obj.optDouble("cardPaddingRightDp", 36.0).toFloat().coerceIn(0f, 300f),
            verseCitationGapArabicDp = obj.optDouble("verseCitationGapArabicDp", 12.0).toFloat().coerceIn(0f, 100f),
            verseCitationGapEnglishDp = obj.optDouble("verseCitationGapEnglishDp", 12.0).toFloat().coerceIn(0f, 100f),
            boxLoopContinuous = obj.optBoolean("boxLoopContinuous", true),
            trueFullscreen = obj.optBoolean("trueFullscreen", false),
            autoFitText = obj.optBoolean("autoFitText", true),
            // v1.9.1: the size setting changed meaning (floor -> CAP, Ashraf's
            // model). Legacy saves carry only autoFitMinVerseSp: migrate its
            // value as the cap. New key wins when both exist.
            // Round-2 guard (Ashraf's Midnight Psalm case): a low legacy
            // FLOOR was permissive ("shrink at most down to 16"), but as a
            // CAP it is destructive ("never above 16") — a migrated cap
            // never lands below the style's own verse size. Deliberate
            // small caps remain settable afterwards in the Editor.
            autoFitMaxVerseSp = (if (obj.has("autoFitMaxVerseSp")) obj.optInt("autoFitMaxVerseSp", 48)
                                 else maxOf(obj.optInt("autoFitMinVerseSp", 48), obj.optInt("verseFontSize", 26))).coerceIn(10, 200),
            boxHeightPercent = obj.optInt("boxHeightPercent",
                if (obj.optBoolean("isFullScreen", false)) 100 else 45).coerceIn(10, 100),
            gradientEnabled = obj.optBoolean("gradientEnabled", false),
            gradientColorHex2 = obj.optString("gradientColorHex2", "#0A1128"),
            gradientAngleDeg = obj.optInt("gradientAngleDeg", 90).coerceIn(0, 360),
            gradientAnimation = GradientAnimationType.fromId(obj.optString("gradientAnimation", "none")),
            gradientSpeed = obj.optDouble("gradientSpeed", 1.0).toFloat().coerceIn(0f, 3.0f),
            autoFitMinCitationSp = obj.optInt("autoFitMinCitationSp", 10).coerceIn(10, 120),
            autoFitRatioLock = obj.optBoolean("autoFitRatioLock", true),
            lineSpacingArabic = obj.optDouble("lineSpacingArabic", 1.5).toFloat().coerceIn(1f, 3f),
            lineSpacingEnglish = obj.optDouble("lineSpacingEnglish", 1.5).toFloat().coerceIn(1f, 3f),
            autoFitFrozenRatio = if (obj.has("autoFitFrozenRatio")) obj.optDouble("autoFitFrozenRatio", 0.0).toFloat().takeIf { it > 0f } else null,
            cardGlowRadiusDp = obj.optDouble("cardGlowRadiusDp", 16.0).toFloat().coerceIn(0f, 100f),
            showAccentBorderWidthDp = obj.optDouble("showAccentBorderWidthDp", 2.0).toFloat().coerceIn(0f, 30f)
        ).let { migrateLegacyVideo(it) }.let { migrateLogo(it, obj) }
    }

    /**
     * Rec 3 (1.9.8): logo round-trip + validation on every template load.
     * - Import: if the JSON carries embedded "logoData" (Base64, added by
     *   templateToJsonString on export), decode + store it and point logoUri
     *   at the new local file, so a shared style keeps its logo.
     * - Validation: if logoUri points at a file that no longer exists
     *   (deleted, or imported from another device without logoData), clear
     *   it silently — a missing logo must never crash or block a load.
     */
    private fun migrateLogo(tpl: LowerThirdTemplate, obj: JSONObject): LowerThirdTemplate {
        var logoUri = tpl.logoUri
        try {
            val logoData = obj.optString("logoData", "")
            if (logoData.isNotEmpty()) {
                val bytes = android.util.Base64.decode(logoData, android.util.Base64.DEFAULT)
                val id = com.arabicchristianmedia.server.LogoStore(appContext).importFromBytes(bytes)
                if (id != null) logoUri = id
            }
        } catch (e: Exception) {
            android.util.Log.w("TemplateRepository", "Logo data import failed, keeping logoUri", e)
        }
        if (logoUri.isNotEmpty()) {
            try {
                if (com.arabicchristianmedia.server.LogoStore(appContext).fileFor(logoUri) == null) {
                    logoUri = ""
                }
            } catch (e: Exception) {
                logoUri = ""
            }
        }
        return if (logoUri == tpl.logoUri) tpl else tpl.copy(logoUri = logoUri)
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
