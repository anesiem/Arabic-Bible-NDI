package com.arabicchristianmedia.model

/**
 * 1.9.27 (item 1): how a verse row in a native list marks its kind
 * (recent / sermon). The six treatments are exactly the options Ashraf
 * picked from in the colours review (widget v2, 2026-11-20):
 * SOLID = full fill in the token colour (the pre-1.9.27 look),
 * LINE = plain card + a thin side line in the token colour,
 * TINT = pale tint, 12% token over the card,
 * SOFT = soft fill, 45% token over the card,
 * BORDER = plain card + a border in the token colour,
 * TAG = plain card + a small tag chip in the token colour.
 * The token colour always comes from the active theme; the treatment
 * only decides how it is applied. Pure Kotlin — no Android or Compose
 * dependency, so the defaults table is unit-testable.
 */
enum class ListTreatment {
    SOLID, LINE, TINT, SOFT, BORDER, TAG
}

/** Which kind of verse row a treatment applies to. */
enum class ListKind {
    RECENT, SERMON
}

/**
 * Per-theme, per-mode defaults from Ashraf's review picks (widget v155,
 * confirmed 2026-11-20). A user override (Setup > Customizations > List
 * colours; null = theme default) replaces this. Unknown theme ids
 * (custom themes) fall back to the majority pick for the mode.
 */
fun defaultListTreatment(themeId: String, isDark: Boolean, kind: ListKind): ListTreatment {
    return when (kind) {
        ListKind.SERMON -> {
            // Solid everywhere except Desert Dawn light (soft fill).
            if (!isDark && themeId == "desert_dawn") ListTreatment.SOFT else ListTreatment.SOLID
        }
        ListKind.RECENT -> {
            if (isDark) {
                when (themeId) {
                    "classic", "midnight" -> ListTreatment.SOFT
                    "fresh" -> ListTreatment.LINE
                    else -> ListTreatment.BORDER
                }
            } else {
                when (themeId) {
                    "clear", "gentle", "pulse" -> ListTreatment.BORDER
                    else -> ListTreatment.TINT
                }
            }
        }
    }
}

/** Safe parse for the persisted override name; unknown/null = theme default. */
fun listTreatmentFromName(name: String?): ListTreatment? {
    if (name == null) return null
    return ListTreatment.entries.firstOrNull { it.name == name }
}
