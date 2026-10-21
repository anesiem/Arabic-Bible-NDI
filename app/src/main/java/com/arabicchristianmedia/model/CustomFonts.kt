package com.arabicchristianmedia.model

/**
 * v1.9.1: a user-imported font (TTF/OTF picked from device storage and
 * copied into the app's private storage — see FontStore). Imported fonts
 * appear in BOTH the Arabic and English font dropdowns under [name], and
 * resolve everywhere bundled fonts do (editor preview, NDI canvas, HTTP
 * overlay @font-face). One imported file = one weight; bold is synthesized
 * by the renderers, exactly like single-weight bundled fonts (e.g. Katibeh).
 */
data class CustomFont(
    val id: String,
    val name: String,
    /** Lowercase file extension without the dot: "ttf" or "otf". */
    val ext: String
) {
    /** Private-storage file name: "<id>.<ext>". */
    val fileName: String get() = "$id.$ext"

    /** Name the HTTP server exposes it under: "/fonts/custom-<id>.<ext>". */
    val serveName: String get() = "custom-$id.$ext"
}

/**
 * Process-wide registry of imported fonts, refreshed by the ViewModel
 * whenever the FontStore contents change (init, import, delete). Kept as a
 * simple lookup so the NDI renderer and the HTTP server can resolve a
 * family name to a custom font without a Context of their own.
 */
object CustomFonts {

    @Volatile
    var all: List<CustomFont> = emptyList()
        private set

    @Volatile
    private var byName: Map<String, CustomFont> = emptyMap()

    fun refresh(fonts: List<CustomFont>) {
        all = fonts
        byName = fonts.associateBy { it.name }
    }

    /** Null for bundled/system/unknown families. */
    fun find(family: String): CustomFont? = byName[family]

    val names: List<String> get() = all.map { it.name }
}
