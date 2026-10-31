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
    displaySettingsNonce: Int = 0,
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
    modifier: Modifier = Modifier
) {
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
    LaunchedEffect(displaySettingsNonce) {
        if (displaySettingsNonce > 0) displayMenuExpanded = true
    }
    var scripturePickerExpanded by remember { mutableStateOf(false) }
    // Batch item 3: pending picker chapter jump (target chapter); the
    // effect below scrolls to verse 1 once that chapter's list arrives.
    var pickerJumpChapter by remember { mutableStateOf<Int?>(null) }
    val recentsSnackbarHostState = remember { SnackbarHostState() }
    val recentsScope = rememberCoroutineScope()

    val bento = LocalBentoColors.current
    val listColors = LocalListColors.current
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
    LaunchedEffect(pickerJumpChapter, uiState.displayedVerses) {
        val target = pickerJumpChapter
        if (target != null && uiState.selectedChapter == target && uiState.displayedVerses.isNotEmpty()) {
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
                        // Side reservation: each side gets at most 28% of
                        // the bar, so the label's centre is the bar's
                        // centre no matter how long the book/chapter is.
                        val side = maxWidth * 0.28f
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterStart).widthIn(max = side)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = bento.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${uiState.selectedBook.arabicName} ${ArabicTextFormatter.toEasternArabicDigits(uiState.selectedChapter)}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "الكتاب المقدس - إضغط هنا",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.primary,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = side),
                            textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier.align(Alignment.CenterEnd).size(26.dp).background(bento.primary.copy(alpha = 0.16f), CircleShape),
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
                                label = { Text("OT", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = otNtFilter == Testament.NEW_TESTAMENT,
                                onClick = { otNtFilter = if (otNtFilter == Testament.NEW_TESTAMENT) null else Testament.NEW_TESTAMENT },
                                label = { Text("NT", fontSize = 12.sp) }
                            )
                            // Batch-4: search icon button instead of full-width field.
                            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                                Icon(Icons.Default.Search, "بحث (Search)", tint = bento.textPrimary)
                            }
                        }
                        // Batch-4: expandable search field below the OT/NT row.
                        if (searchExpanded) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = bookSearch,
                                onValueChange = { bookSearch = it },
                                placeholder = { Text("ابحث…", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().trackTextInputFocus(),
                                trailingIcon = {
                                    IconButton(onClick = { searchExpanded = false; bookSearch = "" }) {
                                        Icon(Icons.Default.Close, "إغلاق", tint = bento.textSecondary)
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
                        val bookLabelSize = (uiState.readerFontSize * 13 / 18).coerceIn(11, 24).sp

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
                        if (pickerBooks.isEmpty()) {
                            Text(
                                "لا نتائج مطابقة.",
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
                                Text(
                                    "العهد القديم (Old Testament)",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = bento.textSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
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
                                                    BibleBookGroups.shortDisplayName(book.arabicName),
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
                                Text(
                                    "العهد الجديد (New Testament)",
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = bento.textSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
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
                                                    BibleBookGroups.shortDisplayName(book.arabicName),
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
                                items((1..uiState.selectedBook.totalChapters).toList()) { ch ->
                                    val isSelected = ch == uiState.selectedChapter
                                    // J15: chapter numbers scale with the
                                    // picker font (18 -> 14sp, 30 -> 23sp)
                                    // and the circle grows with the number,
                                    // so 3-digit chapters never overflow.
                                    val chNumSize = (uiState.readerFontSize * 14 / 18).coerceIn(11, 26).sp
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

                    }
                }

                // Sub-controls (v1.9.1 #9): chapter steppers + search on the
                // left, ON AIR toggle + appearance on the right. This row
                // replaces the removed output-options card.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ashraf's mapping (Arabic is RTL): the LEFT arrow moves
                    // to the NEXT chapter, the RIGHT arrow to the PREVIOUS
                    // chapter. Chapter browsing never changes the live
                    // outputs — the server holds the on-air verse.
                    IconButton(onClick = onNextChapter) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Next chapter", tint = bento.textSecondary)
                    }
                    IconButton(onClick = onPrevChapter) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Previous chapter", tint = bento.textSecondary)
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
                    IconButton(onClick = { showRecentsSheet = true }) {
                        Icon(Icons.Default.History, "Recent verses", tint = bento.textSecondary)
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    // Batch-3: the ON AIR button and the display-settings
                    // gear moved to MainActivity's top bar (ON AIR replaces
                    // the LIVE pill; the gear sits beside the keyboard
                    // icon on the Library tab). This row keeps chapter
                    // navigation + search + recents only.
                }

                if (showSearchBar) {
                    // Item 8: the search field is RTL — the "بحث…" placeholder
                    // used to inherit the system (LTR) direction, which put
                    // the ellipsis BEFORE the word; in RTL it lands after it.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    OutlinedTextField(
                        value = uiState.searchQuery, onValueChange = onSearchChanged,
                        placeholder = { Text("بحث…", maxLines = 1) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester)
                            .trackTextInputFocus(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    onClearSearch()
                                    searchFocusRequester.requestFocus()
                                }) {
                                    Icon(Icons.Default.Close, "Clear search", tint = bento.textSecondary)
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // Batch-3: RTL header — RIGHT: gear + share icon,
                            // CENTER: title, LEFT: clear (destructive, far
                            // from the primary actions).
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                // One-update item 3: per-section font gear.
                                IconButton(onClick = { recentsFontDialog = true }) {
                                    Icon(Icons.Filled.Settings, "حجم خط الآيات الأخيرة", tint = bento.textSecondary)
                                }
                                if (uiState.recentVerses.isNotEmpty()) {
                                    IconButton(onClick = { showSendDialog = true }) {
                                        Icon(Icons.Filled.Share, "إرسال للعظة (To Sermon)", tint = bento.textSecondary)
                                    }
                                }
                                Text(
                                    text = "الآيات الأخيرة",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = bento.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                // Item 10: user reset for the persisted recents
                                // (stays open so the empty state confirms it).
                                if (uiState.recentVerses.isNotEmpty()) {
                                    TextButton(onClick = { onClearRecents() }) {
                                        Text("مسح (Clear)", fontSize = 12.sp)
                                    }
                                }
                            }
                            // Item 12: Send recents to sermon.
                            if (showSendDialog) {
                                AlertDialog(
                                    onDismissRequest = { showSendDialog = false },
                                    title = { Text("إرسال الآيات الأخيرة للعظة") },
                                    text = { Text("القائمة الحالية تحتوي على آيات. هل تستبدلها بالآيات الأخيرة، أم تدمجها في أعلى القائمة الحالية؟") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            onSendRecentsToSermon(false)
                                            showSendDialog = false
                                        }) { Text("استبدال (Replace)") }
                                    },
                                    dismissButton = {
                                        Row {
                                            TextButton(onClick = {
                                                onSendRecentsToSermon(true)
                                                showSendDialog = false
                                            }) { Text("دمج (Merge)") }
                                            TextButton(onClick = { showSendDialog = false }) { Text("إلغاء") }
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (uiState.recentVerses.isEmpty()) {
                                Text(
                                    text = "لم تفتح أي آية بعد.",
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
                                                            message = "تم حذف الآية من الآيات الأخيرة",
                                                            actionLabel = "تراجع",
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
                                            enableDismissFromEndToStart = !isLiveRecent,
                                            backgroundContent = {
                                                Box(
                                                    modifier = Modifier.fillMaxSize()
                                                        .clip(RoundedCornerShape(VerseRowSpec.CardCorner))
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
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(VerseRowSpec.CardCorner))
                                                .background(if (recentsBandA[index]) listColors.bandA else listColors.bandB)
                                                .clickable {
                                                    showRecentsSheet = false
                                                    onClearSearch()
                                                    onOpenRecentVerse(verse)
                                                }
                                                .padding(VerseRowSpec.RowPadding)
                                        ) {
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
                            SnackbarHost(hostState = recentsSnackbarHostState)
                        }
                        // One-update item 3: per-section font dialog —
                        // "Follow universal" (default) + local sizes.
                        if (recentsFontDialog) {
                            AlertDialog(
                                onDismissRequest = { recentsFontDialog = false },
                                title = { Text("حجم خط الآيات الأخيرة", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
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
                                            Text("اتبع العام (Follow universal: ${uiState.readerFontSize}sp)", fontSize = 13.sp)
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
                val versesToShow = if (uiState.isSearching) uiState.searchResults else uiState.displayedVerses
                // v1.9.1: verses that appear in the sermon list get a finder
                // tint in ANY chapter the operator browses to.
                val sermonVerseIds = remember(uiState.sermonPlaylist) {
                    uiState.sermonPlaylist.map { it.verse.id }.toSet()
                }
                // Item 6: recents marker set (same verses the sheet lists).
                val recentVerseIds = remember(uiState.recentVerses) {
                    uiState.recentVerses.map { it.id }.toSet()
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
                                "جاري تحميل الكتاب المقدس...",
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
                                            if (dragX > 0) {
                                                onNextChapter()
                                            } else {
                                                onPrevChapter()
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
                            isRecent = verse.id in recentVerseIds,
                            version = uiState.bibleVersion,
                            fontSizeSp = uiState.readerFontSize,
                            showCitation = uiState.isSearching,
                            lineSpacingArabic = uiState.activeTemplate.lineSpacingArabic,
                            lineSpacingEnglish = uiState.activeTemplate.lineSpacingEnglish,
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
            title = { Text("إعدادات العرض") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("حجم الخط (${uiState.readerFontSize}sp)")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onDecreaseFontSize) { Text("-") }
                        Text(uiState.readerFontSize.toString(), fontWeight = FontWeight.Bold)
                        Button(onClick = onIncreaseFontSize) { Text("+") }
                    }
                    HorizontalDivider()
                    Text("المظهر")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.values().forEach { mode ->
                            FilterChip(
                                selected = uiState.appThemeMode == mode,
                                onClick = { onSetAppThemeMode(mode) },
                                label = { Text(mode.displayName) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { displayMenuExpanded = false }) { Text("تم") } }
        )
    }
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
    lineSpacingEnglish: Float = 1.5f
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
        else -> bento.card
    }
    val bgColor by animateColorAsState(targetColor, label = "cardBg")
    val borderColor = when {
        isLiveOnAir || isCued -> bento.primary
        isFocused -> listColors.focusedBorder
        inSermonList -> bento.border
        else -> bento.border
    }
    val borderWidth = if (isCued || isFocused || inSermonList) 2.dp else 1.dp
    val showRecentLine = isRecent && !isLiveOnAir && !isCued && !isFocused

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(VerseRowSpec.CardCorner),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(VerseRowSpec.RowPadding)) {
                if (showCitation || isCued) {
                    Text(
                        text = verse.getFormattedArabicCitation(true),
                        fontSize = VerseRowSpec.CitationSize, fontWeight = FontWeight.Bold, color = bento.primary,
                        modifier = Modifier.padding(bottom = VerseRowSpec.CitationBodySpacing)
                    )
                }
                
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bento.primary)) {
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

