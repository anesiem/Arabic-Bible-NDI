package com.arabicchristianmedia

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material.icons.filled.Info
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
import com.arabicchristianmedia.ui.TemplateEditorScreen
import com.arabicchristianmedia.ui.theme.LocalBentoColors
import com.arabicchristianmedia.ui.theme.MyApplicationTheme
import com.arabicchristianmedia.model.ColorThemes

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

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

                LaunchedEffect(uiState.statusMessage) {
                    uiState.statusMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.dismissStatusMessage()
                    }
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
                                if (selectedTab == 1 && LocalConfiguration.current.screenWidthDp >= 900) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                        if (if (editorTab == 0) uiState.lowerWorkingDirty else uiState.showWorkingDirty) {
                                            Surface(shape = RoundedCornerShape(10.dp), color = bento.primaryContainer) {
                                                Text("معدّل (Modified)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                            }
                                        }
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
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (uiState.isLiveOnAir && uiState.activeVerse != null) Color(0xFFFFDAD6) else bento.surfaceContainer,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (uiState.isLiveOnAir && uiState.activeVerse != null) bento.liveRed else bento.border
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { selectedTab = 2 }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (uiState.isLiveOnAir && uiState.activeVerse != null) bento.liveRedBright else if (uiState.isNativeNdiActive || uiState.isNativeShowActive) bento.green else bento.textSecondary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.isLiveOnAir && uiState.activeVerse != null) "LIVE" else "NDI",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.isLiveOnAir && uiState.activeVerse != null) bento.liveRed else bento.textSecondary
                                        )
                                    }
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
                                    onClick = { selectedTab = 0 },
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
                                onSelectVersion = { viewModel.setBibleVersion(it) },
                                onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                                onClearSearch = { viewModel.clearSearch() },
                                onOpenRecentVerse = { viewModel.openRecentVerseInReader(it) },
                                onClearRecents = { viewModel.clearRecents() },
                                onToggleLive = { viewModel.toggleLive() },
                                onIncreaseFontSize = { viewModel.increaseFontSize() },
                                onDecreaseFontSize = { viewModel.decreaseFontSize() },
                                onSetAppThemeMode = { viewModel.setAppThemeMode(it) }
                            )
                            1 -> TemplateEditorScreen(
                                currentTemplate = uiState.activeTemplate,
                                activeShowTemplate = uiState.activeShowTemplate,
                                templates = uiState.templates,
                                highlightedLowerStyleId = uiState.highlightedLowerStyleId,
                                highlightedShowStyleId = uiState.highlightedShowStyleId,
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
                                onShowHowItWorksChange = { editorTipsVisible = it }
                            )
                            2 -> BroadcastControlScreen(
                                uiState = uiState,
                                onStartServer = { viewModel.startNdiFeeds() },
                                onStopServer = { viewModel.stopNdiFeeds() },
                                onLaunchFullscreenOverlay = {
                                    val intent = Intent(context, FullscreenOverlayActivity::class.java)
                                    context.startActivity(intent)
                                },
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
                                onToggleTashkeel = { viewModel.setTashkeelEnabled(it) },
                                onSelectEnglishVersion = { viewModel.setEnglishVersion(it) },
                                selectedTheme = ColorThemes.byId(uiState.selectedThemeId, uiState.customThemes),
                                customThemes = uiState.customThemes,
                                onSelectTheme = { viewModel.selectTheme(it) },
                                onSaveNewTheme = { viewModel.saveNewCustomTheme(it) },
                                onUpdateTheme = { viewModel.updateCustomTheme(it) }
                            )
                            3 -> SermonListScreen(
                                uiState = uiState,
                                onShowVerse = { viewModel.showSermonVerse(it) },
                                onOpenInReader = { viewModel.openSermonVerseInReader(it) },
                                onRemove = { viewModel.removeSermonEntry(it) },
                                onClear = { viewModel.clearSermonPlaylist() },
                                onSave = { viewModel.saveCurrentSermon(it) },
                                onOpenSaved = { id, merge -> viewModel.openSavedSermon(id, merge) },
                                onRenameSaved = { id, name -> viewModel.renameSavedSermon(id, name) },
                                onDeleteSaved = { viewModel.deleteSavedSermon(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

