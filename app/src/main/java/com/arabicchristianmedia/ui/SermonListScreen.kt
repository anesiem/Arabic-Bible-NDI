package com.arabicchristianmedia.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
 * - Rows are colored by CHAPTER bands (band flips when book/chapter
 *   changes); the live row gets the navy + yellow live treatment + LIVE
 *   badge so the operator can see where one area ends and the next begins.
 * - Grouping control [الأصل | حسب السفر | OT/NT] at the top: Original is
 *   compact reference rows in manual order (always preserved underneath);
 *   By Book / OT/NT show expanded cards under sticky, collapsible headers
 *   sorted by Bible book order → chapter → verse.
 * - The list persists across restarts; the header is ONE slim icon row
 *   (save / open / clear / share / info) so the verse list keeps all its
 *   screen space — every other action lives in dialogs, a modal sheet,
 *   or a swipe.
 * - Swipe a row to reveal the trash icon; a full swipe removes the verse
 *   (the live row can never be swiped away).
 * - The book icon at the end of a row opens the verse in the Library tab
 *   WITHOUT putting it on air.
 * - Position memory: switching grouping modes auto-scrolls to the live
 *   verse; a jump-to-live FAB appears when the live verse is off-screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onPrevSermon: () -> Boolean = { false },
    // Follow-up Phase 4 (item 7): no drag-drop library in the project
    // (compose-foundation drag gestures only, no reorderable), so verse
    // reordering is explicit move-up/move-down. The ViewModel constrains
    // moves inside the verse's own share-group.
    onMoveUp: (SermonEntry) -> Unit = {},
    onMoveDown: (SermonEntry) -> Unit = {}
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
    // Batch-3 sermon redesign: grouping mode (0=Original, 1=By Book, 2=OT/NT),
    // collapsed sticky headers, info dialog, and list state for position memory.
    var groupingMode by remember { mutableIntStateOf(0) }
    var collapsedHeaders by remember { mutableStateOf(setOf<String>()) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
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
            // Slim header: name + verse count on the left, icon-only actions.
            // Batch-3: subtitle is the count only; hints moved behind ⓘ.
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
                               else "${playlist.size} آية",
                        fontSize = 12.sp,
                        color = bento.textSecondary
                    )
                }
                // Undo delete button — restores the last deleted entry to its exact position.
                IconButton(
                    onClick = { viewModel.undoLastDelete() },
                    enabled = uiState.canUndoDelete
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "تراجع (Undo)",
                        tint = if (uiState.canUndoDelete) bento.textPrimary else bento.textSecondary.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "تلميحات (Hints)",
                        tint = bento.textSecondary
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

            // Batch-3: compact grouping control — one tap, no mistakes.
            // Original keeps the manual order underneath at all times.
            if (playlist.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val options = listOf("الأصل", "حسب السفر", "OT/NT")
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = groupingMode == index,
                            onClick = { groupingMode = index },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = options.size
                            ),
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (groupingMode == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                    // 1.9.3: Collapse/expand all arrow (modes 1 and 2 only)
                                    if (index == 1 || index == 2) {
                                        val allHeaderIds = remember(playlist, index, bookOrder, otBookIds) {
                                            getAllHeaderIdsForMode(playlist, index, bookOrder, otBookIds)
                                        }
                                        val allCollapsed = allHeaderIds.isNotEmpty() && allHeaderIds.all { it in collapsedHeaders }
                                        IconButton(
                                            onClick = {
                                                collapsedHeaders = if (allCollapsed) {
                                                    collapsedHeaders - allHeaderIds
                                                } else {
                                                    collapsedHeaders + allHeaderIds
                                                }
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (allCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                                contentDescription = if (allCollapsed) "Expand all" else "Collapse all",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

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
                // Batch-3: flat display list for the active grouping mode.
                // Headers are sticky + collapsible; chapter bands are
                // computed on the verse sequence in final display order.
                val bookOrder = remember {
                    com.arabicchristianmedia.model.BibleBookGroups.ALL.flatMap { it.bookIds }
                }
                val otBookIds = remember {
                    com.arabicchristianmedia.model.BibleBookGroups.ALL.take(5)
                        .flatMap { it.bookIds }.toSet()
                }
                val displayItems = remember(playlist, groupingMode, collapsedHeaders) {
                    buildSermonDisplayItems(playlist, groupingMode, collapsedHeaders, bookOrder, otBookIds)
                }
                // Share-group-local move constraints (ViewModel contract):
                // position of each entry inside its own share-group.
                val groupPositions = remember(playlist) {
                    val map = mutableMapOf<String, Pair<Int, Int>>()
                    playlist.groupBy { it.groupId }.values.forEach { group ->
                        group.forEachIndexed { i, e -> map[e.key] = i to group.size }
                    }
                    map
                }
                val liveEntryId = uiState.activeVerse?.id
                val liveIsOnAir = uiState.isLiveOnAir
                // Position memory: switching grouping modes auto-scrolls
                // to the live verse so the operator never loses it.
                LaunchedEffect(groupingMode) {
                    if (liveEntryId != null && liveIsOnAir) {
                        val idx = displayItems.indexOfFirst {
                            it is SermonDisplayItem.Verse && it.entry.verse.id == liveEntryId
                        }
                        if (idx >= 0) listState.scrollToItem(idx)
                    }
                }
                // Jump-to-live FAB: only when the live verse is off-screen.
                val liveIndex = remember(displayItems, liveEntryId, liveIsOnAir) {
                    if (liveEntryId != null && liveIsOnAir) {
                        displayItems.indexOfFirst {
                            it is SermonDisplayItem.Verse && it.entry.verse.id == liveEntryId
                        }
                    } else -1
                }
                val liveOffScreen by remember {
                    derivedStateOf {
                        liveIndex >= 0 &&
                            listState.layoutInfo.visibleItemsInfo.none { it.index == liveIndex }
                    }
                }
                // Shared remove-with-undo for both row styles.
                val handleRemove: (SermonEntry, Int) -> Unit = { entry, globalIndex ->
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
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(VerseRowSpec.RowSpacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        displayItems.forEach { item ->
                            when (item) {
                                is SermonDisplayItem.Header -> {
                                    item(key = "hdr-${item.id}") {
                                        val isCollapsed = item.id in collapsedHeaders
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    collapsedHeaders = if (isCollapsed)
                                                        collapsedHeaders - item.id
                                                    else collapsedHeaders + item.id
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
                                                    text = "${item.title} (${item.count})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = bento.textPrimary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                                is SermonDisplayItem.Verse -> {
                                    val entry = item.entry
                                    val globalIndex = playlist.indexOf(entry)
                                    val isLive = liveEntryId == entry.verse.id && liveIsOnAir
                                    val isRecent = entry.verse.id in recentVerseIds
                                    val (posInGroup, groupSize) =
                                        groupPositions[entry.key] ?: (0 to 1)
                                    item(key = "verse-${entry.groupId}-${entry.verse.id}") {
                                        if (groupingMode == 0) {
                                            // Original: compact reference rows only.
                                            SermonCompactRow(
                                                entry = entry,
                                                index = globalIndex,
                                                isLive = isLive,
                                                bandA = item.bandA,
                                                isRecent = isRecent,
                                                onTap = { onShowVerse(entry) },
                                                onRemove = { handleRemove(entry, globalIndex) },
                                                canMoveUp = posInGroup > 0,
                                                canMoveDown = posInGroup < groupSize - 1,
                                                onMoveUp = { onMoveUp(entry) },
                                                onMoveDown = { onMoveDown(entry) }
                                            )
                                        } else {
                                            SermonRow(
                                                entry = entry,
                                                index = globalIndex,
                                                isLive = isLive,
                                                bandA = item.bandA,
                                                isRecent = isRecent,
                                                onTap = { onShowVerse(entry) },
                                                onOpenInReader = { onOpenInReader(entry) },
                                                onRemove = { handleRemove(entry, globalIndex) },
                                                canMoveUp = posInGroup > 0,
                                                canMoveDown = posInGroup < groupSize - 1,
                                                onMoveUp = { onMoveUp(entry) },
                                                onMoveDown = { onMoveDown(entry) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (liveOffScreen) {
                        FloatingActionButton(
                            onClick = { scope.launch { listState.scrollToItem(liveIndex) } },
                            containerColor = listColors.sermonLiveYellow,
                            contentColor = listColors.sermonLiveNavy,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "العودة للآية المباشرة (Jump to live)"
                            )
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

        // ── Batch-3: hints dialog (tap-to-broadcast, swipe-to-delete,
        // move chevrons, grouping explainer) ──────────────────────────
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("تلميحات قائمة الخدمة") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• اضغط أي آية لبثّها مباشرة.", fontSize = 13.sp, color = bento.textPrimary)
                        Text("• اسحب الآية لليسار لحذفها (مع تراجع).", fontSize = 13.sp, color = bento.textPrimary)
                        Text("• أسهم ⌃ ⌄ تنقل الآية داخل مجموعتها.", fontSize = 13.sp, color = bento.textPrimary)
                        Text(
                            "• «الأصل» يعرض ترتيبك اليدوي صفوفاً مضغوطة. «حسب السفر» و«OT/NT» يجمّعان الآيات تحت عناوين لاصقة قابلة للطي مرتبة بسفر/إصحاح/آية — والتبديل بينها لا يمس ترتيبك الأصلي.",
                            fontSize = 13.sp,
                            color = bento.textPrimary
                        )
                        Text("• لون الصف يتبدل مع تغيّر الإصحاح لتعرف أنك انتقلت لمنطقة جديدة.", fontSize = 13.sp, color = bento.textPrimary)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) { Text("فهمت") }
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
// Batch-3: bandA replaces the old share-group shade logic — the band
// flips when (book, chapter) changes, computed in display order.

@Composable
private fun SermonRow(
    entry: SermonEntry,
    index: Int,
    isLive: Boolean,
    onTap: () -> Unit,
    onOpenInReader: () -> Unit,
    onRemove: () -> Unit,
    bandA: Boolean = true,
    isRecent: Boolean = false,
    // Follow-up Phase 4 (item 7): explicit move up/down (no drag-drop
    // library in the project). Defaults keep previews working.
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
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
                    bandA -> listColors.bandA
                    else -> listColors.bandB
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                // Batch-3: the live beacon is a 3dp yellow border.
                if (isLive) 3.dp else if (isRecent) 2.dp else 1.dp,
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
                // Follow-up Phase 4 (item 7): slim up/down move column (24dp
                // buttons, minimal width so the verse list keeps its space).
                // Edge rows show a dimmed disabled chevron.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "نقل لأعلى (Move up)",
                            tint = if (canMoveUp) (if (isLive) listColors.sermonLivePaleYellow else bento.primary)
                                   else bento.textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "نقل لأسفل (Move down)",
                            tint = if (canMoveDown) (if (isLive) listColors.sermonLivePaleYellow else bento.primary)
                                   else bento.textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
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

/**
 * Batch-3: Original mode — a compact reference row (number badge +
 * citation only, no verse text, no expand). Tap = go live. Slim
 * move chevrons kept; swipe-to-delete kept (the live row is protected).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SermonCompactRow(
    entry: SermonEntry,
    index: Int,
    isLive: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit,
    bandA: Boolean = true,
    isRecent: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
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
        enableDismissFromEndToStart = !isLive,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
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
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isLive -> listColors.sermonLiveNavy
                    bandA -> listColors.bandA
                    else -> listColors.bandB
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                if (isLive) 3.dp else if (isRecent) 2.dp else 1.dp,
                if (isLive) listColors.sermonLiveYellow else if (isRecent) listColors.recents else bento.border
            ),
            modifier = Modifier.fillMaxWidth().clickable { onTap() }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLive) listColors.sermonLiveYellow
                            else bento.primaryContainer
                ) {
                    Text(
                        text = ArabicTextFormatter.toEasternArabicDigits(index + 1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) listColors.sermonLiveNavy else bento.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = verse.getFormattedArabicCitation(true),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLive) listColors.sermonLivePaleYellow else bento.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = listColors.sermonLiveYellow
                    ) {
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = listColors.sermonLiveNavy,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "نقل لأعلى (Move up)",
                            tint = if (canMoveUp) (if (isLive) listColors.sermonLivePaleYellow else bento.primary)
                                   else bento.textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "نقل لأسفل (Move down)",
                            tint = if (canMoveDown) (if (isLive) listColors.sermonLivePaleYellow else bento.primary)
                                   else bento.textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            // Verse text below the citation row.
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = verse.arabicText,
                fontSize = 13.sp,
                color = if (isLive) listColors.sermonLivePaleYellow.copy(alpha = 0.9f) else bento.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            }
        }
    }
}

/**
 * Batch-3: flat display list for the sermon LazyColumn. Headers render
 * as stickyHeader; verses carry their chapter band (flips whenever
 * book/chapter changes, computed in final display order, all modes).
 */
private sealed interface SermonDisplayItem {
    data class Header(val id: String, val title: String, val count: Int) : SermonDisplayItem
    data class Verse(val entry: SermonEntry, val bandA: Boolean) : SermonDisplayItem
}

/**
 * 1.9.3: Get all header IDs for a grouping mode (for collapse/expand all).
 * Mode 1 (By Book): "book-$bookId" for each unique book.
 * Mode 2 (OT/NT): "testament-ot" and "testament-nt" (only those present).
 */
private fun getAllHeaderIdsForMode(
    playlist: List<SermonEntry>,
    groupingMode: Int,
    bookOrder: List<String>,
    otBookIds: Set<String>
): Set<String> {
    if (playlist.isEmpty()) return emptySet()
    return when (groupingMode) {
        1 -> playlist.map { "book-${it.verse.bookId}" }.toSet()
        2 -> {
            val ids = mutableSetOf<String>()
            if (playlist.any { it.verse.bookId in otBookIds }) ids += "testament-ot"
            if (playlist.any { it.verse.bookId !in otBookIds }) ids += "testament-nt"
            ids
        }
        else -> emptySet()
    }
}

private fun buildSermonDisplayItems(
    playlist: List<SermonEntry>,
    groupingMode: Int,
    collapsedHeaders: Set<String>,
    bookOrder: List<String>,
    otBookIds: Set<String>
): List<SermonDisplayItem> {
    if (playlist.isEmpty()) return emptyList()
    // Bands flip when (book, chapter) changes — computed on the verse
    // sequence in final display order, in ALL modes.
    fun bandsOf(ordered: List<SermonEntry>): List<Boolean> =
        com.arabicchristianmedia.model.BibleBookGroups
            .chapterBands(ordered.map { it.verse })

    val orderIndex = bookOrder.withIndex().associate { it.value to it.index }
    fun sortBiblical(list: List<SermonEntry>): List<SermonEntry> =
        list.sortedWith(
            compareBy(
                { orderIndex[it.verse.bookId] ?: Int.MAX_VALUE },
                { it.verse.chapter },
                { it.verse.verse }
            )
        )

    return when (groupingMode) {
        // By Book: expanded cards under sticky, collapsible book headers.
        1 -> {
            val sorted = sortBiblical(playlist)
            val bands = bandsOf(sorted)
            val items = mutableListOf<SermonDisplayItem>()
            var i = 0
            while (i < sorted.size) {
                val bookId = sorted[i].verse.bookId
                val headerId = "book-$bookId"
                var j = i
                while (j < sorted.size && sorted[j].verse.bookId == bookId) j++
                items += SermonDisplayItem.Header(
                    headerId,
                    sorted[i].verse.bookArabicName,
                    j - i
                )
                if (headerId !in collapsedHeaders) {
                    for (k in i until j) items += SermonDisplayItem.Verse(sorted[k], bands[k])
                }
                i = j
            }
            items
        }
        // OT/NT: two sticky collapsible groups, same inner sort.
        2 -> {
            val sorted = sortBiblical(playlist)
            val bands = bandsOf(sorted)
            val items = mutableListOf<SermonDisplayItem>()
            var i = 0
            while (i < sorted.size) {
                val isOt = sorted[i].verse.bookId in otBookIds
                val headerId = if (isOt) "testament-ot" else "testament-nt"
                val title = if (isOt) "العهد القديم" else "العهد الجديد"
                var j = i
                while (j < sorted.size && (sorted[j].verse.bookId in otBookIds) == isOt) j++
                items += SermonDisplayItem.Header(headerId, title, j - i)
                if (headerId !in collapsedHeaders) {
                    for (k in i until j) items += SermonDisplayItem.Verse(sorted[k], bands[k])
                }
                i = j
            }
            items
        }
        // Original: manual order, compact rows, no headers.
        else -> {
            val bands = bandsOf(playlist)
            playlist.mapIndexed { i, e -> SermonDisplayItem.Verse(e, bands[i]) }
        }
    }
}
