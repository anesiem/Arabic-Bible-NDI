package com.example.ui

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArabicTextFormatter
import com.example.data.BibleRepository
import com.example.model.AppThemeMode
import com.example.model.BibleBook
import com.example.model.BibleVerse
import com.example.model.BibleVersion
import com.example.model.Testament
import com.example.ui.theme.*

@Composable
fun BibleReaderScreen(
    uiState: BibleNdiUiState,
    onVerseClicked: (BibleVerse) -> Unit,
    onSelectTestament: (Testament) -> Unit,
    onSelectBook: (BibleBook, Int) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSelectVersion: (BibleVersion) -> Unit,
    onSearchChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleLive: () -> Unit,
    onClearBroadcast: () -> Unit,
    onNextVerse: () -> Unit,
    onPrevVerse: () -> Unit,
    onIncreaseFontSize: () -> Unit = {},
    onDecreaseFontSize: () -> Unit = {},
    onSetAppThemeMode: (AppThemeMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var versionDropdownExpanded by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bento.bg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var outputOptionsExpanded by remember { mutableStateOf(false) }

        // 1. Output Options & Live Status (Top Bento Card)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bento.card,
            border = BorderStroke(
                1.dp,
                if (uiState.isLiveOnAir && uiState.activeVerse != null) bento.liveRed.copy(alpha = 0.6f) else bento.border
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { outputOptionsExpanded = !outputOptionsExpanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(if (uiState.isLiveOnAir && uiState.activeVerse != null) bento.liveRedBright else bento.textSecondary))
                        Text(
                            text = if (uiState.isLiveOnAir && uiState.activeVerse != null) "NDI ON AIR" else "NDI STANDBY",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (uiState.isLiveOnAir && uiState.activeVerse != null) bento.liveRed else bento.textPrimary
                        )
                    }
                    IconButton(onClick = { outputOptionsExpanded = !outputOptionsExpanded }, modifier = Modifier.size(24.dp)) {
                        Icon(if (outputOptionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = bento.primary)
                    }
                }

                AnimatedVisibility(visible = outputOptionsExpanded) {
                    Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = bento.borderSubtle)
                        BentoBroadcastControlsBar(uiState, onToggleLive, onClearBroadcast, onNextVerse, onPrevVerse)
                    }
                }
            }
        }

        // 2. Scripture Browser Card
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
                            Icon(Icons.Default.MenuBook, null, tint = bento.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${uiState.selectedBook.arabicName} ${ArabicTextFormatter.toEasternArabicDigits(uiState.selectedChapter)}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary
                            )
                        }
                        Icon(if (scripturePickerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = bento.primary)
                    }
                }

                AnimatedVisibility(visible = scripturePickerExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().background(bento.card).padding(12.dp)) {
                        // Bible Version Picker (Moved inside)
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("الترجمة:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary, modifier = Modifier.width(60.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = bento.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().clickable { versionDropdownExpanded = true }
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(uiState.bibleVersion.displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                DropdownMenu(expanded = versionDropdownExpanded, onDismissRequest = { versionDropdownExpanded = false }) {
                                    BibleVersion.entries.forEach { version ->
                                        DropdownMenuItem(text = { Text(version.displayName) }, onClick = { onSelectVersion(version); versionDropdownExpanded = false })
                                    }
                                }
                            }
                        }

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
                        Box(modifier = Modifier.height(130.dp)) {
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
                                            .clickable { onSelectChapter(ch) },
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
                            Box(modifier = Modifier.height(100.dp)) {
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

                // Sub-controls (Search, Appearance, etc.)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showSearchBar = !showSearchBar; if (!showSearchBar) onClearSearch() }) {
                        Icon(if (showSearchBar) Icons.Default.Close else Icons.Default.Search, null, tint = bento.textSecondary)
                    }

                    IconButton(onClick = { displayMenuExpanded = true }) {
                        Icon(Icons.Default.Settings, null, tint = bento.textSecondary)
                    }
                }

                if (showSearchBar) {
                    OutlinedTextField(
                        value = uiState.searchQuery, onValueChange = onSearchChanged,
                        placeholder = { Text("بحث...") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Verse List
                val versesToShow = if (uiState.isSearching) uiState.searchResults else uiState.displayedVerses
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(versesToShow) { verse ->
                        val isCued = uiState.activeVerse?.id == verse.id
                        BentoVerseCard(
                            verse = verse,
                            isCued = isCued,
                            isLiveOnAir = isCued && uiState.isLiveOnAir,
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
    onClick: () -> Unit
) {
    val bento = LocalBentoColors.current
    val highlightColor = if (isLiveOnAir) Color(0xFFFFECE8) else bento.primary.copy(alpha = 0.25f)
    val bgColor by animateColorAsState(if (isCued) highlightColor else bento.card, label = "cardBg")
    val borderColor = if (isLiveOnAir) bento.liveRed else if (isCued) bento.primary else bento.border

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = androidx.compose.foundation.BorderStroke(if (isCued) 2.dp else 1.dp, borderColor),
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

@Composable
fun BentoBroadcastControlsBar(
    uiState: BibleNdiUiState,
    onToggleLive: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val bento = LocalBentoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.ArrowBack, null, tint = bento.primary) }
            IconButton(onClick = onNext) { Icon(Icons.Default.ArrowForward, null, tint = bento.primary) }
            IconButton(onClick = onClear) { Icon(Icons.Default.LayersClear, null, tint = bento.liveRed) }
        }

        Button(
            onClick = onToggleLive,
            colors = ButtonDefaults.buttonColors(containerColor = if (uiState.isLiveOnAir) bento.liveRed else bento.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (uiState.isLiveOnAir) "CUT (إيقاف)" else "PUSH LIVE (بث)")
        }
    }
}
