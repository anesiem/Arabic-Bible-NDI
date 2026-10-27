package com.arabicchristianmedia.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wide (tablet) layout gate, shared by the Editor two-pane layout and the
 * Setup two-column layout + anchor strip. A screen is wide only when it
 * is BOTH at least 900dp wide AND at least 600dp tall. Width alone
 * misfires on phones in landscape (a Pixel 7 is ~914dp wide but only
 * ~411dp tall), which used to get the tablet layouts in a cramped
 * strip. Tablet portrait is unaffected: at 914dp wide it is 1200dp+
 * tall, so it still qualifies; at 800dp wide it never did.
 *
 * Call from a BoxWithConstraints scope, passing its maxWidth/maxHeight.
 */
fun isWideLayout(maxWidth: Dp, maxHeight: Dp): Boolean =
    maxWidth >= 900.dp && maxHeight >= 600.dp
