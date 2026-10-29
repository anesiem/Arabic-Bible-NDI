package com.arabicchristianmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.model.BibleBookGroups
import com.arabicchristianmedia.model.ColorTheme
import com.arabicchristianmedia.model.ColorThemes
import com.arabicchristianmedia.model.groupColor
import com.arabicchristianmedia.model.withGroupColor
import com.arabicchristianmedia.model.SermonShadePair
import com.arabicchristianmedia.model.SermonShadePairs
import com.arabicchristianmedia.ui.theme.LocalBentoColors

/**
 * App theme picker (Setup → Customizations).
 *
 * Top: the theme list (built-ins + customs) — tapping a theme selects it
 * immediately. Below: a Customize section that edits a WORKING COPY:
 * band pairs (the existing SermonShadePairs lists) + recents swatches,
 * dark and light separately. Switching themes discards unsaved edits
 * (same rule as style templates: update before you switch). Save-as-new
 * persists the working copy as a new custom theme; Update replaces a
 * custom theme (built-ins cannot be overwritten).
 *
 * Note: the working copy is dialog-local — the app re-themes on select /
 * save, not live while customizing. The dialog's own swatches are the
 * preview (recents rows render on the working theme's bands).
 *
 * Long-pressing a custom theme row deletes it (after confirmation);
 * built-ins cannot be deleted. Deleting the selected theme falls back
 * to Classic.
 */
@Composable
fun ThemeDialog(
    selectedTheme: ColorTheme,
    customThemes: List<ColorTheme>,
    onSelectTheme: (String) -> Unit,
    onSaveNewTheme: (ColorTheme) -> Unit,
    onUpdateTheme: (ColorTheme) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val bento = LocalBentoColors.current
    // Re-seeds when the selection changes (select / save) — unsaved
    // customize edits are discarded on theme switch, by design.
    var working by remember(selectedTheme) { mutableStateOf(selectedTheme) }
    val dirty = working != selectedTheme
    val selectedIsBuiltIn = ColorThemes.isBuiltIn(selectedTheme.id)
    var showNameField by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("My theme") }
    var themeToDelete by remember { mutableStateOf<ColorTheme?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مظهر التطبيق (App theme)", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("المظاهر (Themes)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                (ColorThemes.BUILT_INS + customThemes).forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        selected = theme.id == selectedTheme.id,
                        onClick = { onSelectTheme(theme.id) },
                        onLongClick = if (ColorThemes.isBuiltIn(theme.id)) null else ({ themeToDelete = theme })
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تخصيص (Customize)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                    if (dirty) {
                        Text(
                            "تم التعديل (Modified)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.primary
                        )
                    }
                }
                Text("الوضع الداكن — ألوان الصفوف (Dark bands)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = bento.textPrimary)
                SermonShadePairs.DARK.forEach { pair ->
                    PairOptionRow(
                        pair = pair,
                        selected = working.dark.bandA == pair.shadeA && working.dark.bandB == pair.shadeB,
                        onClick = {
                            working = working.copy(
                                dark = working.dark.copy(bandA = pair.shadeA, bandB = pair.shadeB)
                            )
                        }
                    )
                }
                Text("الوضع الفاتح — ألوان الصفوف (Light bands)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = bento.textPrimary)
                SermonShadePairs.LIGHT.forEach { pair ->
                    PairOptionRow(
                        pair = pair,
                        selected = working.light.bandA == pair.shadeA && working.light.bandB == pair.shadeB,
                        onClick = {
                            working = working.copy(
                                light = working.light.copy(bandA = pair.shadeA, bandB = pair.shadeB)
                            )
                        }
                    )
                }
                Text("الوضع الداكن — لون الآيات الأخيرة (Dark recents)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = bento.textPrimary)
                DarkRecentsSwatches.forEachIndexed { index, swatch ->
                    RecentsOptionRow(
                        swatch = swatch,
                        bandBg = if (index % 2 == 0) Color(working.dark.bandA) else Color(working.dark.bandB),
                        textColor = Color(working.dark.textPrimary),
                        selected = working.dark.recents == swatch.argb,
                        onClick = { working = working.copy(dark = working.dark.copy(recents = swatch.argb)) }
                    )
                }
                Text("الوضع الفاتح — لون الآيات الأخيرة (Light recents)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = bento.textPrimary)
                LightRecentsSwatches.forEachIndexed { index, swatch ->
                    RecentsOptionRow(
                        swatch = swatch,
                        bandBg = if (index % 2 == 0) Color(working.light.bandA) else Color(working.light.bandB),
                        textColor = Color(working.light.textPrimary),
                        selected = working.light.recents == swatch.argb,
                        onClick = { working = working.copy(light = working.light.copy(recents = swatch.argb)) }
                    )
                }
                // One-update item 21: the 10 Bible-study group colors are
                // theme variables — fully user-controllable per mode.
                Text("الوضع الداكن — ألوان مجموعات الأسفار (Dark book groups)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = bento.textPrimary)
                BibleBookGroups.ALL.forEach { group ->
                    GroupColorRow(
                        groupName = group.arabicName,
                        current = working.dark.groupColor(group.id),
                        swatches = GroupSwatchesDark,
                        onPick = { argb -> working = working.copy(dark = working.dark.withGroupColor(group.id, argb)) }
                    )
                }
                Text("الوضع الفاتح — ألوان مجموعات الأسفار (Light book groups)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = bento.textPrimary)
                BibleBookGroups.ALL.forEach { group ->
                    GroupColorRow(
                        groupName = group.arabicName,
                        current = working.light.groupColor(group.id),
                        swatches = GroupSwatchesLight,
                        onPick = { argb -> working = working.copy(light = working.light.withGroupColor(group.id, argb)) }
                    )
                }
                if (dirty) {
                    Spacer(Modifier.height(8.dp))
                    if (!selectedIsBuiltIn) {
                        TextButton(onClick = { onUpdateTheme(working) }) {
                            Text("تحديث المظهر (Update theme)", fontSize = 13.sp)
                        }
                    }
                    if (!showNameField) {
                        TextButton(onClick = { showNameField = true }) {
                            Text("حفظ كمظهر جديد (Save as new)", fontSize = 13.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("اسم المظهر (Theme name)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        TextButton(
                            onClick = {
                                val name = newName.ifBlank { "My theme" }
                                onSaveNewTheme(
                                    working.copy(
                                        id = "custom_" + System.currentTimeMillis(),
                                        name = name
                                    )
                                )
                                showNameField = false
                                newName = "My theme"
                            }
                        ) {
                            Text("حفظ (Save)", fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق (Close)") }
        }
    )
    themeToDelete?.let { theme ->
        AlertDialog(
            onDismissRequest = { themeToDelete = null },
            title = { Text("حذف المظهر (Delete theme)", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "سيتم حذف المظهر \"${theme.name}\" نهائياً. (Theme \"${theme.name}\" will be permanently deleted.)",
                        fontSize = 13.sp
                    )
                    if (theme.id == selectedTheme.id) {
                        Text(
                            "المظهر الحالي سيعود إلى Classic. (The current theme will fall back to Classic.)",
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTheme(theme.id)
                        // The deleted theme is the fallback's own target:
                        // selectedTheme is still the stale prop here (the
                        // just-deleted theme) until recompose re-seeds
                        // working, so reset to Classic explicitly.
                        if (working.id == theme.id) working = ColorThemes.CLASSIC
                        themeToDelete = null
                    }
                ) { Text("حذف (Delete)") }
            },
            dismissButton = {
                TextButton(onClick = { themeToDelete = null }) { Text("إلغاء (Cancel)") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeRow(theme: ColorTheme, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val bento = LocalBentoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onClick() }, onLongClick = onLongClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(22.dp).background(Color(theme.light.bandA), RoundedCornerShape(6.dp)))
            Box(Modifier.size(22.dp).background(Color(theme.light.bandB), RoundedCornerShape(6.dp)))
            Box(Modifier.size(22.dp).background(Color(theme.light.recents), RoundedCornerShape(6.dp)))
        }
        Text(theme.name, fontSize = 13.sp, color = bento.textPrimary, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = bento.primary)
    }
}

@Composable
private fun PairOptionRow(pair: SermonShadePair, selected: Boolean, onClick: () -> Unit) {
    val bento = LocalBentoColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(22.dp).background(Color(pair.shadeA), RoundedCornerShape(6.dp)))
            Box(Modifier.size(22.dp).background(Color(pair.shadeB), RoundedCornerShape(6.dp)))
        }
        Text(pair.displayName, fontSize = 13.sp, color = bento.textPrimary, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = bento.primary)
    }
}

private data class RecentsSwatch(val name: String, val argb: Long)

// Curated recents hues, verified >= 3:1 on every theme's card + bands per
// mode (dark list bright on dark rows; light list dark on light rows).
// Two marginal notes: light teal + light cyan read 2.9:1 on Clear's sage
// band only — kept because teal is the default recents color.
private val DarkRecentsSwatches = listOf(
    RecentsSwatch("Teal", 0xFF0D9488),
    RecentsSwatch("Cyan", 0xFF22D3EE),
    RecentsSwatch("Amber", 0xFFFBBF24),
    RecentsSwatch("Rose", 0xFFFB7185),
    RecentsSwatch("Lime", 0xFFA3E635),
    RecentsSwatch("Pink", 0xFFF472B6)
)

private val LightRecentsSwatches = listOf(
    RecentsSwatch("Teal", 0xFF0D9488),
    RecentsSwatch("Amber", 0xFFB45309),
    RecentsSwatch("Cyan", 0xFF0891B2),
    RecentsSwatch("Rose", 0xFFBE123C),
    RecentsSwatch("Lime", 0xFF4D7C0F),
    RecentsSwatch("Violet", 0xFF7C3AED)
)

@Composable
private fun RecentsOptionRow(swatch: RecentsSwatch, bandBg: Color, textColor: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .background(bandBg, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // The marker edge, as it appears on a real row. Text uses the
        // section mode's own text color (not bento's): the light section
        // must stay readable even when the system is in dark mode.
        Box(
            Modifier.size(width = 4.dp, height = 22.dp)
                .background(Color(swatch.argb), RoundedCornerShape(2.dp))
        )
        Text(swatch.name, fontSize = 13.sp, color = textColor, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, null, tint = textColor)
    }
}

// One-update item 21: categorical swatches for the book-group colors
// (dark list bright on dark rows; light list deep on light rows).
private val GroupSwatchesDark = listOf(
    0xFFF59E0BL, 0xFF38BDF8L, 0xFFA78BFAL, 0xFFF472B6L, 0xFFFB923CL, 0xFF34D399L,
    0xFF22D3EEL, 0xFF60A5FAL, 0xFFA3E635L, 0xFFF87171L, 0xFF94A3B8L, 0xFFFBBF24L
)

private val GroupSwatchesLight = listOf(
    0xFFB45309L, 0xFF0284C7L, 0xFF7C3AEDL, 0xFFDB2777L, 0xFFEA580CL, 0xFF047857L,
    0xFF0891B2L, 0xFF1D4ED8L, 0xFF4D7C0FL, 0xFFDC2626L, 0xFF64748BL, 0xFF0D9488L
)

@Composable
private fun GroupColorRow(groupName: String, current: Long, swatches: List<Long>, onPick: (Long) -> Unit) {
    val bento = LocalBentoColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(Color(current))
            )
            Text(
                groupName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = bento.textPrimary, modifier = Modifier.weight(1f)
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            items(swatches) { sw ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(Color(sw))
                        .border(
                            if (sw == current) 2.dp else 0.dp,
                            bento.textPrimary, CircleShape
                        )
                        .clickable { onPick(sw) }
                )
            }
        }
    }
}
