package com.arabicchristianmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.model.SavedSermon
import com.arabicchristianmedia.model.SermonEntry
import com.arabicchristianmedia.model.SermonShadePair
import com.arabicchristianmedia.model.SermonShadePairs
import com.arabicchristianmedia.ui.theme.LocalBentoColors
import com.arabicchristianmedia.ui.theme.BentoCardDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.9.1 sermon list (rework of the v2.0 playlist):
 * - Sharing STAGES verses; tapping a row puts it on air and silently syncs
 *   the reader to it. Nothing airs automatically.
 * - Rows are colored per share-GROUP (one reference = one group, shades
 *   alternate); the live row gets the red live treatment + LIVE badge.
 * - The list persists across restarts; the header is ONE slim icon row
 *   (save / open / clear) so the verse list keeps all its screen space —
 *   every other action lives in dialogs, a modal sheet, or a swipe.
 * - Swipe a row to reveal the trash icon; a full swipe removes the verse.
 * - The book icon at the end of a row opens the verse in the Library tab
 *   WITHOUT putting it on air.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SermonListScreen(
    uiState: BibleNdiUiState,
    onShowVerse: (SermonEntry) -> Unit,
    onOpenInReader: (SermonEntry) -> Unit,
    onRemove: (SermonEntry) -> Unit,
    onClear: () -> Unit,
    onSave: (String) -> Unit,
    onOpenSaved: (String, Boolean) -> Unit,
    onRenameSaved: (String, String) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onSelectSermonPair: (Boolean, String) -> Unit
) {
    val bento = LocalBentoColors.current
    val playlist = uiState.sermonPlaylist
    // v1.9.1: alternating row colors — user-picked pair per screen mode
    // (same dark detection idiom as the reader screen).
    val isDark = MaterialTheme.colorScheme.surface == BentoCardDark
    val sermonPair = if (isDark) SermonShadePairs.dark(uiState.sermonPairDarkId)
                     else SermonShadePairs.light(uiState.sermonPairLightId)
    var showColorDialog by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }
    var showOpenSheet by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf<SavedSermon?>(null) }
    var renameTarget by remember { mutableStateOf<SavedSermon?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedSermon?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Slim header: name + counts on the left, icon-only actions.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.currentSermonName ?: "قائمة الخدمة (Sermon list)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (playlist.isEmpty()) "لا توجد آيات بعد"
                               else "${playlist.size} آية — اضغط آية للبث • اسحب لحذفها",
                        fontSize = 12.sp,
                        color = bento.textSecondary
                    )
                }
                IconButton(
                    onClick = {
                        saveNameInput = uiState.currentSermonName ?: ""
                        showSaveDialog = true
                    },
                    enabled = playlist.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = "حفظ القائمة (Save sermon)",
                        tint = if (playlist.isNotEmpty()) bento.primary else bento.textSecondary
                    )
                }
                IconButton(onClick = { showOpenSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "فتح عظة محفوظة (Open saved sermon)",
                        tint = bento.primary
                    )
                }
                IconButton(onClick = { showColorDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = "ألوان القائمة (Sermon list colors)",
                        tint = bento.primary
                    )
                }
                if (playlist.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "مسح كل القائمة (Clear all)",
                            tint = bento.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlist.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = bento.textSecondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "شارك ملاحظة من Google Keep أو من تطبيق الكتاب المقدس إلى «Bible NDI» — ستظهر مراجعها هنا مجمّعة بالألوان، واضغط أي آية لبثّها. أو افتح عظة محفوظة من أيقونة المجلد بالأعلى.",
                        fontSize = 14.sp,
                        color = bento.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        playlist,
                        key = { _, e -> "${e.groupId}-${e.verse.id}" }
                    ) { index, entry ->
                        SermonRow(
                            entry = entry,
                            index = index,
                            isLive = uiState.activeVerse?.id == entry.verse.id && uiState.isLiveOnAir,
                            onTap = { onShowVerse(entry) },
                            onOpenInReader = { onOpenInReader(entry) },
                            onRemove = { onRemove(entry) },
                            shadeColorA = Color(sermonPair.shadeA),
                            shadeColorB = Color(sermonPair.shadeB)
                        )
                    }
                }
            }
        }

        // ── Sermon colors dialog (v1.9.1: user-picked pairs per mode) ──
        if (showColorDialog) {
            AlertDialog(
                onDismissRequest = { showColorDialog = false },
                title = { Text("ألوان قائمة الخدمة (Sermon colors)", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("الوضع الداكن (Dark screen)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                        SermonShadePairs.DARK.forEach { pair ->
                            SermonPairOption(pair, uiState.sermonPairDarkId == pair.id) { onSelectSermonPair(true, pair.id) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("الوضع الفاتح (Light screen)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                        SermonShadePairs.LIGHT.forEach { pair ->
                            SermonPairOption(pair, uiState.sermonPairLightId == pair.id) { onSelectSermonPair(false, pair.id) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showColorDialog = false }) { Text("إغلاق (Close)") }
                }
            )
        }

        // ── Save dialog ─────────────────────────────────────────────────
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("حفظ العظة (Save sermon)") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = saveNameInput,
                            onValueChange = { saveNameInput = it },
                            label = { Text("اسم العظة (Sermon name)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "الحفظ بنفس اسم عظة موجودة يحدّثها بدلاً من إنشاء نسخة جديدة.",
                            fontSize = 11.sp,
                            color = bento.textSecondary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onSave(saveNameInput); showSaveDialog = false },
                        enabled = saveNameInput.isNotBlank()
                    ) { Text("حفظ") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) { Text("إلغاء") }
                }
            )
        }

        // ── Saved sermons sheet ─────────────────────────────────────────
        if (showOpenSheet) {
            ModalBottomSheet(onDismissRequest = { showOpenSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "العظات المحفوظة (Saved sermons)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.savedSermons.isEmpty()) {
                        Text(
                            text = "لا توجد عظات محفوظة بعد — ابنِ قائمة ثم احفظها من أيقونة الحفظ بالأعلى.",
                            fontSize = 13.sp,
                            color = bento.textSecondary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            items(uiState.savedSermons, key = { it.id }) { saved ->
                                var menuOpen by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            showOpenSheet = false
                                            if (playlist.isEmpty()) onOpenSaved(saved.id, false)
                                            else pendingOpen = saved
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = saved.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = bento.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${saved.entries.size} آية • " +
                                                SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(saved.updatedAt)),
                                            fontSize = 11.sp,
                                            color = bento.textSecondary
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = "خيارات (Options)",
                                                tint = bento.textSecondary
                                            )
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text("إعادة تسمية (Rename)") },
                                                onClick = {
                                                    menuOpen = false
                                                    showOpenSheet = false
                                                    renameInput = saved.name
                                                    renameTarget = saved
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("حذف (Delete)") },
                                                onClick = {
                                                    menuOpen = false
                                                    showOpenSheet = false
                                                    deleteTarget = saved
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // ── Replace or merge chooser ────────────────────────────────────
        pendingOpen?.let { saved ->
            AlertDialog(
                onDismissRequest = { pendingOpen = null },
                title = { Text("فتح «${saved.name}»") },
                text = {
                    Text("القائمة الحالية تحتوي على آيات. هل تستبدلها بهذه العظة، أم تدمج العظة في أعلى القائمة الحالية؟")
                },
                confirmButton = {
                    Button(onClick = { onOpenSaved(saved.id, false); pendingOpen = null }) {
                        Text("استبدال (Replace)")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { onOpenSaved(saved.id, true); pendingOpen = null }) {
                            Text("دمج (Merge)")
                        }
                        TextButton(onClick = { pendingOpen = null }) { Text("إلغاء") }
                    }
                }
            )
        }

        // ── Rename dialog ───────────────────────────────────────────────
        renameTarget?.let { saved ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("إعادة تسمية العظة (Rename)") },
                text = {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("الاسم الجديد (New name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onRenameSaved(saved.id, renameInput); renameTarget = null },
                        enabled = renameInput.isNotBlank()
                    ) { Text("حفظ") }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text("إلغاء") }
                }
            )
        }

        // ── Delete saved sermon confirm ─────────────────────────────────
        deleteTarget?.let { saved ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("حذف «${saved.name}»؟") },
                text = { Text("سيتم حذف هذه العظة المحفوظة نهائياً. القائمة الحالية لن تتأثر.") },
                confirmButton = {
                    Button(onClick = { onDeleteSaved(saved.id); deleteTarget = null }) {
                        Text("حذف")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// v1.9.1: on-air row colors — dark blue with light yellow (Ashraf's
// pick, replacing the red tint). Fixed in both screen modes.
private val SermonLiveNavy = Color(0xFF1B3A5C)
private val SermonLiveYellow = Color(0xFFF5D76E)
private val SermonLivePaleYellow = Color(0xFFFDE9A8)

/** One selectable shade pair in the sermon colors dialog. */
@Composable
private fun SermonPairOption(pair: SermonShadePair, selected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun SermonRow(
    entry: SermonEntry,
    index: Int,
    isLive: Boolean,
    onTap: () -> Unit,
    onOpenInReader: () -> Unit,
    onRemove: () -> Unit,
    shadeColorA: Color,
    shadeColorB: Color
) {
    val bento = LocalBentoColors.current
    val verse = entry.verse
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "حذف الآية (Remove verse)",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isLive -> SermonLiveNavy
                    entry.shade == 0 -> shadeColorA
                    else -> shadeColorB
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                if (isLive) 2.dp else 1.dp,
                if (isLive) SermonLiveYellow else bento.border
            ),
            modifier = Modifier.fillMaxWidth().clickable { onTap() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLive) SermonLiveYellow
                            else bento.primaryContainer,
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Text(
                        text = ArabicTextFormatter.toEasternArabicDigits(index + 1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) SermonLiveNavy else bento.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = verse.getFormattedArabicCitation(true),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) SermonLivePaleYellow else bento.primary
                        )
                        if (isLive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SermonLiveYellow
                            ) {
                                Text(
                                    text = "على الهواء • LIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SermonLiveNavy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = verse.arabicText,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = if (isLive) SermonLivePaleYellow else bento.textPrimary,
                        textAlign = TextAlign.Right,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(
                    onClick = onOpenInReader,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "فتح في المكتبة (Open in Library)",
                        tint = if (isLive) SermonLivePaleYellow else bento.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
