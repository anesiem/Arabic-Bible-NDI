package com.arabicchristianmedia.model

/**
 * Bundled Arabic Google Fonts (SIL Open Font License).
 * TTF files live in `assets/fonts/` as `<id>-400.ttf` / `<id>-700.ttf`.
 * Single source of truth for: editor dropdown, NDI canvas typeface loading,
 * and the HTTP overlay `@font-face` rules (served from the app, fully offline).
 * `fontFamily` style values use [family] (e.g. "Amiri"); "System" is a
 * special value meaning the Android system default typeface.
 */
data class BundledFont(
    val family: String,
    val asset400: String,
    val asset700: String?,
)

object ArabicFonts {
    const val SYSTEM = "System"

    val all: List<BundledFont> = listOf(
        BundledFont("Alan Sans", "fonts/alan-sans-400.ttf", "fonts/alan-sans-700.ttf"),
        BundledFont("Alexandria", "fonts/alexandria-400.ttf", "fonts/alexandria-700.ttf"),
        BundledFont("Alkalami", "fonts/alkalami-400.ttf", null),
        BundledFont("Almarai", "fonts/almarai-400.ttf", "fonts/almarai-700.ttf"),
        BundledFont("Alyamama", "fonts/alyamama-400.ttf", "fonts/alyamama-700.ttf"),
        BundledFont("Amiri", "fonts/amiri-400.ttf", "fonts/amiri-700.ttf"),
        BundledFont("Aref Ruqaa", "fonts/aref-ruqaa-400.ttf", "fonts/aref-ruqaa-700.ttf"),
        BundledFont("Aref Ruqaa Ink", "fonts/aref-ruqaa-ink-400.ttf", "fonts/aref-ruqaa-ink-700.ttf"),
        BundledFont("Badeen Display", "fonts/badeen-display-400.ttf", null),
        BundledFont("Baloo Bhaijaan 2", "fonts/baloo-bhaijaan-2-400.ttf", "fonts/baloo-bhaijaan-2-700.ttf"),
        BundledFont("Beiruti", "fonts/beiruti-400.ttf", "fonts/beiruti-700.ttf"),
        BundledFont("Blaka", "fonts/blaka-400.ttf", null),
        BundledFont("Blaka Hollow", "fonts/blaka-hollow-400.ttf", null),
        BundledFont("Blaka Ink", "fonts/blaka-ink-400.ttf", null),
        BundledFont("Cairo", "fonts/cairo-400.ttf", "fonts/cairo-700.ttf"),
        BundledFont("Cairo Play", "fonts/cairo-play-400.ttf", "fonts/cairo-play-700.ttf"),
        BundledFont("Cascadia Code", "fonts/cascadia-code-400.ttf", "fonts/cascadia-code-700.ttf"),
        BundledFont("Cascadia Mono", "fonts/cascadia-mono-400.ttf", "fonts/cascadia-mono-700.ttf"),
        BundledFont("Changa", "fonts/changa-400.ttf", "fonts/changa-700.ttf"),
        BundledFont("El Messiri", "fonts/el-messiri-400.ttf", "fonts/el-messiri-700.ttf"),
        BundledFont("Estedad", "fonts/estedad-400.ttf", "fonts/estedad-700.ttf"),
        BundledFont("Fustat", "fonts/fustat-400.ttf", "fonts/fustat-700.ttf"),
        BundledFont("Gulzar", "fonts/gulzar-400.ttf", null),
        BundledFont("Handjet", "fonts/handjet-400.ttf", "fonts/handjet-700.ttf"),
        BundledFont("Harmattan", "fonts/harmattan-400.ttf", "fonts/harmattan-700.ttf"),
        BundledFont("IBM Plex Sans Arabic", "fonts/ibm-plex-sans-arabic-400.ttf", "fonts/ibm-plex-sans-arabic-700.ttf"),
        BundledFont("Jomhuria", "fonts/jomhuria-400.ttf", null),
        BundledFont("Katibeh", "fonts/katibeh-400.ttf", null),
        BundledFont("Kufam", "fonts/kufam-400.ttf", "fonts/kufam-700.ttf"),
        BundledFont("Lalezar", "fonts/lalezar-400.ttf", null),
        BundledFont("Lateef", "fonts/lateef-400.ttf", "fonts/lateef-700.ttf"),
        BundledFont("Lemonada", "fonts/lemonada-400.ttf", "fonts/lemonada-700.ttf"),
        BundledFont("Mada", "fonts/mada-400.ttf", "fonts/mada-700.ttf"),
        BundledFont("Marhey", "fonts/marhey-400.ttf", "fonts/marhey-700.ttf"),
        BundledFont("Markazi Text", "fonts/markazi-text-400.ttf", "fonts/markazi-text-700.ttf"),
        BundledFont("Mirza", "fonts/mirza-400.ttf", "fonts/mirza-700.ttf"),
        BundledFont("Noto Kufi Arabic", "fonts/noto-kufi-arabic-400.ttf", "fonts/noto-kufi-arabic-700.ttf"),
        BundledFont("Noto Naskh Arabic", "fonts/noto-naskh-arabic-400.ttf", "fonts/noto-naskh-arabic-700.ttf"),
        BundledFont("Noto Nastaliq Urdu", "fonts/noto-nastaliq-urdu-400.ttf", "fonts/noto-nastaliq-urdu-700.ttf"),
        BundledFont("Noto Sans Arabic", "fonts/noto-sans-arabic-400.ttf", "fonts/noto-sans-arabic-700.ttf"),
        BundledFont("Oi", "fonts/oi-400.ttf", null),
        BundledFont("Parastoo", "fonts/parastoo-400.ttf", "fonts/parastoo-700.ttf"),
        BundledFont("Playpen Sans Arabic", "fonts/playpen-sans-arabic-400.ttf", "fonts/playpen-sans-arabic-700.ttf"),
        BundledFont("Qahiri", "fonts/qahiri-400.ttf", null),
        BundledFont("Rakkas", "fonts/rakkas-400.ttf", null),
        BundledFont("Readex Pro", "fonts/readex-pro-400.ttf", "fonts/readex-pro-700.ttf"),
        BundledFont("Reem Kufi", "fonts/reem-kufi-400.ttf", "fonts/reem-kufi-700.ttf"),
        BundledFont("Reem Kufi Fun", "fonts/reem-kufi-fun-400.ttf", "fonts/reem-kufi-fun-700.ttf"),
        BundledFont("Reem Kufi Ink", "fonts/reem-kufi-ink-400.ttf", null),
        BundledFont("Rubik", "fonts/rubik-400.ttf", "fonts/rubik-700.ttf"),
        BundledFont("Ruwudu", "fonts/ruwudu-400.ttf", "fonts/ruwudu-700.ttf"),
        BundledFont("Scheherazade New", "fonts/scheherazade-new-400.ttf", "fonts/scheherazade-new-700.ttf"),
        BundledFont("Tajawal", "fonts/tajawal-400.ttf", "fonts/tajawal-700.ttf"),
        BundledFont("Vazirmatn", "fonts/vazirmatn-400.ttf", "fonts/vazirmatn-700.ttf"),
        BundledFont("Vibes", "fonts/vibes-400.ttf", null),
        BundledFont("Zain", "fonts/zain-400.ttf", "fonts/zain-700.ttf"),
    )

    private val byFamily: Map<String, BundledFont> = all.associateBy { it.family }

    /** Null for "System" or unknown families (caller falls back to system typeface). */
    fun find(family: String): BundledFont? = byFamily[family]

    /** Display names for the editor dropdown, with "System" first. */
    val displayNames: List<String> = listOf(SYSTEM) + all.map { it.family }
}
