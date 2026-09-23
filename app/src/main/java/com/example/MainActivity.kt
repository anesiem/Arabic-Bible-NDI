package com.example

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BibleNdiViewModel
import com.example.ui.BibleReaderScreen
import com.example.ui.BroadcastControlScreen
import com.example.ui.FullscreenOverlayActivity
import com.example.ui.TemplateEditorScreen
import com.example.ui.theme.LocalBentoColors
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BibleNdiViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(uiState.isKeepScreenOn) {
                if (uiState.isKeepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MyApplicationTheme(themeMode = uiState.appThemeMode) {
                val bento = LocalBentoColors.current
                val snackbarHostState = remember { SnackbarHostState() }
                var selectedTab by remember { mutableIntStateOf(0) }
                val context = LocalContext.current

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
                            modifier = Modifier.fillMaxWidth()
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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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

                                    Column {
                                        Text(
                                            text = "Bible NDI",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = bento.textPrimary
                                        )
                                        Text(
                                            text = "Broadcast Live / SVD Arabic (الكتاب المقدس)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = bento.textSecondary
                                        )
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
                                                .background(if (uiState.isLiveOnAir && uiState.activeVerse != null) bento.liveRedBright else bento.textSecondary)
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
                                            imageVector = if (selectedTab == 0) Icons.Filled.MenuBook else Icons.Outlined.MenuBook,
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
                                    label = { Text("NDI Link (البث)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = bento.onPrimaryContainer,
                                        selectedTextColor = bento.primary,
                                        indicatorColor = bento.primaryContainer,
                                        unselectedIconColor = bento.textSecondary,
                                        unselectedTextColor = bento.textSecondary
                                    ),
                                    modifier = Modifier.testTag("tab_broadcast")
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
                                onToggleLive = { viewModel.toggleLive() },
                                onClearBroadcast = { viewModel.clearBroadcast() },
                                onNextVerse = { viewModel.nextVerse() },
                                onPrevVerse = { viewModel.prevVerse() },
                                onIncreaseFontSize = { viewModel.increaseFontSize() },
                                onDecreaseFontSize = { viewModel.decreaseFontSize() },
                                onSetAppThemeMode = { viewModel.setAppThemeMode(it) }
                            )
                            1 -> TemplateEditorScreen(
                                currentTemplate = uiState.activeTemplate,
                                activeShowTemplate = uiState.activeShowTemplate,
                                templates = uiState.templates,
                                activeVerse = uiState.activeVerse,
                                onSelectTemplate = { viewModel.selectTemplate(it) },
                                onUpdateTemplate = { viewModel.updateActiveTemplate(it) },
                                onUpdateShowTemplate = { viewModel.updateActiveShowTemplate(it) },
                                onSaveAsNew = { name, base -> viewModel.saveAsNewTemplate(name, base) },
                                onResetDefaults = { viewModel.resetTemplatesToDefault() },
                                onToggleNdiSource = { viewModel.toggleNdiSource(it) },
                                ndiLowerThirdActive = uiState.ndiLowerThirdEnabled,
                                ndiFullShowActive = uiState.ndiFullShowEnabled
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
                                onToggleKeepScreenOn = { viewModel.toggleKeepScreenOn() }
                            )
                        }
                    }
                }
            }
        }
    }
}

