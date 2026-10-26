package com.arabicchristianmedia.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * R4: ONE row spec for verse/sermon/recents rows (Library card, Sermon
 * row, recents sheet row). Body text size stays per-list by design: the
 * Library uses the reader's font-size setting, sermon rows 15sp, sheet
 * rows 14sp. The remote page follows the same structure through CSS
 * variables (R5); its absolute px sizes stay larger because the remote
 * page is read at a glance during services and CSS px do not scale with
 * the tablet's large system font the way sp does.
 */
object VerseRowSpec {
    val RowPadding = 12.dp
    val CardCorner = 16.dp
    val CitationSize = 11.sp
    val CitationBodySpacing = 4.dp
    val RowSpacing = 8.dp
}
