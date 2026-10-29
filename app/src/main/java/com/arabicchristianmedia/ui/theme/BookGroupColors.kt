package com.arabicchristianmedia.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One-update item 21: color-coded section headers for the 10 Bible-study
 * groups in the Library book picker. Dark/light variants are resolved
 * from the active theme mode (like every other theme variable), so the
 * headers stay readable in both modes. The palette is categorical and
 * fixed — it is not user-editable per theme (unlike bandA/bandB).
 */
object BookGroupColors {
    /** Group id -> (dark ARGB, light ARGB). */
    private val COLORS: Map<String, Pair<Long, Long>> = mapOf(
        "pentateuch" to (0xFFF59E0BL to 0xFFB45309L),
        "historical" to (0xFF38BDF8L to 0xFF0284C7L),
        "poetry" to (0xFFA78BFAL to 0xFF7C3AEDL),
        "major_prophets" to (0xFFF472B6L to 0xFFDB2777L),
        "minor_prophets" to (0xFFFB923CL to 0xFFEA580CL),
        "gospels" to (0xFF34D399L to 0xFF047857L),
        "acts" to (0xFF22D3EEL to 0xFF0891B2L),
        "pauline" to (0xFF60A5FAL to 0xFF1D4ED8L),
        "general" to (0xFFA3E635L to 0xFF4D7C0FL),
        "revelation" to (0xFFF87171L to 0xFFDC2626L)
    )

    fun color(groupId: String, isDark: Boolean): Color {
        val (dark, light) = COLORS[groupId] ?: (0xFF94A3B8L to 0xFF64748BL)
        return Color(if (isDark) dark else light)
    }
}
