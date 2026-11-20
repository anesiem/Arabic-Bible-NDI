package com.arabicchristianmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.R
import com.arabicchristianmedia.ui.theme.LocalBentoColors

/**
 * One-update item 20: the shortcuts help card — the full keyboard map.
 * Opened by the ? key or the keyboard entry inside the gear's
 * display-settings dialog (item 32). Also documented in README + user docs.
 */
@Composable
fun ShortcutsHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bento = LocalBentoColors.current
    val rows = listOf(
        "→ / PageDown" to context.getString(R.string.s640),
        "← / PageUp" to context.getString(R.string.s641),
        "↑ / ↓" to context.getString(R.string.s642),
        "Enter" to context.getString(R.string.s643),
        "Space" to context.getString(R.string.s644),
        "Esc" to context.getString(R.string.s645),
        "C" to context.getString(R.string.s646),
        "O" to context.getString(R.string.s647),
        "Ctrl+F" to context.getString(R.string.s648),
        "Ctrl+1..4" to context.getString(R.string.s649),
        "+ / −" to context.getString(R.string.s650),
        "?" to context.getString(R.string.s651),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.s14), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    context.getString(R.string.s639),
                    fontSize = 11.sp, color = bento.textSecondary
                )
                rows.forEach { (key, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            key,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = bento.primary,
                            modifier = Modifier.weight(0.35f)
                        )
                        Text(
                            desc,
                            fontSize = 12.sp, color = bento.textPrimary,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                }
                // 1.9.25 (item 12): icon legend — what each toolbar icon
                // does. It lives in this help card (the app's one help
                // surface, reached from display settings) so a user
                // looking for "what does this button do" finds it next
                // to the keys. No new ⓘ icon: this card IS the ⓘ surface.
                HorizontalDivider(color = bento.border)
                Text(
                    context.getString(R.string.s886),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary
                )
                LegendRow(icon = { Icon(Icons.Filled.Check, contentDescription = null, tint = bento.textSecondary, modifier = Modifier.size(18.dp)) }, text = context.getString(R.string.s884))
                LegendRow(icon = { Icon(Icons.Filled.Search, contentDescription = null, tint = bento.textSecondary, modifier = Modifier.size(18.dp)) }, text = context.getString(R.string.s31))
                // The app icon IS the display-settings button (1.9.25
                // item 1: the gear merged into the icon).
                LegendRow(icon = { Icon(Icons.Filled.LiveTv, contentDescription = null, tint = bento.textSecondary, modifier = Modifier.size(18.dp)) }, text = context.getString(R.string.s52))
                LegendRow(
                    icon = {
                        Surface(shape = RoundedCornerShape(6.dp), color = bento.liveRed) {
                            Text("ON AIR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    },
                    text = context.getString(R.string.s647)
                )
                LegendRow(icon = { Icon(Icons.Filled.Menu, contentDescription = null, tint = bento.textSecondary, modifier = Modifier.size(18.dp)) }, text = context.getString(R.string.s49))
                LegendRow(icon = { Icon(Icons.Filled.MoreVert, contentDescription = null, tint = bento.textSecondary, modifier = Modifier.size(18.dp)) }, text = context.getString(R.string.s883))
                LegendRow(icon = { Text("▼ / ▲", fontSize = 12.sp, color = bento.textSecondary) }, text = context.getString(R.string.s885))
                LegendRow(icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = bento.textSecondary, modifier = Modifier.size(18.dp)) }, text = context.getString(R.string.s351))
                LegendRow(icon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = bento.textSecondary, modifier = Modifier.size(18.dp)) }, text = context.getString(R.string.s51))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.s376)) }
        }
    )
}

/**
 * 1.9.25 (item 12): one icon-legend row — the icon on the left (same
 * 0.35/0.65 split as the shortcut rows), its meaning on the right.
 */
@Composable
private fun LegendRow(icon: @Composable () -> Unit, text: String) {
    val bento = LocalBentoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(0.35f), contentAlignment = Alignment.CenterStart) {
            icon()
        }
        Text(
            text,
            fontSize = 12.sp, color = bento.textPrimary,
            modifier = Modifier.weight(0.65f)
        )
    }
}
