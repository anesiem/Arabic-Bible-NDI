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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.model.AppThemeMode
import com.arabicchristianmedia.model.BibleBook
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.BibleVersion
import com.arabicchristianmedia.model.Testament
import com.arabicchristianmedia.ui.theme.*
import kotlin.math.abs

@Composable
fun BibleReaderScreen(
    uiState: BibleNdiUiState,
    onVerseClicked: (BibleVerse) -> Unit,
    onSelectTestament: (Testament) -> Unit,
    onSelectBook: (BibleBook, Int) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onNextChapter: () -> Unit = {},
    onPrevChapter: () -> Unit = {},
    onSelectVersion: (BibleVersion) -> Unit,
    onSearchChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenRecentVerse: (BibleVerse) -> Unit = {},
    onToggleLive: () -> Unit,
    onIncreaseFontSize: () -> Unit = {},
    onDecreaseFontSize: () -> Unit = {},
    onSetAppThemeMode: (AppThemeMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSearchBar by remember { mutableStateOf(false) }
    var showRecentsSheet by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var displayMenuExpanded by remember { mutableStateOf(false) }
    var scripturePickerExpanded by remember { mutableStateOf(false) }

    val bento = LocalBentoColors.current
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bento.bg)
            .padding(12.dp),
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
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = bento.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${uiState.selectedBook.arabicName} ${ArabicTextFormatter.toEasternArabicDigits(uiState.selectedChapter)}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary
                            )
                        }
                        // The middle label names what the bar opens (the v1.9.1
                        // "إضغط هنا" alone still read as unclear). The whole bar
                        // stays clickable; the label is one line + ellipsis so
                        // narrow phones + large font cannot break the bar.
                        Text(
                            "الكتاب المقدس - إضغط هنا",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.primary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier.size(26.dp).background(bento.primary.copy(alpha = 0.16f), CircleShape),
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
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                            .background(bento.card)
                            .padding(12.dp)
                    ) {
                        // OT/NT Selector
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.selectedTestament == Testament.NEW_TESTAMENT,
                                onClick = { onSelectTestament(Testament.NEW_TESTAMENT) },
                                label = { Text("العهد الجديد") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = uiState.selectedTestament == Testament.OLD_TESTAMENT,
                                onClick = { onSelectTestament(Testament.OLD_TESTAMENT) },
                                label = { Text("العهد القديم") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Book List (Scrollable Row)
                        val books = BibleRepository.allBooks.filter { it.testament == uiState.selectedTestament }
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(books) { book ->
                                FilterChip(
                                    selected = book.id == uiState.selectedBook.id,
                                    onClick = { onSelectBook(book, 1) },
                                    label = { Text(book.arabicName, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Chapters Grid
                        Text("الفصل:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        Box(modifier = Modifier.heightIn(min = 100.dp, max = 160.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 40.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items((1..uiState.selectedBook.totalChapters).toList()) { ch ->
                                    val isSelected = ch == uiState.selectedChapter
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape)
                                            .background(if (isSelected) bento.primary else bento.surfaceVariant)
                                            .clickable { 
                                                onSelectChapter(ch)
                                                scripturePickerExpanded = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(ArabicTextFormatter.toEasternArabicDigits(ch), color = if (isSelected) Color.White else bento.textPrimary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Verse Grid (Optional quick access)
                        if (uiState.displayedVerses.isNotEmpty()) {
                            Text("الآية:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary, modifier = Modifier.padding(top = 8.dp))
                            Box(modifier = Modifier.heightIn(min = 80.dp, max = 140.dp)) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 36.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(uiState.displayedVerses) { verse ->
                                        val isSelected = uiState.activeVerse?.id == verse.id
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) bento.primary else bento.surfaceVariant)
                                                .clickable { 
                                                    onVerseClicked(verse)
                                                    scripturePickerExpanded = false 
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(ArabicTextFormatter.toEasternArabicDigits(verse.verse), color = if (isSelected) Color.White else bento.textPrimary, fontSize = 10.sp)
                                        }
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
                    IconButton(onClick = { showSearchBar = !showSearchBar; if (!showSearchBar) onClearSearch() }) {
                        Icon(if (showSearchBar) Icons.Default.Close else Icons.Default.Search, null, tint = bento.textSecondary)
                    }
                    IconButton(onClick = { showRecentsSheet = true }) {
                        Icon(Icons.Default.History, "Recent verses", tint = bento.textSecondary)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // ON AIR toggle: red while a verse is live. Pressing
                    // it clears the verse from the outputs and the button
                    // turns grey; pressing again brings the SAME verse back.
                    // It never stops the broadcast service or the feeds.
                    // Muted grey is fixed (not textSecondary: too light for
                    // white text on either theme).
                    val isOnAir = uiState.isLiveOnAir && uiState.activeVerse != null
                    Button(
                        onClick = onToggleLive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOnAir) bento.liveRed else Color(0xFF6B7280),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (isOnAir) "ON AIR (على الهواء)" else "OFF AIR (خارج الهواء)",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = { displayMenuExpanded = true }) {
                        Icon(Icons.Default.Settings, null, tint = bento.textSecondary)
                    }
                }

                if (showSearchBar) {
                    OutlinedTextField(
                        value = uiState.searchQuery, onValueChange = onSearchChanged,
                        placeholder = { Text("بحث…", maxLines = 1) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .focusRequester(searchFocusRequester),
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

                // Session recents sheet: verses opened this session, most
                // recent first. Tapping one jumps the reader to it WITHOUT
                // putting it on air (the verse itself is tapped for that,
                // as usual). No permanent screen space is taken.
                if (showRecentsSheet) {
                    ModalBottomSheet(onDismissRequest = { showRecentsSheet = false }) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "الآيات الأخيرة",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = bento.textPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (uiState.recentVerses.isEmpty()) {
                                Text(
                                    text = "لم تفتح أي آية بعد في هذه الجلسة.",
                                    fontSize = 13.sp,
                                    color = bento.textSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(uiState.recentVerses) { verse ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(bento.surfaceVariant)
                                                .clickable {
                                                    showRecentsSheet = false
                                                    onClearSearch()
                                                    onOpenRecentVerse(verse)
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = verse.getFormattedArabicCitation(),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = bento.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = verse.arabicText,
                                                fontSize = 14.sp,
                                                color = bento.textPrimary
                                            )
                                            if (verse.englishText.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = verse.englishText,
                                                    fontSize = 12.sp,
                                                    color = bento.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Verse List
                val versesToShow = if (uiState.isSearching) uiState.searchResults else uiState.displayedVerses
                // v1.9.1: verses that appear in the sermon list get a finder
                // tint in ANY chapter the operator browses to.
                val sermonVerseIds = remember(uiState.sermonPlaylist) {
                    uiState.sermonPlaylist.map { it.verse.id }.toSet()
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(versesToShow) { verse ->
                        val isCued = uiState.activeVerse?.id == verse.id
                        BentoVerseCard(
                            verse = verse,
                            isCued = isCued,
                            isLiveOnAir = isCued && uiState.isLiveOnAir,
                            isFocused = uiState.readerFocusVerseId == verse.id,
                            inSermonList = verse.id in sermonVerseIds,
                            version = uiState.bibleVersion,
                            fontSizeSp = uiState.readerFontSize,
                            showCitation = uiState.isSearching,
                            onClick = { onVerseClicked(verse) }
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
    inSermonList: Boolean = false
) {
    val bento = LocalBentoColors.current
    val isDark = MaterialTheme.colorScheme.surface == BentoCardDark
    val highlightColor = if (isLiveOnAir) {
        if (isDark) Color(0xFF1E293B) else Color(0xFFE0F2FE)
    } else {
        bento.primary.copy(alpha = 0.25f)
    }
    // v1.9.1 highlight priority: live > focused (jumped-to from a sermon
    // row) > cued > in-sermon-list finder tint > plain card.
    val targetColor = when {
        isLiveOnAir || isCued -> highlightColor
        isFocused -> bento.primaryContainer
        inSermonList -> bento.secondary.copy(alpha = 0.35f)
        else -> bento.card
    }
    val bgColor by animateColorAsState(targetColor, label = "cardBg")
    // v1.8: Use primary (theme-aware) instead of red for the verse highlight border.
    val borderColor = if (isLiveOnAir || isCued || isFocused) bento.primary else bento.border

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = androidx.compose.foundation.BorderStroke(if (isCued || isFocused) 2.dp else 1.dp, borderColor),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (showCitation || isCued) {
                    Text(
                        text = verse.getFormattedArabicCitation(true),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bento.primary)) {
                            append("${ArabicTextFormatter.toEasternArabicDigits(verse.verse)} ")
                        }
                        append(verse.arabicText)
                    },
                    fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * 1.5).sp,
                    color = bento.textPrimary, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()
                )

                if (version == BibleVersion.DUAL_BILINGUAL || version == BibleVersion.ENGLISH_KJV || version == BibleVersion.ENGLISH_WEB) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = verse.englishText ?: "",
                            fontSize = (fontSizeSp - 4).sp, color = bento.textSecondary,
                            textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

