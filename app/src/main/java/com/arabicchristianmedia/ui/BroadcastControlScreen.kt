package com.arabicchristianmedia.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.arabicchristianmedia.BuildConfig
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.R
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.server.InterfaceType
import com.arabicchristianmedia.server.NdiNativeSender
import com.arabicchristianmedia.server.NdiSourceSpec
import com.arabicchristianmedia.server.NetworkInterfaceInfo
import com.arabicchristianmedia.model.ColorTheme
import com.arabicchristianmedia.model.ColorThemes
import com.arabicchristianmedia.model.ListKind
import com.arabicchristianmedia.model.ListTreatment
import com.arabicchristianmedia.ui.theme.*

@Composable
fun BroadcastControlScreen(
    uiState: BibleNdiUiState,
    onStartServer: (Int) -> Unit,
    onStopServer: () -> Unit,
    onLaunchFullscreenOverlay: () -> Unit,
    onToggleLive: () -> Unit,
    onClearBroadcast: () -> Unit,
    onSelectInterface: (NetworkInterfaceInfo) -> Unit = {},
    onRefreshInterfaces: () -> Unit = {},
    onToggleKeepScreenOn: () -> Unit = {},
    onToggleNdiSource: (String) -> Unit = {},
    onUpdateNdiSourceSpec: (String, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    onToggleNdiMotion: (String) -> Unit = {},
    onToggleAdvancedNdi: () -> Unit = {},
    onRefreshNdiDiagnostics: () -> Unit = {},
    onClearNdiDiagnostics: () -> Unit = {},
    onSetAdvertiseByName: (Boolean) -> Unit = {},
    onSetAdvertiseName: (String) -> Unit = {},
    onToggleAdvertiseTips: () -> Unit = {},
    selectedTheme: ColorTheme = ColorThemes.CLASSIC,
    customThemes: List<ColorTheme> = emptyList(),
    onSelectTheme: (String) -> Unit = {},
    onSaveNewTheme: (ColorTheme) -> Unit = {},
    onUpdateTheme: (ColorTheme) -> Unit = {},
    onDeleteTheme: (String) -> Unit = {},
    // Batch-3: /stream background dots (moved from Editor) — controls the
    // MJPEG /stream background only.
    onStreamBackgroundChange: (com.arabicchristianmedia.model.StreamBackgroundMode) -> Unit = {},
    // 1.9.8: Launch Full Show full-screen (via FullscreenOverlayActivity).
    onLaunchFullscreenShow: () -> Unit = {},
    // 1.9.3: HDMI background override toggle.
    onHdmiBackgroundOverrideChange: (Boolean) -> Unit = {},
    // Item 72 (1.9.9): HDMI presentation toggle + live status.
    hdmiEnabled: Boolean = true,
    hdmiConnected: Boolean = false,
    hdmiPresentationShowing: Boolean = false,
    onHdmiEnabledChange: (Boolean) -> Unit = {},
    // Batch-3: per-tab per-section expand memory (ViewModel-backed map).
    sectionExpandedMap: Map<String, Boolean> = emptyMap(),
    onSetSectionExpanded: (String, String, Boolean) -> Unit = { _, _, _ -> },
    // 1.9.23 (item 10): auto-clear settings (own rail section).
    onSetAutoClearEnabled: (Boolean) -> Unit = {},
    onSetAutoClearDurationMinutes: (Int) -> Unit = {},
    // 1.9.25 (item 2, language mode): interface-language choice
    // ("system" | "ar" | "en"); MainActivity persists + recreates.
    onSetAppLanguage: (String) -> Unit = {},
    // 1.9.27 (item 7): list-colour treatment override for one kind
    // (RECENT / SERMON); null = theme default from his review picks.
    onSetListTreatment: (ListKind, ListTreatment?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bento = LocalBentoColors.current
    /** Label showing the source's *current* configured spec, e.g. "Lower Third • 1280×720 @ 30fps". */
    val specLabel = { feedKey: String, tier: String ->
        val sp = uiState.ndiSourceSpecs[feedKey] ?: NdiNativeSender.defaultSpec(feedKey)
        "$tier • ${sp.width}×${sp.height} @ ${sp.fps}fps"
    }

    var copiedUrlType by remember { mutableStateOf<String?>(null) }
    var showHelpGuide by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // 1.9.23 (Item 7): left-rail navigation (tablet wide layout). The
    // rail lists the sections; tapping one shows ONLY that section on
    // the right. The old anchor-scroll system (chip strip + two
    // LazyColumns + jumpToSetupAnchor) is removed — the narrow layout
    // never used it, so nothing keeps it alive.
    // Future rail order (decided 2026-11-16): social media integration
    // and remote authenticated client go at the TOP of this list, above
    // Device — social media first, then remote client. No
    // placeholders now; the list accepts them at the top later.
    var selectedSetupSection by remember { mutableStateOf("device") }
    val setupRailItems = listOf(
        // 1.9.23 (item 10): the Device section is the very top — the
        // tablet's address (IP/domain) + Keep Screen Awake are the
        // first things needed before a service. It is also the
        // default open section (selectedSetupSection above).
        // 1.9.23: TabletAndroid does not resolve in this icon set (CI #112);
        // Settings is used in SermonListScreen/BibleReaderScreen/MainActivity
        // and needs its own import here (CI #113) — this file imports
        // icons one by one, no wildcard.
        SetupRailEntry("device", context.getString(R.string.s395), Icons.Filled.Settings),
        // 1.9.23 (item 10): auto-clear has its own rail section — it
        // clears ALL outputs (NDI + HTTP), not just one, so it is not
        // an NDI setting and not a Customization.
        SetupRailEntry("autoclear", context.getString(R.string.s396), Icons.Filled.Timer),
        SetupRailEntry("hdmi", context.getString(R.string.s397), Icons.Filled.Tv),
        SetupRailEntry("broadcast", context.getString(R.string.s398), Icons.Filled.LiveTv),
        SetupRailEntry("urls", context.getString(R.string.s399), Icons.Filled.Link),
        SetupRailEntry("vmix", context.getString(R.string.s400), Icons.Filled.Movie),
        SetupRailEntry("propresenter", context.getString(R.string.s401), Icons.Filled.Slideshow),
        SetupRailEntry("obs", context.getString(R.string.s402), Icons.Filled.Videocam),
        SetupRailEntry("api", context.getString(R.string.s403), Icons.Filled.Code),
        SetupRailEntry("custom", context.getString(R.string.s404), Icons.Filled.Palette)
    )

    // Setup redesign (same treatment as the Editor): the page is a CONSOLE
    // (Bible strip + broadcast console) over a SPEC SHEET of summary
    // sections. Summaries = each section's one-line current state.
    val loSpec = uiState.ndiSourceSpecs[NdiNativeSender.FEED_LOWER] ?: NdiNativeSender.defaultSpec(NdiNativeSender.FEED_LOWER)
    val fuSpec = uiState.ndiSourceSpecs[NdiNativeSender.FEED_FULL] ?: NdiNativeSender.defaultSpec(NdiNativeSender.FEED_FULL)
    val specOf = { feed: String -> uiState.ndiSourceSpecs[feed] ?: NdiNativeSender.defaultSpec(feed) }
    val netSummary = (uiState.selectedInterface?.labelAr
        ?: uiState.availableInterfaces.firstOrNull { it.ip == uiState.activeIpAddress }?.labelAr
        ?: context.getString(R.string.s413)) + " · " + uiState.activeIpAddress
    val netWarning = uiState.activeIpAddress.isBlank() ||
        (uiState.availableInterfaces.isNotEmpty() && uiState.availableInterfaces.none { it.isRecommended })

    val broadcastStatusBlock: @Composable () -> Unit = {
        // Card 1: NDI & Stream Server Status + Master Controls
        // Sender state (not the HTTP flag) gates the whole card: after a
        // Stop the HTTP server flag can stay set while the senders are
        // off, and the card must not stay green in that state.
        val anyActive = uiState.isNativeNdiActive || uiState.isNativeShowActive
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bento.card,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (anyActive) bento.green else bento.border
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isNativeNdiActive || uiState.isNativeShowActive) bento.green else bento.liveRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.isNativeNdiActive || uiState.isNativeShowActive) context.getString(R.string.s414) else context.getString(R.string.s415),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isNativeNdiActive || uiState.isNativeShowActive) bento.green else bento.textPrimary
                        )
                    }

                    // Single Unified Start/Stop NDI Broadcast Button.
                    // The button follows the SENDERS only: STOP whenever
                    // either sender is active (it stops both), START only
                    // when neither is. The HTTP server flag must NOT gate
                    // it — in partial states it hid STOP and left only the
                    // per-feed toggles able to stop. (anyActive is hoisted
                    // to the top of this block so the card border and the
                    // badge below share the same gate.)
                    if (anyActive) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = bento.liveRed.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, bento.liveRed),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onStopServer)
                                .testTag("stop_server_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = bento.liveRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(context.getString(R.string.s416), fontSize = 11.sp, color = bento.liveRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = bento.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onStartServer(uiState.serverPort) }
                                .testTag("start_server_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = bento.onPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(context.getString(R.string.s417), fontSize = 11.sp, color = bento.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 1.9.23 (Item 4): the NDI protocol switch is REMOVED —
                // one button (Stop All / Start All) now controls NDI.
                // Stop All turns the protocol off internally too, and
                // Start All turns it back on; the per-feed toggles
                // re-enable it when flipped on. No UI exposes it.

                Spacer(modifier = Modifier.height(12.dp))

                // NDI Protocol Badge (native NDI 6 senders; the OS-level NDI discovery
                // is handled by the NDI runtime itself — no in-app beacon)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (anyActive) bento.green.copy(alpha = 0.12f) else bento.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (anyActive) bento.green else bento.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = if (anyActive) bento.green else bento.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (anyActive) context.getString(R.string.s418) else context.getString(R.string.s419),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (anyActive) bento.green else bento.textSecondary
                            )
                            Text(
                                text = if (anyActive)
                                    context.getString(R.string.s420, loSpec.width, loSpec.height, loSpec.fps, fuSpec.width, fuSpec.height, fuSpec.fps)
                                else
                                    context.getString(R.string.s421, uiState.serverPort),
                                fontSize = 10.sp,
                                color = if (anyActive) bento.green else bento.textSecondary
                            )
                        }
                    }
                }
            }
        }

    }

    val overlayBlock: @Composable () -> Unit = {
        // Card 3: Fullscreen Transparent Overlay Option
        // v1.9.5: restructured to Column — each element on its own line
        // with proper spacing (was Row with SpaceBetween causing overlap).
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bento.primaryContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, bento.secondary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bento.surfaceContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = bento.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.s422),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = context.getString(R.string.s423),
                            fontSize = 10.sp,
                            color = bento.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bento.primary,
                    onClick = onLaunchFullscreenOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fullscreen_overlay_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            context.getString(R.string.s424),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 1.9.14 (item 31): Full Show button now matches the Lower
                // Show button above — same full-width Surface, same 12sp
                // bold text, same 10dp vertical padding; the redundant
                // label above it (same words as the button) is dropped.
                // 1.9.8: Full Show full-screen button (uses Full Show
                // template via FullscreenOverlayActivity).
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bento.primary,
                    onClick = onLaunchFullscreenShow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fullscreen_show_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            context.getString(R.string.s425),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 1.9.3: 4 color dots moved here from old location. Controls HDMI/full-screen background.
                Text(
                    text = context.getString(R.string.s426),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val streamModes = listOf(
                        com.arabicchristianmedia.model.StreamBackgroundMode.TRANSPARENT_ALPHA,
                        com.arabicchristianmedia.model.StreamBackgroundMode.CHROMA_GREEN,
                        com.arabicchristianmedia.model.StreamBackgroundMode.PURE_BLACK,
                        com.arabicchristianmedia.model.StreamBackgroundMode.DARK_STUDIO
                    )
                    val currentStreamMode = uiState.activeTemplate.streamBackgroundMode
                    streamModes.forEach { mode ->
                        val dotColor = try {
                            Color(android.graphics.Color.parseColor(mode.colorHex))
                        } catch (e: Exception) { bento.textSecondary }
                        val dotSelected = currentStreamMode == mode
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = dotColor,
                            border = androidx.compose.foundation.BorderStroke(
                                if (dotSelected) 3.dp else 1.dp,
                                if (dotSelected) bento.primary else bento.border
                            ),
                            modifier = Modifier
                                .size(if (dotSelected) 36.dp else 32.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { onStreamBackgroundChange(mode) }
                        ) {}
                    }
                }
                // 1.9.3: Override toggle — when ON, color applies to both Lower and Full Show.
                // 1.9.4: fixed overlap — Column layout with proper spacing.
                // Item 21 (1.9.8): ensure no overlap — each element on its own line.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = context.getString(R.string.s427),
                        fontSize = 12.sp,
                        color = bento.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.material3.Switch(
                        checked = uiState.hdmiBackgroundOverride,
                        onCheckedChange = { onHdmiBackgroundOverrideChange(it) }
                    )
                }
            }
        }
    }

    val urlsCardBlock: @Composable () -> Unit = {
        // Output URLs card (expanded by default for immediate accessibility)
        var showOutputUrls by remember { mutableStateOf(true) }
        // 1.9.15 item 7: per-row QR dialog state (Lower / Full).
        var showQrLower by remember { mutableStateOf(false) }
        var showQrFull by remember { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bento.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showOutputUrls = !showOutputUrls }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("toggle_output_urls"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = bento.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 1.9.25 (item 9): the "انقر للطي" hint is gone —
                        // the chevron alone is the control.
                        Text(
                            text = context.getString(R.string.s428),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.textPrimary
                        )
                    }

                    // 1.9.25 (item 13): the same ▲/▼ chevron as every
                    // other expandable card (▲ open, ▼ closed) — the
                    // "طي"/"عرض" text button is gone with unification.
                    Text(
                        text = if (showOutputUrls) "▲" else "▼",
                        fontSize = 12.sp,
                        color = bento.textSecondary
                    )
                }

                AnimatedVisibility(
                    visible = showOutputUrls,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.HorizontalDivider(color = bento.borderSubtle)

                        // Broadcast URL 1: HTML5 Transparent Overlay URL (Browser Source)
                        Text(
                            context.getString(R.string.s433),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bento.textPrimary
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bento.card,
                            border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiState.serverUrl.isNotBlank()) uiState.serverUrl else "http://192.168.x.x:${uiState.serverPort}/lower",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = bento.primary,
                                    modifier = Modifier.weight(1f)
                                )

                                // 1.9.25 (items 4/5): the shared icon-only
                                // copy/open/QR set.
                                UrlActionIcons(
                                    url = uiState.serverUrl,
                                    copyType = "OVERLAY",
                                    copiedUrlType = copiedUrlType,
                                    onCopied = { copiedUrlType = it },
                                    clipLabel = context.getString(R.string.s597),
                                    copiedToast = context.getString(R.string.s692),
                                    qrDescription = context.getString(R.string.s588),
                                    onQr = { showQrLower = true }
                                )
                            }
                        }
                        if (showQrLower) {
                            QrCodeDialog(url = uiState.serverUrl, label = context.getString(R.string.s439), onDismiss = { showQrLower = false })
                        }

                        // Broadcast URL 2: Full Show Projector URL
                        Text(
                            context.getString(R.string.s434),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bento.textPrimary
                        )

                        // 1.9.15 item 7: showUrl hoisted to the column level
                        // so the QR dialog below the row can use it too.
                        val showUrl = uiState.serverUrl.replace("/lower", "/full")
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bento.card,
                            border = BorderStroke(1.dp, bento.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showUrl.isNotBlank()) showUrl else "http://192.168.x.x:${uiState.serverPort}/full",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = bento.primary,
                                    modifier = Modifier.weight(1f)
                                )

                                // 1.9.25 (items 4/5): the shared icon-only
                                // copy/open/QR set.
                                UrlActionIcons(
                                    url = showUrl,
                                    copyType = "SHOW",
                                    copiedUrlType = copiedUrlType,
                                    onCopied = { copiedUrlType = it },
                                    clipLabel = context.getString(R.string.s598),
                                    copiedToast = context.getString(R.string.s694),
                                    qrDescription = context.getString(R.string.s589),
                                    onQr = { showQrFull = true }
                                )
                            }
                        }
                        if (showQrFull) {
                            QrCodeDialog(url = showUrl, label = context.getString(R.string.s440), onDismiss = { showQrFull = false })
                        }


                        // Error banner: visible even when Diagnostics is collapsed, so the user always knows
                        if (!uiState.showAdvancedNdi && uiState.ndiErrorEvents.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bento.liveRed.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, bento.liveRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = context.getString(R.string.s441, uiState.ndiErrorEvents.size),
                                    fontSize = 10.sp,
                                    color = bento.liveRed,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val mjpegBlock: @Composable () -> Unit = {
        // Broadcast URL 3: Direct MJPEG Video Stream URL (old numbering)
        // 1.9.25 (item 5): QR dialog state for the MJPEG URL.
        var showQrStream by remember { mutableStateOf(false) }

        Text(
            context.getString(R.string.s435),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = bento.textPrimary
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = bento.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.streamUrl.isNotBlank()) uiState.streamUrl else "http://192.168.x.x:${uiState.serverPort}/stream",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = bento.primary,
                    modifier = Modifier.weight(1f)
                )

                // 1.9.25 (items 4/5/15): the shared icon-only
                // copy/open/QR set (was a green copy-only button).
                UrlActionIcons(
                    url = uiState.streamUrl,
                    copyType = "STREAM",
                    copiedUrlType = copiedUrlType,
                    onCopied = { copiedUrlType = it },
                    clipLabel = context.getString(R.string.s599),
                    copiedToast = context.getString(R.string.s695),
                    qrDescription = context.getString(R.string.s880),
                    onQr = { showQrStream = true }
                )
            }
        }
        if (showQrStream) {
            QrCodeDialog(url = uiState.streamUrl, label = context.getString(R.string.s879), onDismiss = { showQrStream = false })
        }
        // 1.9.3: 4 color dots MOVED to HDMI section (see hdmiCardBlock).
        // The MJPEG /stream background is now controlled from there.
    }

    val allUrlsBlock: @Composable () -> Unit = {
        // Card: All available URLs. 1.9.25 (item 5): copy lives in the
        // shared UrlActionIcons set now (with open + QR per row), so
        // the block only passes the copied-state through.
        AllUrlsCard(
            baseUrl = uiState.serverUrl,
            textPrimary = bento.textPrimary,
            textSecondary = bento.textSecondary,
            cardColor = bento.card,
            borderColor = bento.border,
            copiedUrlType = copiedUrlType,
            onCopied = { copiedUrlType = it }
        )
    }

    // Batch-3: URL Reference — all pages, APIs and their parameters,
    // at the bottom of Setup after All URLs.
    val urlReferenceBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s442),
            sectionId = "url-reference",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = context.getString(R.string.s443)
        ) {
            val base = if (uiState.serverUrl.isNotBlank()) uiState.serverUrl.removeSuffix("/lower") else "http://<IP>:${uiState.serverPort}"
            val urls = listOf(
                "/lower" to context.getString(R.string.s444),
                "/full" to context.getString(R.string.s445),
                "/remote" to context.getString(R.string.s446),
                "/stage" to context.getString(R.string.s447),
                "/stream" to context.getString(R.string.s448),
                "/api/trigger" to context.getString(R.string.s449),
                "/api/onair" to context.getString(R.string.s450),
                "/api/clear" to context.getString(R.string.s451),
                "/api/verse" to context.getString(R.string.s452),
                "/api/books" to context.getString(R.string.s453),
                "/api/chapter" to context.getString(R.string.s454),
                "/api/recents" to context.getString(R.string.s455),
                "/api/sermons" to context.getString(R.string.s347),
                "/api/sermons/current" to context.getString(R.string.s456),
                "/api/sermons/next" to context.getString(R.string.s457),
                "/api/sermons/prev" to context.getString(R.string.s458),
                "/api/preflight" to context.getString(R.string.s459),
                "/api/debug" to context.getString(R.string.s460),
                "/api/status" to context.getString(R.string.s461),
                "/events" to context.getString(R.string.s462)
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                urls.forEach { (path, desc) ->
                    Column {
                        Text(
                            text = base + path,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = bento.green
                        )
                        Text(
                            text = desc,
                            fontSize = 10.sp,
                            color = bento.textSecondary
                        )
                    }
                }
            }
        }
    }

    // 1.9.23 (Item 7): /api-docs link row for the API rail section —
    // the interactive Swagger docs for the endpoints listed above.
    // Copy pattern mirrors allUrlsBlock (clipboard + toast).
    val apiDocsBlock: @Composable () -> Unit = {
        // 1.9.25 (item 5): QR dialog state for the API docs URL.
        var showQrApiDocs by remember { mutableStateOf(false) }
        val base = if (uiState.serverUrl.isNotBlank()) uiState.serverUrl.removeSuffix("/lower") else "http://<IP>:${uiState.serverPort}"
        val full = "$base/api-docs"
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = bento.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(context.getString(R.string.s463), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                    Text(full, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = bento.textSecondary)
                }
                // 1.9.25 (item 5): the shared icon-only copy/open/QR
                // set (open + QR are new here).
                UrlActionIcons(
                    url = full,
                    copyType = "APIDOCS",
                    copiedUrlType = copiedUrlType,
                    onCopied = { copiedUrlType = it },
                    clipLabel = context.getString(R.string.s600),
                    copiedToast = context.getString(R.string.s697),
                    qrDescription = context.getString(R.string.s882),
                    onQr = { showQrApiDocs = true }
                )
            }
        }
        if (showQrApiDocs) {
            QrCodeDialog(url = full, label = context.getString(R.string.s881), onDismiss = { showQrApiDocs = false })
        }
    }

    val lowerBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s464),
            sectionId = "ndi-lower",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            // 1.9.25 (item 10): the feed is named in full (Lower Third /
            // Full Show), not the bare "Lower"/"Full".
            summary = context.getString(R.string.s465) + loSpec.width + "×" + loSpec.height + "@" + loSpec.fps
        ) {
            // Item 20: the restart is immediate only when this feed
            // and the NDI protocol are both on; otherwise the new spec
            // is only persisted and applies at the next start.
            val restartNote = if (uiState.ndiLowerEnabled && uiState.isNdiProtocolEnabled)
                context.getString(R.string.s466)
            else
                context.getString(R.string.s467)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(R.string.s468) + restartNote,
                    fontSize = 11.sp,
                    color = bento.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                InfoPopupButton(
                    infoTitle = context.getString(R.string.s464),
                    infoText = context.getString(R.string.s468) + restartNote
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            NdiSourceRow(
                feedKey = NdiNativeSender.FEED_LOWER,
                toggleKey = "lower",
                tierLabel = specLabel(NdiNativeSender.FEED_LOWER, context.getString(R.string.s469)),
                enabled = uiState.ndiLowerEnabled,
                active = uiState.isNativeNdiActive,
                tint = bento.primary,
                textSecondary = bento.textSecondary,
                onToggle = onToggleNdiSource
            )
            Spacer(modifier = Modifier.height(8.dp))
            NdiSourceSettingsRow(
                feedKey = NdiNativeSender.FEED_LOWER,
                spec = specOf(NdiNativeSender.FEED_LOWER),
                tint = bento.primary,
                textSecondary = bento.textSecondary,
                onUpdate = onUpdateNdiSourceSpec,
                onToggleMotion = onToggleNdiMotion
            )
        }
    }

    val fullBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s470),
            sectionId = "ndi-full",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            // 1.9.25 (item 10): the feed is named in full (Lower Third /
            // Full Show), not the bare "Lower"/"Full".
            summary = context.getString(R.string.s471) + fuSpec.width + "×" + fuSpec.height + "@" + fuSpec.fps
        ) {
            // Item 20: the restart is immediate only when this feed
            // and the NDI protocol are both on; otherwise the new spec
            // is only persisted and applies at the next start.
            val restartNote = if (uiState.ndiFullShowEnabled && uiState.isNdiProtocolEnabled)
                context.getString(R.string.s466)
            else
                context.getString(R.string.s467)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(R.string.s468) + restartNote,
                    fontSize = 11.sp,
                    color = bento.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                InfoPopupButton(
                    infoTitle = context.getString(R.string.s470),
                    infoText = context.getString(R.string.s468) + restartNote
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            NdiSourceRow(
                feedKey = NdiNativeSender.FEED_FULL,
                toggleKey = "full",
                tierLabel = specLabel(NdiNativeSender.FEED_FULL, context.getString(R.string.s472)),
                enabled = uiState.ndiFullShowEnabled,
                active = uiState.isNativeShowActive,
                tint = bento.green,
                textSecondary = bento.textSecondary,
                onToggle = onToggleNdiSource
            )
            Spacer(modifier = Modifier.height(8.dp))
            NdiSourceSettingsRow(
                feedKey = NdiNativeSender.FEED_FULL,
                spec = specOf(NdiNativeSender.FEED_FULL),
                tint = bento.green,
                textSecondary = bento.textSecondary,
                onUpdate = onUpdateNdiSourceSpec,
                onToggleMotion = onToggleNdiMotion
            )
        }
    }

    // 1.9.15 item 5: ONE HDMI section (was three pieces — a static
    // non-collapsible group header + the always-visible overlayBlock +
    // the collapsible hdmiBlock whose title duplicated the header).
    // A single SetupSection, title "HDMI (شاشة خارجية)", sectionId
    // "hdmi" (reuses the "setup:hdmi" expand key), default collapsed.
    // Content: the presentation toggle FIRST, then the status text,
    // then the overlay block nested below, unchanged.
    val hdmiSection: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s473),
            sectionId = "hdmi",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = if (hdmiPresentationShowing) context.getString(R.string.s474)
                else if (hdmiConnected) context.getString(R.string.s475)
                else context.getString(R.string.s476)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.s477),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = bento.textPrimary
                    )
                    Text(
                        text = context.getString(R.string.s478),
                        fontSize = 11.sp,
                        color = bento.textSecondary
                    )
                }
                androidx.compose.material3.Switch(
                    checked = hdmiEnabled,
                    onCheckedChange = { onHdmiEnabledChange(it) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = context.getString(R.string.s479) + if (hdmiConnected) context.getString(R.string.s475) else context.getString(R.string.s476) +
                    " • " + if (hdmiPresentationShowing) context.getString(R.string.s480) else context.getString(R.string.s481),
                fontSize = 11.sp,
                color = bento.textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            overlayBlock()
        }
    }

    // 1.9.14 (item 33): Keep Screen Awake — MOVED out of customBlock to
    // the very top of the Setup tab (above the Broadcast header, both
    // layouts). Same card, same toggle + info button + icon.
    // 1.9.15 item 6: slim row now (was a full ~66dp card). It lives
    // directly above the URLs header (wide: first child of the urls
    // column; compact: above the URLs header) — a plain slim row, NOT
    // a SetupSection. Padding 8dp vert / 12dp horiz, icon box 28dp,
    // icon 16dp, radius 14dp — about 40-44dp tall.
    val keepAwakeBlock: @Composable () -> Unit = {
        // Card: Keep Screen Awake
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bento.card,
            border = BorderStroke(1.dp, bento.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isKeepScreenOn) bento.primaryContainer else bento.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (uiState.isKeepScreenOn) Icons.Default.LiveTv else Icons.Default.Tv,
                            contentDescription = null,
                            tint = if (uiState.isKeepScreenOn) bento.primary else bento.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            context.getString(R.string.s482),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.textPrimary
                        )
                        InfoPopupButton(context.getString(R.string.s482), context.getString(R.string.s483))
                    }
                }

                Switch(
                    checked = uiState.isKeepScreenOn,
                    onCheckedChange = { onToggleKeepScreenOn() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = bento.primary
                    )
                )
            }
        }
    }

    // 1.9.25 (item 2, language mode): interface-language switcher in the
    // Device section — System language (the default), Arabic, or English.
    // Tapping a chip persists the choice and recreates the Activity (via
    // onSetAppLanguage), so every string + the layout direction re-resolve.
    // Bible text is untouched; Android's per-app language picker (system
    // Settings) is the second way to switch — both read the same choice.
    val languageBlock: @Composable () -> Unit = {
        val currentChoice = AppLanguage.currentChoice(context)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bento.card,
            border = BorderStroke(1.dp, bento.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    context.getString(R.string.s896),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = currentChoice == AppLanguage.SYSTEM,
                        onClick = { onSetAppLanguage(AppLanguage.SYSTEM) },
                        label = { Text(context.getString(R.string.s897)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = currentChoice == AppLanguage.ARABIC,
                        onClick = { onSetAppLanguage(AppLanguage.ARABIC) },
                        label = { Text(context.getString(R.string.s898)) }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = currentChoice == AppLanguage.ENGLISH,
                        onClick = { onSetAppLanguage(AppLanguage.ENGLISH) },
                        label = { Text(context.getString(R.string.s899)) }
                    )
                }
            }
        }
    }

    val customBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s484),
            sectionId = "custom",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = selectedTheme.displayName(context)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bento.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
                modifier = Modifier.fillMaxWidth().clickable { showThemeDialog = true }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = context.getString(R.string.s592),
                        tint = bento.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.s485),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.textPrimary
                        )
                        Text(
                            text = selectedTheme.displayName(context),
                            fontSize = 11.sp,
                            color = bento.textSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(22.dp).background(Color(selectedTheme.light.bandA), RoundedCornerShape(6.dp)))
                        Box(modifier = Modifier.size(22.dp).background(Color(selectedTheme.light.bandB), RoundedCornerShape(6.dp)))
                        Box(modifier = Modifier.size(22.dp).background(Color(selectedTheme.light.recents), RoundedCornerShape(6.dp)))
                    }
                }
            }
            // 1.9.27 (item 7): list colours — per-kind treatment
            // overrides. Theme default = his review picks per theme
            // and mode; a choice replaces the default everywhere.
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = bento.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = context.getString(R.string.s900),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ListTreatmentDropdown(
                        label = context.getString(R.string.s901),
                        selected = uiState.listTreatmentRecents,
                        onSelect = { onSetListTreatment(ListKind.RECENT, it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ListTreatmentDropdown(
                        label = context.getString(R.string.s902),
                        selected = uiState.listTreatmentSermon,
                        onSelect = { onSetListTreatment(ListKind.SERMON, it) }
                    )
                }
            }
        }
    }

    val obsBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s486),
            sectionId = "obs",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = context.getString(R.string.s487)
        ) {
                                // How-to OBS Guide Banner
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = bento.green.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, bento.green),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = bento.green,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                context.getString(R.string.s488),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = bento.green
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = context.getString(R.string.s489),
                                            fontSize = 9.sp,
                                            color = bento.green,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
            Spacer(modifier = Modifier.height(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = bento.primary
                    ) {
                        Text(
                            text = context.getString(R.string.s411),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        context.getString(R.string.s490),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1.9.25 (item 6): Arabic-Indic step numbers (١٢٣).
                val obsSteps = listOf(
                    context.getString(R.string.s491),
                    context.getString(R.string.s492, if (uiState.serverUrl.isNotBlank()) uiState.serverUrl else "http://<IP>:8080/lower"),
                    context.getString(R.string.s493),
                    context.getString(R.string.s494),
                    context.getString(R.string.s495)
                )

                obsSteps.forEach { step ->
                    Text(
                        text = step,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = bento.textSecondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
    val vmixBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s496),
            sectionId = "vmix",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = context.getString(R.string.s497)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = bento.green
                    ) {
                        Text(
                            text = context.getString(R.string.s409),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.s498),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1.9.25 (item 6): Arabic-Indic step numbers (١٢٣).
                val vmixSteps = listOf(
                    context.getString(R.string.s499),
                    context.getString(R.string.s500),
                    context.getString(R.string.s501),
                    context.getString(R.string.s502)
                )

                vmixSteps.forEach { step ->
                    Text(
                        text = step,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = bento.textSecondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        // v1.8: vMix SetText push settings (LAN-only).
        VMixSettingsCard(
            cardColor = bento.card,
            borderColor = bento.border,
            textPrimary = bento.textPrimary,
            textSecondary = bento.textSecondary
        )
        }
    }
    // Batch-2 item 25: ProPresenter stage-message push settings (LAN-only).
    val propresenterBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s503),
            sectionId = "propresenter",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = context.getString(R.string.s504)
        ) {
        ProPresenterSettingsCard(
            cardColor = bento.card,
            borderColor = bento.border,
            textPrimary = bento.textPrimary,
            textSecondary = bento.textSecondary
        )
        }
    }
    val networkBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s505),
            sectionId = "network",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = netSummary,
            seedExpanded = netWarning,
            seedKey = netWarning
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = bento.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = context.getString(R.string.s506),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.textPrimary
                        )
                    }

                    // Refresh Network Interfaces Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bento.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onRefreshInterfaces)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = context.getString(R.string.s593),
                                tint = bento.textPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(context.getString(R.string.s507), fontSize = 10.sp, color = bento.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.availableInterfaces.isEmpty()) {
                    Text(
                        text = context.getString(R.string.s508, uiState.activeIpAddress),
                        fontSize = 11.sp,
                        color = bento.textSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.availableInterfaces.forEach { iface ->
                            val isSelected = uiState.selectedInterface?.id == iface.id || uiState.activeIpAddress == iface.ip
                            val cardBg = when {
                                isSelected -> bento.primaryContainer
                                iface.isRecommended -> bento.green.copy(alpha = 0.12f)
                                !iface.reachableFromLan -> bento.liveRed.copy(alpha = 0.12f)
                                else -> bento.surfaceContainer
                            }
                            val borderCol = when {
                                isSelected -> bento.primary
                                iface.isRecommended -> bento.green
                                !iface.reachableFromLan -> bento.liveRed
                                else -> bento.border
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cardBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectInterface(iface) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = when (iface.type) {
                                                InterfaceType.WIFI -> Icons.Default.Wifi
                                                InterfaceType.ETHERNET -> Icons.Default.NetworkCheck
                                                InterfaceType.HOTSPOT -> Icons.Default.Router
                                                else -> Icons.Default.Info
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) bento.primary else bento.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = iface.labelAr,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) bento.onPrimaryContainer else bento.textPrimary
                                            )
                                            if (iface.isRecommended) {
                                                Text(
                                                    text = context.getString(R.string.s509),
                                                    fontSize = 9.sp,
                                                    color = bento.green,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            } else if (!iface.reachableFromLan) {
                                                Text(
                                                    text = "⚠️ " + context.getString(R.string.s510),
                                                    fontSize = 9.sp,
                                                    color = bento.liveRed
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = bento.primary
                                        ) {
                                            Text(
                                                text = context.getString(R.string.s511),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = bento.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Connection Warning & Troubleshooting Explanation Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bento.warning.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, bento.warning),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = bento.warning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = context.getString(R.string.s512),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = bento.warning
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = context.getString(R.string.s513),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = bento.warning
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = context.getString(R.string.s514),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = bento.warning
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = context.getString(R.string.s515),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = bento.warning
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
                                // v1.9.1 (#16): advertise overlay URLs by IP or by the tablet's network name,
                                // with a collapsible information & tips card (his design).
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = bento.card,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            context.getString(R.string.s516),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = bento.textPrimary
                                        )
                                        Text(context.getString(R.string.s517), fontSize = 10.sp, color = bento.textSecondary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        // 1.9.23: the advertise-by-name toggle sits beside
                                        // the field — ON uses the domain, OFF uses the IP.
                                        // The field is always visible (IP as grey
                                        // placeholder) and disabled while the toggle is off.
                                        var nameText by remember(uiState.advertiseName) { mutableStateOf(uiState.advertiseName) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TextField(
                                                value = nameText,
                                                onValueChange = { nameText = it },
                                                label = { Text(context.getString(R.string.s518)) },
                                                placeholder = { Text(uiState.activeIpAddress, color = bento.textSecondary) },
                                                singleLine = true,
                                                enabled = uiState.advertiseByName,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Switch(
                                                checked = uiState.advertiseByName,
                                                onCheckedChange = { onSetAdvertiseByName(it) }
                                            )
                                        }
                                        if (uiState.advertiseByName) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    context.getString(R.string.s519) + nameText.ifBlank { "…" } + ":" + uiState.serverPort + "/lower",
                                                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = bento.textSecondary
                                                )
                                                androidx.compose.material3.TextButton(onClick = { onSetAdvertiseName(nameText) }) {
                                                    Text(context.getString(R.string.s520))
                                                }
                                            }
                                            Text(context.getString(R.string.s521) + uiState.activeIpAddress, fontSize = 10.sp, color = bento.textSecondary)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(context.getString(R.string.s522), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                                            androidx.compose.material3.TextButton(onClick = { onToggleAdvertiseTips() }) {
                                                Text(if (uiState.advertiseTipsCollapsed) context.getString(R.string.s523) else context.getString(R.string.s524))
                                            }
                                        }
                                        if (!uiState.advertiseTipsCollapsed) {
                                            Text(context.getString(R.string.s525), fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(context.getString(R.string.s526), fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(context.getString(R.string.s527), fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(context.getString(R.string.s528), fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(context.getString(R.string.s529), fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                    }
                                }
        }
    }
    val diagnosticsBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s530),
            sectionId = "diagnostics",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            // 1.9.25 (item 8): two short lines — connected count and
            // error count each on its own line (the one-line form
            // reordered confusingly in RTL).
            summary = context.getString(R.string.s531, uiState.sseConnectedNow, uiState.ndiErrorEvents.size),
            controlledExpanded = uiState.showAdvancedNdi,
            onToggleControlled = onToggleAdvancedNdi
        ) {
            if (uiState.showAdvancedNdi) {
                LaunchedEffect(Unit) { onRefreshNdiDiagnostics() }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = bento.border)
                Spacer(modifier = Modifier.height(8.dp))
                val activeOf = mapOf(
                    NdiNativeSender.FEED_LOWER to uiState.isNativeNdiActive,
                    NdiNativeSender.FEED_FULL to uiState.isNativeShowActive
                )
                NdiNativeSender.ALL_FEEDS.forEach { feed ->
                    val spec = uiState.ndiSourceSpecs[feed] ?: NdiNativeSender.defaultSpec(feed)
                    val running = activeOf[feed] == true
                    Text(
                        text = "${NdiNativeSender.displayName(feed)}: ${spec.width}×${spec.height} @ ${spec.fps}fps — " +
                            if (running) context.getString(R.string.s532) else context.getString(R.string.s533),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (running) bento.green else bento.textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    context.getString(R.string.s534),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                // 2026-10-28 diagnostics split: viewer activity is
                // informational (grey), never an error. A viewer
                // leaving is recorded as "viewer left", not an error,
                // and Clear clears this line too (see ViewModel).
                Text(
                    text = context.getString(R.string.s535, uiState.sseConnectedNow, uiState.sseConnectTotal, uiState.sseDropTotal) +
                        if (uiState.sseLastDropReason.isNotEmpty() && uiState.sseLastDropAgoSec >= 0)
                            context.getString(R.string.s536, uiState.sseLastDropReason, uiState.sseLastDropAgoSec)
                        else "",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = bento.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    context.getString(R.string.s537, uiState.ndiErrorEvents.size),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                if (uiState.ndiErrorEvents.isEmpty()) {
                    Text(
                        context.getString(R.string.s538),
                        fontSize = 10.sp,
                        color = bento.green
                    )
                } else {
                    uiState.ndiErrorEvents.takeLast(20).reversed().forEach { e ->
                        Text(
                            "${e.time} • ${e.source}: ${e.message}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = bento.liveRed
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.TextButton(onClick = { onRefreshNdiDiagnostics() }) {
                        Text(context.getString(R.string.s507), fontSize = 10.sp)
                    }
                    androidx.compose.material3.TextButton(onClick = { onClearNdiDiagnostics() }) {
                        Text(context.getString(R.string.s539), fontSize = 10.sp)
                    }
                }
            }
        }
    }

    // 1.9.23 (item 10): auto-clear — its own rail section (position 2,
    // after NDI Broadcast). It clears ALL outputs (NDI + HTTP), so it
    // is not an NDI setting and not a Customization. The ring + pause +
    // stop live in the Library header and on /remote; this section holds
    // only the settings (ON/OFF + duration).
    val autoClearBlock: @Composable () -> Unit = {
        SetupSection(
            title = context.getString(R.string.s407),
            sectionId = "autoclear",
            sectionExpandedMap = sectionExpandedMap,
            onSetSectionExpanded = onSetSectionExpanded,
            summary = if (uiState.autoClearEnabled) context.getString(R.string.s540, uiState.autoClearDurationMinutes) else context.getString(R.string.s541)
        ) {
            // ON/OFF toggle.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.s542),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                    Text(
                        text = context.getString(R.string.s543),
                        fontSize = 11.sp,
                        color = bento.textSecondary
                    )
                }
                Switch(
                    checked = uiState.autoClearEnabled,
                    onCheckedChange = { onSetAutoClearEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = bento.onPrimary,
                        checkedTrackColor = bento.primary,
                        uncheckedThumbColor = bento.textSecondary,
                        uncheckedTrackColor = bento.surfaceVariant
                    ),
                    modifier = Modifier.testTag("autoclear_toggle")
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Duration: a NUMBER INPUT in minutes (not a picker — his
            // correction). Digits only, 1–60, committed on Done or when
            // the field loses focus; invalid input restores the last
            // saved value.
            val focusManager = LocalFocusManager.current
            var durationText by remember { mutableStateOf(uiState.autoClearDurationMinutes.toString()) }
            var durationFocused by remember { mutableStateOf(false) }
            fun commitDuration() {
                val parsed = durationText.toIntOrNull()
                if (parsed == null) {
                    durationText = uiState.autoClearDurationMinutes.toString()
                } else {
                    onSetAutoClearDurationMinutes(parsed)
                    durationText = parsed.coerceIn(1, 60).toString()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.s544),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                    Text(
                        text = context.getString(R.string.s545),
                        fontSize = 11.sp,
                        color = bento.textSecondary
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = durationText,
                    onValueChange = { next ->
                        // Digits only, max 2 characters (bounds are 1–60).
                        if (next.length <= 2 && next.all { it.isDigit() }) durationText = next
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = {
                        commitDuration()
                        focusManager.clearFocus()
                    }),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        color = bento.textPrimary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .width(96.dp)
                        .onFocusChanged {
                            if (durationFocused && !it.isFocused) commitDuration()
                            durationFocused = it.isFocused
                        }
                        .testTag("autoclear_duration_input")
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(bento.bg)) {
        // Screen-size gate (see LayoutBreakpoints): content size is
        // ~567dp tall on tablet landscape, so the wide layout gets the
        // left rail + single-section right side (1.9.23 Item 7).
        val screenConfig = LocalConfiguration.current
        if (isWideLayout(screenConfig.screenWidthDp.dp, screenConfig.screenHeightDp.dp)) {
            Column(Modifier.fillMaxSize()) {
                // 1.9.23 (Item 7): left-rail navigation — the rail lists
                // the sections, the right side shows ONLY the selected
                // one. The chip strip and the two-column layout are gone.
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    // Left rail: vertical section list, selected item
                    // highlighted. Scrolls if the list ever outgrows it.
                    Column(
                        Modifier.width(250.dp).fillMaxHeight()
                            .background(bento.card)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        setupRailItems.forEach { entry ->
                            SetupRailItem(
                                entry = entry,
                                selected = selectedSetupSection == entry.id,
                                onClick = { selectedSetupSection = entry.id }
                            )
                        }
                    }
                    // Divider between rail and content.
                    Box(Modifier.width(1.dp).fillMaxHeight().background(bento.border))
                    // Right side: ONLY the selected section's content.
                    // Every block that existed in the old two-column
                    // layout still renders here, under its rail item.
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedSetupSection) {
                            "device" -> {
                                // 1.9.23 (item 10): Device is the top
                                // rail section — Keep Screen Awake +
                                // the Network block (status + Advertise
                                // address) moved here from URLs.
                                SetupSectionHeader(context.getString(R.string.s405))
                                keepAwakeBlock()
                                Spacer(modifier = Modifier.height(12.dp))
                                languageBlock()
                                Spacer(modifier = Modifier.height(12.dp))
                                networkBlock()
                            }
                            "broadcast" -> {
                                SetupSectionHeader(context.getString(R.string.s406))
                                broadcastStatusBlock()
                                lowerBlock()
                                fullBlock()
                                diagnosticsBlock()
                            }
                            "autoclear" -> {
                                // 1.9.23 (item 10): own rail section —
                                // position 2, after Device.
                                // 1.9.25 (item 14): no rail header — the
                                // block titles itself ("المسح التلقائي").
                                autoClearBlock()
                            }
                            "hdmi" -> {
                                // 1.9.15 item 5: one HDMI section (the
                                // SetupSection carries its own title).
                                hdmiSection()
                            }
                            "urls" -> {
                                SetupSectionHeader(context.getString(R.string.s408))
                                urlsCardBlock()
                                mjpegBlock()
                                allUrlsBlock()
                            }
                            // 1.9.25 (item 14): no rail header — each
                            // block titles itself (vMix / ProPresenter /
                            // OBS guide titles).
                            "vmix" -> {
                                vmixBlock()
                            }
                            "propresenter" -> {
                                propresenterBlock()
                            }
                            "obs" -> {
                                obsBlock()
                            }
                            "api" -> {
                                SetupSectionHeader(context.getString(R.string.s412))
                                urlReferenceBlock()
                                apiDocsBlock()
                            }
                            "custom" -> customBlock()
                        }
                    }
                }
        // App version footer: confirms which build is installed on this tablet.
        Text(
            text = context.getString(R.string.s546, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_DATE),
            fontSize = 11.sp,
            color = bento.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1.9.24: the narrow column follows the agreed rail order
                // exactly — Device first (Keep Screen Awake + Network
                // together), then Auto-Clear, HDMI, Broadcast, URLs,
                // vMix, ProPresenter, OBS, API, Customizations. The
                // blocks carry their own titles, so only the Device
                // section gets a header (it groups two blocks).
                SetupSectionHeader(context.getString(R.string.s405))
                keepAwakeBlock()
                Spacer(modifier = Modifier.height(12.dp))
                languageBlock()
                Spacer(modifier = Modifier.height(12.dp))
                networkBlock()
                // 1.9.25 (item 14): no header — the block titles itself.
                autoClearBlock()
                // 1.9.15 item 5: one HDMI section (the SetupSection
                // carries its own title).
                hdmiSection()
                SetupSectionHeader(context.getString(R.string.s406))
                broadcastStatusBlock()
                lowerBlock()
                fullBlock()
                diagnosticsBlock()
                SetupSectionHeader(context.getString(R.string.s408))
                urlsCardBlock()
                mjpegBlock()
                allUrlsBlock()
                // 1.9.24: vMix, ProPresenter, OBS and API in rail order
                // (URL Reference belongs to the API section, as on
                // tablet); apiDocsBlock was missing from the phone
                // layout entirely — 1.9.23 used it only on tablet.
                vmixBlock()
                propresenterBlock()
                obsBlock()
                urlReferenceBlock()
                apiDocsBlock()
                customBlock()
        // App version footer: confirms which build is installed on this tablet.
        Text(
            text = context.getString(R.string.s546, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_DATE),
            fontSize = 11.sp,
            color = bento.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
            }
        }
    }
    if (showThemeDialog) {
        ThemeDialog(
            selectedTheme = selectedTheme,
            customThemes = customThemes,
            onSelectTheme = onSelectTheme,
            onSaveNewTheme = onSaveNewTheme,
            onUpdateTheme = onUpdateTheme,
            onDeleteTheme = onDeleteTheme,
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SetupSectionHeader(title: String) {
    val bento = LocalBentoColors.current
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = bento.textSecondary,
        modifier = Modifier.padding(top = 2.dp)
    )
}

// 1.9.23 (Item 7): one left-rail navigation entry — stable id (drives
// the right-side when), bilingual label, and icon.
// 1.9.25 (item 2, language mode): one label, resolved at build time in
// the interface language — the old bilingual Arabic-bold + English-
// subtitle pair is gone with the rest of the bilingual labels.
private data class SetupRailEntry(
    val id: String,
    val label: String,
    val icon: ImageVector
)

/**
 * 1.9.23 (Item 7): one left-rail navigation item for the Setup tab.
 * The selected item gets a primary-tinted background and primary
 * text/icon; the rest are plain.
 */
@Composable
private fun SetupRailItem(
    entry: SetupRailEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bento = LocalBentoColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) bento.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = if (selected) bento.primary else bento.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                entry.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) bento.onPrimaryContainer else bento.textPrimary
            )
        }
    }
}

/**
 * Setup redesign: one spec-sheet section - a card whose header is an (i)
 * row with the title and (when collapsed) a one-line summary of the
 * section's current state. Expansion is internal (seeded by
 * [seedExpanded], re-seeded when [seedKey] changes - e.g. the Network
 * section opens itself when a connection warning appears), or fully
 * controlled via [controlledExpanded] + [onToggleControlled] (the
 * Diagnostics section, whose state lives in the UI state as before).
 */
// 1.9.25 (item 2, language mode): SplitLabel is gone — each language's
// resource value is monolingual (product names aside), so the bidi
// splitting it did for mixed Arabic+English labels is no longer needed.
// All 9 call sites are plain Text(context.getString(...)) now.
@Composable
private fun SetupSection(
    title: String,
    summary: String = "",
    seedExpanded: Boolean = false,
    seedKey: Any? = null,
    controlledExpanded: Boolean? = null,
    onToggleControlled: (() -> Unit)? = null,
    // Batch-3: per-section expand memory (ViewModel-backed). When
    // sectionId is set, expansion is read from / written to the shared
    // map under the key "setup:<sectionId>" — survives tab switches,
    // cleared on app close (all collapsed on fresh launch).
    sectionId: String? = null,
    sectionExpandedMap: Map<String, Boolean> = emptyMap(),
    onSetSectionExpanded: (String, String, Boolean) -> Unit = { _, _, _ -> },
    content: @Composable () -> Unit
) {
    val bento = LocalBentoColors.current
    val vmKey = sectionId?.let { "setup:$it" }
    val vmExpanded = vmKey?.let { sectionExpandedMap[it] }
    var internalExpanded by remember(seedKey) { mutableStateOf(seedExpanded) }
    val expanded = controlledExpanded ?: vmExpanded ?: internalExpanded
    fun toggleExpanded() {
        if (onToggleControlled != null) onToggleControlled()
        else if (sectionId != null) onSetSectionExpanded("setup", sectionId, !(vmExpanded ?: seedExpanded))
        else internalExpanded = !internalExpanded
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = bento.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { toggleExpanded() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = bento.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                    if (!expanded && summary.isNotEmpty()) {
                        // 1.9.25 (item 8): two lines allowed — the
                        // diagnostics summary is two short lines.
                        Text(summary, fontSize = 11.sp, color = bento.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                // 1.9.25 (item 13): ONE expand/collapse pattern
                // everywhere — ▲ when open, ▼ when closed.
                Text(if (expanded) "▲" else "▼", fontSize = 12.sp, color = bento.textSecondary)
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                content()
            }
        }
    }
}


/**
 * One NDI source row: exact on-air name ("<TabletModel> - <FeedName>"),
 * live status dot, and an independent on/off switch.
 */
@Composable
private fun NdiSourceRow(
    feedKey: String,
    toggleKey: String,
    tierLabel: String,
    enabled: Boolean,
    active: Boolean,
    tint: Color,
    textSecondary: Color,
    onToggle: (String) -> Unit
) {
    val context = LocalContext.current
    val bento = LocalBentoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) bento.green else bento.textSecondary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = NdiNativeSender.displayName(feedKey),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            Text(
                text = tierLabel + if (active) context.getString(R.string.s547) else context.getString(R.string.s895),
                fontSize = 9.sp,
                color = textSecondary
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle(toggleKey) }
        )
    }
}

/**
 * Per-source NDI settings: resolution + frame-rate dropdowns + motion toggle.
 * Changing any of them restarts the source immediately with the new spec.
 */
@Composable
private fun NdiSourceSettingsRow(
    feedKey: String,
    spec: NdiSourceSpec,
    tint: Color,
    textSecondary: Color,
    onUpdate: (String, Int, Int, Int) -> Unit,
    onToggleMotion: (String) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = NdiNativeSender.displayName(feedKey),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val resOptions = NdiNativeSender.RESOLUTION_OPTIONS.map { (w, h) -> "${w}×${h} • 16:9" }
            val selectedRes = "${spec.width}×${spec.height} • 16:9"
            NdiDropdown(
                label = context.getString(R.string.s381),
                options = resOptions,
                selected = if (resOptions.contains(selectedRes)) selectedRes else resOptions.first(),
                onSelect = { sel ->
                    val (w, h) = NdiNativeSender.RESOLUTION_OPTIONS[resOptions.indexOf(sel)]
                    onUpdate(feedKey, w, h, spec.fps)
                }
            )
            val fpsOptions = NdiNativeSender.FPS_OPTIONS.map { context.getString(R.string.s383, it) }
            val selectedFps = "${spec.fps} fps"
            NdiDropdown(
                label = context.getString(R.string.s382),
                options = fpsOptions,
                selected = if (fpsOptions.contains(selectedFps)) selectedFps else fpsOptions.first(),
                onSelect = { sel ->
                    onUpdate(feedKey, spec.width, spec.height, sel.substringBefore(" ").toInt())
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(context.getString(R.string.s548), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
                InfoPopupButton(context.getString(R.string.s548), context.getString(R.string.s549))
            }
            Switch(
                checked = spec.motionEnabled,
                onCheckedChange = { onToggleMotion(feedKey) }
            )
        }
    }
}

/** 1.9.27 (item 7): label for one treatment option (null = Theme default). */
private fun listTreatmentLabelRes(treatment: ListTreatment?): Int = when (treatment) {
    null -> R.string.s903
    ListTreatment.SOLID -> R.string.s904
    ListTreatment.LINE -> R.string.s905
    ListTreatment.TINT -> R.string.s906
    ListTreatment.SOFT -> R.string.s907
    ListTreatment.BORDER -> R.string.s908
    ListTreatment.TAG -> R.string.s909
}

/**
 * 1.9.27 (item 7): one list-colour picker (Recent verses / Sermon
 * verses) — Theme default + the six treatments, same dropdown pattern
 * as NdiDropdown below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListTreatmentDropdown(
    label: String,
    selected: ListTreatment?,
    onSelect: (ListTreatment?) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val options: List<ListTreatment?> = listOf(null) + ListTreatment.entries
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = context.getString(listTreatmentLabelRes(selected)),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 9.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            textStyle = TextStyle(fontSize = 11.sp),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(context.getString(listTreatmentLabelRes(opt)), fontSize = 11.sp) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NdiDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 9.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(150.dp),
            textStyle = TextStyle(fontSize = 11.sp),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, fontSize = 11.sp) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

/**
 * Item 22: QR code dialog for a Setup URL. Generates the QR with ZXing,
 * displays it with the label. The URL uses the tablet IP (or custom
 * domain if the user configured one).
 * Follow-up Phase 5 (item 9): the app icon is drawn centered over the
 * QR (~22% of the code size) on a white rounded square, keeping the
 * code scannable.
 */
@Composable
private fun QrCodeDialog(
    url: String,
    label: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(url) {
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(url, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            // Follow-up Phase 5 (item 9): overlay the app icon in the
            // center (~22% of the QR size) on a white rounded square, so
            // the code stays scannable. Falls back to the bare QR when
            // the icon cannot be loaded.
            val iconBitmap = try {
                val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, c.width, c.height)
                drawable.draw(c)
                bmp
            } catch (e: Exception) {
                null
            }
            if (iconBitmap != null) {
                val out = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(out)
                val iconSize = (width * 0.22f).toInt()
                val pad = (iconSize * 0.18f).toInt()
                val bgSize = iconSize + pad * 2
                val left = (width - bgSize) / 2f
                val top = (height - bgSize) / 2f
                val bgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    isAntiAlias = true
                }
                canvas.drawRoundRect(left, top, left + bgSize, top + bgSize, pad.toFloat(), pad.toFloat(), bgPaint)
                val dst = android.graphics.RectF(left + pad, top + pad, left + pad + iconSize, top + pad + iconSize)
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                }
                canvas.drawBitmap(iconBitmap, null, dst, paint)
                out
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (qrBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = context.getString(R.string.s594, url),
                        modifier = Modifier.size(256.dp)
                    )
                } else {
                    androidx.compose.material3.Text(context.getString(R.string.s375), fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(
                    url,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = LocalBentoColors.current.textSecondary
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(context.getString(R.string.s376))
            }
        }
    )
}

/**
 * 1.9.25 (items 4/5): the ONE URL action set — copy + open-in-browser +
 * QR, icon-only, same IconButton style on every URL row in Setup
 * (/lower, /full, MJPEG, the all-URLs list, API docs). Copy shows a
 * Check + green tint once this row was copied (copiedUrlType key);
 * all three actions guard on a blank URL with the no-Wi-Fi toast.
 * Pass onQr = null to hide the QR button (no row does today).
 */
@Composable
private fun UrlActionIcons(
    url: String,
    copyType: String,
    copiedUrlType: String?,
    onCopied: (String) -> Unit,
    clipLabel: String,
    copiedToast: String,
    qrDescription: String,
    onQr: (() -> Unit)?
) {
    val context = LocalContext.current
    val bento = LocalBentoColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        androidx.compose.material3.IconButton(onClick = {
            if (url.isBlank()) {
                Toast.makeText(context, context.getString(R.string.s691), Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, url))
                onCopied(copyType)
                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
            }
        }) {
            val isCopied = copiedUrlType == copyType
            Icon(
                if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = context.getString(R.string.s586),
                tint = if (isCopied) bento.green else bento.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        androidx.compose.material3.IconButton(onClick = {
            if (url.isBlank()) {
                Toast.makeText(context, context.getString(R.string.s691), Toast.LENGTH_SHORT).show()
            } else {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.s693), Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = context.getString(R.string.s587), tint = bento.textSecondary, modifier = Modifier.size(16.dp))
        }
        if (onQr != null) {
            androidx.compose.material3.IconButton(onClick = {
                if (url.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.s691), Toast.LENGTH_SHORT).show()
                } else {
                    onQr()
                }
            }) {
                Icon(Icons.Default.QrCode, contentDescription = qrDescription, tint = bento.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Lists every HTTP endpoint the tablet serves, so the user knows all the URLs
 * they can use (browser sources, MJPEG, snapshots, events, status, API).
 */
@Composable
private fun AllUrlsCard(
    baseUrl: String,
    textPrimary: Color,
    textSecondary: Color,
    cardColor: Color,
    borderColor: Color,
    copiedUrlType: String?,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current
    // 2026-10-28 old URL numbering (restored): 1 Lower, 2 Full,
    // 3 MJPEG, 4 PNG Lower, 5 PNG Full, 6 SSE, 7 Status, 8 API,
    // then trigger, remote, vMix XML, JSON in order. No gap, no
    // collapsed "All links" number — every row carries its own.
    // 1.9.25 (item 2): the numerals follow the interface language
    // (Arabic-Indic in Arabic UI, Western in English UI — matching
    // the s433-s435 resource values).
    val arabicUi = AppLanguage.isArabicUi(context)
    fun num(i: Int): String = if (arabicUi) ArabicTextFormatter.toEasternArabicDigits(i) else i.toString()
    val urls = listOf(
        // Item 16: simplified URLs — user-facing only + API Docs.
        // API endpoints moved to /api-docs (Swagger).
        Triple(num(1), context.getString(R.string.s550), "/lower"),
        Triple(num(2), context.getString(R.string.s470), "/full"),
        Triple(num(3), context.getString(R.string.s551), "/stream"),
        Triple(num(4), context.getString(R.string.s552), "/remote"),
        Triple(num(5), context.getString(R.string.s463), "/api-docs"),
        // v1.9.5: RSS feeds for ProPresenter/vMix data sources.
        Triple(num(6), context.getString(R.string.s553), "/lower/rss.xml"),
        Triple(num(7), context.getString(R.string.s554), "/full/rss.xml")
    )
    Text(
        text = context.getString(R.string.s555),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = textPrimary
    )
    // NDI sources are not URLs — they appear in OBS/vMix directly,
    // so no NDI entry appears in this URL list.
    Text(
        text = context.getString(R.string.s556),
        fontSize = 10.sp,
        color = textSecondary
    )
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (baseUrl.isBlank()) {
                Text(
                    context.getString(R.string.s557),
                    fontSize = 10.sp,
                    color = textSecondary
                )
            } else {
                urls.forEach { (num, label, path) ->
                    // v1.8: Strip /lower from base to avoid duplication (e.g., /lower/full).
                    val full = baseUrl.removeSuffix("/lower") + path
                    // Item 22: QR dialog state per URL.
                    var showQr by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1.9.25 (item 7): tighter rows — title + URL
                        // stacked left, single-line with ellipsis.
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$num. $label", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(full, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        // 1.9.25 (item 5): the shared icon-only
                        // copy/open/QR set (open is new here).
                        UrlActionIcons(
                            url = full,
                            copyType = path,
                            copiedUrlType = copiedUrlType,
                            onCopied = onCopied,
                            clipLabel = context.getString(R.string.s600),
                            copiedToast = context.getString(R.string.s696, label),
                            qrDescription = context.getString(R.string.s595, label),
                            onQr = { showQr = true }
                        )
                    }
                    if (showQr) {
                        QrCodeDialog(url = full, label = label, onDismiss = { showQr = false })
                    }
                }
            }
        }
    }
}

/**
 * v1.8: vMix SetText push settings (LAN-only).
 * Configures the vMix IP, port, input, and field mappings for pushing
 * verse text via vMix's HTTP API (Function=SetText).
 */
@Composable
private fun VMixSettingsCard(
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val bento = LocalBentoColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("vmix_prefs", Context.MODE_PRIVATE)
    }

    var enabled by remember { mutableStateOf(prefs.getBoolean("vmix_enabled", false)) }
    var host by remember { mutableStateOf(prefs.getString("vmix_host", "") ?: "") }
    var port by remember { mutableStateOf(prefs.getInt("vmix_port", 8088).toString()) }
    var input by remember { mutableStateOf(prefs.getString("vmix_input", "") ?: "") }
    var fieldArabicVerse by remember { mutableStateOf(prefs.getString("vmix_field_arabic_verse", "") ?: "") }
    var fieldArabicCitation by remember { mutableStateOf(prefs.getString("vmix_field_arabic_citation", "") ?: "") }
    var fieldEnglishVerse by remember { mutableStateOf(prefs.getString("vmix_field_english_verse", "") ?: "") }
    var fieldEnglishCitation by remember { mutableStateOf(prefs.getString("vmix_field_english_citation", "") ?: "") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showTestConfirm by remember { mutableStateOf(false) }

    fun save() {
        prefs.edit()
            .putBoolean("vmix_enabled", enabled)
            .putString("vmix_host", host.trim())
            .putInt("vmix_port", port.toIntOrNull() ?: 8088)
            .putString("vmix_input", input.trim())
            .putString("vmix_field_arabic_verse", fieldArabicVerse.trim())
            .putString("vmix_field_arabic_citation", fieldArabicCitation.trim())
            .putString("vmix_field_english_verse", fieldEnglishVerse.trim())
            .putString("vmix_field_english_citation", fieldEnglishCitation.trim())
            .apply()
    }

    // Item 20: the test writes a real string into the target vMix
    // field, overwriting live content — it now runs only after the
    // confirm dialog below names the field.
    fun runTest() {
        isTesting = true
        testResult = null
        Thread {
            try {
                val controller = com.arabicchristianmedia.server.VMixController(
                    host = host.trim(),
                    port = port.toIntOrNull() ?: 8088,
                    input = input.trim()
                )
                val testField = fieldArabicVerse.trim().ifEmpty { fieldEnglishVerse.trim() }
                val (ok, msg) = controller.testConnection(testField, context.getString(R.string.s558))
                testResult = if (ok) "✓ $msg" else "✗ $msg"
            } catch (e: Exception) {
                testResult = context.getString(R.string.s559, e.message)
            } finally {
                isTesting = false
            }
        }.start()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    context.getString(R.string.s561),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.s560), fontSize = 12.sp, color = textSecondary)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            save()
                        }
                    )
                }
            }

            Text(
                context.getString(R.string.s562),
                fontSize = 11.sp,
                color = textSecondary
            )

            if (enabled) {
                VMixTextField(context.getString(R.string.s563), host, { host = it; save() }, textPrimary, textSecondary)
                VMixTextField(context.getString(R.string.s564), port, { port = it; save() }, textPrimary, textSecondary)
                VMixTextField(context.getString(R.string.s565), input, { input = it; save() }, textPrimary, textSecondary)

                Text(context.getString(R.string.s566), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                VMixTextField(context.getString(R.string.s567), fieldArabicVerse, { fieldArabicVerse = it; save() }, textPrimary, textSecondary)
                VMixTextField(context.getString(R.string.s568), fieldArabicCitation, { fieldArabicCitation = it; save() }, textPrimary, textSecondary)
                VMixTextField(context.getString(R.string.s569), fieldEnglishVerse, { fieldEnglishVerse = it; save() }, textPrimary, textSecondary)
                VMixTextField(context.getString(R.string.s570), fieldEnglishCitation, { fieldEnglishCitation = it; save() }, textPrimary, textSecondary)

                androidx.compose.material3.Button(
                    onClick = { showTestConfirm = true },
                    enabled = !isTesting && host.isNotBlank() && input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTesting) context.getString(R.string.s571) else context.getString(R.string.s572))
                }

                if (showTestConfirm) {
                    val testField = fieldArabicVerse.trim().ifEmpty { fieldEnglishVerse.trim() }
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showTestConfirm = false },
                        title = { Text(context.getString(R.string.s371), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                if (testField.isNotBlank())
                                    context.getString(R.string.s372, testField)
                                else
                                    context.getString(R.string.s373)
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(
                                onClick = { showTestConfirm = false; runTest() }
                            ) { Text(context.getString(R.string.s374)) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { showTestConfirm = false }
                            ) { Text(context.getString(R.string.s7)) }
                        }
                    )
                }

                testResult?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = if (it.startsWith("✓")) bento.green else bento.liveRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Batch-2 item 25: ProPresenter stage-message push settings (LAN-only).
 * Configures host/port/auth for pushing the live verse into ProPresenter's
 * stage message via PUT /v1/stage/message. Mirrors VMixSettingsCard.
 * Password is stored obfuscated (never plaintext) per his principle.
 */
@Composable
private fun ProPresenterSettingsCard(
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val bento = LocalBentoColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("propresenter_prefs", Context.MODE_PRIVATE)
    }
    val pp = com.arabicchristianmedia.server.ProPresenterController

    var enabled by remember { mutableStateOf(prefs.getBoolean("pp_enabled", false)) }
    var host by remember { mutableStateOf(prefs.getString("pp_host", "") ?: "") }
    var port by remember { mutableStateOf(prefs.getInt("pp_port", 50001).toString()) }
    var username by remember { mutableStateOf(prefs.getString("pp_user", "") ?: "") }
    var password by remember {
        mutableStateOf(pp.deobfuscatePassword(prefs.getString("pp_pass", "") ?: ""))
    }
    var format by remember { mutableStateOf(prefs.getString("pp_format", "both") ?: "both") }
    var target by remember { mutableStateOf(prefs.getString("pp_target", "stage") ?: "stage") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<String>?>(null) }

    fun save() {
        prefs.edit()
            .putBoolean("pp_enabled", enabled)
            .putString("pp_host", host.trim())
            .putInt("pp_port", port.toIntOrNull() ?: 50001)
            .putString("pp_user", username.trim())
            .putString("pp_pass", pp.obfuscatePassword(password))
            .putString("pp_format", format)
            .putString("pp_target", target)
            .apply()
    }

    fun controller(): com.arabicchristianmedia.server.ProPresenterController {
        return com.arabicchristianmedia.server.ProPresenterController(
            host = host.trim(),
            port = port.toIntOrNull() ?: 50001,
            username = username.trim(),
            password = password
        )
    }

    fun runTest() {
        isTesting = true
        testResult = null
        Thread {
            try {
                val (ok, msg) = controller().testConnection()
                testResult = if (ok) "✓ $msg" else "✗ $msg"
            } catch (e: Exception) {
                testResult = context.getString(R.string.s559, e.message)
            } finally {
                isTesting = false
            }
        }.start()
    }

    fun runScan() {
        isScanning = true
        scanResults = null
        Thread {
            try {
                val prefix = pp.localSubnetPrefix(context)
                if (prefix == null) {
                    scanResults = emptyList()
                } else {
                    scanResults = pp.scanForProPresenter(
                        subnetPrefix = prefix,
                        port = port.toIntOrNull() ?: 50001,
                        username = username.trim(),
                        password = password
                    )
                }
            } catch (e: Exception) {
                scanResults = emptyList()
            } finally {
                isScanning = false
            }
        }.start()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    context.getString(R.string.s573),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(R.string.s560), fontSize = 12.sp, color = textSecondary)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            save()
                        }
                    )
                }
            }

            Text(
                context.getString(R.string.s574),
                fontSize = 11.sp,
                color = textSecondary
            )

            if (enabled) {
                VMixTextField(context.getString(R.string.s575), host, { host = it; save() }, textPrimary, textSecondary)
                VMixTextField(context.getString(R.string.s576), port, { port = it; save() }, textPrimary, textSecondary)
                VMixTextField(context.getString(R.string.s577), username, { username = it; save() }, textPrimary, textSecondary)

                Column {
                    Text(context.getString(R.string.s578), fontSize = 11.sp, color = textSecondary, modifier = Modifier.padding(bottom = 4.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; save() },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(context.getString(R.string.s384), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("stage" to context.getString(R.string.s385), "audience" to context.getString(R.string.s386), "both" to context.getString(R.string.s387)).forEach { (value, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = target == value,
                            onClick = { target = value; save() },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Text(context.getString(R.string.s388), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("both" to context.getString(R.string.s389), "arabic" to context.getString(R.string.s390), "english" to context.getString(R.string.s391)).forEach { (value, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = format == value,
                            onClick = { format = value; save() },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { runScan() },
                        enabled = !isScanning,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isScanning) context.getString(R.string.s579) else context.getString(R.string.s580))
                    }
                    androidx.compose.material3.Button(
                        onClick = { runTest() },
                        enabled = !isTesting && host.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isTesting) context.getString(R.string.s571) else context.getString(R.string.s572))
                    }
                }

                testResult?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = if (it.startsWith("✓")) bento.green else bento.liveRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isScanning) {
                    Text(context.getString(R.string.s581), fontSize = 12.sp, color = textSecondary)
                }
            }
        }
    }

    // Discovery results dialog.
    val results = scanResults
    if (results != null && !isScanning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { scanResults = null },
            title = { Text(context.getString(R.string.s377), fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (results.isEmpty()) {
                        Text(
                            context.getString(R.string.s378, port.toIntOrNull() ?: 50001),
                            fontSize = 13.sp
                        )
                    } else {
                        Text(context.getString(R.string.s379), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                        results.forEach { ip ->
                            androidx.compose.material3.TextButton(
                                onClick = { host = ip; save(); scanResults = null },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(ip, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { scanResults = null }) { Text(context.getString(R.string.s380)) }
            }
        )
    }

    // Batch-3: Target 2 (English) — independent ProPresenter target.
    // Target 1 pushes Arabic; Target 2 pushes English when enabled.
    var pp2Enabled by remember { mutableStateOf(prefs.getBoolean("pp2_enabled", false)) }
    var pp2Host by remember { mutableStateOf(prefs.getString("pp2_host", "") ?: "") }
    var pp2Port by remember { mutableStateOf(prefs.getInt("pp2_port", 50001).toString()) }
    var pp2Target by remember { mutableStateOf(prefs.getString("pp2_target", "stage") ?: "stage") }
    var pp2Expanded by remember { mutableStateOf(false) }

    fun save2() {
        prefs.edit()
            .putBoolean("pp2_enabled", pp2Enabled)
            .putString("pp2_host", pp2Host.trim())
            .putInt("pp2_port", pp2Port.toIntOrNull() ?: 50001)
            .putString("pp2_target", pp2Target)
            .apply()
    }

    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = borderColor
    )
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { pp2Expanded = !pp2Expanded },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
                androidx.compose.material3.Checkbox(
                    checked = pp2Enabled,
                    onCheckedChange = { pp2Enabled = it; save2() }
                )
                Text(
                    context.getString(R.string.s582),
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                Text(
                    if (pp2Expanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }
    if (pp2Expanded) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Text(
                context.getString(R.string.s583),
                fontSize = 12.sp,
                color = textSecondary
            )
            VMixTextField(context.getString(R.string.s584), pp2Host, { pp2Host = it; save2() }, textPrimary, textSecondary)
            VMixTextField(context.getString(R.string.s585), pp2Port, { pp2Port = it; save2() }, textPrimary, textSecondary)
            Text(context.getString(R.string.s392), fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = textPrimary)
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                listOf("stage" to context.getString(R.string.s393), "audience" to context.getString(R.string.s394), "both" to context.getString(R.string.s387)).forEach { (value, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = pp2Target == value,
                        onClick = { pp2Target = value; save2() },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VMixTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    textPrimary: Color,
    textSecondary: Color
) {
    Column {
        Text(label, fontSize = 11.sp, color = textSecondary, modifier = Modifier.padding(bottom = 4.dp))
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = textPrimary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, widthDp = 1200, heightDp = 800)
@Composable
fun BroadcastControlScreenPreview() {
    MyApplicationTheme {
        BroadcastControlScreen(
            uiState = BibleNdiUiState(),
            onStartServer = {},
            onStopServer = {},
            onLaunchFullscreenOverlay = {},
            onToggleLive = {},
            onClearBroadcast = {},
            onSelectInterface = {},
            onRefreshInterfaces = {}
        )
    }
}
