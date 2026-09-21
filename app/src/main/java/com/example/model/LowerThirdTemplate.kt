package com.example.model

enum class TemplateStyle(val displayName: String, val description: String) {
    MODERN_GLASS("Modern Glass", "Frosted glass card with vibrant accent border and clean typography"),
    CLASSIC_BANNER("Classic Broadcast", "Full-width traditional television lower third with split reference"),
    MINIMALIST_PILL("Minimalist Floating", "Compact floating rounded pill, ideal for sermon streaming"),
    TWO_LINE_SPLIT("Split Line Strip", "Streamlined two-tier layout with citation accent pill"),
    TRANSPARENT_OUTLINE("Pure Alpha Outline", "100% transparent background with bold outlined text for camera video"),
    ROYAL_LITURGICAL("Cathedral Liturgical", "Deep liturgical colors with gold frame and cross accent")
}

enum class TransitionType(val displayName: String) {
    FADE("Smooth Fade"),
    SLIDE_UP("Slide From Bottom"),
    SLIDE_RIGHT("Slide From Right (RTL)"),
    CUT("Instant Cut")
}

enum class BroadcastTextAlignment(val displayName: String) {
    RIGHT("Right (RTL Standard)"),
    CENTER("Centered"),
    LEFT("Left (LTR)")
}

enum class AnimatedBackgroundType(val id: String, val displayNameAr: String, val description: String) {
    NONE("none", "بدون فيديو (شفاف / عادي)", "No animated video"),
    GOLDEN_DIVINE_RAYS("golden_rays", "أشعة ذهبية سماوية (Golden Rays)", "Gentle moving golden celestial rays"),
    ETHEREAL_BLUE_WAVES("blue_waves", "أمواج زرقاء لاهوتية (Blue Waves)", "Soft ethereal deep blue undulating wave ribbons"),
    CANDLE_LITURGICAL_GLOW("candle_glow", "توهج قناديل كنسية (Candle Glow)", "Warm breathing cathedral liturgical candle ambience"),
    ROYAL_PURPLE_SILK("purple_silk", "حرير ملوكي بنفسجي (Purple Silk)", "Graceful royal liturgical violet curtains"),
    PARTICLE_STARS("particles", "ذرات نورانية عائمة (Holy Particles)", "Slowly floating stardust holy particles"),
    CUSTOM_VIDEO("custom_video", "رابط فيديو مخصص (Custom MP4)", "Custom transparent/looping video URL")
}

enum class StreamBackgroundMode(val displayNameAr: String, val colorHex: String) {
    TRANSPARENT_ALPHA("خلفية شفافة تماماً (100% Alpha Transparency)", "#00000000"),
    CHROMA_GREEN("خلفية خضراء للتفريغ (Chroma Green)", "#00FF00"),
    PURE_BLACK("خلفية سوداء مفرغة (Luma Black)", "#000000"),
    DARK_STUDIO("استوديو كنسي داكن (Dark Studio)", "#0A1128")
}

data class LowerThirdTemplate(
    val id: String,
    val name: String,
    val style: TemplateStyle = TemplateStyle.TRANSPARENT_OUTLINE,
    val bgColorHex: String = "#000000",
    val bgOpacity: Float = 0.0f,
    val accentColorHex: String = "#F59E0B",
    val textColorHex: String = "#FFFFFF",
    val referenceColorHex: String = "#FBBF24",
    val verseFontSize: Int = 30,
    val referenceFontSize: Int = 21,
    val fontFamily: String = "Amiri",
    val secondaryTextColorHex: String = "#CBD5E1",
    val secondaryReferenceColorHex: String = "#94A3B8",
    val secondaryVerseFontSize: Int = 18,
    val secondaryReferenceFontSize: Int = 14,
    val secondaryFontFamily: String = "System",
    val isFullScreen: Boolean = false,
    val bilingualSpacing: Int = 20,
    val showBilingualSpacing: Boolean = true,
    val verseIsBold: Boolean = true,
    val verseIsItalic: Boolean = false,
    val referenceIsBold: Boolean = true,
    val referenceIsItalic: Boolean = false,
    val secondaryVerseIsBold: Boolean = false,
    val secondaryVerseIsItalic: Boolean = false,
    val secondaryReferenceIsBold: Boolean = true,
    val secondaryReferenceIsItalic: Boolean = true,
    val useEasternArabicNumerals: Boolean = true,
    val useArabicPunctuation: Boolean = true,
    val alignment: BroadcastTextAlignment = BroadcastTextAlignment.RIGHT,
    val positionBottomPercent: Int = 8,
    val horizontalMarginPercent: Int = 5,
    val cornerRadiusDp: Int = 12,
    val transition: TransitionType = TransitionType.SLIDE_UP,
    val transitionDurationMs: Int = 400,
    val showAccentBorder: Boolean = false,
    val showDropShadow: Boolean = true,
    val showCrossEmblem: Boolean = true,
    val bilingualMode: Boolean = false,
    val isPureTransparentBackground: Boolean = true,
    val animatedBackground: AnimatedBackgroundType = AnimatedBackgroundType.NONE,
    val animatedBackgroundOpacity: Float = 0.65f,
    val customVideoUrl: String = "",
    val streamBackgroundMode: StreamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
)
