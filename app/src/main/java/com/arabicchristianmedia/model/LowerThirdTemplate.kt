package com.arabicchristianmedia.model

import kotlin.math.roundToInt

enum class TemplateStyle(val displayName: String, val description: String) {
    MODERN_GLASS("Modern Glass", "Frosted glass card with vibrant accent border and clean typography"),
    CLASSIC_BANNER("Classic Broadcast", "Full-width traditional television lower third with split reference"),
    MINIMALIST_PILL("Minimalist Floating", "Compact floating rounded pill, ideal for sermon streaming"),
    TWO_LINE_SPLIT("Split Line Strip", "Streamlined two-tier layout with citation accent pill"),
    TRANSPARENT_OUTLINE("Pure Alpha Outline", "100% transparent background with bold outlined text for camera video"),
    ROYAL_LITURGICAL("Cathedral Liturgical", "Deep liturgical colors with gold frame and cross accent")
}

enum class TransitionType(val displayName: String) {
    // Split transitions (combined batch): NONE = instant, no animation.
    // It is a no-op in every existing transition when-branch, so adding it
    // cannot change any saved template's behaviour.
    NONE("None"),
    FADE("Smooth Fade"),
    SLIDE_UP("Slide From Bottom"),
    SLIDE_DOWN("Slide From Top"),
    SLIDE_RIGHT("Slide From Right"),
    SLIDE_LEFT("Slide From Left"),
    SCALE_ZOOM("Zoom In / Scale"),
    BOUNCE("Pop / Elastic Bounce"),
    CUT("Instant Cut")
}

enum class BroadcastTextAlignment(val displayName: String) {
    RIGHT("Right (RTL Standard)"),
    CENTER("Centered"),
    LEFT("Left (LTR)")
}

/**
 * v1.8: three-way language control replacing the [LowerThirdTemplate.bilingualMode]
 * boolean. ARABIC_ONLY shows Arabic verse+citation; ENGLISH_ONLY shows English;
 * BOTH shows Arabic followed by English (with [LowerThirdTemplate.bilingualSpacing]
 * between them when [LowerThirdTemplate.showBilingualSpacing] is on).
 */
/**
 * Clear Mode (2026-10-28 hotfix, Ashraf: needed for tonight's service).
 * What OFF AIR / Clear does to a feed:
 * - CLEAR_ALL: today's behaviour — verse AND every layer below it go
 *   away (NDI: 100% transparent frame; HTTP: container hidden).
 * - CLEAR_VERSE_ONLY: verse text, citation and emblem are blanked,
 *   but card colour/gradient, motion video, glow, accent border,
 *   padding, position and size stay exactly as when live. Motion
 *   keeps playing (it is not frozen or paused).
 * Per template, per feed: Lower and Full are edited separately, so
 * each tab carries its own value. Defaults by feed: Full Show
 * defaults to CLEAR_VERSE_ONLY, Lower Third to CLEAR_ALL. Old
 * templates without the key migrate to those defaults in fromJson.
 */
enum class ClearMode(val displayName: String) {
    CLEAR_ALL("Clear all layers"),
    CLEAR_VERSE_ONLY("Clear verse only")
}

/** Pure off-air frame decision, unit-testable without Android.
 * LIVE: render normally. CLEAR_ALL_TRANSPARENT: blank every output
 * (NDI transparent / HTTP hidden). CLEAR_VERSE_BACKGROUND: render
 * layers without any text. The clear-transition window is handled
 * by callers BEFORE this decision (the verse still animates out
 * first); this decides only the steady off-air state. hasVerse is
 * false on a fresh launch with no verse ever selected — that stays
 * transparent/hidden even in verse-only mode (nothing was live). */
object ClearModeDecision {
    enum class OffAirFrame { LIVE, CLEAR_ALL_TRANSPARENT, CLEAR_VERSE_BACKGROUND }
    fun decide(isLive: Boolean, clearMode: ClearMode, hasVerse: Boolean): OffAirFrame {
        if (isLive) return OffAirFrame.LIVE
        if (!hasVerse) return OffAirFrame.CLEAR_ALL_TRANSPARENT
        return if (clearMode == ClearMode.CLEAR_VERSE_ONLY) OffAirFrame.CLEAR_VERSE_BACKGROUND
               else OffAirFrame.CLEAR_ALL_TRANSPARENT
    }
}

enum class LanguageMode(val displayName: String) {
    ARABIC_ONLY("Arabic only"),
    ENGLISH_ONLY("English only"),
    BOTH("Arabic + English")
}

/**
 * v2.0: How a custom background video loops.
 * CUT = hard loop (visible cut); FADE = fade through black at the loop point.
 */
/**
 * How a custom background video plays. FADE/CUT are the existing
 * continuous loops (unchanged ids, so saved templates keep working).
 * PLAY_ONCE (combined batch, 2026-10-28) is NOT a loop: when a verse
 * goes live the video plays once from the start; when it ends, the
 * WHOLE video layer turns off (not frozen on the last frame), card +
 * text remain alone. It replays once on the next new verse going
 * live, and stays off while the same verse stays live and when
 * OFF AIR / the verse is cleared. Applies to video files only:
 * procedural animated backgrounds have no clip end, so Play Once
 * does not apply to them (no invented duration).
 */
enum class VideoLoopMode(val id: String, val displayName: String) {
    CUT("cut", "Cut"),
    FADE("fade", "Crossfade"),
    // Legacy single Play Once (combined batch, unpushed): kept only
    // as a saved-value alias for Per Verse, never offered in UI.
    PLAY_ONCE("once", "Play once then off"),
    /** Play once on EVERY new verse going live, then layer off. */
    PLAY_ONCE_PER_VERSE("once_verse", "Play once per verse"),
    /** Play once only on the first verse after OFF AIR -> ON AIR,
     * then layer off through verse changes until OFF AIR again. */
    PLAY_ONCE_PER_GOLIVE("once_golive", "Play once per go live");
    companion object {
        fun fromId(id: String): VideoLoopMode = when (id) {
            "once", "play_once", "playOnce" -> PLAY_ONCE_PER_VERSE
            else -> values().find { it.id == id } ?: FADE
        }
    }
    fun isPlayOnce(): Boolean = this == PLAY_ONCE || this == PLAY_ONCE_PER_VERSE || this == PLAY_ONCE_PER_GOLIVE
}

/** Pure state machine for Play Once, unit-testable without Android.
 * States: IDLE (layer off), PLAYING, FINISHED (layer off, not last
 * frame). Transitions: startVerse -> PLAYING (from any state, on a
 * NEW verse only); onEnded -> FINISHED; clear/offAir -> IDLE.
 * isLayerVisible is true only while PLAYING. A re-apply for the
 * SAME verse must not call startVerse again — callers key on verse
 * identity, this class does not guess it. */
object PlayOnceState {
    enum class State { IDLE, PLAYING, FINISHED }
    fun startVerse(@Suppress("UNUSED_PARAMETER") state: State): State = State.PLAYING
    /** Per Go Live: only start when not already played this live
     * session; caller tracks the session, this is the pure guard:
     * starting from FINISHED in the same session is a no-op. */
    fun startGoLive(state: State): State = if (state == State.FINISHED) state else State.PLAYING
    fun onEnded(state: State): State = if (state == State.PLAYING) State.FINISHED else state
    fun clear(@Suppress("UNUSED_PARAMETER") state: State): State = State.IDLE
    fun isLayerVisible(state: State): Boolean = state == State.PLAYING
}

enum class AnimatedBackgroundType(val id: String, val displayNameAr: String, val description: String) {
    NONE("none", "بدون فيديو (شفاف / عادي)", "No animated video"),
    GOLDEN_DIVINE_RAYS("golden_rays", "أشعة ذهبية سماوية (Golden Rays)", "Gentle moving golden celestial rays"),
    ETHEREAL_BLUE_WAVES("blue_waves", "أمواج زرقاء لاهوتية (Blue Waves)", "Soft ethereal deep blue undulating wave ribbons"),
    CANDLE_LITURGICAL_GLOW("candle_glow", "توهج قناديل كنسية (Candle Glow)", "Warm breathing cathedral liturgical candle ambience"),
    ROYAL_PURPLE_SILK("purple_silk", "حرير ملوكي بنفسجي (Purple Silk)", "Graceful royal liturgical violet curtains"),
    PARTICLE_STARS("particles", "ذرات نورانية عائمة (Holy Particles)", "Slowly floating stardust holy particles"),
    EMERALD_GARDEN_WAVES("emerald_waves", "أمواج زمردية (Emerald Waves)", "Soft green flowing garden waves"),
    ROSE_DAWN_GLOW("rose_glow", "توهج وردي فجري (Rose Dawn)", "Gentle pink rose dawn glow"),
    GOLDEN_PARTICLES("gold_particles", "ذرات ذهبية (Golden Particles)", "Floating golden light particles"),
    CUSTOM_VIDEO("custom_video", "رابط فيديو مخصص (Custom MP4)", "Custom transparent/looping video URL")
}

/**
 * Design-gradient card animation (2026-10-28 batch): the user chooses
 * two card colors and an angle, and the gradient itself moves. NONE is
 * a static gradient; SLIDE sweeps right-to-left; ROTATE turns the angle
 * slowly; PULSE breathes the card opacity. No video decode is involved.
 * CSS angle convention (shared with NDI): 0 = bottom-to-top,
 * 90 = left-to-right, 180 = top-to-bottom, 270 = right-to-left.
 */
enum class GradientAnimationType(val id: String) {
    NONE("none"),
    SLIDE("slide"),
    ROTATE("rotate"),
    PULSE("pulse"),
    // Combined batch, 2026-10-28: three new card-color motions.
    // Existing ids/behaviour are untouched, so saved templates keep
    // rendering exactly as before. All three are sin-periodic, so
    // the loop is seamless by construction (see GradientMotion).
    FLOW("flow"),
    SHIMMER("shimmer"),
    WAVE("wave");
    companion object {
        fun fromId(id: String): GradientAnimationType =
            values().find { it.id == id } ?: NONE
    }
}

/**
 * Pure, testable math for the new gradient motions.
 *
 * Loop guarantee: every effect is a function of phase = 2*PI * speed
 * * timeSec, with NO modulo wrap on time (NDI uses absolute
 * System.currentTimeMillis, HTTP uses Date.now). sin/cos are
 * 2*PI-periodic, so value(t) == value(t + 1/speed) exactly (up to
 * float rounding) for any speed > 0. There is no first-frame /
 * last-frame mismatch, unlike a recorded video loop: the gradient
 * is generated from the phase, it never has to cut or fade back.
 * Speed 0 pauses (phase frozen, value constant) — same convention
 * as the old effects.
 *
 * FLOW: bright band sweeps left-to-right then back: offset =
 * sin(phase) in [-1, 1], multiplied by 0.35 * extent in the
 * renderer. At phase 0 and 2*PI the offset is 0, at PI/2 it is
 * full right, at 3*PI/2 full left — a smooth return, never a jump.
 * SHIMMER: brightness multiplier 0.88 + 0.12*sin(phase), i.e.
 * 0.76..1.00, subtle enough that verse text stays readable.
 * WAVE: angle = baseAngle + 20*sin(phase) degrees. The +/-20 deg
 * swing never wraps past 360, so no angle discontinuity.
 */
object GradientMotion {
    fun phase(timeSec: Double, speed: Float): Double =
        2.0 * Math.PI * speed.toDouble() * timeSec
    fun flowOffset(phase: Double): Float = kotlin.math.sin(phase).toFloat()
    fun shimmerMultiplier(phase: Double): Float =
        (0.88 + 0.12 * kotlin.math.sin(phase)).toFloat()
    fun waveAngle(baseDeg: Float, phase: Double): Float =
        baseDeg + 20f * kotlin.math.sin(phase).toFloat()
}

/** Pure = card opacity 0, derived, not a user toggle any more.
 * The legacy [LowerThirdTemplate.isPureTransparentBackground] flag
 * is still read on import for migration, but renderers must use
 * this: opacity 0 OR the migrated flag, so a manually-zeroed
 * slider and an old pure template behave identically. */
val LowerThirdTemplate.isPureDerived: Boolean
    get() = bgOpacity <= 0f || isPureTransparentBackground

/**
 * Continuous box-effect math (combined batch, II Card). Pure functions
 * so they are unit-testable without Android: every effect is a sin/cos
 * function of one phase, so first frame == last frame exactly at the
 * period boundary (period = 1 / boxMotionSpeed seconds) — no reset
 * jump, no fade dip, unlike recorded video loops.
 * - Zoom: 1.0 -> 1.05 -> 1.0 via (1 - cos(phase)) / 2, so phase 0 is
 *   exactly neutral (scale 1), unlike a raw sin which starts mid-swing.
 * - Float: translateY = +/-8dp * sin(phase); Sway: translateX = +/-8dp
 *   * sin(phase); both start at 0 (neutral) at phase 0.
 * - Glow Pulse: shadow intensity factor 0.7..1.3 * sin(phase); no scale,
 *   no position change, so it never moves text.
 * - ramp: 0 while the verse/box transition is still running, then a
 *   smoothstep (f*f*(3-2f)) over 600 ms to 1, so effects fade in
 *   instead of snapping on at the transition's end.
 * The same math runs on NDI (Canvas translate/scale around the box
 * centre, shadow-alpha factor) and HTTP (JS transform on cardBox,
 * box-shadow alpha), with the same speed and amplitudes.
 */
object BoxMotion {
    fun phase(speed: Float, elapsedSec: Float): Float =
        (2f * Math.PI.toFloat()) * speed * elapsedSec
    fun zoomScale(phase: Float, ramp: Float): Float =
        1f + 0.05f * (1f - kotlin.math.cos(phase)) / 2f * ramp
    fun floatDp(phase: Float, ramp: Float): Float =
        8f * kotlin.math.sin(phase) * ramp
    fun swayDp(phase: Float, ramp: Float): Float =
        8f * kotlin.math.sin(phase) * ramp
    fun glowFactor(phase: Float, ramp: Float): Float =
        1f + 0.3f * kotlin.math.sin(phase) * ramp
    fun ramp(elapsedMs: Long, transitionEndMs: Long): Float {
        if (elapsedMs <= transitionEndMs) return 0f
        val f = ((elapsedMs - transitionEndMs) / 600f).coerceIn(0f, 1f)
        return f * f * (3f - 2f * f)
    }
    /** 0..1 progress of a box transition, or null when it is instant. */
    fun boxProgress(elapsedMs: Long, durationMs: Int): Float? {
        if (durationMs <= 0) return null
        return (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

enum class StreamBackgroundMode(val displayNameAr: String, val colorHex: String) {
    TRANSPARENT_ALPHA("خلفية شفافة تماماً (100% Alpha — تظهر خضراء في بث MJPEG)", "#00000000"),
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
    val useSystemFontForBrackets: Boolean = true,
    val alignment: BroadcastTextAlignment = BroadcastTextAlignment.RIGHT,
    val positionBottomPercent: Int = 8,
    val horizontalMarginPercent: Int = 5,
    val cornerRadiusDp: Int = 12,
    val transition: TransitionType = TransitionType.SLIDE_UP,
    val transitionDurationMs: Int = 400,
    // Split transitions (combined batch): the verse transition above
    // animates only verse text + citation; the box transition animates
    // only the card box itself. Each has its own type + duration and
    // each can be NONE, so verse-only, box-only, or both are possible.
    // Constructor defaults mirror the verse values, so a freshly built
    // template moves box + verse together until the user isolates one;
    // fromJson below does the same migration for saved templates that
    // predate the box fields (old single value becomes BOTH).
    val boxTransition: TransitionType = transition,
    val boxTransitionDurationMs: Int = transitionDurationMs,
    // Continuous box effects (combined batch, II Card): each has its own
    // on/off flag and they share ONE Box Motion Speed (less tapping).
    // All use elapsed time since the verse went live, start neutral
    // (scale 1, translate 0, glow base) at verse live, stay neutral
    // during the verse/box transition, then grow smoothly after it
    // ends (see BoxMotion.ramp) — so transition -> continuous has no
    // jump. Separate from design-gradient Pulse (colour only, no scale)
    // and from Video speed / Card gradient speed.
    val boxZoomEnabled: Boolean = false,
    val boxFloatEnabled: Boolean = false,
    val boxSwayEnabled: Boolean = false,
    val boxGlowPulseEnabled: Boolean = false,
    val boxMotionSpeed: Float = 1.0f,
    // Clear Mode (2026-10-28 hotfix): per-feed, per-template. The
    // default depends on an earlier constructor parameter (isFullScreen):
    // Full Show -> verse-only, Lower Third -> all. copy() preserves the
    // explicit value; fromJson migrates old saves the same way.
    val clearMode: ClearMode = if (isFullScreen) ClearMode.CLEAR_VERSE_ONLY else ClearMode.CLEAR_ALL,
    val showAccentBorder: Boolean = false,
    /** Independent text shadow on verse/citation text. */
    val textShadowEnabled: Boolean = true,
    val textShadowColorHex: String = "#000000",
    /**
     * v1.8: shadow thickness (blur radius, dp), offset distance (dp) and
     * compass direction in degrees (0 = right, 90 = down, 180 = left,
     * 270 = up). Defaults (8/4/90) reproduce the pre-v1.8 look
     * (setShadowLayer(8, 0, 4)).
     */
    val textShadowBlurDp: Float = 8f,
    val textShadowOffsetDp: Float = 4f,
    val textShadowAngleDeg: Int = 90,
    /** Independent glow on the lower-third card/container (where a card exists). */
    val cardGlowEnabled: Boolean = true,
    val cardGlowColorHex: String = "#000000",
    /**
     * User emblem: any emoji/symbol typed via the system keyboard (empty = none).
     * Rendered next to the citation on every surface (NDI, HTTP, preview).
     * Replaces the legacy [showCrossEmblem] boolean; old styles migrate to "✝".
     */
    val emblem: String = "",
    /**
     * Legacy bilingual switch, kept only for one-time JSON migration.
     * New code must use [languageMode] instead.
     */
    val bilingualMode: Boolean = false,
    /** v1.8: three-way language control (replaces [bilingualMode]). */
    val languageMode: LanguageMode = LanguageMode.ARABIC_ONLY,
    val isPureTransparentBackground: Boolean = true,
    val animatedBackground: AnimatedBackgroundType = AnimatedBackgroundType.NONE,
    val animatedBackgroundOpacity: Float = 0.65f,
    val customVideoUrl: String = "",
    /**
     * v1.7: opaque ID of the user-picked background video in private storage
     * (VideoStore). Replaces the legacy raw [customVideoUrl]; old templates
     * migrate best-effort on load.
     */
    val customVideoId: String = "",
    /**
     * v1.8: Play the custom background video with sound. Default true (muted)
     * for church production — background visuals shouldn't have audio.
     */
    val customVideoMuted: Boolean = true,
    /**
     * v1.8: Motion/animation playback speed multiplier. 1.0 = normal,
     * 0 = paused, 3.0 = triple speed. Applies to custom video
     * and procedural animations.
     */
    val motionSpeed: Float = 1.0f,
    /** v2.0: How the custom video loops. Default FADE (crossfade through black). */
    val videoLoopMode: VideoLoopMode = VideoLoopMode.FADE,
    val streamBackgroundMode: StreamBackgroundMode = StreamBackgroundMode.TRANSPARENT_ALPHA,
    /**
     * v1.9: per-side card padding (dp) — the breathing room between the card
     * edge and the text. Replaces the old hardcoded 36/32 paddings; defaults
     * reproduce the pre-v1.9 look so existing styles migrate unchanged.
     */
    val cardPaddingTopDp: Float = 32f,
    val cardPaddingBottomDp: Float = 32f,
    val cardPaddingLeftDp: Float = 36f,
    val cardPaddingRightDp: Float = 36f,
    /**
     * Batch-2: verse-to-citation gap (dp) — space between the verse text and
     * its citation, per language. Separate from card padding (which is the
     * box edge breathing room).
     */
    val verseCitationGapArabicDp: Float = 12f,
    val verseCitationGapEnglishDp: Float = 12f,
    /**
     * Batch-2: box transition loop mode. True = transition loops continuously
     * during live (current behavior). False = plays once per verse change,
     * then holds still.
     */
    val boxLoopContinuous: Boolean = true,
    /**
     * Batch-2: true fullscreen. When true, bypasses the card/box entirely —
     * no padding, no border, no box transitions — verse fills the screen
     * edge-to-edge, respecting positionBottomPercent/boxHeightPercent
     * (0% bottom + 100% height = true full bleed).
     */
    val trueFullscreen: Boolean = false,
    /**
     * v1.9: shrink-to-fit. When true, verse + citation + English section
     * (preserving their ratio) are capped at [autoFitMaxVerseSp] and shrink
     * together until they fit the text box ([boxHeightPercent] of the frame,
     * width from the horizontal margins). Off = text renders at the selected
     * sizes and may overflow.
     */
    val autoFitText: Boolean = true,
    /**
     * v1.9.1 (Ashraf's model, replaces the v1.9 floor autoFitMinVerseSp):
     * the MAXIMUM size (sp, verse) the fitted text may reach under
     * [autoFitText] — a short verse must not blow up huge on a full screen.
     * Long content shrinks below it to fit the box (hard floor 10sp).
     * Inert when autoFitText is off. Legacy saves migrate value-for-value.
     */
    val autoFitMaxVerseSp: Int = 48,
    /**
     * v1.9.1: the text box height as % of the frame height — the region
     * auto-fit fits into. Lower default 45 (the pre-v1.9.1 hardcoded
     * budget); Full Show templates use 100 (the whole frame).
     */
    val boxHeightPercent: Int = 45,
    /**
     * Design-gradient card (2026-10-28 batch, off by default so every
     * existing style renders exactly as before). When on, the card fill
     * is a two-color gradient from [bgColorHex] to [gradientColorHex2]
     * at [gradientAngleDeg] degrees, at [bgOpacity] transparency, on
     * BOTH renderers (HTTP CSS gradient, NDI native gradient shader).
     * [gradientSpeed] scales the animation only (0 = paused); video
     * speed ([motionSpeed]) is untouched.
     */
    val gradientEnabled: Boolean = false,
    val gradientColorHex2: String = "#0A1128",
    val gradientAngleDeg: Int = 90,
    val gradientAnimation: GradientAnimationType = GradientAnimationType.NONE,
    val gradientSpeed: Float = 1.0f,
    /**
     * Auto-fit citation floor (2026-10-28 batch): the primary citation
     * never renders below this size (sp) under auto-fit. Default 10
     * preserves the old hard floor; raising it is the fix for
     * "increasing the citation makes it smaller".
     */
    val autoFitMinCitationSp: Int = 10,
    /**
     * Auto-fit ratio lock (on by default): verse and citation shrink
     * by one shared ratio, preserving their designed ratio. Off: the
     * citation stays at its set size (clamped to the floor above)
     * while only the verse shrinks to fit the box.
     */
    val autoFitRatioLock: Boolean = true,
    /**
     * Verse line spacing multipliers (one-update item 7): line height
     * within a wrapped verse, separate for Arabic and English. 1.5
     * reproduces the pre-item-7 rendering exactly (NDI StaticLayout
     * multipliers and the HTTP line-height formulas are scaled by
     * value/1.5), so existing templates look identical until the user
     * moves a slider. Range in the Editor: 1.0..3.0.
     */
    val lineSpacingArabic: Float = 1.5f,
    val lineSpacingEnglish: Float = 1.5f,
    /**
     * Auto-fit frozen verse:citation ratio (one-update item 8). Null =
     * never recorded (sliders behave independently, as before). Once
     * auto-fit is applied the ratio verseFontSize/referenceFontSize is
     * recorded here; later slider moves scale verse+citation TOGETHER
     * preserving it, and the Editor shows the recorded percentage live.
     * A different percentage is set explicitly once and becomes the new
     * recorded value. Toggling auto-fit off/on never clears it.
     */
    val autoFitFrozenRatio: Float? = null,
)

/** Auto-fit frozen-ratio math (one-update item 8), pure and unit-testable.
 * The frozen ratio is verseSp / citationSp recorded when auto-fit first
 * applies. [record] captures it from current sizes (no-op when one is
 * zero). [scaledCitation] returns the citation size that preserves the
 * ratio for a new verse size, so one slider move updates both. */
object AutoFitRatio {
    fun record(verseSp: Int, citationSp: Int): Float? {
        if (verseSp <= 0 || citationSp <= 0) return null
        return verseSp.toFloat() / citationSp.toFloat()
    }
    fun scaledCitation(newVerseSp: Int, frozenRatio: Float): Int {
        if (frozenRatio <= 0f) return 0
        return (newVerseSp / frozenRatio).roundToInt().coerceAtLeast(1)
    }
    /** Display percentage of the ratio, e.g. 214% for 30sp/14sp. */
    fun percent(frozenRatio: Float): Int = (frozenRatio * 100f).roundToInt()
}
