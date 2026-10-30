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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.model.SavedSermon
import com.arabicchristianmedia.model.SermonEntry
import com.arabicchristianmedia.model.Testament
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
 * - Batch-3 redesign: rows are colored by CHAPTER (the band flips whenever
 *   the chapter changes, in every grouping mode) so the operator can see
 *   where one passage ends and the next begins; the live row gets the red
 *   live treatment + LIVE badge + beacon border.
 * - Grouping control [Original|By Book|OT/NT] at the top: Original shows
 *   compact reference rows (tap = go live) in the user's manual order;
 *   By Book / OT-NT show expanded verse cards under sticky, collapsible
 *   group headers, sorted by Bible book order, then chapter, then verse.
 * - Scroll position and the live verse survive grouping switches; a
 *   "jump to live" button appears when the live verse is off-screen.
 * - The list persists across restarts; the header is ONE slim icon row
 *   (info / save / open / clear / share) so the verse list keeps all its
 *   screen space — hints live behind the ⓘ icon, every other action lives
 *   in dialogs, a modal sheet, or a swipe.
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
    // Batch-3: grouping mode + collapsed group headers (replaces the old
    // share-group collapsible headers, which were redundant).
    var groupMode by remember { mutableStateOf(SermonGroupMode.ORIGINAL) }
    var collapsedHeaders by remember { mutableStateOf(setOf<String>()) }
    var showHintsDialog by remember { mutableStateOf(false) }
    val sermonListState = rememberLazyListState()
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
            // Slim header: name + count on the left, icon-only actions on the
            // right (info / save / open / clear / share). Batch-3: the hint
            // line moved behind the ⓘ icon to save screen space.
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
                IconButton(onClick = { showHintsDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "معلومات (Hints)",
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

            // Batch-3: grouping control — compact segmented [Original|By Book|OT/NT].
            if (playlist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SermonGroupSegmented(
                    selected = groupMode,
                    onSelect = { groupMode = it }
                )
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
                // Batch-3: sections per grouping mode. Original = one
                // header-less section in the user's manual order (compact
                // rows). By Book / OT-NT = sticky, collapsible group
                // headers with expanded cards sorted by Bible book order.
                val bookOrder = remember {
                    BibleRepository.allBooks.mapIndexed { i, b -> b.id.lowercase() to i }.toMap()
                }
                val sections = remember(playlist, groupMode) {
                    buildSermonSections(playlist, groupMode, bookOrder)
                }
                // Flat item list (headers + verses, in LazyColumn order) so
                // a flat index maps 1:1 to LazyColumn item indices.
                val flatItems = remember(sections) {
                    sections.flatMap { s -> listOfNotNull(s.header) + s.verses }
                }
                val liveVerseId = uiState.activeVerse?.id
                val isLiveOnAir = uiState.isLiveOnAir
                // Batch-3: position memory — switching grouping modes keeps
                // the live verse in view instead of jumping to the top.
                LaunchedEffect(groupMode) {
                    val idx = flatItems.indexOfFirst {
                        it is SermonDisplayItem.Verse && isLiveOnAir && it.entry.verse.id == liveVerseId
                    }
                    if (idx >= 0) sermonListState.scrollToItem(idx)
                }
                // Shared remove-with-undo.
                val removeWithUndo: (SermonEntry) -> Unit = { v ->
                    val globalIndex = playlist.indexOf(v)
                    onRemove(v)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "تم حذف الآية من القائمة",
                            actionLabel = "تراجع",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onRestoreVerse(v, globalIndex)
                        }
                    }
                }
                // Batch-3: jump-to-live visibility.
                val liveFlatIndex = flatItems.indexOfFirst {
                    it is SermonDisplayItem.Verse && it.entry.verse.id == liveVerseId
                }
                val showJumpToLive by remember(liveFlatIndex, isLiveOnAir) {
                    derivedStateOf {
                        isLiveOnAir && liveFlatIndex >= 0 &&
                            sermonListState.layoutInfo.visibleItemsInfo.none { it.index == liveFlatIndex }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = sermonListState,
                        verticalArrangement = Arrangement.spacedBy(VerseRowSpec.RowSpacing),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        sections.forEach { section ->
                            val header = section.header
                            if (header != null) {
                                stickyHeader(key = header.key) {
                                    SermonGroupHeader(
                                        header = header,
                                        collapsed = header.key in collapsedHeaders,
                                        onToggle = {
                                            collapsedHeaders =
                                                if (header.key in collapsedHeaders)
                                                    collapsedHeaders - header.key
                                                else collapsedHeaders + header.key
                                        }
                                    )
                                }
                            }
                            if (header == null || header.key !in collapsedHeaders) {
                                items(
                                    section.verses,
                                    key = { it.entry.key }
                                ) { vi ->
                                    val v = vi.entry
                                    val globalIndex = vi.globalIndex
                                    val isLiveRow = isLiveOnAir && v.verse.id == liveVerseId
                                    if (groupMode == SermonGroupMode.ORIGINAL) {
                                        // Compact reference row; moves stay
                                        // inside the verse's share-group.
                                        val mates = playlist.filter { it.groupId == v.groupId }
                                        val localIdx = mates.indexOf(v)
                                        SermonCompactRow(
                                            entry = v,
                                            index = globalIndex,
                                            isLive = isLiveRow,
                                            bandA = vi.bandA,
                                            shadeColorA = listColors.bandA,
                                            shadeColorB = listColors.bandB,
                                            onTap = { onShowVerse(v) },
                                            onOpenInReader = { onOpenInReader(v) },
                                            onRemove = { removeWithUndo(v) },
                                            canMoveUp = localIdx > 0,
                                            canMoveDown = localIdx >= 0 && localIdx < mates.size - 1,
                                            onMoveUp = { onMoveUp(v) },
                                            onMoveDown = { onMoveDown(v) }
                                        )
                                    } else {
                                        SermonRow(
                                            entry = v,
                                            index = globalIndex,
                                            isLive = isLiveRow,
                                            isRecent = v.verse.id in recentVerseIds,
                                            onTap = { onShowVerse(v) },
                                            onOpenInReader = { onOpenInReader(v) },
                                            onRemove = { removeWithUndo(v) },
                                            shadeColorA = listColors.bandA,
                                            shadeColorB = listColors.bandB,
                                            bandA = vi.bandA
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Batch-3: jump-to-live button (appears when the live
                    // verse is off-screen).
                    if (showJumpToLive) {
                        androidx.compose.material3.FloatingActionButton(
                            onClick = {
                                scope.launch { sermonListState.animateScrollToItem(liveFlatIndex) }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            containerColor = listColors.sermonLiveYellow,
                            contentColor = listColors.sermonLiveNavy
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "العودة إلى الآية الحية (Jump to live verse)"
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

        // ── Batch-3: hints dialog (behind the ⓘ icon) ───────────────────
        if (showHintsDialog) {
            AlertDialog(
                onDismissRequest = { showHintsDialog = false },
                title = { Text("تلميحات (Hints)") },
                text = {
                    Text(
                        text = "• اضغط أي آية لبثّها مباشرة.\n" +
                            "• اسحب الآية لحذفها (يمكن التراجع).\n" +
                            "• أسهم ↑↓ تنقل الآية داخل مجموعتها.\n" +
                            "• أيقونة الكتاب تفتح الآية في المكتبة دون بثّ.\n" +
                            "• «الأصل» يعرض ترتيبك اليدوي بصفوف مدمجة.\n" +
                            "• «حسب السفر» و«OT / NT» يعرضان بطاقات موسّعة مرتبة.",
                        fontSize = 13.sp,
                        color = bento.textPrimary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showHintsDialog = false }) { Text("حسناً") }
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
    isRecent: Boolean = false,
    // Follow-up Phase 4 (item 7): explicit move up/down (no drag-drop
    // library in the project). Defaults keep previews working.
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    // Batch-3: chapter-alternating band (replaces the old share-group
    // shade). True = band A, false = band B.
    bandA: Boolean = true
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
                    bandA -> shadeColorA
                    else -> shadeColorB
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                // Batch-3: live beacon — thicker yellow border on the live row.
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

// ── Batch-3 sermon redesign: grouping model ─────────────────────────────

/** Grouping modes for the sermon list. */
private enum class SermonGroupMode { ORIGINAL, BY_BOOK, OT_NT }

/** One row in the sermon LazyColumn: a sticky group header or a verse. */
private sealed interface SermonDisplayItem {
    data class Header(val key: String, val title: String, val count: Int) : SermonDisplayItem
    data class Verse(
        val entry: SermonEntry,
        /** Chapter-alternating band: true = band A, false = band B. */
        val bandA: Boolean,
        /** Index in the original playlist (stable numbering + restore). */
        val globalIndex: Int
    ) : SermonDisplayItem
}

/** A header with the verses under it (header null = Original mode). */
private data class SermonSection(
    val header: SermonDisplayItem.Header?,
    val verses: List<SermonDisplayItem.Verse>
)

/**
 * Batch-3: chapter-alternating bands. Walks [entries] in display order;
 * the band flips whenever (bookId, chapter) changes, so the operator can
 * see where one passage ends and the next begins — in every grouping mode.
 */
private fun chapterBands(entries: List<SermonEntry>): List<Boolean> {
    val bands = ArrayList<Boolean>(entries.size)
    var bandA = true
    var lastKey: String? = null
    for (e in entries) {
        val key = "${e.verse.bookId.lowercase()}:${e.verse.chapter}"
        if (lastKey != null && key != lastKey) bandA = !bandA
        lastKey = key
        bands.add(bandA)
    }
    return bands
}

private fun makeTestamentSection(
    title: String,
    key: String,
    entries: List<SermonEntry>,
    playlist: List<SermonEntry>
): SermonSection {
    val bands = chapterBands(entries)
    return SermonSection(
        header = SermonDisplayItem.Header(key = "testament:$key", title = title, count = entries.size),
        verses = entries.mapIndexed { i, e ->
            SermonDisplayItem.Verse(e, bands[i], playlist.indexOf(e))
        }
    )
}

/**
 * Batch-3: builds the display sections for [mode].
 * - ORIGINAL: single header-less section, user's manual order.
 * - BY_BOOK: one section per book, sorted by Bible book order, then
 *   chapter, then verse; sticky, collapsible headers.
 * - OT_NT: two sections (Old / New Testament), same inner sort.
 */
private fun buildSermonSections(
    playlist: List<SermonEntry>,
    mode: SermonGroupMode,
    bookOrder: Map<String, Int>
): List<SermonSection> {
    val byBookThenRef = compareBy<SermonEntry>(
        { bookOrder[it.verse.bookId.lowercase()] ?: Int.MAX_VALUE },
        { it.verse.chapter },
        { it.verse.verse }
    )
    return when (mode) {
        SermonGroupMode.ORIGINAL -> {
            val bands = chapterBands(playlist)
            listOf(
                SermonSection(
                    header = null,
                    verses = playlist.mapIndexed { i, e ->
                        SermonDisplayItem.Verse(e, bands[i], i)
                    }
                )
            )
        }
        SermonGroupMode.BY_BOOK -> {
            val sorted = playlist.sortedWith(byBookThenRef)
            val sections = mutableListOf<SermonSection>()
            var currentBookId: String? = null
            var current = mutableListOf<SermonEntry>()
            fun flush() {
                if (current.isEmpty()) return
                val first = current.first()
                val book = BibleRepository.getBookById(first.verse.bookId)
                val bands = chapterBands(current)
                sections.add(
                    SermonSection(
                        header = SermonDisplayItem.Header(
                            key = "book:${first.verse.bookId.lowercase()}",
                            title = book?.arabicName ?: first.verse.bookArabicName,
                            count = current.size
                        ),
                        verses = current.mapIndexed { i, e ->
                            SermonDisplayItem.Verse(e, bands[i], playlist.indexOf(e))
                        }
                    )
                )
                current = mutableListOf()
            }
            for (e in sorted) {
                val bid = e.verse.bookId.lowercase()
                if (currentBookId != null && bid != currentBookId) flush()
                currentBookId = bid
                current.add(e)
            }
            flush()
            sections
        }
        SermonGroupMode.OT_NT -> {
            val ot = mutableListOf<SermonEntry>()
            val nt = mutableListOf<SermonEntry>()
            for (e in playlist) {
                if (BibleRepository.getBookById(e.verse.bookId)?.testament == Testament.OLD_TESTAMENT) ot.add(e)
                else nt.add(e)
            }
            listOf(
                makeTestamentSection("العهد القديم (Old Testament)", "ot", ot.sortedWith(byBookThenRef), playlist),
                makeTestamentSection("العهد الجديد (New Testament)", "nt", nt.sortedWith(byBookThenRef), playlist)
            ).filter { it.verses.isNotEmpty() }
        }
    }
}

/**
 * Batch-3: compact grouping segmented control [Original|By Book|OT/NT].
 */
@Composable
private fun SermonGroupSegmented(
    selected: SermonGroupMode,
    onSelect: (SermonGroupMode) -> Unit
) {
    val bento = LocalBentoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bento.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (mode in SermonGroupMode.values()) {
            val isSel = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSel) bento.primary else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (mode) {
                        SermonGroupMode.ORIGINAL -> "الأصل"
                        SermonGroupMode.BY_BOOK -> "حسب السفر"
                        SermonGroupMode.OT_NT -> "OT / NT"
                    },
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) Color.White else bento.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Batch-3: sticky, collapsible group header (book name or testament).
 */
@Composable
private fun SermonGroupHeader(
    header: SermonDisplayItem.Header,
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    val bento = LocalBentoColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle),
        // Solid (not translucent): sticky headers scroll content underneath.
        color = bento.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                contentDescription = if (collapsed) "توسيع (Expand)" else "طي (Collapse)",
                tint = bento.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${header.title} (${header.count})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = bento.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Batch-3: compact reference row for Original mode — number badge +
 * citation only (no verse text, no expand). Tap = go live. Slim move
 * chevrons keep the v1.9.1 share-group-local reorder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SermonCompactRow(
    entry: SermonEntry,
    index: Int,
    isLive: Boolean,
    bandA: Boolean,
    shadeColorA: Color,
    shadeColorB: Color,
    onTap: () -> Unit,
    onOpenInReader: () -> Unit,
    onRemove: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val bento = LocalBentoColors.current
    val listColors = LocalListColors.current
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
                modifier = Modifier
                    .fillMaxSize()
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
                containerColor = if (isLive) listColors.sermonLiveNavy
                                 else if (bandA) shadeColorA else shadeColorB
            ),
            border = androidx.compose.foundation.BorderStroke(
                // Batch-3: live beacon — thicker yellow border on the live row.
                if (isLive) 3.dp else 1.dp,
                if (isLive) listColors.sermonLiveYellow else bento.border
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTap() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLive) listColors.sermonLiveYellow else bento.primaryContainer
                ) {
                    Text(
                        text = ArabicTextFormatter.toEasternArabicDigits(index + 1),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) listColors.sermonLiveNavy else bento.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = entry.verse.getFormattedArabicCitation(true),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
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
                            text = "على الهواء • LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = listColors.sermonLiveNavy,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                // Slim move chevrons (share-group-local reorder).
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
                IconButton(
                    onClick = onOpenInReader,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "فتح في المكتبة (Open in Library)",
                        tint = if (isLive) listColors.sermonLivePaleYellow else bento.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
