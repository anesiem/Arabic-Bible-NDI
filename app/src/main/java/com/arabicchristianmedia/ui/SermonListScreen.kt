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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.arabicchristianmedia.ui.theme.LocalBentoColors
import com.arabicchristianmedia.ui.theme.LocalListColors
import com.arabicchristianmedia.ui.theme.VerseRowSpec
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
    onRestoreVerse: (SermonEntry, Int) -> Unit,
    onClear: () -> Unit,
    onSave: (String) -> Unit,
    onOpenSaved: (String, Boolean) -> Unit,
    onRenameSaved: (String, String) -> Unit,
    onDeleteSaved: (String) -> Unit,
    // Presenter clicker (sermon APIs): next/prev steps, same logic as
    // POST /api/sermons/current/next|prev. Returns true only when a
    // verse actually went live, so a failed step (empty list, at an
    // end) falls through to normal scrolling instead of swallowing
    // the key. Defaults are no-ops so previews do not need them.
    onNextSermon: () -> Boolean = { false },
    onPrevSermon: () -> Boolean = { false }
) {
    val bento = LocalBentoColors.current
    val listColors = LocalListColors.current
    val playlist = uiState.sermonPlaylist
    // Theme bands + recents marker (from the selected app theme).
    // Item 6: the same recents color marks recent verses here as in the
    // Library list (live still wins over the marker).
    val recentVerseIds = remember(uiState.recentVerses) {
        uiState.recentVerses.map { it.id }.toSet()
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }
    var showOpenSheet by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf<SavedSermon?>(null) }
    var renameTarget by remember { mutableStateOf<SavedSermon?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedSermon?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Item 14: collapsible groups — set of collapsed groupIds.
    var collapsedGroups by remember { mutableStateOf(setOf<Long>()) }
    // Item 14 (#23): share as plain text.
    val context = LocalContext.current

    // Presenter keys are handled here (this screen is foreground only
    // when the Sermon tab is selected, so keys cannot step a sermon
    // while the operator is editing a template or a dialog elsewhere).
    // PageDown/PageUp are also handled by MainActivity as a fallback;
    // a consumed key here is not delivered again there, so no double
    // step. Text fields consume their own keys first (typing a sermon
    // name does not step), and DPAD focus navigation is unchanged
    // when no sermon step applies (handler returns false).
    val sermonFocusRequester = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { sermonFocusRequester.requestFocus() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier.fillMaxSize()
                .focusRequester(sermonFocusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.PageDown, Key.DirectionRight, Key.Spacebar, Key.Enter -> onNextSermon()
                        Key.PageUp, Key.DirectionLeft -> onPrevSermon()
                        else -> false
                    }
                }
        ) {
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
                if (playlist.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "مسح كل القائمة (Clear all)",
                            tint = bento.textSecondary
                        )
                    }
                    // Item 14 (#23): share as plain text (one verse per line).
                    IconButton(onClick = {
                        val text = playlist.joinToString("\n") { entry ->
                            entry.verse.getFormattedArabicCitation()
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة العظة (Share sermon)"))
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "مشاركة (Share)",
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
                // Item 14: collapsible groups — entries grouped by groupId,
                // each with a slim header (tap to expand/collapse).
                val grouped = remember(playlist) {
                    playlist.groupBy { it.groupId }.entries.toList()
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(VerseRowSpec.RowSpacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    grouped.forEach { (groupId, entries) ->
                        val isCollapsed = groupId in collapsedGroups
                        val firstVerse = entries.firstOrNull()?.verse
                        // Group header
                        item(key = "header-$groupId") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        collapsedGroups = if (isCollapsed)
                                            collapsedGroups - groupId
                                        else collapsedGroups + groupId
                                    },
                                color = bento.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isCollapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = bento.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${firstVerse?.getFormattedArabicCitation() ?: ""} (${entries.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bento.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        // Group entries (if expanded)
                        if (!isCollapsed) {
                            itemsIndexed(
                                entries,
                                key = { _, e -> "${e.groupId}-${e.verse.id}" }
                            ) { index, entry ->
                                // Find global index for restore
                                val globalIndex = playlist.indexOf(entry)
                                SermonRow(
                                    entry = entry,
                                    index = globalIndex,
                                    isLive = uiState.activeVerse?.id == entry.verse.id && uiState.isLiveOnAir,
                                    isRecent = entry.verse.id in recentVerseIds,
                                    onTap = { onShowVerse(entry) },
                                    onOpenInReader = { onOpenInReader(entry) },
                                    onRemove = {
                                        val removed = entry to globalIndex
                                        onRemove(entry)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "تم حذف الآية من القائمة",
                                                actionLabel = "تراجع",
                                                duration = SnackbarDuration.Long
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                onRestoreVerse(removed.first, removed.second)
                                            }
                                        }
                                    },
                                    shadeColorA = listColors.bandA,
                                    shadeColorB = listColors.bandB
                                )
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
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

        // ── Clear-all confirm: names the verse count ───────────────────
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("مسح كل القائمة؟") },
                text = { Text("سيتم مسح ${playlist.size} آية من القائمة الحالية نهائياً، وفصلها عن العظة المحفوظة إن كانت مرتبطة بها.") },
                confirmButton = {
                    Button(onClick = { onClear(); showClearConfirm = false }) {
                        Text("مسح الكل")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("إلغاء") }
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
// R1: the sermon live colors moved to the token file (ui/theme/Color.kt)
// — same values, now defined once for every list.

@Composable
private fun SermonRow(
    entry: SermonEntry,
    index: Int,
    isLive: Boolean,
    onTap: () -> Unit,
    onOpenInReader: () -> Unit,
    onRemove: () -> Unit,
    shadeColorA: Color,
    shadeColorB: Color,
    isRecent: Boolean = false
) {
    val bento = LocalBentoColors.current
    val listColors = LocalListColors.current
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
        // The live row cannot be swiped away at all: removing it would
        // delete it from the list while it keeps broadcasting.
        enableDismissFromEndToStart = !isLive,
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
            shape = RoundedCornerShape(VerseRowSpec.CardCorner),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isLive -> listColors.sermonLiveNavy
                    entry.shade == 0 -> shadeColorA
                    else -> shadeColorB
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                if (isLive || isRecent) 2.dp else 1.dp,
                if (isLive) listColors.sermonLiveYellow else if (isRecent) listColors.recents else bento.border
            ),
            modifier = Modifier.fillMaxWidth().clickable { onTap() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(VerseRowSpec.RowPadding),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLive) listColors.sermonLiveYellow
                            else bento.primaryContainer,
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Text(
                        text = ArabicTextFormatter.toEasternArabicDigits(index + 1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) listColors.sermonLiveNavy else bento.primary,
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
                            fontSize = VerseRowSpec.CitationSize,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) listColors.sermonLivePaleYellow else bento.primary
                        )
                        if (isLive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = listColors.sermonLiveYellow
                            ) {
                                Text(
                                    text = "على الهواء • LIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = listColors.sermonLiveNavy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(VerseRowSpec.CitationBodySpacing))
                    Text(
                        text = verse.arabicText,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = if (isLive) listColors.sermonLivePaleYellow else bento.textPrimary,
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
                        tint = if (isLive) listColors.sermonLivePaleYellow else bento.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
