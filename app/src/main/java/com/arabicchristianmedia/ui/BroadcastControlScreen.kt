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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.arabicchristianmedia.server.InterfaceType
import com.arabicchristianmedia.server.NdiNativeSender
import com.arabicchristianmedia.server.NdiSourceSpec
import com.arabicchristianmedia.server.NetworkInterfaceInfo
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bento = LocalBentoColors.current
    /** Label showing the source's *current* configured spec, e.g. "Lower Third • 1280×720 @ 30fps". */
    val specLabel = { feedKey: String, tier: String ->
        val sp = uiState.ndiSourceSpecs[feedKey] ?: NdiNativeSender.defaultSpec(feedKey)
        "$tier • ${sp.width}×${sp.height} @ ${sp.fps}fps"
    }
    val BentoBg = bento.bg
    val BentoCardWhite = bento.card
    val BentoSurfaceContainer = bento.surfaceContainer
    val BentoSurfaceVariant = bento.surfaceVariant
    val BentoBorder = bento.border
    val BentoBorderSubtle = bento.borderSubtle
    val BentoTextPrimary = bento.textPrimary
    val BentoTextSecondary = bento.textSecondary
    val BentoPrimary = bento.primary
    val BentoOnPrimary = bento.onPrimary
    val BentoPrimaryContainer = bento.primaryContainer
    val BentoOnPrimaryContainer = bento.onPrimaryContainer
    val BentoSecondary = bento.secondary
    val BentoOnSecondary = bento.onSecondary

    var copiedUrlType by remember { mutableStateOf<String?>(null) }
    var showHelpGuide by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BentoBg)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: NDI & Stream Server Status + Master Controls
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BentoCardWhite,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (uiState.isServerRunning) Color(0xFF10B981) else BentoBorder
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
                                .background(if (uiState.isServerRunning) Color(0xFF10B981) else BentoLiveRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isServerRunning) "بث NDI 6 نشط (NDI Broadcast Active)" else "بث NDI متوقف (NDI Broadcast Standby)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isServerRunning) Color(0xFF10B981) else BentoTextPrimary
                        )
                    }

                    // Single Unified Start/Stop NDI Broadcast Button
                    val bothActive = uiState.isNativeNdiActive && uiState.isNativeShowActive
                    if (uiState.isServerRunning && bothActive) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFECE8),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BentoLiveRed),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onStopServer)
                                .testTag("stop_server_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = BentoLiveRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إيقاف المصادر", fontSize = 11.sp, color = BentoLiveRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BentoPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onStartServer(uiState.serverPort) }
                                .testTag("start_server_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BentoOnPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تشغيل المصادر", fontSize = 11.sp, color = BentoOnPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // NDI Protocol Badge (native NDI 6 senders; the OS-level NDI discovery
                // is handled by the NDI runtime itself — no in-app beacon)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.isServerRunning) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.isServerRunning) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
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
                            tint = if (uiState.isServerRunning) Color(0xFF16A34A) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isServerRunning)
                                    "خادم البث المباشر نشط (Live Broadcast Active)"
                                else
                                    "خادم البث في وضع الاستعداد (Ready to Start)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isServerRunning) Color(0xFF166534) else BentoTextSecondary
                            )
                            Text(
                                text = if (uiState.isServerRunning)
                                    "المصدر: Bible-NDI | دقة 1920x1080 | شفافية ألفا نقية 100% لـ OBS / vMix ودفق MJPEG مباشر"
                                else
                                    "المصدر: Bible-NDI | المنفذ: ${uiState.serverPort} | اضغط 'تشغيل بث NDI' لبدء البث",
                                fontSize = 10.sp,
                                color = if (uiState.isServerRunning) Color(0xFF15803D) else BentoTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Output URLs and OBS / vMix Setup Guide Section (Expanded by default for immediate accessibility)
                var showOutputUrls by remember { mutableStateOf(true) }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BentoSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
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
                                    tint = BentoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "روابط الإخراج المباشر لـ OBS و vMix (شفافية 100%)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BentoTextPrimary
                                    )
                                    Text(
                                        text = if (showOutputUrls) "انقر للطي" else "انقر لعرض روابط Browser Source و MJPEG للبرامج الأخرى",
                                        fontSize = 9.sp,
                                        color = BentoTextSecondary
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BentoPrimaryContainer.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (showOutputUrls) "طي" else "عرض",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoPrimary
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = if (showOutputUrls) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = BentoPrimary,
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
                                androidx.compose.material3.HorizontalDivider(color = BentoBorderSubtle)

                                // How-to OBS Guide Banner
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "روابط الربط لبرامج البث (OBS / vMix / Projector):",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• للآيات أسفل الشاشة (Lower Third): استخدم الرابط رقم ١.\n• للعرض الكامل (Projector/Full): استخدم الرابط رقم ١.١.\n• لبرامج المونتاج القديمة: استخدم رابط MJPEG رقم ٢.\n• للأداء الأقصى وجودة NDI 6: ابحث عن المصادر رقم ٣ مباشرة.",
                                            fontSize = 9.sp,
                                            color = Color(0xFF166534),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }

                                // Broadcast URL 1: HTML5 Transparent Overlay URL (Browser Source)
                                Text(
                                    text = "١. رابط التغذية الشفافة (Lower Third Overlay):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BentoTextPrimary
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BentoCardWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
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
                                            color = BentoPrimary,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val isCopied = copiedUrlType == "OVERLAY"
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCopied) Color(0xFF10B981) else BentoPrimary,
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
                                                color = BentoSurfaceVariant,
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
                                                    tint = BentoTextPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Broadcast URL 1.1: Full Show Projector URL
                                Text(
                                    text = "١.١ رابط عرض الشاشة الكامل (Full Show Projector):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BentoTextPrimary
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BentoCardWhite,
                                    border = BorderStroke(1.dp, BentoBorder),
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
                                            text = showUrl,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = BentoPrimary,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val isCopied = copiedUrlType == "SHOW"
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCopied) Color(0xFF10B981) else BentoPrimary,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("Bible NDI Show URL", showUrl)
                                                        clipboard.setPrimaryClip(clip)
                                                        copiedUrlType = "SHOW"
                                                        Toast.makeText(context, "تم نسخ رابط العرض الكامل!", Toast.LENGTH_SHORT).show()
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
                                                color = BentoSurfaceVariant,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(showUrl))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.OpenInBrowser,
                                                    contentDescription = "Open in Browser",
                                                    tint = BentoTextPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Native NDI Section
                                Text(
                                    text = "٣. مصادر NDI الأصلية (Native NDI 6 Sources):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BentoTextPrimary
                                )
                                
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("ابحث في برامج vMix/OBS عن المصادر التالية:", fontSize = 10.sp, color = BentoTextSecondary)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        // Exact on-air names: "<TabletModel> - <FeedName>" (never localhost/IP)
                                        // Exactly two genuine NDI senders: Lower Third and Full Show.
                                        NdiSourceRow(
                                            feedKey = NdiNativeSender.FEED_LOWER,
                                            toggleKey = "lower",
                                            tierLabel = specLabel(NdiNativeSender.FEED_LOWER, "Lower Third • شفاف"),
                                            enabled = uiState.ndiLowerEnabled,
                                            active = uiState.isNativeNdiActive,
                                            tint = BentoPrimary,
                                            textSecondary = BentoTextSecondary,
                                            onToggle = onToggleNdiSource
                                        )
                                        NdiSourceRow(
                                            feedKey = NdiNativeSender.FEED_FULL,
                                            toggleKey = "full",
                                            tierLabel = specLabel(NdiNativeSender.FEED_FULL, "Full Show • شفاف"),
                                            enabled = uiState.ndiFullShowEnabled,
                                            active = uiState.isNativeShowActive,
                                            tint = Color(0xFF10B981),
                                            textSecondary = BentoTextSecondary,
                                            onToggle = onToggleNdiSource
                                        )
                                    }
                                }

                                // Error banner: visible even when Advanced is off, so the user always knows
                                if (!uiState.showAdvancedNdi && uiState.ndiErrorEvents.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFEF2F2),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "⚠ تم تسجيل ${uiState.ndiErrorEvents.size} من أخطاء NDI — فعّل «الخيارات المتقدمة» بالأسفل لعرض التفاصيل",
                                            fontSize = 10.sp,
                                            color = Color(0xFFB91C1C),
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                // NDI Source Settings: per-source resolution + frame rate dropdowns
                                Text(
                                    text = "٤. إعدادات مصادر NDI (الدقة ومعدل الإطارات):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BentoTextPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BentoCardWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            "اختر الدقة ومعدل الإطارات لكل مصدر. التغيير يُعاد تشغيل المصدر فوراً.",
                                            fontSize = 10.sp,
                                            color = BentoTextSecondary
                                        )
                                        val specOf = { feed: String -> uiState.ndiSourceSpecs[feed] ?: NdiNativeSender.defaultSpec(feed) }
                                        NdiSourceSettingsRow(
                                            feedKey = NdiNativeSender.FEED_LOWER,
                                            spec = specOf(NdiNativeSender.FEED_LOWER),
                                            tint = BentoPrimary,
                                            textSecondary = BentoTextSecondary,
                                            onUpdate = onUpdateNdiSourceSpec,
                                            onToggleMotion = onToggleNdiMotion
                                        )
                                        NdiSourceSettingsRow(
                                            feedKey = NdiNativeSender.FEED_FULL,
                                            spec = specOf(NdiNativeSender.FEED_FULL),
                                            tint = Color(0xFF10B981),
                                            textSecondary = BentoTextSecondary,
                                            onUpdate = onUpdateNdiSourceSpec,
                                            onToggleMotion = onToggleNdiMotion
                                        )
                                    }
                                }

                                // Advanced diagnostics toggle
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BentoCardWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "خيارات متقدمة (Advanced)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BentoTextPrimary
                                                )
                                                Text(
                                                    "عرض أخطاء NDI التشخيصية وحالة المصادر",
                                                    fontSize = 10.sp,
                                                    color = BentoTextSecondary
                                                )
                                            }
                                            Switch(
                                                checked = uiState.showAdvancedNdi,
                                                onCheckedChange = { onToggleAdvancedNdi() }
                                            )
                                        }
                                        if (uiState.showAdvancedNdi) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = BentoBorder)
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
                                                    color = if (running) Color(0xFF047857) else BentoTextSecondary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "سجل الأخطاء (${uiState.ndiErrorEvents.size}):",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BentoTextPrimary
                                            )
                                            if (uiState.ndiErrorEvents.isEmpty()) {
                                                Text(
                                                    "لا توجد أخطاء مسجلة — جميع المصادر تعمل بشكل طبيعي.",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF047857)
                                                )
                                            } else {
                                                uiState.ndiErrorEvents.takeLast(20).reversed().forEach { e ->
                                                    Text(
                                                        "${e.time} • ${e.source}: ${e.message}",
                                                        fontSize = 9.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color(0xFFB91C1C)
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

                                // Broadcast URL 2: Direct MJPEG Video Stream URL
                                Text(
                                    text = "٢. رابط بث الفيديو المباشر (Direct Video Stream / MJPEG في vMix و OBS):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BentoTextPrimary
                                )

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BentoCardWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
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
                                            color = Color(0xFF0D9488),
                                            modifier = Modifier.weight(1f)
                                        )

                                        val isCopied = copiedUrlType == "STREAM"
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCopied) Color(0xFF10B981) else Color(0xFF0D9488),
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
                        }
                    }
                }
            }
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Card: All available URLs
        AllUrlsCard(
            baseUrl = uiState.serverUrl,
            textPrimary = BentoTextPrimary,
            textSecondary = BentoTextSecondary,
            cardColor = BentoCardWhite,
            borderColor = BentoBorder,
            onCopy = { label, url ->
                val clip = ClipData.newPlainText("URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "تم نسخ: $label", Toast.LENGTH_SHORT).show()
            }
        )

        // v1.8: vMix SetText push settings (LAN-only).
        VMixSettingsCard(
            cardColor = BentoCardWhite,
            borderColor = BentoBorder,
            textPrimary = BentoTextPrimary,
            textSecondary = BentoTextSecondary
        )

        // Card: Keep Screen Awake
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BentoCardWhite,
            border = BorderStroke(1.dp, BentoBorder),
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
                            .background(if (uiState.isKeepScreenOn) BentoPrimaryContainer else BentoSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (uiState.isKeepScreenOn) Icons.Default.LiveTv else Icons.Default.Tv,
                            contentDescription = null,
                            tint = if (uiState.isKeepScreenOn) BentoPrimary else BentoTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "إبقاء الشاشة مفعلة (Keep Screen Awake)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                        Text(
                            text = "يمنع خمول الشاشة أو توقف معالج الجهاز أثناء البث المباشر",
                            fontSize = 11.sp,
                            color = BentoTextSecondary
                        )
                    }
                }

                Switch(
                    checked = uiState.isKeepScreenOn,
                    onCheckedChange = { onToggleKeepScreenOn() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BentoPrimary
                    )
                )
            }
        }

        // Card 2: Network Interface Selector & IP Troubleshooter
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BentoCardWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = BentoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "عناوين وبطاقات الشبكة المتاحة في الهاتف:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextPrimary
                        )
                    }

                    // Refresh Network Interfaces Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BentoSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onRefreshInterfaces)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Interfaces",
                                tint = BentoTextPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحديث", fontSize = 10.sp, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.availableInterfaces.isEmpty()) {
                    Text(
                        text = "العنوان النشط حالياً: ${uiState.activeIpAddress}",
                        fontSize = 11.sp,
                        color = BentoTextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.availableInterfaces.forEach { iface ->
                            val isSelected = uiState.selectedInterface?.id == iface.id || uiState.activeIpAddress == iface.ip
                            val cardBg = when {
                                isSelected -> BentoPrimaryContainer
                                iface.isRecommended -> Color(0xFFF0FDF4)
                                !iface.reachableFromLan -> Color(0xFFFEF2F2)
                                else -> BentoSurfaceContainer
                            }
                            val borderCol = when {
                                isSelected -> BentoPrimary
                                iface.isRecommended -> Color(0xFF86EFAC)
                                !iface.reachableFromLan -> Color(0xFFFECACA)
                                else -> BentoBorder
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
                                            tint = if (isSelected) BentoPrimary else BentoTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = iface.labelAr,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) BentoOnPrimaryContainer else BentoTextPrimary
                                            )
                                            if (iface.isRecommended) {
                                                Text(
                                                    text = "✓ موصى به للربط مع كمبيوتر البث في نفس الراوتر",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF15803D),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            } else if (!iface.reachableFromLan) {
                                                Text(
                                                    text = "⚠️ بيانات شريحة SIM (لا يمكن للكمبيوتر الوصول إليها)",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFFDC2626)
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = BentoPrimary
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
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
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
                                color = Color(0xFF92400E)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "١. في شريط عنوان المتصفح: اكتب http:// صراحة وليس https:// لأن الخادم يعمل محلياً داخل الشبكة بدون شهادة SSL خارجية. برامج vMix و OBS تتصل مباشرة وبسرعة فائقة بدون أي تنبيهات.",
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF78350F)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "٢. تأكد من اختيار عنوان Wi-Fi (يبدأ عادة بـ 192.168 أو 10.0) من القائمة أعلاه، وتجنب اختيار عنوان بيانات الشريحة (Cellular).",
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF78350F)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "٣. إذا كانت شبكة الواي فاي تفصل الأجهزة عن بعضها (AP Isolation)، شغّل (نقطة اتصال الهواتف المحمولة / Mobile Hotspot) في الهاتف واشبك كمبيوتر البث بها مباشرة، وسيعمل فوراً بدون مشاكل.",
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF78350F)
                        )
                    }
                }
            }
        }

        // Card 3: Fullscreen Transparent Overlay Option
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BentoPrimaryContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoSecondary),
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
                            .background(BentoSurfaceContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = BentoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "عرض الشاشة الشفافة الكامل",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoOnPrimaryContainer
                        )
                        Text(
                            text = "HDMI capture or NDI Screen capture ready",
                            fontSize = 10.sp,
                            color = BentoTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BentoPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onLaunchFullscreenOverlay)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("fullscreen_overlay_button")
                ) {
                    Text("عرض كامل", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BentoOnPrimary)
                }
            }
        }

        // Card 4: OBS Studio Quick Setup Guide Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BentoCardWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BentoPrimary
                    ) {
                        Text(
                            text = "OBS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoOnPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "طريقة الربط مع OBS Studio (3 خطوات سريعة):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
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
                        color = BentoTextSecondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Card 5: vMix Quick Setup Guide Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BentoCardWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981)
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
                        color = BentoTextPrimary
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
                        color = BentoTextSecondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // App version footer: confirms which build is installed on this tablet.
        Text(
            text = "Arabic Bible NDI v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • ${BuildConfig.BUILD_DATE}",
            fontSize = 11.sp,
            color = BentoTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
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
                .background(if (active) Color(0xFF10B981) else Color(0xFF94A3B8))
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
            Column(modifier = Modifier.weight(1f)) {
                Text("الحركة (Motion)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
                Text(
                    "إعادة رسم الخلفيات المتحركة باستمرار (بحد أقصى 30 إطار/ثا)",
                    fontSize = 9.sp,
                    color = textSecondary
                )
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
        "لقطة PNG" to "/snapshot.png",
        "أحداث SSE (الآيات)" to "/events",
        "الحالة" to "/api/status",
        "واجهة الآيات (API)" to "/api/verse",
        "تشغيل آية عن بُعد" to "/api/trigger?ref=John+3:16",
        "صفحة التحكم عن بُعد" to "/remote",
        "خلاصة vMix (XML)" to "/bibleshow.xml"
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
                        color = if (it.startsWith("✓")) Color(0xFF16A34A) else Color(0xFFDC2626),
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
