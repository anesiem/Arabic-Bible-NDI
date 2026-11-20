package com.arabicchristianmedia.ui

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.RadioButton
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
import com.arabicchristianmedia.R
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
import androidx.compose.ui.unit.TextUnit
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
    onMoveDown: (SermonEntry) -> Unit = {},
    // 1.9.3: Undo last delete callback.
    onUndo: () -> Unit = {},
    // 1.9.15 item 12: sermon list font — null = follow universal.
    // Defaults to a no-op so previews do not need it.
    onSetSermonFontSize: (Int?) -> Unit = {}
) {
    val bento = LocalBentoColors.current
    val listColors = LocalListColors.current
    val playlist = uiState.sermonPlaylist
    // 1.9.15 item 12: sermon font — follow universal (reader size) by
    // default, or the local override. Citation keeps the 14->11
    // body/citation ratio. Compact body is 2sp smaller (min 10sp).
    val sermonBodySp = uiState.sermonFontSize ?: uiState.readerFontSize
    val sermonBodySize = sermonBodySp.sp
    val sermonCitationSize = (sermonBodySp * 11 / 14).coerceAtLeast(9).sp
    val sermonCompactBodySize = (sermonBodySp - 2).coerceAtLeast(10).sp
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
    var sermonFontDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Batch-3 sermon redesign: grouping mode (0=Original, 1=By Book, 2=OT/NT),
    // collapsed sticky headers, info dialog, and list state for position memory.
    var groupingMode by remember { mutableIntStateOf(0) }
    var collapsedHeaders by remember { mutableStateOf(setOf<String>()) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 1.9.3: bookOrder and otBookIds needed for collapse/expand all arrows (moved up from below).
    val bookOrder = remember {
        com.arabicchristianmedia.model.BibleBookGroups.ALL.flatMap { it.bookIds }
    }
    val otBookIds = remember {
        com.arabicchristianmedia.model.BibleBookGroups.ALL.take(5)
            .flatMap { it.bookIds }.toSet()
    }
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
            // 1.9.25 (item 17): one row — the title takes the flexible
            // width, the verse count is a round badge with the number
            // inside, Save + Open + Clear stay visible, and Share /
            // Settings / Info / Undo move behind the ⋮ menu.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.currentSermonName ?: context.getString(R.string.s347),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (playlist.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(bento.primary)
                    ) {
                        Text(
                            text = ArabicTextFormatter.toEasternArabicDigits(playlist.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.onPrimary
                        )
                    }
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
                        contentDescription = context.getString(R.string.s352),
                        tint = if (playlist.isNotEmpty()) bento.primary else bento.textSecondary
                    )
                }
                IconButton(onClick = { showOpenSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = context.getString(R.string.s353),
                        tint = bento.primary
                    )
                }
                if (playlist.isNotEmpty()) {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = context.getString(R.string.s354),
                            tint = bento.textSecondary
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showHeaderMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = context.getString(R.string.s883),
                            tint = bento.textSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.s356)) },
                            onClick = {
                                showHeaderMenu = false
                                val text = playlist.joinToString("\n") { entry ->
                                    entry.verse.getFormattedArabicCitation()
                                }
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.s355)))
                            },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            enabled = playlist.isNotEmpty()
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.s330)) },
                            onClick = { showHeaderMenu = false; sermonFontDialog = true },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.s351)) },
                            onClick = { showHeaderMenu = false; showInfoDialog = true },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.s350)) },
                            onClick = { showHeaderMenu = false; onUndo() },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                            enabled = uiState.canUndoDelete
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
                    val options = listOf(context.getString(R.string.s357), context.getString(R.string.s358), context.getString(R.string.s359))
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
                                    // 1.9.3: Collapse/expand all arrow (modes 1 and 2 only).
                                    // 1.9.25 (item 18): only on the ACTIVE
                                    // grouped mode — inactive segments show
                                    // plain text (there is nothing to
                                    // collapse until that mode is selected).
                                    if ((index == 1 || index == 2) && groupingMode == index) {
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
                                                contentDescription = if (allCollapsed) context.getString(R.string.s360) else context.getString(R.string.s361),
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
                        text = context.getString(R.string.s362),
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
                // (bookOrder and otBookIds are defined at the top for the 1.9.3 collapse/expand arrows.)
                val displayItems = remember(playlist, groupingMode, collapsedHeaders) {
                    buildSermonDisplayItems(context, playlist, groupingMode, collapsedHeaders, bookOrder, otBookIds)
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
                            message = context.getString(R.string.s688),
                            actionLabel = context.getString(R.string.s690),
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
                                                onOpenInReader = { onOpenInReader(entry) },
                                                onRemove = { handleRemove(entry, globalIndex) },
                                                canMoveUp = posInGroup > 0,
                                                canMoveDown = posInGroup < groupSize - 1,
                                                onMoveUp = { onMoveUp(entry) },
                                                onMoveDown = { onMoveDown(entry) },
                                                compactBodySize = sermonCompactBodySize
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
                                                onMoveDown = { onMoveDown(entry) },
                                                bodySize = sermonBodySize,
                                                citationSize = sermonCitationSize
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
                                contentDescription = context.getString(R.string.s363)
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
                title = { Text(context.getString(R.string.s323)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(context.getString(R.string.s324), fontSize = 13.sp, color = bento.textPrimary)
                        Text(context.getString(R.string.s325), fontSize = 13.sp, color = bento.textPrimary)
                        Text(context.getString(R.string.s326), fontSize = 13.sp, color = bento.textPrimary)
                        Text(
                            context.getString(R.string.s327),
                            fontSize = 13.sp,
                            color = bento.textPrimary
                        )
                        Text(context.getString(R.string.s328), fontSize = 13.sp, color = bento.textPrimary)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) { Text(context.getString(R.string.s329)) }
                }
            )
        }

        // 1.9.15 item 12: per-section font dialog — "Follow universal"
        // (default) + local sizes. Same pattern as the recents sheet.
        if (sermonFontDialog) {
            AlertDialog(
                onDismissRequest = { sermonFontDialog = false },
                title = { Text(context.getString(R.string.s330), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val followUniversal = uiState.sermonFontSize == null
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { onSetSermonFontSize(null); sermonFontDialog = false }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = followUniversal, onClick = { onSetSermonFontSize(null); sermonFontDialog = false })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.s17, uiState.readerFontSize), fontSize = 13.sp)
                        }
                        listOf(12, 14, 16, 18, 20, 24).forEach { size ->
                            val selected = uiState.sermonFontSize == size
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable { onSetSermonFontSize(size); sermonFontDialog = false }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = { onSetSermonFontSize(size); sermonFontDialog = false })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$size sp", fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ── Save dialog ─────────────────────────────────────────────────
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text(context.getString(R.string.s331)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = saveNameInput,
                            onValueChange = { saveNameInput = it },
                            label = { Text(context.getString(R.string.s332)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = context.getString(R.string.s333),
                            fontSize = 11.sp,
                            color = bento.textSecondary
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onSave(saveNameInput); showSaveDialog = false },
                        enabled = saveNameInput.isNotBlank()
                    ) { Text(context.getString(R.string.s70)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) { Text(context.getString(R.string.s7)) }
                }
            )
        }

        // ── Clear-all confirm: names the verse count ───────────────────
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(context.getString(R.string.s334)) },
                text = { Text(context.getString(R.string.s335, playlist.size)) },
                confirmButton = {
                    Button(onClick = { onClear(); showClearConfirm = false }) {
                        Text(context.getString(R.string.s336))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text(context.getString(R.string.s7)) }
                }
            )
        }

        // ── Saved sermons sheet ─────────────────────────────────────────
        if (showOpenSheet) {
            ModalBottomSheet(onDismissRequest = { showOpenSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = context.getString(R.string.s337),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.savedSermons.isEmpty()) {
                        Text(
                            text = context.getString(R.string.s338),
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
                                            text = context.getString(R.string.s339, saved.entries.size) +
                                                SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(saved.updatedAt)),
                                            fontSize = 11.sp,
                                            color = bento.textSecondary
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { menuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = context.getString(R.string.s340),
                                                tint = bento.textSecondary
                                            )
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text(context.getString(R.string.s75)) },
                                                onClick = {
                                                    menuOpen = false
                                                    showOpenSheet = false
                                                    renameInput = saved.name
                                                    renameTarget = saved
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(context.getString(R.string.s79)) },
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
                title = { Text(context.getString(R.string.s341, saved.name)) },
                text = {
                    Text(context.getString(R.string.s342))
                },
                confirmButton = {
                    Button(onClick = { onOpenSaved(saved.id, false); pendingOpen = null }) {
                        Text(context.getString(R.string.s5))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { onOpenSaved(saved.id, true); pendingOpen = null }) {
                            Text(context.getString(R.string.s6))
                        }
                        TextButton(onClick = { pendingOpen = null }) { Text(context.getString(R.string.s7)) }
                    }
                }
            )
        }

        // ── Rename dialog ───────────────────────────────────────────────
        renameTarget?.let { saved ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(context.getString(R.string.s343)) },
                text = {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text(context.getString(R.string.s344)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onRenameSaved(saved.id, renameInput); renameTarget = null },
                        enabled = renameInput.isNotBlank()
                    ) { Text(context.getString(R.string.s70)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) { Text(context.getString(R.string.s7)) }
                }
            )
        }

        // ── Delete saved sermon confirm ─────────────────────────────────
        deleteTarget?.let { saved ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(context.getString(R.string.s345, saved.name)) },
                text = { Text(context.getString(R.string.s346)) },
                confirmButton = {
                    Button(onClick = { onDeleteSaved(saved.id); deleteTarget = null }) {
                        Text(context.getString(R.string.s88))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text(context.getString(R.string.s7)) }
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
    onMoveDown: () -> Unit = {},
    // 1.9.15 item 12: verse font sizes, resolved in SermonListScreen
    // (follow universal or the local override). SermonRow is a separate
    // function, so the sizes arrive as params, not closure vals.
    bodySize: TextUnit = 15.sp,
    citationSize: TextUnit = 11.sp
) {
    val context = LocalContext.current
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
                    contentDescription = context.getString(R.string.s42),
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
                            fontSize = citationSize,
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) listColors.sermonLivePaleYellow else bento.primary
                        )
                        if (isLive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = listColors.sermonLiveYellow
                            ) {
                                Text(
                                    text = context.getString(R.string.s364),
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
                        fontSize = bodySize,
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
                            contentDescription = context.getString(R.string.s366),
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
                            contentDescription = context.getString(R.string.s367),
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
                        contentDescription = context.getString(R.string.s368),
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
    onOpenInReader: () -> Unit,
    onRemove: () -> Unit,
    bandA: Boolean = true,
    isRecent: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    // 1.9.15 item 12: verse font size, resolved in SermonListScreen.
    // SermonCompactRow is a separate function, so it arrives as a param.
    compactBodySize: TextUnit = 13.sp
) {
    val context = LocalContext.current
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
                    contentDescription = context.getString(R.string.s42),
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
                            text = context.getString(R.string.s365),
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
                            contentDescription = context.getString(R.string.s366),
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
                            contentDescription = context.getString(R.string.s367),
                            tint = if (canMoveDown) (if (isLive) listColors.sermonLivePaleYellow else bento.primary)
                                   else bento.textSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // 1.9.21: restore the Open in Library icon (same as SermonRow).
                // Batch-3 introduced SermonCompactRow without it, so the main
                // sermon list lost the jump-to-Library button.
                IconButton(
                    onClick = onOpenInReader,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = context.getString(R.string.s368),
                        tint = if (isLive) listColors.sermonLivePaleYellow else bento.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Verse text below the citation row.
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = verse.arabicText,
                fontSize = compactBodySize,
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

// 1.9.25 (item 2): takes a Context for the OT/NT group-header labels
// (s369/s370) — a pure helper, not a composable, so it cannot read
// LocalContext itself. The call site is inside remember; a locale change
// recreates the Activity, so the remembered items re-resolve.
private fun buildSermonDisplayItems(
    context: Context,
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
                val title = if (isOt) context.getString(R.string.s369) else context.getString(R.string.s370)
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
