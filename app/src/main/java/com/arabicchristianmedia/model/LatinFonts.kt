package com.arabicchristianmedia.model

/**
 * Bundled Latin (English) Google Fonts (SIL Open Font License), chosen for
 * church / Bible scripture use: classic book serifs for verse text, inscriptional
 * display faces for citations, and calligraphy scripts for accents.
 * TTF files live in `assets/fonts/` as `latin-<id>-400.ttf` / `latin-<id>-700.ttf`
 * (variable fonts pinned to static instances at build time; scripts have no bold).
 * Resolution goes through [BundledFonts.find] so the NDI renderer, HTTP @font-face
 * rules, and the editor preview all pick these up automatically.
 * `secondaryFontFamily` style values use [family]; "System" means the Android
 * system default typeface.
 */
object LatinFonts {

    val all: List<BundledFont> = listOf(
        BundledFont("EB Garamond", "fonts/latin-eb-garamond-400.ttf", "fonts/latin-eb-garamond-700.ttf"),
        BundledFont("Cinzel", "fonts/latin-cinzel-400.ttf", "fonts/latin-cinzel-700.ttf"),
        BundledFont("Cormorant Garamond", "fonts/latin-cormorant-garamond-400.ttf", "fonts/latin-cormorant-garamond-700.ttf"),
        BundledFont("Playfair Display", "fonts/latin-playfair-display-400.ttf", "fonts/latin-playfair-display-700.ttf"),
        BundledFont("Lora", "fonts/latin-lora-400.ttf", "fonts/latin-lora-700.ttf"),
        BundledFont("Merriweather", "fonts/latin-merriweather-400.ttf", "fonts/latin-merriweather-700.ttf"),
        BundledFont("Crimson Pro", "fonts/latin-crimson-pro-400.ttf", "fonts/latin-crimson-pro-700.ttf"),
        BundledFont("Libre Baskerville", "fonts/latin-libre-baskerville-400.ttf", "fonts/latin-libre-baskerville-700.ttf"),
        BundledFont("Dancing Script", "fonts/latin-dancing-script-400.ttf", "fonts/latin-dancing-script-700.ttf"),
        BundledFont("Great Vibes", "fonts/latin-great-vibes-400.ttf", null),
        BundledFont("Alex Brush", "fonts/latin-alex-brush-400.ttf", null),
        BundledFont("Allura", "fonts/latin-allura-400.ttf", null),
    )

    private val byFamily: Map<String, BundledFont> = all.associateBy { it.family }

    fun find(family: String): BundledFont? = byFamily[family]

    /** Display names for the English font dropdown, with "System" first. */
    val displayNames: List<String> = listOf(ArabicFonts.SYSTEM) + all.map { it.family }
}

/** Single lookup across every bundled family (Arabic + Latin). */
object BundledFonts {
    fun find(family: String): BundledFont? = ArabicFonts.find(family) ?: LatinFonts.find(family)
    val all: List<BundledFont> = ArabicFonts.all + LatinFonts.all
}
