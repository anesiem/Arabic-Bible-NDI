package com.arabicchristianmedia.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.arabicchristianmedia.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.trackTextInputFocus
import com.arabicchristianmedia.model.AppThemeMode
import com.arabicchristianmedia.model.BibleBook
import com.arabicchristianmedia.model.BibleBookGroups
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.BibleVersion
import com.arabicchristianmedia.model.Testament
import com.arabicchristianmedia.ui.theme.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlin.math.abs

/**
 * 1.9.26 (item 9): the book name as shown on a picker chip — the short
 * Arabic form (leading سفر / إنجيل / رسالة stripped) or the English
 * name as-is (no prefix to strip). One helper for both grids (OT +
 * NT) so they can't drift.
 */
private fun pickerBookName(book: BibleBook, english: Boolean): String =
    if (english) book.englishName else BibleBookGroups.shortDisplayName(book.arabicName)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleReaderScreen(
    uiState: BibleNdiUiState,
    onVerseClicked: (BibleVerse) -> Unit,
    onSelectTestament: (Testament) -> Unit,
    onSelectBook: (BibleBook, Int) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onNextChapter: () -> Unit = {},
    onPrevChapter: () -> Unit = {},
    // Batch-3: keyboard left/right arrows navigate verses (RTL: left =
    // next, right = previous, matching the chapter arrows).
    onNextVerse: () -> Unit = {},
    onPrevVerse: () -> Unit = {},
    onSelectVersion: (BibleVersion) -> Unit,
    onSearchChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    // One-update item 1: search-result tap selects WITHOUT going live;
    // box-reopen re-arms remembered results; dismiss closes-but-remembers.
    onSearchResultClicked: (BibleVerse) -> Unit = {},
    onSearchBoxReopened: () -> Unit = {},
    onDismissSearch: () -> Unit = {},
    // One-update item 20: Ctrl+F nonce from MainActivity — opens the
    // search bar and focuses the field.
    searchFocusNonce: Int = 0,
    onOpenRecentVerse: (BibleVerse) -> Unit = {},
    onClearRecents: () -> Unit = {},
    // Batch-3: display-settings nonce from MainActivity's top-bar gear —
    // opens the existing "إعدادات العرض" dialog without duplicating it.
    // 2026-11-02 bugfix: the nonce is consumed via onDisplaySettingsConsumed
    // after opening, otherwise every tab switch recreates the screen and the
    // LaunchedEffect reopens the dialog (nonce stays > 0 forever).
    displaySettingsNonce: Int = 0,
    onDisplaySettingsConsumed: () -> Unit = {},
    // Item 32: keyboard-shortcuts entry inside the display-settings
    // dialog — opens the same shortcuts help card MainActivity owns.
    onShowShortcuts: () -> Unit = {},
    onIncreaseFontSize: () -> Unit = {},
    onDecreaseFontSize: () -> Unit = {},
    onSetAppThemeMode: (AppThemeMode) -> Unit = {},
    // 2026-10-28: Library-tap nonce from MainActivity — every bump
    // closes search, recents sheet and chapter picker by themselves.
    closeOverlaysNonce: Int = 0,
    // 2026-10-28: recents swipe-to-delete (sermon pattern, with Undo).
    onRemoveRecent: (BibleVerse) -> Unit = {},
    onRestoreRecent: (BibleVerse, Int) -> Unit = { _, _ -> },
    // One-update item 3: recents sheet font — null = follow universal.
    onSetRecentsFontSize: (Int?) -> Unit = {},
    // Item 12: Send recents to sermon — merge=true adds to top, false replaces.
    onSendRecentsToSermon: (Boolean) -> Unit = {},
    // 1.9.23 (Sermon Builder, Item 1): send an explicit verse list to the
    // sermon (multi-select from Recents or Library) — merge=true adds to
    // top (deduped), false replaces.
    onSendVersesToSermon: (List<BibleVerse>, Boolean) -> Unit = { _, _ -> },
    // Item 92: Library grid direction (LTR/RTL) — persisted in ui_prefs.
    onSetLibraryGridRtl: (Boolean) -> Unit = {},
    // 1.9.26 (item 9): Library picker book-name language (FOLLOW /
    // ARABIC / ENGLISH) — persisted in ui_prefs by name.
    onSetBookNameLanguage: (BookNameLanguage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSearchBar by remember { mutableStateOf(false) }
    var showRecentsSheet by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    // One-update item 20: Ctrl+F opens the search bar and focuses it.
    LaunchedEffect(searchFocusNonce) {
        if (searchFocusNonce > 0) {
            showSearchBar = true
            onSearchBoxReopened()
            searchFocusRequester.requestFocus()
        }
    }
    var displayMenuExpanded by remember { mutableStateOf(false) }
    // Batch-3: top-bar gear nonce opens the display settings dialog.
    // 2026-11-02 bugfix: consume the nonce after opening so tab switches
    // don't reopen the dialog (screen recreation re-runs this effect).
    LaunchedEffect(displaySettingsNonce) {
        if (displaySettingsNonce > 0) {
            displayMenuExpanded = true
            onDisplaySettingsConsumed()
        }
    }
    var scripturePickerExpanded by remember { mutableStateOf(false) }
    // Batch item 3: pending picker chapter jump (target chapter); the
    // effect below scrolls to verse 1 once that chapter's list arrives.
    var pickerJumpChapter by remember { mutableStateOf<Int?>(null) }
    val recentsSnackbarHostState = remember { SnackbarHostState() }
    val recentsScope = rememberCoroutineScope()
    // 1.9.23 (Sermon Builder, Item 3): Library selection-mode state —
    // transient UI state, dropped on tab switch (screen recreation).
    var libraryIsSelecting by remember { mutableStateOf(false) }
    var librarySelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLibrarySendDialog by remember { mutableStateOf(false) }
    // 1.9.23: selection is per-chapter — a chapter change clears the
    // selection (LaunchedEffect fires only when the keys change, never
    // on a plain recomposition). Selection mode itself stays on, so he
    // can start selecting in the new chapter right away.
    LaunchedEffect(uiState.selectedBook.id, uiState.selectedChapter) {
        librarySelectedIds = emptySet()
    }

    val bento = LocalBentoColors.current
    val listColors = LocalListColors.current
    // 1.9.26 (item 9): the picker's book-name language, resolved here so
    // it is always fresh — FOLLOW tracks the interface language as it
    // renders right now; an explicit choice wins over it.
    val bookNameEnglish = when (uiState.bookNameLanguage) {
        BookNameLanguage.ENGLISH -> true
        BookNameLanguage.ARABIC -> false
        BookNameLanguage.FOLLOW -> !AppLanguage.isArabicUi(context)
    }
    val lazyListState = rememberLazyListState()

    // Auto-scroll to active verse
    LaunchedEffect(uiState.activeVerse, uiState.displayedVerses) {
        uiState.activeVerse?.let { active ->
            val index = uiState.displayedVerses.indexOfFirst { it.id == active.id }
            if (index != -1) {
                lazyListState.animateScrollToItem(index)
            }
        }
    }

    // v1.9.1: when the operator jumps from a sermon row's book icon, scroll
    // the focused verse into view (the Library tab opens on it).
    LaunchedEffect(uiState.readerJumpNonce) {
        val focusId = uiState.readerFocusVerseId ?: return@LaunchedEffect
        val index = uiState.displayedVerses.indexOfFirst { it.id == focusId }
        if (index != -1) {
            lazyListState.animateScrollToItem(index)
        }
    }

    // 2026-10-28 auto-close rule: tapping the Library tab (or
    // navigating to Library) closes search, the recents sheet and
    // the chapter picker by themselves — never left open behind.
    // One-update item 1: search closes BUT REMEMBERS query+results
    // (reopening the box restores them); only the × button fully clears.
    LaunchedEffect(closeOverlaysNonce) {
        if (closeOverlaysNonce > 0) {
            showSearchBar = false
            showRecentsSheet = false
            scripturePickerExpanded = false
            onDismissSearch()
        }
    }

    // Batch item 3: a picker chapter tap opens that chapter from verse 1.
    // The active-verse effect above would otherwise jump to the live
    // verse or keep the old offset. Pending until the tapped chapter's
    // list arrives (selectedChapter match), then cleared — so later list
    // changes (search, verse taps) never re-trigger it. Declared after
    // the active-verse effect so this scroll wins for the tap.
    // 2026-11-02 bugfix: chapter arrows/swipe also scroll to verse 1.
    // They set pickerJumpChapter = -1 (sentinel: any chapter), since the
    // screen doesn't know the target chapter number in advance.
    LaunchedEffect(pickerJumpChapter, uiState.displayedVerses) {
        val target = pickerJumpChapter
        if (target != null && uiState.displayedVerses.isNotEmpty() &&
            (target == -1 || uiState.selectedChapter == target)) {
            lazyListState.scrollToItem(0)
            pickerJumpChapter = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bento.bg)
            .padding(12.dp)
            // Batch-3: keyboard arrows navigate verses (intercepted BEFORE
            // the focus system can move through the bottom tabs). RTL:
            // LEFT = next verse, RIGHT = previous verse.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onNextVerse(); true }
                    Key.DirectionRight -> { onPrevVerse(); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Scripture Browser Card
        // v1.9.1 (#9): the old collapsible "output options" card that sat
        // above this one is REMOVED — its controls moved into the
        // sub-controls row inside the browser card (chapter arrows, search,
        // ON AIR toggle), and the freed space goes to the verse list.
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bento.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Hierarchical scripture picker line
                Surface(
                    color = bento.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { scripturePickerExpanded = !scripturePickerExpanded }
                ) {
                    // 2026-10-28 centering: a Box, not a Row — the label is
                    // centred in the WHOLE bar (not in the space left
                    // between book/chapter and the arrow), with side
                    // padding so a long book/chapter can never push it
                    // off-centre or under the sides. One line + ellipsis;
                    // the whole bar stays clickable.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // 1.9.25 (item 3): the "الكتاب المقدس - إضغط هنا"
                        // hint is gone — the book name gets the full
                        // flexible width instead (everything except the
                        // 26dp collapse arrow + gaps).
                        // Item 93: the book + chapter reference sits on the
                        // RIGHT side; the collapse arrow moves to the left.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = maxWidth - 40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = bento.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                // 1.9.23 (item 11): ALWAYS the browsing book + chapter —
                                // follows the swipe, so he always knows which chapter he
                                // is on. Supersedes 1.9.14 item 25 (which showed the live
                                // verse's citation here and froze the reference while
                                // browsing). The live verse's citation moved to the top
                                // bar (MainActivity), matching /remote.
                                // 1.9.26 (item 9): the book name follows
                                // the picker's name language; the chapter
                                // digits match it (Eastern for Arabic,
                                // Western for English) so the reference
                                // reads as one unit.
                                text = if (bookNameEnglish) "${uiState.selectedBook.englishName} ${uiState.selectedChapter}" else "${uiState.selectedBook.arabicName} ${ArabicTextFormatter.toEasternArabicDigits(uiState.selectedChapter)}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier.align(Alignment.CenterStart).size(26.dp).background(bento.primary.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (scripturePickerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = bento.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                AnimatedVisibility(visible = scripturePickerExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 1.9.4: full-height picker (was max 380.dp) — covers
                            // the library behind, no peeking.
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .background(bento.card)
                            .padding(12.dp)
                    ) {
                        // Item 18: OT/NT filter + Search on one top line.
                        // Font size moved to gear at bottom. Recents removed.
                        var otNtFilter by remember { mutableStateOf<Testament?>(null) }
                        var bookSearch by remember { mutableStateOf("") }
                        // Batch-4: search is an icon button, not a full-width field.
                        var searchExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = otNtFilter == Testament.OLD_TESTAMENT,
                                onClick = { otNtFilter = if (otNtFilter == Testament.OLD_TESTAMENT) null else Testament.OLD_TESTAMENT },
                                label = { Text(context.getString(R.string.s29), fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = otNtFilter == Testament.NEW_TESTAMENT,
                                onClick = { otNtFilter = if (otNtFilter == Testament.NEW_TESTAMENT) null else Testament.NEW_TESTAMENT },
                                label = { Text(context.getString(R.string.s30), fontSize = 12.sp) }
                            )
                            // Batch-4: search icon button instead of full-width field.
                            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                                Icon(Icons.Default.Search, context.getString(R.string.s31), tint = bento.textPrimary)
                            }
                            // 1.9.14 item 19: grid direction as a single
                            // icon-only toggle at the far end of the search
                            // row (the old grid row + spacer are gone).
                            Spacer(Modifier.weight(1f))
                            // 1.9.26 (item 9): book-name language — the chip
                            // shows the language the book names are in now
                            // (العربية / English, the language's own name,
                            // like the web language select) and cycles
                            // FOLLOW → Arabic → English on tap. Selected =
                            // an explicit choice is active; unselected =
                            // following the interface language.
                            FilterChip(
                                selected = uiState.bookNameLanguage != BookNameLanguage.FOLLOW,
                                onClick = { onSetBookNameLanguage(uiState.bookNameLanguage.next()) },
                                label = { Text(if (bookNameEnglish) "English" else "العربية", fontSize = 12.sp) }
                            )
                            IconButton(onClick = { onSetLibraryGridRtl(!uiState.libraryGridRtl) }) {
                                Icon(
                                    if (uiState.libraryGridRtl) Icons.Default.FormatTextdirectionRToL else Icons.Default.FormatTextdirectionLToR,
                                    context.getString(R.string.s32),
                                    tint = bento.textPrimary
                                )
                            }
                        }
                        // Batch-4: expandable search field below the OT/NT row.
                        if (searchExpanded) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = bookSearch,
                                onValueChange = { bookSearch = it },
                                placeholder = { Text(context.getString(R.string.s33), fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().trackTextInputFocus(),
                                trailingIcon = {
                                    IconButton(onClick = { searchExpanded = false; bookSearch = "" }) {
                                        Icon(Icons.Default.Close, context.getString(R.string.s34), tint = bento.textSecondary)
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // One-update item 21: the grouped color grid picker replaced
                        // the OT/NT tabs + long scrolling book list. 10
                        // Bible-study groups; books as short chips with
                        // سفر/إنجيل/رسالة prefix stripped for display only
                        // (citations keep the full names).
                        // Follow-up item 5: category titles removed — one
                        // centered wrapping flow; group colors survive as
                        // chip background tints (theme variables).
                        // J15: the picker font scales book names and chapter
                        // numbers, not only verses (18 -> 13sp, 30 -> 21sp).
                        // Item 18: recents removed (saves space).
                        // Book label size scales with reader font.
                        // 2026-11-02: float division for smooth scaling (was integer truncation).
                        val bookLabelSize = (uiState.readerFontSize * 13f / 18f).coerceIn(11f, 24f).sp

                        // ONE generic search across all 66 books (moved out
                        // of the OT/NT tabs). Picking a book follows its
                        // Item 18: search is at top (with OT/NT). This old field removed.
                        val bookQuery = bookSearch.trim()
                        val isDarkMode = when (uiState.appThemeMode) {
                            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                            AppThemeMode.LIGHT -> false
                            AppThemeMode.DARK -> true
                        }
                        // Follow-up item 5: single CENTERED wrapping flow of
                        // all 66 books — category titles removed. The 10
                        // Bible-study group colors survive as chip background
                        // tints (theme variables via BookGroupColors, dark/
                        // light aware). The OT/NT filter applies live to the
                        // whole grid; search narrows the same grid (tap the
                        // OT/NT chip again to clear the filter).
                        val bookIdToGroupId = remember {
                            buildMap<String, String> {
                                BibleBookGroups.ALL.forEach { group ->
                                    group.bookIds.forEach { id -> put(id, group.id) }
                                }
                            }
                        }
                        val pickerBooks = remember(bookQuery, otNtFilter) {
                            BibleRepository.allBooks.filter { book ->
                                (otNtFilter == null || book.testament == otNtFilter) &&
                                        (bookQuery.isEmpty() ||
                                                book.arabicName.contains(bookQuery) ||
                                                book.englishName.contains(bookQuery, ignoreCase = true))
                            }
                        }
                        // Item 92: ONE direction for BOTH grids (books +
                        // chapters flip together, never mixed). LTR = first
                        // item top-left, RTL = first item top-right.
                        // Default LTR (persisted in ui_prefs).
                        val gridDirection = if (uiState.libraryGridRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        CompositionLocalProvider(LocalLayoutDirection provides gridDirection) {
                        if (pickerBooks.isEmpty()) {
                            Text(
                                context.getString(R.string.s35),
                                fontSize = 12.sp, color = bento.textSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            @OptIn(ExperimentalLayoutApi::class)
                            // 1.9.4: OT and NT in separate sections — NT starts
                            // on a new line (was one mixed flow).
                            val otBooks = pickerBooks.filter { it.testament == Testament.OLD_TESTAMENT }
                            val ntBooks = pickerBooks.filter { it.testament == Testament.NEW_TESTAMENT }
                            if (otBooks.isNotEmpty()) {
                                // v1.9.5: OT/NT header labels removed per user request —
                                // chips stay grouped OT then NT, just without headers.
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    // Item 92: Start follows the grid-direction control
                                    // (LTR = left, RTL = right); first book top-left
                                    // in LTR, top-right in RTL — same as chapters.
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    otBooks.forEach { book ->
                                        val groupColor = BookGroupColors.color(
                                            bookIdToGroupId[book.id] ?: "", isDarkMode
                                        )
                                        FilterChip(
                                            selected = book.id == uiState.selectedBook.id,
                                            onClick = {
                                                pickerJumpChapter = 1
                                                onSelectBook(book, 1)
                                            },
                                            label = {
                                                Text(
                                                    pickerBookName(book, bookNameEnglish),
                                                    fontSize = bookLabelSize, maxLines = 1
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = groupColor.copy(alpha = 0.16f)
                                            )
                                        )
                                    }
                                }
                            }
                            if (ntBooks.isNotEmpty()) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    // Item 92: Start follows the grid-direction control
                                    // (LTR = left, RTL = right); first book top-left
                                    // in LTR, top-right in RTL — same as chapters.
                                    horizontalArrangement = Arrangement.Start,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ntBooks.forEach { book ->
                                        val groupColor = BookGroupColors.color(
                                            bookIdToGroupId[book.id] ?: "", isDarkMode
                                        )
                                        FilterChip(
                                            selected = book.id == uiState.selectedBook.id,
                                            onClick = {
                                                pickerJumpChapter = 1
                                                onSelectBook(book, 1)
                                            },
                                            label = {
                                                Text(
                                                    pickerBookName(book, bookNameEnglish),
                                                    fontSize = bookLabelSize, maxLines = 1
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = groupColor.copy(alpha = 0.16f)
                                            )
                                        )
                                    }
                                }
                            }
                        }


                        // Chapters Grid
                        // Chapter number grid (tap = jump). The +/- stepper
                        // row was removed in batch-3 — tap a circle directly.
                        Box(modifier = Modifier.heightIn(min = 120.dp, max = 220.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 52.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items((1..uiState.selectedBook.totalChapters).toList()) { ch -> // Item 92: ascending from 1, in the grid direction (LTR = 1 top-left, RTL = 1 top-right)
                                    val isSelected = ch == uiState.selectedChapter
                                    // J15: chapter numbers scale with the
                                    // picker font (18 -> 14sp, 30 -> 23sp)
                                    // and the circle grows with the number,
                                    // so 3-digit chapters never overflow.
                                    // 2026-11-02: float division for smooth scaling.
                                    val chNumSize = (uiState.readerFontSize * 14f / 18f).coerceIn(11f, 26f).sp
                                    val chCircle = (48 + (uiState.readerFontSize - 18).coerceAtLeast(0) * 2).coerceAtMost(72).dp
                                    Box(
                                        modifier = Modifier.size(chCircle).clip(CircleShape)
                                            .background(if (isSelected) bento.primary else bento.surfaceVariant)
                                            .clickable { 
                                                pickerJumpChapter = ch
                                                onSelectChapter(ch)
                                                scripturePickerExpanded = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(ArabicTextFormatter.toEasternArabicDigits(ch), color = if (isSelected) bento.onPrimary else bento.textPrimary, fontSize = chNumSize)
                                    }
                                }
                            }
                        }
                        } // Item 92: CompositionLocalProvider(gridDirection)

                    }
                }

                // Verse List source (moved up 1.9.23: the selection-mode
                // header below needs the visible list for Select All).
                val versesToShow = if (uiState.isSearching) uiState.searchResults else uiState.displayedVerses
                // 1.9.23 (Sermon Builder, Items 3+5): the verses currently
                // selected in the Library, in list order (chapter order
                // outside search); the Select All toggle set.
                val librarySelectedVerses = versesToShow.filter { it.id in librarySelectedIds }
                val libraryAllIds = versesToShow.map { it.id }.toSet()
                val libraryAllSelected = libraryAllIds.isNotEmpty() && librarySelectedIds.containsAll(libraryAllIds)

                // Sub-controls (v1.9.1 #9): chapter steppers + search on the
                // left, ON AIR toggle + appearance on the right. This row
                // replaces the removed output-options card.
                // 1.9.23: in selection mode this row becomes the selection
                // header (Done / N selected / Select All / Add to Sermon).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (libraryIsSelecting) {
                        TextButton(onClick = {
                            libraryIsSelecting = false
                            librarySelectedIds = emptySet()
                        }) {
                            Text(context.getString(R.string.s22), fontSize = 12.sp)
                        }
                        Text(
                            text = context.getString(R.string.s23, librarySelectedIds.size),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.textPrimary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = {
                            librarySelectedIds = if (libraryAllSelected) emptySet() else libraryAllIds
                        }) {
                            Text(
                                if (libraryAllSelected) context.getString(R.string.s24) else context.getString(R.string.s25),
                                fontSize = 12.sp
                            )
                        }
                        if (librarySelectedIds.isNotEmpty()) {
                            TextButton(onClick = {
                                if (uiState.sermonPlaylist.isNotEmpty()) {
                                    showLibrarySendDialog = true
                                } else {
                                    onSendVersesToSermon(librarySelectedVerses, true)
                                    libraryIsSelecting = false
                                    librarySelectedIds = emptySet()
                                }
                            }) {
                                Text(context.getString(R.string.s26), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                    // Ashraf's mapping (Arabic is RTL): the LEFT arrow moves
                    // to the NEXT chapter, the RIGHT arrow to the PREVIOUS
                    // chapter. Chapter browsing never changes the live
                    // outputs — the server holds the on-air verse.
                    // 2026-11-02 bugfix: arrows scroll the new chapter to verse 1
                    // (pickerJumpChapter = -1 sentinel; see LaunchedEffect above).
                    IconButton(onClick = { pickerJumpChapter = -1; onNextChapter() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, context.getString(R.string.s37), tint = bento.textSecondary)
                    }
                    IconButton(onClick = { pickerJumpChapter = -1; onPrevChapter() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, context.getString(R.string.s38), tint = bento.textSecondary)
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    // v1.9.5: Search + Recent grouped on the right as "find" actions
                    // (matches /remote page). Recent is now a labeled button, not icon-only.
                    TextButton(onClick = { showRecentsSheet = true }) {
                        Text(context.getString(R.string.s39), color = bento.textSecondary, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) onClearSearch()
                        // One-update item 1: reopening re-arms remembered
                        // results (no retyping); closing via the icon is an
                        // explicit dismiss like ×, so it fully clears.
                        else onSearchBoxReopened()
                    }) {
                        Icon(if (showSearchBar) Icons.Default.Close else Icons.Default.Search, null, tint = bento.textSecondary)
                    }
                    // 1.9.23 (Sermon Builder, Item 3): "Select" enters
                    // selection mode (checkmark icon, next to Search).
                    // Long-press is NOT used (reserved for cross-references).
                    IconButton(onClick = {
                        librarySelectedIds = emptySet()
                        libraryIsSelecting = true
                    }) {
                        Icon(Icons.Filled.Check, context.getString(R.string.s21), tint = bento.textSecondary)
                    }
                    // Batch-3: the ON AIR button and the display-settings
                    // gear moved to MainActivity's top bar (ON AIR replaces
                    // the LIVE pill; the gear sits beside the keyboard
                    // icon on the Library tab). This row keeps chapter
                    // navigation + search + recents only.
                    } // else (not selecting) — 1.9.23
                }

                // 1.9.23 (Sermon Builder, Item 3): Replace/Merge dialog for
                // the Library multi-select add (only when the sermon list
                // is non-empty; an empty list merges directly).
                if (showLibrarySendDialog) {
                    SendToSermonDialog(
                        title = context.getString(R.string.s1),
                        text = context.getString(R.string.s2),
                        onReplace = {
                            onSendVersesToSermon(librarySelectedVerses, false)
                            showLibrarySendDialog = false
                            libraryIsSelecting = false
                            librarySelectedIds = emptySet()
                        },
                        onMerge = {
                            onSendVersesToSermon(librarySelectedVerses, true)
                            showLibrarySendDialog = false
                            libraryIsSelecting = false
                            librarySelectedIds = emptySet()
                        },
                        onDismiss = { showLibrarySendDialog = false }
                    )
                }

                if (showSearchBar) {
                    // Item 8: the search field is RTL — the "بحث…" placeholder
                    // used to inherit the system (LTR) direction, which put
                    // the ellipsis BEFORE the word; in RTL it lands after it.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    OutlinedTextField(
                        value = uiState.searchQuery, onValueChange = onSearchChanged,
                        placeholder = { Text(context.getString(R.string.s36), maxLines = 1) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester)
                            .trackTextInputFocus(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                // v1.9.19 item 4: live result count pill INSIDE
                                // the field, next to the × — no extra line.
                                // Only when a count applies (4+ characters);
                                // otherwise the field looks exactly as it
                                // did (just the ×). The pill uses theme
                                // variables (bento.primary / bento.onPrimary)
                                // so it follows the active theme — no
                                // hardcoded colors. The number is the TRUE
                                // total, not the 100 listed.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val count = uiState.searchResultCount
                                    if (count != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(bento.primary, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = bento.onPrimary
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        onClearSearch()
                                        searchFocusRequester.requestFocus()
                                    }) {
                                        Icon(Icons.Default.Close, context.getString(R.string.s40), tint = bento.textSecondary)
                                    }
                                }
                            }
                        }
                    )
                    }
                }

                // Session recents sheet: verses opened this session, most
                // recent first. Tapping one jumps the reader to it WITHOUT
                // putting it on air (the verse itself is tapped for that,
                // as usual). No permanent screen space is taken.
                if (showRecentsSheet) {
                    // Batch item 2: the 75% height below was capped at ~50% by
                    // the sheet's default partially-expanded state; skipping it
                    // opens the sheet fully expanded so 75% shows at once.
                    ModalBottomSheet(
                        onDismissRequest = { showRecentsSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ) {
                        // Item 9: fixed 75% height (was content-sized, ~50%).
                        // Items 6+7: chapter bands (same pair as the Library)
                        // own the row background; rows are Arabic-only.
                        // Theme bands (same pair as the Sermon list, from the theme).
                        // One-update item 3: the whole sheet is RTL — header
                        // and rows align right for Arabic.
                        // One-update item 3: chapter bands via the
                        // testable BibleBookGroups.chapterBands.
                        val recentsBandA = remember(uiState.recentVerses) {
                            BibleBookGroups.chapterBands(uiState.recentVerses)
                        }
                        // One-update item 3: sheet font — follow universal
                        // (reader size) by default, or the local override.
                        // Citation keeps the 14->11 body/citation ratio.
                        val recentsBodySp = uiState.recentsFontSize ?: uiState.readerFontSize
                        val recentsBodySize = recentsBodySp.sp
                        val recentsCitationSize = (recentsBodySp * 11 / 14).coerceAtLeast(9).sp
                        var recentsFontDialog by remember { mutableStateOf(false) }
                        // Batch-3: send-to-sermon dialog state (hoisted for
                        // the restructured header row below).
                        var showSendDialog by remember { mutableStateOf(false) }
                        // 1.9.23 (Sermon Builder, Item 2): selection-mode
                        // state — transient UI state, dropped when the
                        // sheet closes (no stale selection survives).
                        var recentsIsSelecting by remember { mutableStateOf(false) }
                        var recentsSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
                        var showSelectionSendDialog by remember { mutableStateOf(false) }
                        // 1.9.23 (Items 2+5): the selected recents, in list
                        // order; the Select All toggle set.
                        val recentsSelectedVerses = uiState.recentVerses.filter { it.id in recentsSelectedIds }
                        val recentsAllIds = uiState.recentVerses.map { it.id }.toSet()
                        val recentsAllSelected = recentsAllIds.isNotEmpty() && recentsSelectedIds.containsAll(recentsAllIds)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // Batch-3: RTL header — RIGHT: gear + share icon,
                            // CENTER: title, LEFT: clear (destructive, far
                            // from the primary actions).
                            // 1.9.23: in selection mode this row becomes the
                            // selection header (Done / N selected / Select
                            // All / Add to Sermon).
                            if (recentsIsSelecting) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        recentsIsSelecting = false
                                        recentsSelectedIds = emptySet()
                                    }) {
                                        Text(context.getString(R.string.s22), fontSize = 12.sp)
                                    }
                                    Text(
                                        text = "${recentsSelectedIds.size} selected",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bento.textPrimary,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center
                                    )
                                    TextButton(onClick = {
                                        recentsSelectedIds = if (recentsAllSelected) emptySet() else recentsAllIds
                                    }) {
                                        Text(
                                            if (recentsAllSelected) context.getString(R.string.s24) else context.getString(R.string.s25),
                                            fontSize = 12.sp
                                        )
                                    }
                                    if (recentsSelectedIds.isNotEmpty()) {
                                        TextButton(onClick = {
                                            if (uiState.sermonPlaylist.isNotEmpty()) {
                                                showSelectionSendDialog = true
                                            } else {
                                                onSendVersesToSermon(recentsSelectedVerses, true)
                                                recentsIsSelecting = false
                                                recentsSelectedIds = emptySet()
                                                showRecentsSheet = false
                                            }
                                        }) {
                                            Text(context.getString(R.string.s26), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                // One-update item 3: per-section font gear.
                                IconButton(onClick = { recentsFontDialog = true }) {
                                    Icon(Icons.Filled.Settings, context.getString(R.string.s16), tint = bento.textSecondary)
                                }
                                if (uiState.recentVerses.isNotEmpty()) {
                                    IconButton(onClick = { showSendDialog = true }) {
                                        Icon(Icons.Filled.Share, context.getString(R.string.s20), tint = bento.textSecondary)
                                    }
                                }
                                Text(
                                    text = context.getString(R.string.s18),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = bento.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                // 1.9.23 (Sermon Builder, Item 2): "Select"
                                // enters selection mode (checkmark icon,
                                // next to Clear).
                                if (uiState.recentVerses.isNotEmpty()) {
                                    IconButton(onClick = {
                                        recentsSelectedIds = emptySet()
                                        recentsIsSelecting = true
                                    }) {
                                        Icon(Icons.Filled.Check, context.getString(R.string.s21), tint = bento.textSecondary)
                                    }
                                }
                                // Item 10: user reset for the persisted recents
                                // (stays open so the empty state confirms it).
                                if (uiState.recentVerses.isNotEmpty()) {
                                    TextButton(onClick = { onClearRecents() }) {
                                        Text(context.getString(R.string.s19), fontSize = 12.sp)
                                    }
                                }
                            }
                            } // else (not selecting) — 1.9.23
                            // Item 12: Send recents to sermon (all).
                            if (showSendDialog) {
                                SendToSermonDialog(
                                    title = context.getString(R.string.s3),
                                    text = context.getString(R.string.s4),
                                    onReplace = {
                                        onSendRecentsToSermon(false)
                                        showSendDialog = false
                                    },
                                    onMerge = {
                                        onSendRecentsToSermon(true)
                                        showSendDialog = false
                                    },
                                    onDismiss = { showSendDialog = false }
                                )
                            }
                            // 1.9.23 (Sermon Builder, Item 2): Replace/Merge
                            // dialog for the multi-select add (only when the
                            // sermon list is non-empty; an empty list merges
                            // directly). After adding, the sheet closes and
                            // the app lands on the sermon list (nonce bump).
                            if (showSelectionSendDialog) {
                                SendToSermonDialog(
                                    title = context.getString(R.string.s1),
                                    text = context.getString(R.string.s2),
                                    onReplace = {
                                        onSendVersesToSermon(recentsSelectedVerses, false)
                                        showSelectionSendDialog = false
                                        recentsIsSelecting = false
                                        recentsSelectedIds = emptySet()
                                        showRecentsSheet = false
                                    },
                                    onMerge = {
                                        onSendVersesToSermon(recentsSelectedVerses, true)
                                        showSelectionSendDialog = false
                                        recentsIsSelecting = false
                                        recentsSelectedIds = emptySet()
                                        showRecentsSheet = false
                                    },
                                    onDismiss = { showSelectionSendDialog = false }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (uiState.recentVerses.isEmpty()) {
                                Text(
                                    text = context.getString(R.string.s27),
                                    fontSize = 13.sp,
                                    color = bento.textSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(VerseRowSpec.RowSpacing),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    itemsIndexed(uiState.recentVerses, key = { _, verse -> verse.id }) { index, verse ->
                                        // 2026-10-28 recents swipe = sermon
                                        // swipe exactly: EndToStart only,
                                        // live row guarded, Delete background,
                                        // snackbar Undo restores at same index.
                                        val isLiveRecent = verse.id == uiState.activeVerse?.id && uiState.isLiveOnAir
                                        val recentDismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    val removed = verse to index
                                                    onRemoveRecent(verse)
                                                    recentsScope.launch {
                                                        val result = recentsSnackbarHostState.showSnackbar(
                                                            message = context.getString(R.string.s689),
                                                            actionLabel = context.getString(R.string.s690),
                                                            duration = SnackbarDuration.Long
                                                        )
                                                        if (result == SnackbarResult.ActionPerformed) {
                                                            onRestoreRecent(removed.first, removed.second)
                                                        }
                                                    }
                                                    true
                                                } else false
                                            }
                                        )
                                        SwipeToDismissBox(
                                            state = recentDismissState,
                                            enableDismissFromStartToEnd = false,
                                            // 1.9.23: swipe-to-delete is off
                                            // in selection mode (selection
                                            // only — no gesture conflicts).
                                            enableDismissFromEndToStart = !isLiveRecent && !recentsIsSelecting,
                                            backgroundContent = {
                                                Box(
                                                    modifier = Modifier.fillMaxSize()
                                                        .clip(RoundedCornerShape(VerseRowSpec.CardCorner))
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
                                        // 1.9.23: in selection mode a checkbox
                                        // sits at the start of the row and a
                                        // tap toggles it — it must NOT open
                                        // the verse. Outside selection mode
                                        // the Row holds only the column, so
                                        // the layout is identical to before.
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(VerseRowSpec.CardCorner))
                                                .background(if (recentsBandA[index]) listColors.bandA else listColors.bandB)
                                                .clickable {
                                                    if (recentsIsSelecting) {
                                                        recentsSelectedIds = if (verse.id in recentsSelectedIds) recentsSelectedIds - verse.id else recentsSelectedIds + verse.id
                                                    } else {
                                                        showRecentsSheet = false
                                                        onClearSearch()
                                                        onOpenRecentVerse(verse)
                                                    }
                                                }
                                                .padding(VerseRowSpec.RowPadding),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (recentsIsSelecting) {
                                                VerseSelectionCheckbox(
                                                    checked = verse.id in recentsSelectedIds,
                                                    onCheckedChange = { checked ->
                                                        recentsSelectedIds = if (checked) recentsSelectedIds + verse.id else recentsSelectedIds - verse.id
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = verse.getFormattedArabicCitation(),
                                                fontSize = recentsCitationSize,
                                                fontWeight = FontWeight.Bold,
                                                color = bento.primary
                                            )
                                            Spacer(modifier = Modifier.height(VerseRowSpec.CitationBodySpacing))
                                            Text(
                                                text = verse.arabicText,
                                                fontSize = recentsBodySize,
                                                color = bento.textPrimary
                                            )
                                            }
                                        }
                                        }
                                    }
                                }
                            }
                            SnackbarHost(hostState = recentsSnackbarHostState)
                        }
                        // One-update item 3: per-section font dialog —
                        // "Follow universal" (default) + local sizes.
                        if (recentsFontDialog) {
                            AlertDialog(
                                onDismissRequest = { recentsFontDialog = false },
                                title = { Text(context.getString(R.string.s16), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val followUniversal = uiState.recentsFontSize == null
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                .clickable { onSetRecentsFontSize(null); recentsFontDialog = false }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = followUniversal, onClick = { onSetRecentsFontSize(null); recentsFontDialog = false })
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(context.getString(R.string.s17, uiState.readerFontSize), fontSize = 13.sp)
                                        }
                                        listOf(12, 14, 16, 18, 20, 24).forEach { size ->
                                            val selected = uiState.recentsFontSize == size
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                                    .clickable { onSetRecentsFontSize(size); recentsFontDialog = false }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(selected = selected, onClick = { onSetRecentsFontSize(size); recentsFontDialog = false })
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("$size sp", fontSize = 13.sp)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                        } // CompositionLocalProvider(Rtl) — one-update item 3
                    }
                }

                // Verse List
                // v1.9.1: verses that appear in the sermon list get a finder
                // tint in ANY chapter the operator browses to.
                val sermonVerseIds = remember(uiState.sermonPlaylist) {
                    uiState.sermonPlaylist.map { it.verse.id }.toSet()
                }
                // Item 6: recents marker set (same verses the sheet lists).
                // 2026-11-02: case-insensitive matching (defensive against bookId case drift).
                val recentVerseIds = remember(uiState.recentVerses) {
                    uiState.recentVerses.map { "${it.bookId.lowercase()}_${it.chapter}_${it.verse}" }.toSet()
                }
                if (uiState.isLoadingBible && versesToShow.isEmpty()) {
                    // First-launch database copy: show progress instead of a blank list.
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = bento.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                context.getString(R.string.s41),
                                fontSize = 13.sp,
                                color = bento.textSecondary
                            )
                        }
                    }
                } else
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(uiState.selectedBook.id, uiState.selectedChapter) {
                            coroutineScope {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var dragX = 0f
                                    var dragY = 0f
                                    var swiped = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break

                                        val dragAmount = change.positionChange()
                                        dragX += dragAmount.x
                                        dragY += dragAmount.y

                                        // v1.9.1 (#9): sensitivity raised at
                                        // Ashraf's request — ~36dp of travel
                                        // (was 120 raw px) at a gentler 1.2:1
                                        // direction ratio (was 1.5:1), so one
                                        // natural flick turns the chapter.
                                        if (!swiped && abs(dragX) > 36.dp.toPx() && abs(dragX) > abs(dragY) * 1.2f) {
                                            swiped = true
                                            change.consume()
                                            // v1.9.1 (#9): Arabic is RTL —
                                            // swipe RIGHT = NEXT chapter
                                            // (page turn), LEFT = previous.
                                            // 2026-11-02 bugfix: swipe also scrolls to verse 1.
                                            if (dragX > 0) {
                                                pickerJumpChapter = -1; onNextChapter()
                                            } else {
                                                pickerJumpChapter = -1; onPrevChapter()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(VerseRowSpec.RowSpacing)
                ) {
                    itemsIndexed(versesToShow, key = { _, verse -> verse.id }) { _, verse ->
                        val isCued = uiState.activeVerse?.id == verse.id
                        BentoVerseCard(
                            verse = verse,
                            isCued = isCued,
                            isLiveOnAir = isCued && uiState.isLiveOnAir,
                            isFocused = uiState.readerFocusVerseId == verse.id,
                            inSermonList = verse.id in sermonVerseIds,
                            isRecent = "${verse.bookId.lowercase()}_${verse.chapter}_${verse.verse}" in recentVerseIds,
                            version = uiState.bibleVersion,
                            fontSizeSp = uiState.readerFontSize,
                            showCitation = uiState.isSearching,
                            lineSpacingArabic = uiState.activeTemplate.lineSpacingArabic,
                            lineSpacingEnglish = uiState.activeTemplate.lineSpacingEnglish,
                            // 1.9.23 (Sermon Builder, Item 3): selection mode
                            // — the card shows a checkbox and taps toggle it.
                            isSelecting = libraryIsSelecting,
                            isSelected = verse.id in librarySelectedIds,
                            onSelectionToggle = {
                                librarySelectedIds = if (verse.id in librarySelectedIds) librarySelectedIds - verse.id else librarySelectedIds + verse.id
                            },
                            // One-update item 1: a search-result tap selects
                            // (cues + closes the box, remembers the query)
                            // and NEVER goes live; a Library-list tap keeps
                            // today's go-live behaviour.
                            onClick = {
                                if (uiState.isSearching) {
                                    onSearchResultClicked(verse)
                                    showSearchBar = false
                                } else {
                                    onVerseClicked(verse)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Appearance Menu
    if (displayMenuExpanded) {
        AlertDialog(
            onDismissRequest = { displayMenuExpanded = false },
            title = { Text(context.getString(R.string.s8)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(context.getString(R.string.s9, uiState.readerFontSize))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onDecreaseFontSize) { Text("-") }
                        Text(uiState.readerFontSize.toString(), fontWeight = FontWeight.Bold)
                        Button(onClick = onIncreaseFontSize) { Text("+") }
                    }
                    HorizontalDivider()
                    Text(context.getString(R.string.s10))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = uiState.appThemeMode == mode,
                                onClick = { onSetAppThemeMode(mode) },
                                label = { Text(context.getString(mode.labelRes)) }
                            )
                        }
                    }
                    // Item 32: keyboard-shortcuts entry — closes this
                    // dialog first, then opens the same shortcuts help
                    // card (no second dialog stacked on top of this one).
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            displayMenuExpanded = false
                            onShowShortcuts()
                        }
                    ) {
                        Icon(Icons.Filled.Keyboard, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.s14))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { displayMenuExpanded = false }) { Text(context.getString(R.string.s15)) } }
        )
    }
}

/**
 * 1.9.23 (Sermon Builder, Item 1): the shared selection checkbox used by
 * both the Recents rows and BentoVerseCard in selection mode. One
 * implementation, two call sites. Default Material3 colors — they come
 * from the theme colorScheme (primary = bento.primary), so the checkbox
 * follows the active theme with no hardcoded colors.
 */
@Composable
private fun VerseSelectionCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

/**
 * 1.9.23 (Sermon Builder): the shared Replace/Merge dialog for sending
 * verses to the sermon. One implementation, three call sites (Recents
 * send-all, Recents multi-select, Library multi-select).
 */
@Composable
private fun SendToSermonDialog(
    title: String,
    text: String,
    onReplace: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onReplace) { Text(context.getString(R.string.s5)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onMerge) { Text(context.getString(R.string.s6)) }
                TextButton(onClick = onDismiss) { Text(context.getString(R.string.s7)) }
            }
        }
    )
}

@Composable
fun BentoVerseCard(
    verse: BibleVerse,
    isCued: Boolean,
    isLiveOnAir: Boolean,
    version: BibleVersion,
    fontSizeSp: Int,
    showCitation: Boolean,
    onClick: () -> Unit,
    isFocused: Boolean = false,
    inSermonList: Boolean = false,
    isRecent: Boolean = false,
    // One-update item 7: verse line spacing from the active template
    // (1.5 = the old fixed rendering).
    lineSpacingArabic: Float = 1.5f,
    lineSpacingEnglish: Float = 1.5f,
    // 1.9.23 (Sermon Builder, Item 3): selection mode — a checkbox
    // appears on the card and taps toggle selection instead of going
    // live. Defaults keep every existing call site unaffected.
    // Long-press is NOT used here (reserved for cross-references).
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
    onSelectionToggle: () -> Unit = {}
) {
    val bento = LocalBentoColors.current
    val listColors = LocalListColors.current
    val highlightColor = if (isLiveOnAir) {
        listColors.verseLiveHighlight
    } else {
        bento.primary.copy(alpha = 0.25f)
    }
    // 2026-10-28 Library colours (theme variables, one source):
    // focused = yellow border on a plain card (the earlier beautiful
    // version); recent = plain card + a thin side line ONLY (no full
    // fill — like the remote page's edge); sermon = a muted soft
    // tint, low attention. Priority stays live > focused > cued >
    // recent > sermon > plain. The side line is drawn as an overlay
    // Box inside a wrapping Box so the Card click still works.
    // Note: recentLine is the theme recents variable (Classic teal),
    // not literal yellow in every theme — see the final report.
    val targetColor = when {
        isLiveOnAir || isCued -> highlightColor
        inSermonList -> listColors.sermonTint
        isRecent -> listColors.recents // 2026-11-03: theme variable, no hardcoded alpha (user rule)
        else -> bento.card
    }
    val bgColor by animateColorAsState(targetColor, label = "cardBg")
    val borderColor = when {
        // Item 96: a LIVE verse's border is the theme liveYellow
        // (yellow, like the sermon list); a merely cued verse keeps
        // the primary border. Background stays verseLiveHighlight.
        isLiveOnAir -> bento.liveYellow
        isCued -> bento.primary
        isFocused -> listColors.focusedBorder
        inSermonList -> bento.border
        else -> bento.border
    }
    val borderWidth = if (isCued || isFocused || inSermonList) 2.dp else 1.dp
    val showRecentLine = isRecent

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(VerseRowSpec.CardCorner),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
            // 1.9.23: in selection mode a tap toggles the checkbox —
            // it must NOT go live. Outside selection mode, unchanged.
            modifier = Modifier.fillMaxWidth().clickable(onClick = { if (isSelecting) onSelectionToggle() else onClick() })
        ) {
            // 1.9.23: in selection mode a checkbox sits at the start
            // of the card; the text column takes the remaining width.
            // Outside selection mode the Row holds only the column,
            // so the layout is identical to before.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
            if (isSelecting) {
                VerseSelectionCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectionToggle() },
                    modifier = Modifier.padding(top = VerseRowSpec.RowPadding, start = VerseRowSpec.RowPadding)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(VerseRowSpec.RowPadding)) {
                if (showCitation || isCued) {
                    Text(
                        text = verse.getFormattedArabicCitation(true),
                        // 1.9.26 (item 10): primarySoft, not primary —
                        // the full primary is unreadable on dark cards.
                        fontSize = VerseRowSpec.CitationSize, fontWeight = FontWeight.Bold, color = bento.primarySoft,
                        modifier = Modifier.padding(bottom = VerseRowSpec.CitationBodySpacing)
                    )
                }
                
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bento.primarySoft)) {
                            append("${ArabicTextFormatter.toEasternArabicDigits(verse.verse)} ")
                        }
                        append(verse.arabicText)
                    },
                    fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * lineSpacingArabic).sp,
                    color = bento.textPrimary, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()
                )

                if (version == BibleVersion.DUAL_BILINGUAL || version == BibleVersion.ENGLISH_KJV || version == BibleVersion.ENGLISH_WEB) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = verse.englishText ?: "",
                            fontSize = (fontSizeSp - 4).sp, lineHeight = ((fontSizeSp - 4) * lineSpacingEnglish).sp,
                            color = bento.textSecondary,
                            textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            } // Row (1.9.23 selection checkbox + text column)
        }
        // Recent side line: 3dp on the start edge, overlaying the
        // card (not shifting its content), only when no stronger
        // marker (live/cued/focused) applies.
        if (showRecentLine) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(3.dp)
                    .clip(RoundedCornerShape(topStart = VerseRowSpec.CardCorner, bottomStart = VerseRowSpec.CardCorner))
                    .background(listColors.recentLine)
            )
        }
        }
    }
}

