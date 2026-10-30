package com.arabicchristianmedia

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.ui.BibleNdiViewModel
import com.arabicchristianmedia.ui.BibleReaderScreen
import com.arabicchristianmedia.ui.BroadcastControlScreen
import com.arabicchristianmedia.ui.FullscreenOverlayActivity
import com.arabicchristianmedia.ui.SermonListScreen
import com.arabicchristianmedia.ui.InfoPopupButton
import com.arabicchristianmedia.ui.TemplateEditorScreen
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.ui.theme.LocalBentoColors
import com.arabicchristianmedia.ui.theme.MyApplicationTheme
import com.arabicchristianmedia.model.ColorThemes
import androidx.compose.ui.focus.onFocusChanged

/**
 * One-update item 20: tracks whether a text field has focus anywhere in
 * the app. The Activity's keyboard shortcuts never fire while typing.
 * Text fields opt in via Modifier.trackTextInputFocus().
 */
object TextInputFocusTracker {
    var isFocused: Boolean = false
}

/**
 * One-update item 20: attach to text fields so the Activity keyboard
 * shortcuts never fire while the user is typing.
 */
fun androidx.compose.ui.Modifier.trackTextInputFocus(): androidx.compose.ui.Modifier =
    this.onFocusChanged { TextInputFocusTracker.isFocused = it.isFocused }

class MainActivity : ComponentActivity() {

    private val viewModel: BibleNdiViewModel by viewModels()

    /**
     * v2.0: shared-text entry points — ACTION_SEND (share sheet: Google
     * Keep, Bible apps, ...) and ACTION_PROCESS_TEXT (the text-selection
     * toolbar item). Both hand the text to the ViewModel's sermon-playlist
     * flow; the list tab opens via the sermonListNonce effect below.
     */
    private fun handleShareIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }
        if (!text.isNullOrBlank()) {
            viewModel.handleSharedText(text)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Presenter clicker fallback (sermon APIs): PageDown/PageUp step the
     * working sermon list and go live, same as POST
     * /api/sermons/current/next|prev. This is the Activity fallback
     * only — if a Compose screen already consumed the key (e.g. the
     * Sermon screen's own handler below), this is not called again.
     * Volume, Back, and other keys are untouched: unhandled keys fall
     * through to super. At either end, or on an empty list, stepSermon
     * returns no entry and the key falls through (e.g. to scroll),
     * instead of changing the live verse.
     */
    // One-update item 20: keyboard state bridged from Compose.
    // These MUST be Compose state: onKeyDown runs outside composition,
    // and plain fields would never trigger the recomposition that
    // delivers them to the UI (Ctrl+F / ? key would silently do nothing).
    private var currentTab: Int = 0
    private var requestTab by mutableStateOf<Int?>(null)
    private var searchFocusNonce by mutableStateOf(0)
    private var showShortcutsCard by mutableStateOf(false)

    /**
     * One-update item 20: full keyboard map, app-wide. Never fires while
     * typing in a text field (TextInputFocusTracker). Unmapped keys fall
     * through to super (volume, Back, etc. untouched).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (TextInputFocusTracker.isFocused) return super.onKeyDown(keyCode, event)
        if (event?.repeatCount != 0) return super.onKeyDown(keyCode, event)
        val ctrl = event.isCtrlPressed
        val handled = when {
            // Ctrl+F: open Library search.
            ctrl && keyCode == KeyEvent.KEYCODE_F -> {
                requestTab = 0; searchFocusNonce++; true
            }
            // Ctrl+1..4: switch tabs (Library, Editor, Broadcast, Sermon).
            ctrl && keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_4 -> {
                requestTab = keyCode - KeyEvent.KEYCODE_1; true
            }
            // ?: shortcuts help card.
            keyCode == KeyEvent.KEYCODE_SLASH && event.isShiftPressed -> {
                showShortcutsCard = true; true
            }
            // O: ON AIR toggle.
            keyCode == KeyEvent.KEYCODE_O -> {
                try { viewModel.toggleLive() } catch (e: Exception) {}
                true
            }
            // C: Clear (per-feed Clear mode) -> OFF AIR.
            keyCode == KeyEvent.KEYCODE_C -> {
                try { viewModel.clearBroadcast() } catch (e: Exception) {}
                true
            }
            // Esc: close the shortcuts card, else Clear -> OFF AIR.
            keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                if (showShortcutsCard) showShortcutsCard = false
                else try { viewModel.clearBroadcast() } catch (e: Exception) {}
                true
            }
            // Enter: go live on the keyboard-focused verse (Library).
            (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && currentTab == 0 -> {
                try { viewModel.goLiveOnFocused() } catch (e: Exception) {}
                true
            }
            // Space: next sermon live.
            keyCode == KeyEvent.KEYCODE_SPACE -> {
                try { viewModel.stepSermon(+1) } catch (e: Exception) {}
                true
            }
            // +/-: Library font size.
            keyCode == KeyEvent.KEYCODE_PLUS || keyCode == KeyEvent.KEYCODE_EQUALS -> {
                try { viewModel.increaseFontSize() } catch (e: Exception) {}
                true
            }
            keyCode == KeyEvent.KEYCODE_MINUS -> {
                try { viewModel.decreaseFontSize() } catch (e: Exception) {}
                true
            }
            // Right/PageDown: next sermon; Left/PageUp: previous (stop at ends).
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_PAGE_DOWN -> {
                val outcome = try { viewModel.stepSermon(+1) } catch (e: Exception) { null }
                outcome?.entry != null
            }
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_PAGE_UP -> {
                val outcome = try { viewModel.stepSermon(-1) } catch (e: Exception) { null }
                outcome?.entry != null
            }
            // Up/Down: move the focus ring in the Library list.
            keyCode == KeyEvent.KEYCODE_DPAD_UP && currentTab == 0 -> {
                try { viewModel.moveReaderFocus(-1) } catch (e: Exception) {}
                true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN && currentTab == 0 -> {
                try { viewModel.moveReaderFocus(+1) } catch (e: Exception) {}
                true
            }
            else -> false
        }
        return if (handled) true else super.onKeyDown(keyCode, event)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            // Batch-3: per-tab per-section expand memory (ViewModel-backed).
            val sectionExpandedMap by viewModel.sectionExpanded.collectAsState()

            LaunchedEffect(uiState.isKeepScreenOn) {
                if (uiState.isKeepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MyApplicationTheme(
                themeMode = uiState.appThemeMode,
                colorTheme = ColorThemes.byId(uiState.selectedThemeId, uiState.customThemes)
            ) {
                val bento = LocalBentoColors.current
                val snackbarHostState = remember { SnackbarHostState() }
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                // One-update item 20: bridge Activity keyboard state.
                LaunchedEffect(selectedTab) { currentTab = selectedTab }
                LaunchedEffect(requestTab) {
                    requestTab?.let { selectedTab = it; requestTab = null }
                }
                // One-update item 20: the Activity keyboard fields are
                // Compose state now, so they can be used directly.
                var shortcutsOpen by remember { mutableStateOf(false) }
                LaunchedEffect(showShortcutsCard) { shortcutsOpen = showShortcutsCard }
                // Keep the Activity flag in sync when the dialog closes.
                LaunchedEffect(shortcutsOpen) { if (!shortcutsOpen) showShortcutsCard = false }
                // 2026-10-28 K7 gate, shared by the top bar and the
                // Editor body: width >= 900 AND height >= 600.
                val topBarWide = LocalConfiguration.current.screenWidthDp >= 900 && LocalConfiguration.current.screenHeightDp >= 600
                // 2026-10-28: every Library-tab tap bumps this, even
                // when already on Library — BibleReaderScreen closes
                // search / recents sheet / chapter picker on the bump.
                var libraryTabTapNonce by remember { mutableIntStateOf(0) }
                // Batch-3: display-settings nonce — the top-bar gear (Library
                // tab only) bumps this; BibleReaderScreen opens its existing
                // "إعدادات العرض" dialog on the bump.
                var displaySettingsNonce by remember { mutableIntStateOf(0) }
                // v2.0: a successful share (sermonListNonce bump) opens the
                // sermon list tab so the operator lands on the verses.
                LaunchedEffect(uiState.sermonListNonce) {
                    if (uiState.sermonListNonce > 0) selectedTab = 3
                }
                // v1.9.1: a sermon row's book icon jumps to the Library tab
                // at the focused verse (without putting it on air).
                LaunchedEffect(uiState.readerJumpNonce) {
                    if (uiState.readerJumpNonce > 0) selectedTab = 0
                }
                // v1.9: editor sub-tab (Lower Third / Full Show) hoisted here so it
                // survives both rotation and main-tab switches.
                var editorTab by rememberSaveable { mutableIntStateOf(0) }
                val context = LocalContext.current
                // Editor tips visibility, hoisted so the top-bar info icon
                // (wide Editor layout) can reopen the tips card. Persisted
                // in the same prefs the Editor screen already uses, so it
                // also survives rotation and process death.
                var editorTipsVisible by remember { mutableStateOf(context.getSharedPreferences("editor_ui_prefs", android.content.Context.MODE_PRIVATE).getBoolean("show_how_it_works", true)) }
                // Batch item 5: import-dialog state hoisted from the Editor
                // (same precedent as the tips) so the top-bar feed chip
                // can open the import dialog.
                var editorShowImportDialog by remember { mutableStateOf(false) }
                var editorImportTargetTpl by remember { mutableStateOf<LowerThirdTemplate?>(null) }
                // Batch item 6: Modified-menu visibility, hoisted from the
                // Editor so the top-bar badge opens the same menu.
                var editorShowModifiedMenu by remember { mutableStateOf(false) }

                LaunchedEffect(uiState.statusMessage) {
                    uiState.statusMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.dismissStatusMessage()
                    }
                }

                // One-update item 20: shortcuts help card (? key or top-bar icon).
                if (shortcutsOpen) {
                    com.arabicchristianmedia.ui.ShortcutsHelpDialog(
                        onDismiss = { shortcutsOpen = false }
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        Surface(
                            color = bento.bg,
                            // v1.7: own the status-bar inset so the bar never
                            // slides under the system status area.
                            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    // v1.7: title takes the flexible space; the
                                    // LIVE badge keeps its intrinsic width.
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Bento Round Avatar / Broadcast Badge
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(bento.secondary)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LiveTv,
                                            contentDescription = "Bible NDI",
                                            tint = bento.onSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bible NDI",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = bento.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "SVD Arabic (الكتاب المقدس)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = bento.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Wide Editor layout: the Editor's header chips
                                // live on this line, beside the NDI pill, so the
                                // Editor gains a full row of scroll room.
                                // 2026-10-28 K7 gate: width >= 900 AND height
                                // >= 600 — the same thresholds as
                                // isWideLayout — so the top-bar chips and
                                // the Editor's wide branch can never
                                // disagree and show both header rows.
                                if (selectedTab == 1 && topBarWide) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // 2026-10-28: the feed chip is a label
                                        // only now — Import lives in the design
                                        // menu, so no separate import button.
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = bento.primaryContainer
                                        ) {
                                            Text(
                                                if (editorTab == 0) "السفلي · Lower" else "الكامل · Full",
                                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.onPrimaryContainer,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        // 2026-10-28 design button: always
                                        // visible, name first, Modified
                                        // prefix only when dirty — the
                                        // same button as the compact stage
                                        // row, which hides when this shows.
                                        run {
                                            val designName = if (editorTab == 0) {
                                                uiState.templates.firstOrNull { it.id == uiState.highlightedLowerStyleId }?.name ?: uiState.activeTemplate.name
                                            } else {
                                                uiState.templates.firstOrNull { it.id == uiState.highlightedShowStyleId }?.name ?: uiState.activeShowTemplate.name
                                            }
                                            val dirty = if (editorTab == 0) uiState.lowerWorkingDirty else uiState.showWorkingDirty
                                            Surface(shape = RoundedCornerShape(10.dp), color = bento.primaryContainer, onClick = { editorShowModifiedMenu = true }) {
                                                // Batch-4: ☰ icon indicates this is a menu button, not just a label.
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                                    Text(if (dirty) "معدّل · $designName" else designName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(Icons.Filled.Menu, contentDescription = "قائمة (Menu)", tint = bento.onPrimaryContainer, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                        // Batch-4: Removed redundant ⓘ beside style chip
                                        // (the Editor header has the title info).
                                        if (!editorTipsVisible) {
                                            IconButton(onClick = {
                                                context.getSharedPreferences("editor_ui_prefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("show_how_it_works", true).apply()
                                                editorTipsVisible = true
                                            }) { Icon(Icons.Filled.Info, "كيفية الاستخدام", tint = bento.primary) }
                                        }
                                        IconButton(onClick = { viewModel.resetTemplate(editorTab == 1) }) { Icon(Icons.Filled.Refresh, "إعادة تعيين", tint = bento.primary) }
                                    }
                                }

                                // Live Badge & Quick Switch
                                // One-update item 20: keyboard icon on every
                                // tab — opens the shortcuts help card.
                                IconButton(onClick = { shortcutsOpen = true }) {
                                    Icon(Icons.Filled.Keyboard, "اختصارات لوحة المفاتيح (Keyboard shortcuts)", tint = bento.textSecondary)
                                }
                                // Batch-3: display-settings gear — Library tab
                                // only, beside the keyboard icon. Bumps the
                                // nonce; BibleReaderScreen opens its dialog.
                                if (selectedTab == 0) {
                                    IconButton(onClick = { displaySettingsNonce++ }) {
                                        Icon(Icons.Filled.Settings, "إعدادات العرض (Display settings)", tint = bento.textSecondary)
                                    }
                                }
                                // Batch-3: the display-only LIVE pill is gone
                                // (redundant). The ON AIR button IS the live
                                // indicator AND the toggle — red when a verse
                                // is live on any output, grey when off.
                                val topBarIsOnAir = uiState.isLiveOnAir && uiState.activeVerse != null
                                Button(
                                    onClick = { viewModel.toggleLive() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (topBarIsOnAir) bento.liveRed else Color(0xFF6B7280),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        if (topBarIsOnAir) "ON AIR" else "OFF AIR",
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        Surface(
                            color = bento.surfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            NavigationBar(
                                containerColor = bento.surfaceContainer,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0; libraryTabTapNonce++ },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 0) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                                            contentDescription = "Bible & Live Broadcast"
                                        )
                                    },
                                    label = { Text("Library (الآيات)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = bento.onPrimaryContainer,
                                        selectedTextColor = bento.primary,
                                        indicatorColor = bento.primaryContainer,
                                        unselectedIconColor = bento.textSecondary,
                                        unselectedTextColor = bento.textSecondary
                                    ),
                                    modifier = Modifier.testTag("tab_bible")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 1) Icons.Filled.Palette else Icons.Outlined.Palette,
                                            contentDescription = "Lower Third Template Editor"
                                        )
                                    },
                                    label = { Text("Editor (القوالب)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = bento.onPrimaryContainer,
                                        selectedTextColor = bento.primary,
                                        indicatorColor = bento.primaryContainer,
                                        unselectedIconColor = bento.textSecondary,
                                        unselectedTextColor = bento.textSecondary
                                    ),
                                    modifier = Modifier.testTag("tab_templates")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                                            contentDescription = "NDI & Broadcast Settings"
                                        )
                                    },
                                    label = { Text("الإعدادات (Setup)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = bento.onPrimaryContainer,
                                        selectedTextColor = bento.primary,
                                        indicatorColor = bento.primaryContainer,
                                        unselectedIconColor = bento.textSecondary,
                                        unselectedTextColor = bento.textSecondary
                                    ),
                                    modifier = Modifier.testTag("tab_broadcast")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 3) Icons.AutoMirrored.Filled.PlaylistPlay else Icons.AutoMirrored.Outlined.PlaylistPlay,
                                            contentDescription = "Sermon list (verses shared from notes)"
                                        )
                                    },
                                    label = { Text("Sermon (القائمة)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = bento.onPrimaryContainer,
                                        selectedTextColor = bento.primary,
                                        indicatorColor = bento.primaryContainer,
                                        unselectedIconColor = bento.textSecondary,
                                        unselectedTextColor = bento.textSecondary
                                    ),
                                    modifier = Modifier.testTag("tab_sermon")
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> BibleReaderScreen(
                                uiState = uiState,
                                onVerseClicked = { viewModel.onVerseClicked(it) },
                                onSelectTestament = { viewModel.selectTestament(it) },
                                onSelectBook = { book, ch -> viewModel.selectBook(book, ch) },
                                onSelectChapter = { viewModel.selectChapter(it) },
                                onNextChapter = { viewModel.nextChapter() },
                                onPrevChapter = { viewModel.prevChapter() },
                                // Batch-3: keyboard arrows in Library navigate verses.
                                onNextVerse = { viewModel.nextVerse() },
                                onPrevVerse = { viewModel.prevVerse() },
                                onSelectVersion = { viewModel.setBibleVersion(it) },
                                onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                                onClearSearch = { viewModel.clearSearch() },
                                onSearchResultClicked = { viewModel.onSearchResultClicked(it) },
                                onSearchBoxReopened = { viewModel.onSearchBoxReopened() },
                                onDismissSearch = { viewModel.dismissSearch() },
                                searchFocusNonce = searchFocusNonce,
                                onOpenRecentVerse = { viewModel.openRecentVerseInReader(it) },
                                onClearRecents = { viewModel.clearRecents() },
                                onSendRecentsToSermon = { merge -> viewModel.sendRecentsToSermon(merge) },
                                displaySettingsNonce = displaySettingsNonce,
                                onIncreaseFontSize = { viewModel.increaseFontSize() },
                                onDecreaseFontSize = { viewModel.decreaseFontSize() },
                                onSetAppThemeMode = { viewModel.setAppThemeMode(it) },
                                closeOverlaysNonce = libraryTabTapNonce,
                                onRemoveRecent = { viewModel.removeRecentVerse(it) },
                                onRestoreRecent = { verse, index -> viewModel.restoreRecentVerse(verse, index) },
                                onSetRecentsFontSize = { viewModel.setRecentsFontSize(it) }
                            )
                            1 -> TemplateEditorScreen(
                                currentTemplate = uiState.activeTemplate,
                                activeShowTemplate = uiState.activeShowTemplate,
                                templates = uiState.templates,
                                highlightedLowerStyleId = uiState.highlightedLowerStyleId,
                                highlightedShowStyleId = uiState.highlightedShowStyleId,
                                editingLowerStyleId = uiState.editingLowerStyleId,
                                editingShowStyleId = uiState.editingShowStyleId,
                                tashkeelEnabled = uiState.tashkeelEnabled,
                                onTashkeelChange = { viewModel.setTashkeelEnabled(it) },
                                englishVersion = uiState.englishVersion,
                                onEnglishVersionChange = { viewModel.setEnglishVersion(it) },
                                lowerWorkingDirty = uiState.lowerWorkingDirty,
                                showWorkingDirty = uiState.showWorkingDirty,
                                activeVerse = uiState.activeVerse,
                                onSelectTemplate = { viewModel.selectTemplate(it) },
                                onSelectShowTemplate = { viewModel.selectShowTemplate(it) },
                                onUpdateTemplate = { viewModel.updateActiveTemplate(it) },
                                onUpdateShowTemplate = { viewModel.updateActiveShowTemplate(it) },
                                onSaveAsNew = { name, base, isFull -> viewModel.saveAsNewTemplate(name, base, isFull) },
                                onUpdateStyleInPlace = { isFull -> viewModel.updateStyleInPlace(isFull) },
                                highlightedStyleIsFactory = com.arabicchristianmedia.data.TemplateRepository.isFactoryStyleId(
                                    if (editorTab == 1) uiState.highlightedShowStyleId else uiState.highlightedLowerStyleId
                                ),
                                customFonts = uiState.customFonts,
                                onImportCustomFont = { uri, name -> viewModel.importCustomFont(uri, name) },
                                onDeleteCustomFont = { viewModel.deleteCustomFont(it) },
                                onResetDefaults = { isFull -> viewModel.resetTemplate(isFull) },
                                onDeleteTemplate = { id, isFull -> viewModel.deleteTemplate(id, isFull) },
                                onExportTemplate = { id -> viewModel.exportTemplateJson(id) },
                                onUpdateTemplateFromJson = { id, json, isFull -> viewModel.updateTemplateFromJson(id, json, isFull) },
                                onImportNewTemplate = { json, isFull -> viewModel.importNewTemplate(json, isFull) },
                                editorTab = editorTab,
                                onEditorTabChange = { editorTab = it },
                                showHowItWorks = editorTipsVisible,
                                onShowHowItWorksChange = { editorTipsVisible = it },
                                showImportDialog = editorShowImportDialog,
                                importTargetTpl = editorImportTargetTpl,
                                onShowImportDialogChange = { editorShowImportDialog = it },
                                onImportTargetTplChange = { editorImportTargetTpl = it },
                                showModifiedMenu = editorShowModifiedMenu,
                                onShowModifiedMenuChange = { editorShowModifiedMenu = it },
                                topBarDesignButtonVisible = topBarWide,
                                onExportWorkingTemplate = { isFull -> viewModel.exportWorkingTemplateJson(isFull) },
                                onRenameTemplate = { id, name, isFull -> viewModel.renameTemplate(id, name, isFull) },
                                onRestoreFactoryTemplates = { viewModel.restoreFactoryTemplates() },
                                sectionExpandedMap = sectionExpandedMap,
                                onSetSectionExpanded = { tabId, sectionId, expanded -> viewModel.setSectionExpanded(tabId, sectionId, expanded) }
                            )
                            2 -> BroadcastControlScreen(
                                uiState = uiState,
                                onStartServer = { viewModel.startNdiFeeds() },
                                onStopServer = { viewModel.stopNdiFeeds() },
                                onLaunchFullscreenOverlay = {
                                    val intent = Intent(context, FullscreenOverlayActivity::class.java)
                                    context.startActivity(intent)
                                },
                                // 1.9.3: Launch Full Show full-screen (VersePresentation).
                                onLaunchFullscreenShow = {
                                    val intent = Intent(context, com.arabicchristianmedia.ui.VersePresentation::class.java)
                                    context.startActivity(intent)
                                },
                                onHdmiBackgroundOverrideChange = { viewModel.setHdmiBackgroundOverride(it) },
                                onToggleLive = { viewModel.toggleLive() },
                                onClearBroadcast = { viewModel.clearBroadcast() },
                                onSelectInterface = { viewModel.selectNetworkInterface(it) },
                                onRefreshInterfaces = { viewModel.refreshNetworkInterfaces() },
                                onSetNdiProtocolEnabled = { viewModel.setNdiProtocolEnabled(it) },
                                onToggleKeepScreenOn = { viewModel.toggleKeepScreenOn() },
                                onToggleNdiSource = { viewModel.toggleNdiSource(it) },
                                onUpdateNdiSourceSpec = { feedKey, w, h, fps -> viewModel.updateNdiSourceSpec(feedKey, w, h, fps) },
                                onToggleNdiMotion = { viewModel.toggleNdiMotion(it) },
                                onToggleAdvancedNdi = { viewModel.toggleAdvancedNdi() },
                                onRefreshNdiDiagnostics = { viewModel.refreshNdiDiagnostics() },
                                onClearNdiDiagnostics = { viewModel.clearNdiDiagnostics() },
                                onSetAdvertiseByName = { viewModel.setAdvertiseByName(it) },
                                onSetAdvertiseName = { viewModel.setAdvertiseName(it) },
                                onToggleAdvertiseTips = { viewModel.toggleAdvertiseTips() },
                                selectedTheme = ColorThemes.byId(uiState.selectedThemeId, uiState.customThemes),
                                customThemes = uiState.customThemes,
                                onSelectTheme = { viewModel.selectTheme(it) },
                                onSaveNewTheme = { viewModel.saveNewCustomTheme(it) },
                                onUpdateTheme = { viewModel.updateCustomTheme(it) },
                                onDeleteTheme = { viewModel.deleteCustomTheme(it) },
                                onStreamBackgroundChange = { viewModel.setStreamBackgroundMode(it) },
                                sectionExpandedMap = sectionExpandedMap,
                                onSetSectionExpanded = { tabId, sectionId, expanded -> viewModel.setSectionExpanded(tabId, sectionId, expanded) }
                            )
                            3 -> SermonListScreen(
                                uiState = uiState,
                                onShowVerse = { viewModel.showSermonVerse(it) },
                                onOpenInReader = { viewModel.openSermonVerseInReader(it) },
                                onRemove = { viewModel.removeSermonEntry(it) },
                                onRestoreVerse = { entry, index -> viewModel.restoreSermonEntry(entry, index) },
                                onClear = { viewModel.clearSermonPlaylist() },
                                onSave = { viewModel.saveCurrentSermon(it) },
                                onOpenSaved = { id, merge -> viewModel.openSavedSermon(id, merge) },
                                onRenameSaved = { id, name -> viewModel.renameSavedSermon(id, name) },
                                onDeleteSaved = { viewModel.deleteSavedSermon(it) },
                                onNextSermon = { viewModel.stepSermon(+1).entry != null },
                                onPrevSermon = { viewModel.stepSermon(-1).entry != null },
                                // Follow-up Phase 4 (item 7): move up/down
                                // reordering (stays inside the share-group).
                                onMoveUp = { viewModel.moveSermonEntry(it, -1) },
                                onMoveDown = { viewModel.moveSermonEntry(it, +1) }
                            )
                        }
                    }
                }
            }
        }
    }
}

