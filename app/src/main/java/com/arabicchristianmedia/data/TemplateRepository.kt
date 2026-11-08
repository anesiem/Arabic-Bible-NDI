package com.arabicchristianmedia.data

import android.content.Context
import android.content.SharedPreferences
import com.arabicchristianmedia.model.AnimatedBackgroundType
import com.arabicchristianmedia.model.BroadcastTextAlignment
import com.arabicchristianmedia.model.CardGlowMode
import com.arabicchristianmedia.model.ClearMode
import com.arabicchristianmedia.model.LanguageMode
import com.arabicchristianmedia.model.GradientAnimationType
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.TemplateStyle
import com.arabicchristianmedia.model.TextEffectType
import com.arabicchristianmedia.model.TransitionType
import com.arabicchristianmedia.model.VerticalAlignment
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
            "tpl_fs_midnight_psalm", "tpl_pure_transparent",
            // Item 94 (1.9.10): the entire 10-style 1.9.9 set is replaced
            // by the 20 new designs. Saved entries with these IDs are
            // replaced by the new DEFAULT_TEMPLATES on load.
            "tpl_fs_golden_light", "tpl_fs_midnight", "tpl_fs_desert", "tpl_fs_royal",
            "tpl_lt_classic", "tpl_lt_minimal", "tpl_lt_elegant", "tpl_lt_bold", "tpl_lt_soft"
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
                id = "tpl_fs_pure_word_v2",
                name = "Pure Word",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.0f,
                accentColorHex = "#FFFFFF",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F5D76E",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = false,
                gradientColorHex2 = "#0A1128",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_midnight_clarity",
                name = "Midnight Clarity",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#0A1128",
                bgOpacity = 1.0f,
                accentColorHex = "#60A5FA",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F5D76E",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#1E3A8A",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.SLIDE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_golden_light_v2",
                name = "Golden Light",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#78350F",
                bgOpacity = 1.0f,
                accentColorHex = "#FBBF24",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                cardGlowColorHex = "#FBBF24",
                cardGlowOpacity = 0.6f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#B45309",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_desert_dawn",
                name = "Desert Dawn",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#7C2D12",
                bgOpacity = 1.0f,
                accentColorHex = "#FB923C",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                cardGlowColorHex = "#FB923C",
                cardGlowOpacity = 0.6f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#9A3412",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.SLIDE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_royal_proclamation",
                name = "Royal Proclamation",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#1E1B4B",
                bgOpacity = 1.0f,
                accentColorHex = "#FBBF24",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F5D76E",
                verseFontSize = 50,
                referenceFontSize = 30,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 41,
                secondaryReferenceFontSize = 24,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                cardGlowColorHex = "#FBBF24",
                cardGlowOpacity = 0.8f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                particleEffect = AnimatedBackgroundType.GOLDEN_PARTICLES,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.5f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 62,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#312E81",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_emerald_cathedral",
                name = "Emerald Cathedral",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#022C22",
                bgOpacity = 1.0f,
                accentColorHex = "#34D399",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#065F46",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_crimson_worship",
                name = "Crimson Worship",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#450A0A",
                bgOpacity = 1.0f,
                accentColorHex = "#F87171",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                cardGlowColorHex = "#F87171",
                cardGlowOpacity = 0.6f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#7F1D1D",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_ocean_depth",
                name = "Ocean Depth",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#0C4A6E",
                bgOpacity = 1.0f,
                accentColorHex = "#38BDF8",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#A5F3FC",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#0369A1",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_violet_dawn",
                name = "Violet Dawn",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#2E1065",
                bgOpacity = 1.0f,
                accentColorHex = "#C084FC",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F5D76E",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = true,
                gradientColorHex2 = "#6B21A8",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_fs_soft_ivory",
                name = "Soft Ivory",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#FEF3C7",
                bgOpacity = 1.0f,
                accentColorHex = "#B45309",
                textColorHex = "#1C1917",
                referenceColorHex = "#92400E",
                verseFontSize = 48,
                referenceFontSize = 28,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#334155",
                secondaryReferenceColorHex = "#64748B",
                secondaryVerseFontSize = 39,
                secondaryReferenceFontSize = 22,
                secondaryFontFamily = "System",
                isFullScreen = true,
                verticalAlignment = VerticalAlignment.TOP,
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
                horizontalMarginPercent = 0,
                cornerRadiusDp = 0,
                transition = TransitionType.FADE,
                transitionDurationMs = 550,
                boxTransition = TransitionType.FADE,
                boxTransitionDurationMs = 550,
                boxZoomEnabled = false,
                boxFloatEnabled = false,
                boxSwayEnabled = false,
                boxGlowPulseEnabled = false,
                boxMotionSpeed = 1.0f,
                clearMode = ClearMode.CLEAR_VERSE_ONLY,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                cardPaddingTopDp = 0.0f,
                cardPaddingBottomDp = 0.0f,
                cardPaddingLeftDp = 0.0f,
                cardPaddingRightDp = 0.0f,
                verseCitationGapArabicDp = 12.0f,
                verseCitationGapEnglishDp = 12.0f,
                boxLoopContinuous = true,
                trueFullscreen = true,
                autoFitText = true,
                autoFitMaxVerseSp = 60,
                boxHeightPercent = 100,
                gradientEnabled = false,
                gradientColorHex2 = "#FEF3C7",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_pure_word_v2",
                name = "Pure Word",
                style = TemplateStyle.TRANSPARENT_OUTLINE,
                bgColorHex = "#000000",
                bgOpacity = 0.0f,
                accentColorHex = "#FFFFFF",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F5D76E",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_midnight_navy",
                name = "Midnight Navy",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#0A1128",
                bgOpacity = 1.0f,
                accentColorHex = "#38BDF8",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F5D76E",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#1B3A5C",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_emerald_sanctuary",
                name = "Emerald Sanctuary",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#064E3B",
                bgOpacity = 1.0f,
                accentColorHex = "#34D399",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#065F46",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_royal_purple",
                name = "Royal Purple",
                style = TemplateStyle.ROYAL_LITURGICAL,
                bgColorHex = "#2E1065",
                bgOpacity = 1.0f,
                accentColorHex = "#A78BFA",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#F5D76E",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#4C1D95",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_crimson_dawn_v2",
                name = "Crimson Dawn",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#7F1D1D",
                bgOpacity = 1.0f,
                accentColorHex = "#F87171",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#991B1B",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_ocean_teal",
                name = "Ocean Teal",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#134E4A",
                bgOpacity = 1.0f,
                accentColorHex = "#2DD4BF",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#A5F3FC",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#0F766E",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_golden_desert",
                name = "Golden Desert",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#78350F",
                bgOpacity = 1.0f,
                accentColorHex = "#FBBF24",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                cardGlowEnabled = true,
                cardGlowColorHex = "#FBBF24",
                cardGlowOpacity = 0.6f,
                cardGlowRadiusDp = 16f,
                emblem = "✝",
                bilingualMode = false,
                showArabic = true,
                showEnglish = true,
                isPureTransparentBackground = false,
                animatedBackground = AnimatedBackgroundType.NONE,
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#92400E",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_rose_garden",
                name = "Rose Garden",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#881337",
                bgOpacity = 1.0f,
                accentColorHex = "#FB7185",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FECDD3",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#9F1239",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_forest_pine",
                name = "Forest Pine",
                style = TemplateStyle.MODERN_GLASS,
                bgColorHex = "#14532D",
                bgOpacity = 1.0f,
                accentColorHex = "#4ADE80",
                textColorHex = "#FFFFFF",
                referenceColorHex = "#FDE9A8",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#CBD5E1",
                secondaryReferenceColorHex = "#94A3B8",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientEnabled = true,
                gradientColorHex2 = "#166534",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.FLOW,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            ),
            LowerThirdTemplate(
                id = "tpl_lt_slate_sky",
                name = "Slate Sky",
                style = TemplateStyle.TWO_LINE_SPLIT,
                bgColorHex = "#E2E8F0",
                bgOpacity = 1.0f,
                accentColorHex = "#1D4ED8",
                textColorHex = "#0F172A",
                referenceColorHex = "#92400E",
                verseFontSize = 32,
                referenceFontSize = 20,
                fontFamily = "Amiri",
                secondaryTextColorHex = "#334155",
                secondaryReferenceColorHex = "#64748B",
                secondaryVerseFontSize = 23,
                secondaryReferenceFontSize = 14,
                secondaryFontFamily = "System",
                isFullScreen = false,
                verticalAlignment = VerticalAlignment.BOTTOM,
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
                particleEffect = AnimatedBackgroundType.NONE,
                animatedBackgroundOpacity = 0.65f,
                particleOpacity = 0.65f,
                particleDensity = 1.0f,
                particleColorHex = "",
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
                gradientColorHex2 = "#E2E8F0",
                gradientAngleDeg = 90,
                gradientAnimation = GradientAnimationType.NONE,
                gradientSpeed = 1.0f,
                autoFitMinCitationSp = 10,
                autoFitRatioLockArabic = true,
                autoFitRatioLockEnglish = true,
                lineSpacingArabic = 1.8f,
                lineSpacingEnglish = 1.6f,
                autoFitFrozenRatio = null
            )
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
     * Legacy installs that referenced a deleted shipped style fall back
     * to Pure Word (Lower); a stored null is preserved so a cleared
     * highlight is never resurrected after restart.
     */
    fun getActiveTemplateId(): String? {
        val id = prefs.getString("active_template_id", null)
        // A stored highlight from a previous shipped set maps to the new
        // designs. Unknown IDs (user styles) pass through unchanged.
        if (id != null && id in LEGACY_FACTORY_IDS) {
            return "tpl_lt_pure_word_v2"
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
            return "tpl_fs_pure_word_v2"
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

    /**
     * Item 82 (1.9.9): does a working template exist on disk? Lets callers
     * tell "never saved" (normal on a fresh install) apart from "saved but
     * the load failed" (the 2026-11-05 crash-restart failure mode) so the
     * latter gets logged instead of silently falling back to factory.
     */
    fun hasWorkingTemplate(isFullScreen: Boolean): Boolean {
        val key = if (isFullScreen) "working_show_template" else "working_lower_template"
        return prefs.contains(key)
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
            val knownKeys = listOf("fontFamily", "bgColorHex", "textColorHex", "verseFontSize", "style", "showCrossEmblem", "emblem", "autoFitMinVerseSp", "autoFitMaxVerseSp", "boxHeightPercent", "isFullScreen", "id", "useSystemFontForBrackets", "gradientColorHex2", "gradientAngleDeg", "gradientAnimation", "autoFitMinCitationSp", "autoFitRatioLockArabic", "autoFitRatioLockEnglish", "autoFitRatioLock", "clearMode", "lineSpacingArabic", "lineSpacingEnglish", "autoFitFrozenRatio", "verseCitationGapArabicDp", "verseCitationGapEnglishDp", "boxLoopContinuous", "trueFullscreen", "citationAlignmentArabic", "citationAlignmentEnglish", "verseAlignmentEnglish", "verticalAlignment", "verticalAlignmentArabic", "verticalAlignmentEnglish", "textEffect", "textEffectDepthDp", "cardGlowRadiusDp", "showAccentBorderWidthDp", "cardGlowOpacity", "cardGlowMode", "accentOpacity", "showArabic", "showEnglish", "particleOpacity", "particleEffect", "particleDensity", "particleColorHex", "logoUri", "logoPosition", "logoBoxSizePercent", "logoOffsetXPercent", "logoOffsetYPercent", "logoOpacity", "logoData", "videoEnabled", "particlesEnabled", "cardEnabled", "logoEnabled")
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
            // 1.9.16: ONE vertical alignment for the whole verse block
            // (the two per-language keys are legacy; fromJson still
            // reads them for migration).
            put("verticalAlignment", t.verticalAlignment.name)
            // 1.9.15 items 10 + 13: per-layer on/off flags (see model).
            put("videoEnabled", t.videoEnabled)
            put("particlesEnabled", t.particlesEnabled)
            put("cardEnabled", t.cardEnabled)
            put("logoEnabled", t.logoEnabled)
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
            put("cardGlowMode", t.cardGlowMode.name)
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
            put("particleEffect", t.particleEffect.name)
            put("particleDensity", t.particleDensity.toDouble())
            put("particleColorHex", t.particleColorHex)
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
            // 1.9.16 Part 3: two per-language ratio locks (the single
            // key is legacy; fromJson still reads it for migration).
            put("autoFitRatioLockArabic", t.autoFitRatioLockArabic)
            put("autoFitRatioLockEnglish", t.autoFitRatioLockEnglish)
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
        // Item 75 migration: particles used to live in animatedBackground.
        // A saved particle type there moves to particleEffect (and the
        // video field becomes NONE); a saved particleEffect wins over
        // the migrated value; a saved video type stays as-is.
        val parsedAnimBg = try { AnimatedBackgroundType.valueOf(obj.optString("animatedBackground")) }
            catch (e: Exception) { AnimatedBackgroundType.NONE }
        val parsedParticleEffect = try { AnimatedBackgroundType.valueOf(obj.optString("particleEffect")) }
            catch (e: Exception) { AnimatedBackgroundType.NONE }
        val migratedAnimBg = if (com.arabicchristianmedia.model.isParticleType(parsedAnimBg)) AnimatedBackgroundType.NONE else parsedAnimBg
        val migratedParticleEffect = when {
            parsedParticleEffect != AnimatedBackgroundType.NONE -> parsedParticleEffect
            com.arabicchristianmedia.model.isParticleType(parsedAnimBg) -> parsedAnimBg
            else -> AnimatedBackgroundType.NONE
        }
        // 1.9.15 items 10 + 13 migration: the four layer flags derive from
        // the parsed values, so old saves keep exactly today's visible
        // behavior (a layer that renders today loads enabled; a
        // transparent/NONE one loads disabled). bgOpacity is the migrated
        // value (0f when the legacy pure flag is set), and
        // isPureTransparentBackground is always false after migration, so
        // the card term reduces to bgOpacity > 0f.
        val parsedBgOpacity = if (obj.optBoolean("isPureTransparentBackground", false)) 0f
            else obj.optDouble("bgOpacity", 0.85).toFloat()
        val parsedGradientEnabled = obj.optBoolean("gradientEnabled", false)
        val parsedCardGlowEnabled = if (obj.has("cardGlowEnabled")) obj.optBoolean("cardGlowEnabled", true)
            else safeGlowDefault
        val parsedShowAccentBorder = obj.optBoolean("showAccentBorder", true)
        val migratedCardEnabled = (parsedBgOpacity > 0f) || parsedGradientEnabled || parsedCardGlowEnabled || parsedShowAccentBorder
        val parsedLogoUri = obj.optString("logoUri", "")
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
            bgOpacity = parsedBgOpacity,
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
            // 1.9.17: glow mode — old templates without the key default to OUTLINE.
            cardGlowMode = try { CardGlowMode.valueOf(obj.optString("cardGlowMode")) } catch (e: Exception) { CardGlowMode.OUTLINE },
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
            animatedBackground = migratedAnimBg,
            animatedBackgroundOpacity = obj.optDouble("animatedBackgroundOpacity", 0.65).toFloat(),
            particleOpacity = obj.optDouble("particleOpacity", 0.65).toFloat(),
            particleEffect = migratedParticleEffect,
            particleDensity = obj.optDouble("particleDensity", 1.0).toFloat(),
            particleColorHex = obj.optString("particleColorHex", ""),
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
            cardPaddingTopDp = obj.optDouble("cardPaddingTopDp", 32.0).toFloat().coerceIn(0f, 300f),
            cardPaddingBottomDp = obj.optDouble("cardPaddingBottomDp", 32.0).toFloat().coerceIn(0f, 300f),
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
            // 1.9.16 Part 3 migration: a new key wins; an old save
            // with the single key gives both locks its value; no key
            // at all -> both on (the old default).
            autoFitRatioLockArabic = if (obj.has("autoFitRatioLockArabic")) obj.optBoolean("autoFitRatioLockArabic", true) else obj.optBoolean("autoFitRatioLock", true),
            autoFitRatioLockEnglish = if (obj.has("autoFitRatioLockEnglish")) obj.optBoolean("autoFitRatioLockEnglish", true) else obj.optBoolean("autoFitRatioLock", true),
            lineSpacingArabic = obj.optDouble("lineSpacingArabic", 1.5).toFloat().coerceIn(1f, 5f),
            lineSpacingEnglish = obj.optDouble("lineSpacingEnglish", 1.5).toFloat().coerceIn(1f, 5f),
            autoFitFrozenRatio = if (obj.has("autoFitFrozenRatio")) obj.optDouble("autoFitFrozenRatio", 0.0).toFloat().takeIf { it > 0f } else null,
            cardGlowRadiusDp = obj.optDouble("cardGlowRadiusDp", 16.0).toFloat().coerceIn(0f, 100f),
            showAccentBorderWidthDp = obj.optDouble("showAccentBorderWidthDp", 2.0).toFloat().coerceIn(0f, 30f),
            // 1.9.16 migration: the new single key wins. Old saves
            // carry the two per-language keys — resolve them with the
            // old collapse rule (both equal -> that; Arabic CENTER ->
            // English; else Arabic). No key at all -> default by feed
            // (Full -> TOP, Lower -> BOTTOM), so no existing template
            // changes how it renders.
            verticalAlignment = if (obj.has("verticalAlignment")) {
                try { com.arabicchristianmedia.model.VerticalAlignment.valueOf(obj.optString("verticalAlignment")) } catch (e: Exception) { if (parsedIsFullScreen) com.arabicchristianmedia.model.VerticalAlignment.TOP else com.arabicchristianmedia.model.VerticalAlignment.BOTTOM }
            } else if (obj.has("verticalAlignmentArabic") || obj.has("verticalAlignmentEnglish")) {
                val ar = if (obj.has("verticalAlignmentArabic")) {
                    try { com.arabicchristianmedia.model.VerticalAlignment.valueOf(obj.optString("verticalAlignmentArabic")) } catch (e: Exception) { if (parsedIsFullScreen) com.arabicchristianmedia.model.VerticalAlignment.TOP else com.arabicchristianmedia.model.VerticalAlignment.BOTTOM }
                } else if (parsedIsFullScreen) com.arabicchristianmedia.model.VerticalAlignment.TOP else com.arabicchristianmedia.model.VerticalAlignment.BOTTOM
                val en = if (obj.has("verticalAlignmentEnglish")) {
                    try { com.arabicchristianmedia.model.VerticalAlignment.valueOf(obj.optString("verticalAlignmentEnglish")) } catch (e: Exception) { if (parsedIsFullScreen) com.arabicchristianmedia.model.VerticalAlignment.TOP else com.arabicchristianmedia.model.VerticalAlignment.BOTTOM }
                } else if (parsedIsFullScreen) com.arabicchristianmedia.model.VerticalAlignment.TOP else com.arabicchristianmedia.model.VerticalAlignment.BOTTOM
                when {
                    ar == en -> ar
                    ar == com.arabicchristianmedia.model.VerticalAlignment.CENTER -> en
                    else -> ar
                }
            } else if (parsedIsFullScreen) com.arabicchristianmedia.model.VerticalAlignment.TOP else com.arabicchristianmedia.model.VerticalAlignment.BOTTOM,
            // 1.9.15 items 10 + 13: layer flags. An explicit key wins;
            // old saves derive from the parsed value (see the migration
            // vals above), so no live template changes how it renders.
            videoEnabled = obj.optBoolean("videoEnabled", migratedAnimBg != AnimatedBackgroundType.NONE),
            particlesEnabled = obj.optBoolean("particlesEnabled", migratedParticleEffect != AnimatedBackgroundType.NONE),
            cardEnabled = obj.optBoolean("cardEnabled", migratedCardEnabled),
            logoEnabled = obj.optBoolean("logoEnabled", parsedLogoUri.isNotEmpty())
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
