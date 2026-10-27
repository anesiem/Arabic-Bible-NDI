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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.arabicchristianmedia.BuildConfig
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.arabicchristianmedia.server.InterfaceType
import com.arabicchristianmedia.server.NdiNativeSender
import com.arabicchristianmedia.server.NdiSourceSpec
import com.arabicchristianmedia.server.NetworkInterfaceInfo
import com.arabicchristianmedia.model.ColorTheme
import com.arabicchristianmedia.model.ColorThemes
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
    onSetNdiProtocolEnabled: (Boolean) -> Unit = {},
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
    onToggleTashkeel: (Boolean) -> Unit = {},
    onSelectEnglishVersion: (String) -> Unit = {},
    selectedTheme: ColorTheme = ColorThemes.CLASSIC,
    customThemes: List<ColorTheme> = emptyList(),
    onSelectTheme: (String) -> Unit = {},
    onSaveNewTheme: (ColorTheme) -> Unit = {},
    onUpdateTheme: (ColorTheme) -> Unit = {},
    onDeleteTheme: (String) -> Unit = {},
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

    // Setup anchors (tablet wide layout only, same mechanism as the Editor):
    // each section header registers its offset inside its scroll column;
    // the chip strip above the columns jumps to it and flashes its border.
    // Setup headers are always composed (even collapsed), so unlike the
    // Editor no expand step is needed before scrolling.
    val leftSetupScrollState = rememberScrollState()
    val rightSetupScrollState = rememberScrollState()
    val setupAnchorOffsets = remember { mutableStateMapOf<String, Float>() }
    var leftSetupColumnRootY by remember { mutableStateOf(0f) }
    var rightSetupColumnRootY by remember { mutableStateOf(0f) }
    var highlightedSetupAnchor by remember { mutableStateOf<String?>(null) }
    val setupAnchorScope = rememberCoroutineScope()
    val setupAnchorColumn = mapOf(
        "broadcast" to "left", "lower" to "left", "full" to "left",
        "urls" to "right", "custom" to "right"
    )
    fun jumpToSetupAnchor(id: String) {
        highlightedSetupAnchor = id
        setupAnchorScope.launch {
            val target = setupAnchorOffsets[id] ?: return@launch
            if (setupAnchorColumn[id] == "right") rightSetupScrollState.animateScrollTo(target.toInt())
            else leftSetupScrollState.animateScrollTo(target.toInt())
        }
    }
    LaunchedEffect(highlightedSetupAnchor) {
        if (highlightedSetupAnchor != null) {
            delay(1600)
            highlightedSetupAnchor = null
        }
    }
    fun setupAnchorModifier(id: String): Modifier = Modifier
        .onGloballyPositioned { coords: LayoutCoordinates ->
            val isRight = setupAnchorColumn[id] == "right"
            val rootY = if (isRight) rightSetupColumnRootY else leftSetupColumnRootY
            val scrollValue = if (isRight) rightSetupScrollState.value else leftSetupScrollState.value
            val offset = coords.boundsInRoot().top - rootY + scrollValue
            if (setupAnchorOffsets[id] != offset) setupAnchorOffsets[id] = offset
        }
        .then(if (highlightedSetupAnchor == id) Modifier.border(2.dp, bento.primary, RoundedCornerShape(12.dp)) else Modifier)

    // Setup redesign (same treatment as the Editor): the page is a CONSOLE
    // (Bible strip + broadcast console) over a SPEC SHEET of summary
    // sections. Summaries = each section's one-line current state.
    val loSpec = uiState.ndiSourceSpecs[NdiNativeSender.FEED_LOWER] ?: NdiNativeSender.defaultSpec(NdiNativeSender.FEED_LOWER)
    val fuSpec = uiState.ndiSourceSpecs[NdiNativeSender.FEED_FULL] ?: NdiNativeSender.defaultSpec(NdiNativeSender.FEED_FULL)
    val specOf = { feed: String -> uiState.ndiSourceSpecs[feed] ?: NdiNativeSender.defaultSpec(feed) }
    val netSummary = (uiState.selectedInterface?.labelAr
        ?: uiState.availableInterfaces.firstOrNull { it.ip == uiState.activeIpAddress }?.labelAr
        ?: "الشبكة") + " · " + uiState.activeIpAddress
    val netWarning = uiState.activeIpAddress.isBlank() ||
        (uiState.availableInterfaces.isNotEmpty() && uiState.availableInterfaces.none { it.isRecommended })

    val bibleBlock: @Composable () -> Unit = {
        // The Bible strip: tashkeel (Ashraf's rule: always at the very top
        // of the Setup tab) and the English version picker.
        // v1.9.2 (#10a): now a collapsible SetupSection like every other
        // Setup block — still FIRST, but closed it costs one summary line
        // instead of ~a third of the tablet screen (in the wide layout the
        // strip is pinned above the panes and could never scroll away).
        SetupSection(
            title = "الكتاب المقدس (Bible)",
            summary = "Tashkeel " + (if (uiState.tashkeelEnabled) "On" else "Off") +
                " · English: " + uiState.englishVersion.uppercase(),
            seedExpanded = false
        ) {
            Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SplitLabel(
                    parts = listOf("التشكيل ", "(Tashkeel Diacritics)"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                Switch(
                    checked = uiState.tashkeelEnabled,
                    onCheckedChange = { onToggleTashkeel(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = bento.primary
                    )
                )
            }
                androidx.compose.material3.HorizontalDivider(color = bento.borderSubtle, modifier = Modifier.padding(vertical = 10.dp))
            Column {
                Text(
                    text = "الترجمة الإنجليزية (English Bible Version)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "asv" to "ASV",
                        "kjv" to "KJV"
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = uiState.englishVersion == id,
                            onClick = { onSelectEnglishVersion(id) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                Text(
                    text = "All versions are public domain",
                    fontSize = 10.sp,
                    color = bento.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            }
        }
    }
    val broadcastStatusBlock: @Composable () -> Unit = {
        // Card 1: NDI & Stream Server Status + Master Controls
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bento.card,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (uiState.isServerRunning) bento.green else bento.border
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
                        SplitLabel(
                            parts = if (uiState.isNativeNdiActive || uiState.isNativeShowActive)
                                listOf("بث ", "NDI 6", " نشط ", "(NDI Broadcast Active)")
                            else
                                listOf("بث ", "NDI", " متوقف ", "(NDI Broadcast Standby)"),
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
                    // per-feed toggles able to stop.
                    val anyActive = uiState.isNativeNdiActive || uiState.isNativeShowActive
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
                                Text("إيقاف المصادر", fontSize = 11.sp, color = bento.liveRed, fontWeight = FontWeight.Bold)
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
                                Text("تشغيل المصادر", fontSize = 11.sp, color = bento.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // NDI Protocol Badge (native NDI 6 senders; the OS-level NDI discovery
                // is handled by the NDI runtime itself — no in-app beacon)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.isServerRunning) bento.green.copy(alpha = 0.12f) else bento.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (uiState.isServerRunning) bento.green else bento.border
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
                            tint = if (uiState.isServerRunning) bento.green else bento.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SplitLabel(
                                parts = if (uiState.isServerRunning)
                                    listOf("خادم البث المباشر نشط ", "(Live Broadcast Active)")
                                else
                                    listOf("خادم البث في وضع الاستعداد ", "(Ready to Start)"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isServerRunning) bento.green else bento.textSecondary
                            )
                            Text(
                                text = if (uiState.isServerRunning)
                                    "٣. المصدر: Bible-NDI | دقة 1920x1080 | شفافية ألفا نقية 100% لـ OBS / vMix ودفق MJPEG مباشر"
                                else
                                    "٣. المصدر: Bible-NDI | المنفذ: ${uiState.serverPort} | اضغط 'تشغيل بث NDI' لبدء البث",
                                fontSize = 10.sp,
                                color = if (uiState.isServerRunning) bento.green else bento.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    val overlayBlock: @Composable () -> Unit = {
        // Card 3: Fullscreen Transparent Overlay Option
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = bento.primaryContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, bento.secondary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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
                    Column {
                        Text(
                            text = "عرض الشاشة الشفافة الكامل",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.onPrimaryContainer
                        )
                        Text(
                            text = "HDMI capture or NDI Screen capture ready",
                            fontSize = 10.sp,
                            color = bento.textSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = bento.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onLaunchFullscreenOverlay)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("fullscreen_overlay_button")
                ) {
                    Text("عرض كامل", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.onPrimary)
                }
            }
        }
    }

    val urlsCardBlock: @Composable () -> Unit = {
        // Output URLs card (expanded by default for immediate accessibility)
        var showOutputUrls by remember { mutableStateOf(true) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = bento.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "روابط الإخراج المباشر لـ OBS و vMix (شفافية 100%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = bento.textPrimary
                            )
                            Text(
                                text = if (showOutputUrls) "انقر للطي" else "انقر لعرض روابط Browser Source و MJPEG للبرامج الأخرى",
                                fontSize = 9.sp,
                                color = bento.textSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bento.primaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (showOutputUrls) "طي" else "عرض",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.primary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (showOutputUrls) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = bento.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                        SplitLabel(
                            parts = listOf("١. رابط التغذية الشفافة ", "(Lower Third Overlay):"),
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

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isCopied = copiedUrlType == "OVERLAY"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCopied) bento.green else bento.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (uiState.serverUrl.isBlank()) {
                                                    Toast.makeText(context, "لا يوجد اتصال شبكة محلية (Wi-Fi) — الرابط غير متاح", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Bible NDI Overlay URL", uiState.serverUrl)
                                                    clipboard.setPrimaryClip(clip)
                                                    copiedUrlType = "OVERLAY"
                                                    Toast.makeText(context, "تم نسخ رابط التغذية الشفافة!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                contentDescription = "Copy URL",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isCopied) "تم النسخ" else "نسخ",
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = bento.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (uiState.serverUrl.isBlank()) {
                                                    Toast.makeText(context, "لا يوجد اتصال شبكة محلية (Wi-Fi) — الرابط غير متاح", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.serverUrl))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 7.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = "Open in Browser",
                                            tint = bento.textPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Broadcast URL 2: Full Show Projector URL
                        SplitLabel(
                            parts = listOf("٢. رابط عرض الشاشة الكامل ", "(Full Show Projector):"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bento.textPrimary
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = bento.card,
                            border = BorderStroke(1.dp, bento.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val showUrl = uiState.serverUrl.replace("/lower", "/full")
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

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isCopied = copiedUrlType == "SHOW"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCopied) bento.green else bento.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (showUrl.isBlank()) {
                                                    Toast.makeText(context, "لا يوجد اتصال شبكة محلية (Wi-Fi) — الرابط غير متاح", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Bible NDI Show URL", showUrl)
                                                    clipboard.setPrimaryClip(clip)
                                                    copiedUrlType = "SHOW"
                                                    Toast.makeText(context, "تم نسخ رابط العرض الكامل!", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                contentDescription = "Copy URL",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isCopied) "تم النسخ" else "نسخ",
                                                fontSize = 10.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = bento.surfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (showUrl.isBlank()) {
                                                    Toast.makeText(context, "لا يوجد اتصال شبكة محلية (Wi-Fi) — الرابط غير متاح", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(showUrl))
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 7.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = "Open in Browser",
                                            tint = bento.textPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
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
                                    text = "⚠ تم تسجيل ${uiState.ndiErrorEvents.size} من أخطاء NDI — افتح «التشخيص» في قسم البث لعرض التفاصيل",
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
        // Broadcast URL 4: Direct MJPEG Video Stream URL
        SplitLabel(
            parts = listOf("٤. رابط بث الفيديو المباشر ", "(Direct Video Stream / MJPEG في vMix و OBS):"),
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
                    color = bento.green,
                    modifier = Modifier.weight(1f)
                )

                val isCopied = copiedUrlType == "STREAM"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCopied) bento.green else bento.green,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (uiState.streamUrl.isBlank()) {
                                Toast.makeText(context, "لا يوجد اتصال شبكة محلية (Wi-Fi) — الرابط غير متاح", Toast.LENGTH_SHORT).show()
                            } else {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Bible NDI Stream URL", uiState.streamUrl)
                                clipboard.setPrimaryClip(clip)
                                copiedUrlType = "STREAM"
                                Toast.makeText(context, "تم نسخ رابط بث الفيديو المباشر!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("copy_stream_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.Videocam,
                            contentDescription = "Copy Stream URL",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCopied) "تم النسخ" else "نسخ البث",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    val allUrlsBlock: @Composable () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Card: All available URLs
        AllUrlsCard(
            baseUrl = uiState.serverUrl,
            textPrimary = bento.textPrimary,
            textSecondary = bento.textSecondary,
            cardColor = bento.card,
            borderColor = bento.border,
            onCopy = { label, url ->
                val clip = ClipData.newPlainText("URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "تم نسخ: $label", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val lowerBlock: @Composable () -> Unit = {
        SetupSection(
            title = "الثلث السفلي (Lower Third)",
            summary = "Lower " + loSpec.width + "×" + loSpec.height + "@" + loSpec.fps
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "اختر الدقة ومعدل الإطارات. التغيير يُعاد تشغيل المصدر فوراً.",
                    fontSize = 11.sp,
                    color = bento.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                InfoPopupButton(
                    infoTitle = "الثلث السفلي (Lower Third)",
                    infoText = "اختر الدقة ومعدل الإطارات. التغيير يُعاد تشغيل المصدر فوراً."
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            NdiSourceRow(
                feedKey = NdiNativeSender.FEED_LOWER,
                toggleKey = "lower",
                tierLabel = specLabel(NdiNativeSender.FEED_LOWER, "Lower Third • شفاف"),
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
            title = "العرض الكامل (Full Show)",
            summary = "Full " + fuSpec.width + "×" + fuSpec.height + "@" + fuSpec.fps
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "اختر الدقة ومعدل الإطارات. التغيير يُعاد تشغيل المصدر فوراً.",
                    fontSize = 11.sp,
                    color = bento.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                InfoPopupButton(
                    infoTitle = "العرض الكامل (Full Show)",
                    infoText = "اختر الدقة ومعدل الإطارات. التغيير يُعاد تشغيل المصدر فوراً."
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            NdiSourceRow(
                feedKey = NdiNativeSender.FEED_FULL,
                toggleKey = "full",
                tierLabel = specLabel(NdiNativeSender.FEED_FULL, "Full Show • شفاف"),
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

    val customBlock: @Composable () -> Unit = {
        SetupSection(
            title = "التخصيص (Customizations)",
            summary = selectedTheme.name
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
                        contentDescription = "App theme",
                        tint = bento.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مظهر التطبيق (App theme)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.textPrimary
                        )
                        Text(
                            text = selectedTheme.name,
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
            Spacer(modifier = Modifier.height(10.dp))
            // Card: Keep Screen Awake
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bento.card,
                border = BorderStroke(1.dp, bento.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isKeepScreenOn) bento.primaryContainer else bento.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = if (uiState.isKeepScreenOn) Icons.Default.LiveTv else Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (uiState.isKeepScreenOn) bento.primary else bento.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SplitLabel(
                                parts = listOf("إبقاء الشاشة مفعلة ", "(Keep Screen Awake)"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = bento.textPrimary
                            )
                            InfoPopupButton("إبقاء الشاشة مفعلة (Keep Screen Awake)", "يمنع خمول الشاشة أو توقف معالج الجهاز أثناء البث المباشر")
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
    }

    val obsBlock: @Composable () -> Unit = {
        SetupSection(
            title = "الربط مع OBS Studio (OBS Guide)",
            summary = "Browser Source · 1920×1080"
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
                                            SplitLabel(
                                                parts = listOf("روابط الربط لبرامج البث ", "(OBS / vMix / Projector):"),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = bento.green
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• للآيات أسفل الشاشة (Lower Third): استخدم الرابط رقم ١.\n• للعرض الكامل (Projector/Full): استخدم الرابط رقم ٢.\n• للأداء الأقصى وجودة NDI 6: ابحث عن المصادر رقم ٣ مباشرة.\n• لبرامج المونتاج القديمة: استخدم رابط MJPEG رقم ٤.",
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
                            text = "OBS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SplitLabel(
                        parts = listOf("طريقة الربط مع ", "OBS Studio", " (3 خطوات سريعة):"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val obsSteps = listOf(
                    "1. في برنامج OBS، اضغط على زر (+) في قسم Sources (المصادر) واختر Browser (متصفح).",
                    "2. ضع في خانة URL الرابط أعلاه: ${if (uiState.serverUrl.isNotBlank()) uiState.serverUrl else "http://<IP>:8080/lower"}.",
                    "3. اضبط الأبعاد: Width = 1920 و Height = 1080.",
                    "4. تأكد من تحديد خيار (Shutdown source when not active).",
                    "5. بمجرد النقر على أي آية في التطبيق، ستظهر فوراً في البث المباشر مع خلفية شفافة 100% فوق الكاميرا!"
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
            title = "الربط مع vMix (vMix Guide)",
            summary = "Web Browser input · SetText push"
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = bento.green
                    ) {
                        Text(
                            text = "vMix",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "طريقة الربط مع vMix:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val vmixSteps = listOf(
                    "1. في vMix، اضغط على Add Input أسفل الشاشة.",
                    "2. اختر تبويب Web Browser من القائمة الجانبية (أو تبويب Stream / VLC واستخدم رابط البث المباشر).",
                    "3. الصق رابط التغذية، واضبط الدقة على 1920x1080.",
                    "4. قم بربط المدخل بأحد قنوات الـ Overlay (1 أو 2 أو 3) لدمج الآيات بشفافية ألفا تلقائياً."
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
    val networkBlock: @Composable () -> Unit = {
        SetupSection(
            title = "الشبكة (Network)",
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
                            text = "عناوين وبطاقات الشبكة المتاحة في الهاتف:",
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
                                contentDescription = "Refresh Interfaces",
                                tint = bento.textPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحديث", fontSize = 10.sp, color = bento.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.availableInterfaces.isEmpty()) {
                    Text(
                        text = "العنوان النشط حالياً: ${uiState.activeIpAddress}",
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
                                                    text = "✓ موصى به للربط مع كمبيوتر البث في نفس الراوتر",
                                                    fontSize = 9.sp,
                                                    color = bento.green,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            } else if (!iface.reachableFromLan) {
                                                Text(
                                                    text = "⚠️ بيانات شريحة SIM (لا يمكن للكمبيوتر الوصول إليها)",
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
                                                text = "محدد",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
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
                    color = Color(0xFFD97706).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تنبيه هام للمتصفح وحل مشكلة عدم الاتصال (ERR_ADDRESS_UNREACHABLE):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "١. في شريط عنوان المتصفح: اكتب http:// صراحة وليس https:// لأن الخادم يعمل محلياً داخل الشبكة بدون شهادة SSL خارجية. برامج vMix و OBS تتصل مباشرة وبسرعة فائقة بدون أي تنبيهات.",
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFD97706)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "٢. تأكد من اختيار عنوان Wi-Fi (يبدأ عادة بـ 192.168 أو 10.0) من القائمة أعلاه، وتجنب اختيار عنوان بيانات الشريحة (Cellular).",
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFD97706)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "٣. إذا كانت شبكة الواي فاي تفصل الأجهزة عن بعضها (AP Isolation)، شغّل (نقطة اتصال الهواتف المحمولة / Mobile Hotspot) في الهاتف واشبك كمبيوتر البث بها مباشرة، وسيعمل فوراً بدون مشاكل.",
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFD97706)
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
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                SplitLabel(
                                                    parts = listOf("عنوان البث ", "(Advertise address)"),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = bento.textPrimary
                                                )
                                                Text("إظهار الروابط بعنوان IP أو باسم الجهاز على الشبكة", fontSize = 10.sp, color = bento.textSecondary)
                                            }
                                            Switch(
                                                checked = uiState.advertiseByName,
                                                onCheckedChange = { onSetAdvertiseByName(it) }
                                            )
                                        }
                                        if (uiState.advertiseByName) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            var nameText by remember(uiState.advertiseName) { mutableStateOf(uiState.advertiseName) }
                                            TextField(
                                                value = nameText,
                                                onValueChange = { nameText = it },
                                                label = { Text("اسم الجهاز على الشبكة (Device name)") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    "مثال: http://" + nameText.ifBlank { "…" } + ":" + uiState.serverPort + "/lower",
                                                    fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = bento.textSecondary
                                                )
                                                androidx.compose.material3.TextButton(onClick = { onSetAdvertiseName(nameText) }) {
                                                    Text("تطبيق (Apply)")
                                                }
                                            }
                                            Text("البديل دائماً متاح (IP): " + uiState.activeIpAddress, fontSize = 10.sp, color = bento.textSecondary)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("💡 معلومات ونصائح (Info & tips)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                                            androidx.compose.material3.TextButton(onClick = { onToggleAdvertiseTips() }) {
                                                Text(if (uiState.advertiseTipsCollapsed) "عرض (Show)" else "إخفاء (Hide)")
                                            }
                                        }
                                        if (!uiState.advertiseTipsCollapsed) {
                                            Text("• عنوان IP يعمل دائماً، لكنه قد يتغيّر عند إعادة تشغيل الراوتر أو الجهاز (DHCP) فتنكسر الروابط المحفوظة.", fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("• اسم الجهاز يبقى ثابتاً حتى لو تغيّر الـ IP — اكتب الاسم تماماً كما يفتح في متصفحك الآن (بدون .local).", fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("• جرّب الاسم في المتصفح أولاً: إذا فتحت صفحة التحكم، فالاسم يعمل على شبكتك ويمكن الاعتماد عليه.", fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("• إذا توقّف الاسم عن العمل على شبكة معيّنة، أعد التبديل إلى IP — رابط الـ IP يبقى ظاهراً دائماً كبديل.", fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("• هذا الإعداد يغيّر الروابط المعروضة والمنسوخة فقط؛ البثّ نفسه وطرق الاتصال لا تتغيّر.", fontSize = 10.sp, color = bento.textSecondary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                    }
                                }
        }
    }
    val diagnosticsBlock: @Composable () -> Unit = {
        SetupSection(
            title = "تشخيص NDI (NDI Diagnostics)",
            summary = "أخطاء NDI: " + uiState.ndiErrorEvents.size + " · متصل الآن: " + uiState.sseConnectedNow,
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
                            if (running) "يعمل ✓" else "متوقف",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (running) bento.green else bento.textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    "صحة بث HTTP (الأوفرلاي):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                Text(
                    text = "متصل الآن: ${uiState.sseConnectedNow} • جلسات الاتصال: ${uiState.sseConnectTotal} • الانقطاعات: ${uiState.sseDropTotal}" +
                        if (uiState.sseLastDropReason.isNotEmpty() && uiState.sseLastDropAgoSec >= 0)
                            " • آخر انقطاع: ${uiState.sseLastDropReason} (قبل ${uiState.sseLastDropAgoSec} ث)"
                        else "",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (uiState.sseLastDropAgoSec in 0..60) bento.liveRed else bento.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "سجل الأخطاء (${uiState.ndiErrorEvents.size}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = bento.textPrimary
                )
                if (uiState.ndiErrorEvents.isEmpty()) {
                    Text(
                        "لا توجد أخطاء مسجلة — جميع المصادر تعمل بشكل طبيعي.",
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
                        Text("تحديث", fontSize = 10.sp)
                    }
                    androidx.compose.material3.TextButton(onClick = { onClearNdiDiagnostics() }) {
                        Text("مسح السجل", fontSize = 10.sp)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(bento.bg)) {
        if (maxWidth >= 900.dp) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { bibleBlock() }
                // Setup anchors (wide only): fixed chip strip above the
                // two scroll columns — same jump + flash as the Editor.
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "broadcast" to "البث (Broadcast)",
                        "lower" to "الثلث السفلي (Lower Third)",
                        "full" to "العرض الكامل (Full Show)",
                        "urls" to "الروابط (URLs)",
                        "custom" to "التخصيص (Customizations)"
                    ).forEach { (id, label) ->
                        FilterChip(
                            selected = false,
                            onClick = { jumpToSetupAnchor(id) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.weight(0.5f).fillMaxHeight().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 14.dp)
                            .onGloballyPositioned { leftSetupColumnRootY = it.boundsInRoot().top }
                            .verticalScroll(leftSetupScrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(setupAnchorModifier("broadcast")) { SetupSectionHeader("البث (Broadcast)") }
                        broadcastStatusBlock()
                        overlayBlock()
                        diagnosticsBlock()
                        Box(setupAnchorModifier("lower")) { lowerBlock() }
                        Box(setupAnchorModifier("full")) { fullBlock() }
                    }
                    Column(
                        Modifier.weight(0.5f).fillMaxHeight().padding(start = 6.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
                            .onGloballyPositioned { rightSetupColumnRootY = it.boundsInRoot().top }
                            .verticalScroll(rightSetupScrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(setupAnchorModifier("urls")) { SetupSectionHeader("الروابط (URLs)") }
                        urlsCardBlock()
                        mjpegBlock()
                        allUrlsBlock()
                        networkBlock()
                        obsBlock()
                        vmixBlock()
                        Box(setupAnchorModifier("custom")) { customBlock() }
                    }
                }
        // App version footer: confirms which build is installed on this tablet.
        Text(
            text = "Arabic Bible NDI v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • ${BuildConfig.BUILD_DATE}",
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
                bibleBlock()
                SetupSectionHeader("البث (Broadcast)")
                broadcastStatusBlock()
                overlayBlock()
                diagnosticsBlock()
                lowerBlock()
                fullBlock()
                SetupSectionHeader("الروابط (URLs)")
                urlsCardBlock()
                mjpegBlock()
                allUrlsBlock()
                networkBlock()
                obsBlock()
                vmixBlock()
                customBlock()
        // App version footer: confirms which build is installed on this tablet.
        Text(
            text = "Arabic Bible NDI v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • ${BuildConfig.BUILD_DATE}",
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

/**
 * Setup redesign: one spec-sheet section - a card whose header is an (i)
 * row with the title and (when collapsed) a one-line summary of the
 * section's current state. Expansion is internal (seeded by
 * [seedExpanded], re-seeded when [seedKey] changes - e.g. the Network
 * section opens itself when a connection warning appears), or fully
 * controlled via [controlledExpanded] + [onToggleControlled] (the
 * Diagnostics section, whose state lives in the UI state as before).
 */
// Bidi-safe label: a mixed Arabic/Latin string is rendered as separate
// unidirectional pieces so the bidi engine cannot reorder runs across a
// boundary (e.g. a trailing "(Latin):" flipping). The pieces concatenate
// exactly to the original string, and FlowRow preserves line wrapping.
// Scope: short direct labels only. SetupSection titles stay single-string
// (changing that API is out of scope), as do flowing paragraphs, guide
// sentences, and interpolated/dynamic lines.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitLabel(
    parts: List<String>,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier = modifier) {
        parts.forEach { part ->
            Text(part, fontSize = fontSize, fontWeight = fontWeight, color = color)
        }
    }
}

@Composable
private fun SetupSection(
    title: String,
    summary: String = "",
    seedExpanded: Boolean = false,
    seedKey: Any? = null,
    controlledExpanded: Boolean? = null,
    onToggleControlled: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val bento = LocalBentoColors.current
    var internalExpanded by remember(seedKey) { mutableStateOf(seedExpanded) }
    val expanded = controlledExpanded ?: internalExpanded
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = bento.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, bento.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (onToggleControlled != null) onToggleControlled() else internalExpanded = !internalExpanded
                },
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
                        Text(summary, fontSize = 11.sp, color = bento.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(if (expanded) "▼" else "▶", fontSize = 12.sp, color = bento.textSecondary)
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
                text = tierLabel + if (active) " • يعمل الآن" else " • متوقف",
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
                label = "الدقة",
                options = resOptions,
                selected = if (resOptions.contains(selectedRes)) selectedRes else resOptions.first(),
                onSelect = { sel ->
                    val (w, h) = NdiNativeSender.RESOLUTION_OPTIONS[resOptions.indexOf(sel)]
                    onUpdate(feedKey, w, h, spec.fps)
                }
            )
            val fpsOptions = NdiNativeSender.FPS_OPTIONS.map { "$it fps" }
            val selectedFps = "${spec.fps} fps"
            NdiDropdown(
                label = "الإطارات/ثا",
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
                Text("الحركة (Motion)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
                InfoPopupButton("الحركة (Motion)", "إعادة رسم الخلفيات المتحركة باستمرار (بحد أقصى 30 إطار/ثا)")
            }
            Switch(
                checked = spec.motionEnabled,
                onCheckedChange = { onToggleMotion(feedKey) }
            )
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
    onCopy: (String, String) -> Unit
) {
    val urls = listOf(
        "Lower Third (Browser Source)" to "/lower",
        "العرض الكامل (Full Show)" to "/full",
        "بث MJPEG" to "/stream",
        "لقطة PNG (Lower)" to "/lower/snapshot.png",
        "لقطة PNG (Full)" to "/full/snapshot.png",
        "أحداث SSE (الآيات)" to "/events",
        "الحالة" to "/api/status",
        "واجهة الآيات (API)" to "/api/verse",
        "تشغيل آية عن بُعد" to "/api/trigger?ref=John+3:16",
        "صفحة التحكم عن بُعد" to "/remote",
        "خلاصة vMix (XML) — Lower" to "/lower/bibleshow.xml",
        "خلاصة vMix (XML) — Full" to "/full/bibleshow.xml",
        "خلاصة (JSON) — Lower" to "/lower/bibleshow.json",
        "خلاصة (JSON) — Full" to "/full/bibleshow.json"
    )
    Text(
        text = "٥. جميع الروابط المتاحة:",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = textPrimary
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
                    "لا يوجد اتصال شبكة محلية (Wi-Fi) — الروابط تظهر بعد الاتصال.",
                    fontSize = 10.sp,
                    color = textSecondary
                )
            } else {
                urls.forEach { (label, path) ->
                    // v1.8: Strip /lower from base to avoid duplication (e.g., /lower/full).
                    val full = baseUrl.removeSuffix("/lower") + path
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                            Text(full, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = textSecondary)
                        }
                        androidx.compose.material3.IconButton(onClick = { onCopy(label, full) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", tint = textSecondary, modifier = Modifier.size(16.dp))
                        }
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
                    "vMix SetText Push (LAN)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مفعّل", fontSize = 12.sp, color = textSecondary)
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
                "Push verse text to vMix title fields via LAN. vMix controls all formatting.",
                fontSize = 11.sp,
                color = textSecondary
            )

            if (enabled) {
                VMixTextField("vMix IP (e.g. 192.168.1.50)", host, { host = it; save() }, textPrimary, textSecondary)
                VMixTextField("Port (default 8088)", port, { port = it; save() }, textPrimary, textSecondary)
                VMixTextField("Title input (name, number, or GUID)", input, { input = it; save() }, textPrimary, textSecondary)

                Text("Field mappings (GT field names, e.g. Headline.Text):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                VMixTextField("Arabic verse field", fieldArabicVerse, { fieldArabicVerse = it; save() }, textPrimary, textSecondary)
                VMixTextField("Arabic citation field", fieldArabicCitation, { fieldArabicCitation = it; save() }, textPrimary, textSecondary)
                VMixTextField("English verse field", fieldEnglishVerse, { fieldEnglishVerse = it; save() }, textPrimary, textSecondary)
                VMixTextField("English citation field", fieldEnglishCitation, { fieldEnglishCitation = it; save() }, textPrimary, textSecondary)

                androidx.compose.material3.Button(
                    onClick = {
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
                                val (ok, msg) = controller.testConnection(testField, "Test 123 — Bible NDI")
                                testResult = if (ok) "✓ $msg" else "✗ $msg"
                            } catch (e: Exception) {
                                testResult = "✗ Error: ${e.message}"
                            } finally {
                                isTesting = false
                            }
                        }.start()
                    },
                    enabled = !isTesting && host.isNotBlank() && input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTesting) "Testing…" else "Test Connection")
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
            onRefreshInterfaces = {},
            onSetNdiProtocolEnabled = {}
        )
    }
}
