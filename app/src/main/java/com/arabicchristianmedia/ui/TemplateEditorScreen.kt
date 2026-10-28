package com.arabicchristianmedia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.model.*
import com.arabicchristianmedia.server.VideoStore
import com.arabicchristianmedia.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class PreviewBackground(val displayNameAr: String) {
    CHECKERBOARD_TRANSPARENT("شفاف مفرغ (100% Transparent Alpha)"),
    SIMULATED_STUDIO_CAMERA("استوديو داكن (Studio Dark)"),
    PURE_BLACK("أسود معتم (Pure Black)"),
    CHROMA_GREEN("كروما خضراء (Chroma Green)")
}

/**
 * v2.0: Coordinated card style pairing for motion backgrounds.
 * When the user picks a motion background, the card style, background opacity,
 * and accent color are auto-coordinated. The user can still adjust everything
 * after; the result saves with the style template.
 * Returns null for NONE (leaves current style untouched).
 */
data class BackgroundPairing(
    val style: TemplateStyle,
    val bgOpacity: Float,
    val accentColorHex: String,
    val isPureTransparentBackground: Boolean
)

fun getBackgroundPairing(type: AnimatedBackgroundType): BackgroundPairing? {
    return when (type) {
        AnimatedBackgroundType.PARTICLE_STARS -> BackgroundPairing(
            TemplateStyle.TRANSPARENT_OUTLINE, 0.0f, "#FFFFFF", true
        )
        AnimatedBackgroundType.GOLDEN_PARTICLES -> BackgroundPairing(
            TemplateStyle.TRANSPARENT_OUTLINE, 0.0f, "#F59E0B", true
        )
        AnimatedBackgroundType.GOLDEN_DIVINE_RAYS -> BackgroundPairing(
            TemplateStyle.MODERN_GLASS, 0.7f, "#F59E0B", false
        )
        AnimatedBackgroundType.ETHEREAL_BLUE_WAVES -> BackgroundPairing(
            TemplateStyle.MODERN_GLASS, 0.7f, "#3B82F6", false
        )
        AnimatedBackgroundType.ROYAL_PURPLE_SILK -> BackgroundPairing(
            TemplateStyle.ROYAL_LITURGICAL, 0.85f, "#F59E0B", false
        )
        AnimatedBackgroundType.CANDLE_LITURGICAL_GLOW -> BackgroundPairing(
            TemplateStyle.ROYAL_LITURGICAL, 0.85f, "#FBBF24", false
        )
        AnimatedBackgroundType.EMERALD_GARDEN_WAVES -> BackgroundPairing(
            TemplateStyle.MODERN_GLASS, 0.7f, "#10B981", false
        )
        AnimatedBackgroundType.ROSE_DAWN_GLOW -> BackgroundPairing(
            TemplateStyle.MINIMALIST_PILL, 0.75f, "#F472B6", false
        )
        AnimatedBackgroundType.CUSTOM_VIDEO -> BackgroundPairing(
            TemplateStyle.TRANSPARENT_OUTLINE, 0.0f, "#FFFFFF", true
        )
        AnimatedBackgroundType.NONE -> null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TemplateEditorScreen(
    currentTemplate: LowerThirdTemplate,
    activeShowTemplate: LowerThirdTemplate,
    templates: List<LowerThirdTemplate>,
    highlightedLowerStyleId: String?,
    highlightedShowStyleId: String?,
    lowerWorkingDirty: Boolean,
    showWorkingDirty: Boolean,
    activeVerse: BibleVerse?,
    onSelectTemplate: (LowerThirdTemplate) -> Unit,
    onSelectShowTemplate: (LowerThirdTemplate) -> Unit,
    onUpdateTemplate: (LowerThirdTemplate) -> Unit,
    onUpdateShowTemplate: (LowerThirdTemplate) -> Unit,
    onSaveAsNew: (String, LowerThirdTemplate, Boolean) -> Unit,
    onUpdateStyleInPlace: (Boolean) -> Unit,
    highlightedStyleIsFactory: Boolean,
    customFonts: List<CustomFont>,
    onImportCustomFont: (String, String) -> Unit,
    onDeleteCustomFont: (CustomFont) -> Unit,
    onResetDefaults: (Boolean) -> Unit,
    onDeleteTemplate: (String, Boolean) -> Unit,
    onExportTemplate: (String) -> String?,
    onUpdateTemplateFromJson: (String, String, Boolean) -> Boolean,
    onImportNewTemplate: (String, Boolean) -> Boolean,
    editorTab: Int,
    onEditorTabChange: (Int) -> Unit,
    // Wide layout: tips visibility is hoisted to MainActivity so the
    // top-bar info icon can reopen the tips card.
    showHowItWorks: Boolean,
    onShowHowItWorksChange: (Boolean) -> Unit,
    // Batch item 5: import-dialog state hoisted the same way, so the
    // top-bar feed chip can open the import dialog.
    showImportDialog: Boolean,
    importTargetTpl: LowerThirdTemplate?,
    onShowImportDialogChange: (Boolean) -> Unit,
    onImportTargetTplChange: (LowerThirdTemplate?) -> Unit,
    // Batch item 6: Modified-menu visibility, hoisted so the MainActivity
    // top-bar badge opens the same menu as the compact badge.
    showModifiedMenu: Boolean,
    onShowModifiedMenuChange: (Boolean) -> Unit,
    // 2026-10-28 K7 gate: true when the MainActivity top bar already
    // shows the design button (width >= 900 AND height >= 600, the
    // same thresholds as isWideLayout). The compact stage then hides
    // its own design button, so exactly one design button exists.
    topBarDesignButtonVisible: Boolean = false,
    // 2026-10-28 design menu: export the WORKING design (not just a
    // saved style by id, which would export stale values when dirty).
    onExportWorkingTemplate: (Boolean) -> String? = { null },
    // 2026-10-28 design menu: rename the highlighted saved style
    // (factory styles fork to a user copy, like Update does).
    onRenameTemplate: (String, String, Boolean) -> Unit = { _, _, _ -> },
    // Deep-test fix: empty-state "Restore factory styles" button.
    onRestoreFactoryTemplates: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var templateToDelete by remember { mutableStateOf<LowerThirdTemplate?>(null) }
    var styleMenuTpl by remember { mutableStateOf<LowerThirdTemplate?>(null) } // long-press actions menu
    var newTemplateName by remember { mutableStateOf("") }
    var previewBg by remember { mutableStateOf(PreviewBackground.SIMULATED_STUDIO_CAMERA) }
    // v1.9.1: visual text-box editing on the preview (drag the box edges).
    var boxEdit by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // v1.9: How-it-works tips visibility, persisted, with a "don't show
    // again" option. Deep-test fix: the expanded state itself is now
    // persisted ("how_it_works_expanded", default collapsed), so compact
    // mode is not filled by the tips on every open. Expand via the button
    // in the tips header, or reopen via the ⓘ icon when hidden entirely.
    val tipsPrefs = remember { context.getSharedPreferences("editor_ui_prefs", android.content.Context.MODE_PRIVATE) }
    var howItWorksExpanded by rememberSaveable { mutableStateOf(tipsPrefs.getBoolean("how_it_works_expanded", false)) }

    // Dynamic Recent Colors with delete (x) button
    var recentColors by remember {
        mutableStateOf(listOf("#FFFFFF", "#FBBF24", "#F59E0B", "#EF4444", "#10B981", "#3B82F6", "#8B5CF6", "#000000", "#94A3B8"))
    }

    val onAddRecentColor: (String) -> Unit = { hex ->
        if (!recentColors.any { it.equals(hex, ignoreCase = true) }) {
            recentColors = (listOf(hex) + recentColors).take(12)
        }
    }

    val onRemoveRecentColor: (String) -> Unit = { hex ->
        recentColors = recentColors.filterNot { it.equals(hex, ignoreCase = true) }
    }

    val bento = LocalBentoColors.current
    val editingTemplate = if (editorTab == 0) currentTemplate else activeShowTemplate
    val onUpdate = if (editorTab == 0) onUpdateTemplate else onUpdateShowTemplate
    // Fresh template reference for async callbacks (avoids stale captures).
    val currentEditingTemplate = rememberUpdatedState(editingTemplate)
    val scope = rememberCoroutineScope()
    var isImportingVideo by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // v1.7: copy the picked video into private storage (never keep the
            // raw URI: no persistable permission, and raw paths are not served).
            isImportingVideo = true
            scope.launch(Dispatchers.IO) {
                val id = VideoStore(context).importFromUri(it)
                withContext(Dispatchers.Main) {
                    isImportingVideo = false
                    val current = currentEditingTemplate.value
                    if (id != null) {
                        onUpdate(current.copy(customVideoId = id, customVideoUrl = "", animatedBackground = AnimatedBackgroundType.CUSTOM_VIDEO))
                    }
                }
            }
        }
    }

    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()?.let { text ->
                    importText = text
                    importError = null
                } ?: run { importError = "تعذر قراءة الملف (could not read file)" }
            } catch (e: Exception) {
                importError = "تعذر قراءة الملف (could not read file)"
            }
        }
    }

    // v1.9.1: custom font import — file picker → name dialog → ViewModel
    // copies the TTF/OTF into private storage (FontStore).
    var pendingFontUri by remember { mutableStateOf<Uri?>(null) }
    var fontNameInput by remember { mutableStateOf("") }
    var fontToDelete by remember { mutableStateOf<CustomFont?>(null) }
    var showFactoryUpdateDialog by remember { mutableStateOf(false) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fontNameInput = com.arabicchristianmedia.server.FontStore(context)
                .displayNameFor(uri)?.substringBeforeLast('.')?.trim().orEmpty()
            pendingFontUri = uri
        }
    }
    val launchFontPicker = { fontPicker.launch(arrayOf("*/*")) }

    val displayVerse = activeVerse ?: BibleVerse(
        id = "sample", bookId = "jhn", bookArabicName = "إنجيل يوحنا", bookEnglishName = "John", chapter = 3, verse = 16,
        arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ، لِكَيْ لاَ يَهْلِكَ كُلُّ مَنْ يُؤْمِنُ بِهِ، بَلْ تَكُونُ لَهُ الْحَيَاةُ الأَبَدِيَّةُ.",
        englishText = "For God so loved the world that He gave His only begotten Son, that whoever believes in Him should not perish but have everlasting life."
    )

    // Round 2 (#10): hoisted from the style-selector block - the stage
    // footer (Save / Update) reads them from a different block now.
        val highlightedId = if (editorTab == 0) highlightedLowerStyleId else highlightedShowStyleId
        val tabIsDirty = if (editorTab == 0) lowerWorkingDirty else showWorkingDirty

    // Wide two-pane layout: right-panel anchor jumps. The right panel is
    // a LazyColumn with one stable-key item per section, so a jump is
    // scrollToItem by key — no pixel offsets, no layout callbacks, no
    // frame waits. Section items are always in the list, so a jump can
    // never silently no-op; a sub-link scrolls to its parent section and
    // flashes the exact sub-row. (The old offset scheme desynced whenever
    // a layout shift landed between the callback and the scroll — e.g.
    // after a collapse/expand toggle — which is why jumps worked, then
    // stopped, then worked again after swiping forced a re-layout.)
    val rightLazyListState = rememberLazyListState()
    var highlightedAnchor by remember { mutableStateOf<String?>(null) }
    val anchorScope = rememberCoroutineScope()
    val anchorParent = mapOf(
        "frame-align" to "frame", "frame-position" to "frame",
        "frame-padding" to "frame", "frame-autofit" to "frame",
        "arabic-citation" to "arabic", "arabic-verse" to "arabic",
        "english-citation" to "english", "english-verse" to "english",
        "bg-motion" to "bg", "bg-emblem" to "bg",
        "bg-transition" to "bg", "bg-stream" to "bg",
        "shadow-text" to "shadow", "shadow-glow" to "shadow"
    )
    // Right-column item keys, in order. A jump resolves its id to the
    // parent section key (sub-links) or itself (sections), then scrolls.
    val rightColumnKeys = listOf("frame", "alpha", "arabic", "language", "english", "bg", "shadow")
    // Items 1 + 12: expanded state for the collapsible sections, hoisted
    // here so it survives editor-tab switches (the old remember(syncKey)
    // re-seeded on every template change) and so anchor jumps can expand
    // a section before scrolling to it. First view still uses each
    // section's initiallyExpanded; manual toggles stick after that.
    val sectionExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val collapsibleSectionIds = setOf("frame", "arabic", "language", "english", "bg", "shadow")
    fun setSectionExpanded(sectionId: String, value: Boolean) {
        sectionExpanded[sectionId] = value
    }
    fun jumpToAnchor(id: String) {
        // Jumping to a section (or its sub-group) first expands it, then
        // scrolls to the section item by key. Section items are always
        // present, so no waiting for layout and no silent no-op.
        val sectionId = anchorParent[id] ?: id
        if (sectionId in collapsibleSectionIds) setSectionExpanded(sectionId, true)
        highlightedAnchor = id
        val index = rightColumnKeys.indexOf(sectionId)
        if (index >= 0) anchorScope.launch { rightLazyListState.scrollToItem(index) }
    }
    LaunchedEffect(highlightedAnchor) {
        if (highlightedAnchor != null) {
            delay(1600)
            highlightedAnchor = null
        }
    }
    // Modifier that flashes a border on the jumped-to target. It records
    // nothing — jumps resolve by LazyColumn item key, not by pixel
    // offset. Harmless when the right column isn't on screen (the compact
    // layout never jumps).
    fun anchorModifier(id: String): Modifier = Modifier
        .then(if (highlightedAnchor == id) Modifier.border(2.dp, bento.primary, RoundedCornerShape(12.dp)) else Modifier)

    // Round 2 (#10): the Editor is arranged the way the user works.
    // Compact screens stack everything in one scroll. Wide screens
    // (>= 900dp) use two panes: tabs + style bar full-width, then
    // left = title + preview + anchors, right = all controls, each
    // panel scrolling on its own so control scrolling never moves
    // the preview. The blocks below are shared by both layouts.
    // Wide two-pane layout: the header is split into granular blocks so
    // the wide layout can place each piece (title in the left column,
    // tabs + style bar full-width, chips in MainActivity's top bar)
    // while the compact layout keeps the original stacked header.
    val editorTitleBlock: @Composable () -> Unit = {
        // Item 4: title smaller (20sp -> 16sp); its description moved behind
        // the (i) beside the title — that popup keeps ONLY the description.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Template Editor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            InfoPopupButton("Template Editor", "تخصيص كامل وشامل لطبقات البث والعرض المباشر")
        }
    }
    val tabSwitcherBlock: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bento.surfaceVariant).padding(4.dp)) {
            EditorTabItem("طبقة البث السفلى (Lower Third)", editorTab == 0, { onEditorTabChange(0) }, Modifier.weight(1f))
            EditorTabItem("العرض الكامل (Full Show Projector)", editorTab == 1, { onEditorTabChange(1) }, Modifier.weight(1f))
        }
    }
    val tipsBlock: @Composable () -> Unit = {
        // 2b. Workflow tips: collapsible, with "don't show again". Hidden entirely
        // once dismissed (reopen via the ⓘ icon in the header / top bar).
        if (showHowItWorks) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = bento.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("كيف يعمل المحرر (How it works)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = bento.onPrimaryContainer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                tipsPrefs.edit().putBoolean("show_how_it_works", false).apply()
                                onShowHowItWorksChange(false)
                            }) {
                                Text("عدم الإظهار مجدداً", fontSize = 10.sp, color = bento.onPrimaryContainer)
                            }
                            IconButton(onClick = {
                                howItWorksExpanded = !howItWorksExpanded
                                tipsPrefs.edit().putBoolean("how_it_works_expanded", howItWorksExpanded).apply()
                            }) {
                                Icon(if (howItWorksExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "طي/فتح", tint = bento.onPrimaryContainer)
                            }
                        }
                    }
                    if (howItWorksExpanded) {
                        val tips = listOf(
                            "اختر نمطاً من القائمة لتطبيقه فوراً على المعاينة والبث.",
                            "أي تعديل يظهر مباشرة؛ الحفظ والتصدير والاستيراد من قائمة التصميم (اسم النمط أعلى الشاشة).",
                            "التعديل بدون حفظ يمسح التمييز عن النمط ويظهر شارة «معدّل».",
                            "«حفظ كقالب جديد» ينشئ نمطاً جديداً في قائمة هذا التبويب فقط.",
                            "«إعادة التعيين» يرجع لآخر نمط محدد، أو للوضع الافتراضي.",
                            "اضغط مطولاً على أي نمط لحذفه."
                        )
                        tips.forEach { tip ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("•", fontSize = 11.sp, color = bento.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                Text(tip, fontSize = 11.sp, color = bento.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
    val headerBlock: @Composable () -> Unit = {
        // 1. Header & Quick Actions (responsive: compact icon actions on narrow
        // screens so the title never crushes the buttons under large fonts)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { editorTitleBlock() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 2026-10-28: the feed indicator moved to the design
                    // menu (Import lives there) — one design control, not two.
                    // v1.9: info icon reopens the How-it-works tips when hidden.
                    if (!showHowItWorks) {
                        IconButton(onClick = {
                            tipsPrefs.edit().putBoolean("show_how_it_works", true).apply()
                            tipsPrefs.edit().putBoolean("how_it_works_expanded", true).apply()
                            onShowHowItWorksChange(true)
                            howItWorksExpanded = true
                        }) { Icon(Icons.Default.Info, "كيفية الاستخدام", tint = bento.primary) }
                    }
                    IconButton(onClick = { onResetDefaults(editorTab == 1) }) { Icon(Icons.Default.Refresh, "إعادة تعيين", tint = bento.primary) }
                    // v1.9: header save button removed — the single save action lives
                    // in Quick Setup below, next to the style picker.
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 2. Tab Switcher: Lower Third vs Full Show (v1.8: segmented, unmistakable active target).
        // v1.9.2 (#7b): the "You are editing" line that stood here moved
        // into the header row as a compact chip; the line itself is deleted.
        tabSwitcherBlock()

        Spacer(Modifier.height(12.dp))

        tipsBlock()
        if (showHowItWorks) {
            Spacer(Modifier.height(12.dp))
        }

    }
    val styleCardsRow: @Composable () -> Unit = {
        LazyRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Strict partition: each tab shows ONLY its own collection. An empty tab
            // shows an empty-state hint — never the other tab's styles.
            val filteredTemplates = if (editorTab == 1) templates.filter { it.isFullScreen } else templates.filter { !it.isFullScreen }
            if (filteredTemplates.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)) {
                        Text(
                            if (editorTab == 1)
                                "لا توجد أنماط للعرض الكامل بعد — عدّل ثم «حفظ كقالب جديد» لإنشاء أول نمط."
                            else
                                "لا توجد أنماط للطبقة السفلى بعد — عدّل ثم «حفظ كقالب جديد» لإنشاء أول نمط.",
                            fontSize = 11.sp,
                            color = bento.textSecondary
                        )
                        TextButton(onClick = onRestoreFactoryTemplates) {
                            Text("استعادة الأنماط الافتراضية (Restore factory styles)", fontSize = 11.sp)
                        }
                    }
                }
            }
            items(filteredTemplates, key = { it.id }) { tpl ->
                val isHighlighted = tpl.id == highlightedId
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isHighlighted) bento.primaryContainer else bento.card),
                    border = if (isHighlighted) BorderStroke(2.dp, bento.primary) else BorderStroke(1.dp, bento.borderSubtle),
                    modifier = Modifier.width(130.dp).combinedClickable(
                        onClick = {
                            if (editorTab == 0) {
                                onSelectTemplate(tpl.copy(isFullScreen = false))
                            } else {
                                onSelectShowTemplate(tpl.copy(isFullScreen = true))
                            }
                        },
                        onLongClick = { styleMenuTpl = tpl }
                    )
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // v2.0: Miniature live preview — shows how the template looks
                        // (bg + text colors + accent) in the same 42.dp space.
                        Box(
                            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp))
                                .background(
                                    try {
                                        Color(android.graphics.Color.parseColor(tpl.bgColorHex)).copy(alpha = tpl.bgOpacity.coerceIn(0f, 1f))
                                    } catch (e: Exception) { Color.Black }
                                )
                                .border(
                                    width = if (tpl.showAccentBorder) 2.dp else 1.dp,
                                    color = if (tpl.showAccentBorder) try { Color(android.graphics.Color.parseColor(tpl.accentColorHex)) } catch (e: Exception) { bento.border } else bento.border,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // v2.0: show the card background transparency % (reads the
                            // template, so saved styles follow the same rule).
                            Text(
                                "${(tpl.bgOpacity.coerceIn(0f, 1f) * 100).toInt()}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = try { Color(android.graphics.Color.parseColor(tpl.textColorHex)) } catch (e: Exception) { Color.White },
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(tpl.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    @Composable
    fun previewBlock(onPreviewClick: (() -> Unit)?) {
        // 3. Compact Live Preview Card with Functional Background Modes
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth().then(if (onPreviewClick != null) Modifier.clickable { onPreviewClick() } else Modifier)) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (editorTab == 0) "معاينة البث (Lower Third Preview)" else "معاينة العرض (Full Show Preview)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bento.textPrimary)
                    
                    // Background Mode Selector
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PreviewBackground.entries.forEach { bg ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (bg) {
                                            PreviewBackground.CHECKERBOARD_TRANSPARENT -> Color.LightGray
                                            PreviewBackground.SIMULATED_STUDIO_CAMERA -> Color(0xFF0F172A)
                                            PreviewBackground.PURE_BLACK -> Color.Black
                                            PreviewBackground.CHROMA_GREEN -> Color(0xFF00FF00)
                                        }
                                    )
                                    .border(width = if (previewBg == bg) 2.dp else 1.dp, color = if (previewBg == bg) bento.primary else bento.border, shape = CircleShape)
                                    .clickable { previewBg = bg }
                            )
                        }
                    }
                }
                Text(previewBg.displayNameAr, fontSize = 10.sp, color = bento.textSecondary, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = boxEdit, onClick = { val next = !boxEdit; boxEdit = next; if (next) setSectionExpanded("frame", true) }, label = { Text("تعديل الصندوق (Edit Box)", fontSize = 10.sp) })
                    if (boxEdit) {
                        InfoPopupButton("تعديل الصندوق (Edit Box)", "اسحب حواف الصندوق — drag the box edges")
                    }
                }
                Spacer(Modifier.height(6.dp))
                BroadcastPreviewViewport(editingTemplate, displayVerse, previewBg, boxEditEnabled = boxEdit, onTemplateChange = { onUpdate(it) })

            }
        }
    }
    val frameSectionBlock: @Composable () -> Unit = {
        CollapsibleSection(
            modifier = anchorModifier("frame"),
            title = "الإطار والحشوة (Frame & Padding)",
            // v1.9.2 (#10b): the section opens itself while Edit Box is on —
            // the drag handles and these sliders are two forms of the same
            // three numbers, so they belong open together.
            initiallyExpanded = boxEdit,
            expanded = sectionExpanded.getOrPut("frame") { boxEdit },
            onExpandedChange = { setSectionExpanded("frame", it) },
            summary = "Box " + editingTemplate.boxHeightPercent + "% \u00b7 bottom " + editingTemplate.positionBottomPercent + "% \u00b7 margin " + editingTemplate.horizontalMarginPercent + "%"
        ) {
                    // Alignment
                    Text("محاذاة النص (Alignment)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = anchorModifier("frame-align"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BroadcastTextAlignment.entries.forEach { align ->
                            FilterChip(selected = editingTemplate.alignment == align, onClick = { onUpdate(editingTemplate.copy(alignment = align)) }, label = { Text(align.displayName, fontSize = 10.sp) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // v1.9.1: position / margins / corner radius — template
                    // fields the renderers always honored but the Editor
                    // never exposed (no write path existed anywhere).
                    SliderWithLabel("المسافة من أسفل الشاشة (Bottom Position %)", editingTemplate.positionBottomPercent.toFloat(), 0f..30f, modifier = anchorModifier("frame-position")) { onUpdate(editingTemplate.copy(positionBottomPercent = it.toInt())) }
                    SliderWithLabel("الهامش الأفقي (Horizontal Margin %)", editingTemplate.horizontalMarginPercent.toFloat(), 0f..30f) { onUpdate(editingTemplate.copy(horizontalMarginPercent = it.toInt())) }
                    // v1.9.1: the text box height auto-fit fits into (was a
                    // hardcoded 45% for Lower). Editable visually too — the
                    // Edit Box toggle above the preview.
                    SliderWithLabel("ارتفاع صندوق النص (Text Box Height %)", editingTemplate.boxHeightPercent.toFloat(), 10f..100f) { onUpdate(editingTemplate.copy(boxHeightPercent = it.toInt())) }
                    SliderWithLabel("انحناء زوايا البطاقة (Corner Radius)", editingTemplate.cornerRadiusDp.toFloat(), 0f..60f) { onUpdate(editingTemplate.copy(cornerRadiusDp = it.toInt())) }
                    Spacer(Modifier.height(8.dp))

                    // v1.9: Card padding — breathing room between the card edge
                    // and the text, per side. Honored by NDI, HTTP and preview.
                    // v2.0: Padding collapsed into expandable row to reduce clutter
                    var paddingExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { paddingExpanded = !paddingExpanded }
                            .padding(vertical = 4.dp)
                            .then(anchorModifier("frame-padding")),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "حشوة البطاقة (Card Padding)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = bento.textSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${editingTemplate.cardPaddingTopDp.toInt()}/${editingTemplate.cardPaddingBottomDp.toInt()}/${editingTemplate.cardPaddingLeftDp.toInt()}/${editingTemplate.cardPaddingRightDp.toInt()}",
                                fontSize = 11.sp,
                                color = bento.textSecondary
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (paddingExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = bento.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (paddingExpanded) {
                        SliderWithLabel("علوي (Top)", editingTemplate.cardPaddingTopDp, 0f..120f) { onUpdate(editingTemplate.copy(cardPaddingTopDp = it)) }
                        SliderWithLabel("سفلي (Bottom)", editingTemplate.cardPaddingBottomDp, 0f..120f) { onUpdate(editingTemplate.copy(cardPaddingBottomDp = it)) }
                        SliderWithLabel("أيسر (Left)", editingTemplate.cardPaddingLeftDp, 0f..160f) { onUpdate(editingTemplate.copy(cardPaddingLeftDp = it)) }
                        SliderWithLabel("أيمن (Right)", editingTemplate.cardPaddingRightDp, 0f..160f) { onUpdate(editingTemplate.copy(cardPaddingRightDp = it)) }
                    }
                    Spacer(Modifier.height(8.dp))

                    // v1.9: Auto-fit — shrink a too-long verse (with its citation
                    // and English section) to fit the text box instead of
                    // overflowing the card or screen. Short verses are untouched.
                    FeatureToggleRow("ملاءمة تلقائية للنص (Auto-fit text)", editingTemplate.autoFitText, modifier = anchorModifier("frame-autofit")) { onUpdate(editingTemplate.copy(autoFitText = it)) }
                    if (editingTemplate.autoFitText) {
                        SliderWithLabel("أكبر حجم للنص (Max size)", editingTemplate.autoFitMaxVerseSp.toFloat(), 10f..120f) { onUpdate(editingTemplate.copy(autoFitMaxVerseSp = it.toInt())) }
                        // 2026-10-28 auto-fit: citation floor + ratio lock.
                        // Floor = smallest size the citation may shrink to
                        // (default 10sp = old behaviour); lock ON (default)
                        // shrinks verse + citation together by one shared
                        // ratio, so raising a size never makes the
                        // citation smaller — the complaint that led here.
                        // Lock OFF keeps the citation at its set size
                        // (or the floor) while only the verse shrinks.
                        SliderWithLabel("أصغر حجم للمرجع (Citation floor)", editingTemplate.autoFitMinCitationSp.toFloat(), 10f..60f) { onUpdate(editingTemplate.copy(autoFitMinCitationSp = it.toInt())) }
                        FeatureToggleRow("قفل النسبة (verse + citation together)", editingTemplate.autoFitRatioLock) { onUpdate(editingTemplate.copy(autoFitRatioLock = it)) }
                        // Live readout, labelled honestly: these are the
                        // at-cap sizes before box shrink (the box-fitted
                        // size depends on verse length and the preview
                        // shows it live). "Rendering at X, capped at Y"
                        // in the user's words: at-cap verse, cap, and
                        // the citation size that follows from the lock.
                        val atCapVerse = minOf(editingTemplate.verseFontSize, editingTemplate.autoFitMaxVerseSp)
                        val atCapCitation = if (editingTemplate.autoFitRatioLock) {
                            if (editingTemplate.verseFontSize > 0) maxOf(editingTemplate.autoFitMinCitationSp, (editingTemplate.referenceFontSize * atCapVerse / editingTemplate.verseFontSize)) else editingTemplate.referenceFontSize
                        } else {
                            maxOf(editingTemplate.referenceFontSize, editingTemplate.autoFitMinCitationSp)
                        }
                        Text("عند الحد الأقصى: النص $atCapVerse، المرجع $atCapCitation (قبل ملاءمة الصندوق — المعاينة تعرض الحجم النهائي)", fontSize = 11.sp, color = bento.textSecondary)
                    }
                    // Audit fix (D): a WARNING, never a block - the fit obeys
                    // destructive combinations silently (Ashraf's Midnight
                    // Psalm: cap 16 under a 58sp verse in a 10% box), so say
                    // so right where the numbers are set.
                    if (editingTemplate.autoFitText && (editingTemplate.autoFitMaxVerseSp < editingTemplate.verseFontSize || editingTemplate.boxHeightPercent < 20)) {
                        val warnParts = mutableListOf<String>()
                        if (editingTemplate.autoFitMaxVerseSp < editingTemplate.verseFontSize) warnParts.add("سقف الحجم (" + editingTemplate.autoFitMaxVerseSp + ") أصغر من حجم الآية (" + editingTemplate.verseFontSize + ")")
                        if (editingTemplate.boxHeightPercent < 20) warnParts.add("ارتفاع الصندوق " + editingTemplate.boxHeightPercent + "% صغير جداً")
                        Text(
                            "⚠ " + warnParts.joinToString(" · ") + " — ستظهر الآيات صغيرة (verses will render tiny)",
                            fontSize = 11.sp,
                            color = bento.warning,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

        }
    }

    // 2026-10-28 design menu button: ALWAYS visible (not only when
    // dirty) — its label IS the design name, prefixed with Modified
    // only when the working copy differs from the saved design, and
    // just the name when clean. Tapping opens the one design menu
    // (Save current / Save as new / Duplicate / Rename / Export /
    // Import / Delete). Separate storage buttons are gone: the old
    // Import feed button is removed from the stage row, and the
    // top-bar feed chip no longer opens import (see MainActivity).
    // When the top bar already shows this button (K7 gate), the
    // compact stage hides it, so the two can never both show.
    val designMenuButtonBlock: @Composable () -> Unit = {
        if (!topBarDesignButtonVisible) {
            val designName = templates.firstOrNull { it.id == highlightedId }?.name ?: editingTemplate.name
            val designLabel = if (tabIsDirty) "معدّل · $designName (Modified · $designName)" else designName
            Surface(shape = RoundedCornerShape(10.dp), color = bento.primaryContainer, onClick = { onShowModifiedMenuChange(true) }) {
                Text(designLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
    val stageBlock: @Composable () -> Unit = {
        // 4. Style Selector (per-tab collection; highlight = selected style id, NOT the working template id)
        // 2026-10-28: the design button + style help, then the cards.
        // No separate Import button — Import lives in the design menu.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            designMenuButtonBlock()
            InfoPopupButton("اختر النمط (Select Style)", "اضغط على زر التصميم للحفظ / التصدير / الاستيراد / الحذف • اضغط مطولاً على أي نمط لنفس الخيارات • التعديل بدون حفظ يمسح التمييز")
        }
        styleCardsRow()

        Spacer(Modifier.height(8.dp))

        previewBlock(null)

        Spacer(Modifier.height(16.dp))

        // Round 2 (#10): the frame cluster (alignment, position, box height,
        // padding, auto-fit) moved out of Background & Effects onto the stage -
        // these are the numbers judged against the preview.
        frameSectionBlock()

        Spacer(Modifier.height(8.dp))

        // v1.9.2 (#10b): stage order is now preview -> Frame & Padding ->
        // Alpha -> Save/Update. Alpha used to sit inside the preview card
        // (between the picture and the frame sliders) and saving sat above
        // the frame controls; the flow now reads pick -> frame -> alpha ->
        // save, with saving last.
        FeatureToggleRow("خلفية شفافة تماماً 100% Alpha", editingTemplate.isPureTransparentBackground) { onUpdate(editingTemplate.copy(isPureTransparentBackground = it)) }
        // Batch item 6: the separate Save/Update buttons are gone — saving
        // lives in the Modified menu (tap the Modified chip).
        Spacer(Modifier.height(8.dp))

    }
    val arabicSectionBlock: @Composable () -> Unit = {
CollapsibleSection(
                    modifier = anchorModifier("arabic"),
                    title = "تنسيق الخط العربي (Arabic Typography)",
                    initiallyExpanded = true,
                    expanded = sectionExpanded.getOrPut("arabic") { true },
                    onExpandedChange = { setSectionExpanded("arabic", it) },
                    summary = editingTemplate.fontFamily + " \u00b7 verse " + editingTemplate.verseFontSize + " \u00b7 citation " + editingTemplate.referenceFontSize
                ) {
                    FontDropdown(
                        selected = editingTemplate.fontFamily,
                        onSelect = { onUpdate(editingTemplate.copy(fontFamily = it)) },
                        customFonts = customFonts,
                        onImportFont = launchFontPicker,
                        onDeleteCustomFont = { fontToDelete = it }
                    )
                
                    Row(modifier = anchorModifier("arabic-citation"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("الشاهد:", fontSize = 11.sp, color = bento.textSecondary)
                        StyleToggle("B", editingTemplate.referenceIsBold) { onUpdate(editingTemplate.copy(referenceIsBold = it)) }
                        StyleToggle("I", editingTemplate.referenceIsItalic) { onUpdate(editingTemplate.copy(referenceIsItalic = it)) }
                    }
                    ColorGradePickerRow("لون الشاهد العربي (Citation Color)", editingTemplate.referenceColorHex, { onUpdate(editingTemplate.copy(referenceColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                    SliderWithLabel("حجم الشاهد العربي", editingTemplate.referenceFontSize.toFloat(), 10f..120f) { onUpdate(editingTemplate.copy(referenceFontSize = it.toInt())) }

                    Row(modifier = anchorModifier("arabic-verse"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("الآية:", fontSize = 11.sp, color = bento.textSecondary)
                        StyleToggle("B", editingTemplate.verseIsBold) { onUpdate(editingTemplate.copy(verseIsBold = it)) }
                        StyleToggle("I", editingTemplate.verseIsItalic) { onUpdate(editingTemplate.copy(verseIsItalic = it)) }
                    }
                    ColorGradePickerRow("لون النص العربي (Verse Color)", editingTemplate.textColorHex, { onUpdate(editingTemplate.copy(textColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                    SliderWithLabel("حجم النص العربي", editingTemplate.verseFontSize.toFloat(), 10f..180f) { onUpdate(editingTemplate.copy(verseFontSize = it.toInt())) }
                    FeatureToggleRow("علامات الترقيم العربية (Use Arabic punctuation ، ؛ ؟)", editingTemplate.useArabicPunctuation) { onUpdate(editingTemplate.copy(useArabicPunctuation = it)) }
                    FeatureToggleRow("الأرقام العربية المشرقية (Eastern Arabic numerals ١٢٣)", editingTemplate.useEasternArabicNumerals) { onUpdate(editingTemplate.copy(useEasternArabicNumerals = it)) }
                    FeatureToggleRow("تصحيح الأقواس بخط النظام (Use system font for brackets ( ) [ ] { })", editingTemplate.useSystemFontForBrackets) { onUpdate(editingTemplate.copy(useSystemFontForBrackets = it)) }

                    // v1.8: Three-way language mode (replaces the bilingual toggle).
                
                }
    }

    val languageSectionBlock: @Composable () -> Unit = {
CollapsibleSection(
                    modifier = anchorModifier("language"),
                    title = "لغة العرض (Language)",
                    initiallyExpanded = false,
                    expanded = sectionExpanded.getOrPut("language") { false },
                    onExpandedChange = { setSectionExpanded("language", it) },
                    summary = when (editingTemplate.languageMode) {
                        com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY -> "العربية (Arabic only)"
                        com.arabicchristianmedia.model.LanguageMode.ENGLISH_ONLY -> "English only"
                        com.arabicchristianmedia.model.LanguageMode.BOTH -> "الاثنان (Both)"
                    }
                ) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bento.surfaceVariant).padding(4.dp)) {
                        val modes = listOf(
                            com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY to "العربية",
                            com.arabicchristianmedia.model.LanguageMode.ENGLISH_ONLY to "English",
                            com.arabicchristianmedia.model.LanguageMode.BOTH to "الاثنان (Both)"
                        )
                        modes.forEach { (mode, label) ->
                            val selected = editingTemplate.languageMode == mode
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) bento.primary else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onUpdate(editingTemplate.copy(languageMode = mode)) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) bento.onPrimary else bento.textSecondary
                                )
                            }
                        }
                    }
                    if (editingTemplate.languageMode != com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY) {
                    
                }
                }
    }

    val englishSectionBlock: @Composable () -> Unit = {
CollapsibleSection(
                    modifier = anchorModifier("english"),
                    title = "تنسيق الخط الإنجليزي (English Typography)",
                    initiallyExpanded = editingTemplate.languageMode != com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY,
                    expanded = sectionExpanded.getOrPut("english") { editingTemplate.languageMode != com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY },
                    onExpandedChange = { setSectionExpanded("english", it) },
                    summary = editingTemplate.secondaryFontFamily + " \u00b7 verse " + editingTemplate.secondaryVerseFontSize + " \u00b7 citation " + editingTemplate.secondaryReferenceFontSize
                ) {
                    
                        // English Citation Controls
                        Row(modifier = anchorModifier("english-citation"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("الشاهد الإنجليزي:", fontSize = 11.sp, color = bento.textSecondary)
                            StyleToggle("B", editingTemplate.secondaryReferenceIsBold) { onUpdate(editingTemplate.copy(secondaryReferenceIsBold = it)) }
                            StyleToggle("I", editingTemplate.secondaryReferenceIsItalic) { onUpdate(editingTemplate.copy(secondaryReferenceIsItalic = it)) }
                        }
                        ColorGradePickerRow("لون الشاهد الإنجليزي (English Citation Color)", editingTemplate.secondaryReferenceColorHex, { onUpdate(editingTemplate.copy(secondaryReferenceColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                        SliderWithLabel("حجم الشاهد الإنجليزي", editingTemplate.secondaryReferenceFontSize.toFloat(), 8f..100f) { onUpdate(editingTemplate.copy(secondaryReferenceFontSize = it.toInt())) }

                        // English Verse Controls
                        LatinFontDropdown(
                            selected = editingTemplate.secondaryFontFamily,
                            onSelect = { onUpdate(editingTemplate.copy(secondaryFontFamily = it)) },
                            customFonts = customFonts,
                            onImportFont = launchFontPicker,
                            onDeleteCustomFont = { fontToDelete = it }
                        )
                        Row(modifier = anchorModifier("english-verse"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("الآية الإنجليزية:", fontSize = 11.sp, color = bento.textSecondary)
                            StyleToggle("B", editingTemplate.secondaryVerseIsBold) { onUpdate(editingTemplate.copy(secondaryVerseIsBold = it)) }
                            StyleToggle("I", editingTemplate.secondaryVerseIsItalic) { onUpdate(editingTemplate.copy(secondaryVerseIsItalic = it)) }
                        }
                        ColorGradePickerRow("لون النص الإنجليزي (English Verse Color)", editingTemplate.secondaryTextColorHex, { onUpdate(editingTemplate.copy(secondaryTextColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                        SliderWithLabel("حجم النص الإنجليزي", editingTemplate.secondaryVerseFontSize.toFloat(), 8f..120f) { onUpdate(editingTemplate.copy(secondaryVerseFontSize = it.toInt())) }

                        SliderWithLabel("المسافة الفاصلة بين اللغتين", editingTemplate.bilingualSpacing.toFloat(), 0f..150f) { onUpdate(editingTemplate.copy(bilingualSpacing = it.toInt())) }
                        FeatureToggleRow("إظهار المسافة بين اللغتين (Show spacing)", editingTemplate.showBilingualSpacing) { onUpdate(editingTemplate.copy(showBilingualSpacing = it)) }
                    }
    }

    val bgSectionBlock: @Composable () -> Unit = {
CollapsibleSection(
                    modifier = anchorModifier("bg"),
                    title = "الخلفية والمؤثرات البصرية (Background & Effects)",
                    initiallyExpanded = editingTemplate.animatedBackground != AnimatedBackgroundType.NONE || editingTemplate.customVideoId.isNotEmpty(),
                    expanded = sectionExpanded.getOrPut("bg") { editingTemplate.animatedBackground != AnimatedBackgroundType.NONE || editingTemplate.customVideoId.isNotEmpty() },
                    onExpandedChange = { setSectionExpanded("bg", it) },
                    summary = "Motion: " + editingTemplate.animatedBackground.displayNameAr + " \u00b7 bg " + (editingTemplate.bgOpacity * 100).toInt() + "%"
                ) {
                    // v2.0: Card style is now auto-coordinated via motion background picker
                    // (see getBackgroundPairing). The style field remains adjustable
                    // via style presets; manual override UI removed for simplicity.

                    // Animated Motion Backgrounds (Layer 1: Bottom background layer)
                    Text("الخلفيات المتحركة (Motion Video Backgrounds) — طبقة الخلفية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary, modifier = anchorModifier("bg-motion"))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AnimatedBackgroundType.entries) { type ->
                            FilterChip(
                                selected = editingTemplate.animatedBackground == type,
                                onClick = {
                                    // v2.0: apply coordinated card style pairing
                                    val pairing = getBackgroundPairing(type)
                                    val updated = if (pairing != null) {
                                        editingTemplate.copy(
                                            animatedBackground = type,
                                            style = pairing.style,
                                            bgOpacity = pairing.bgOpacity,
                                            accentColorHex = pairing.accentColorHex,
                                            isPureTransparentBackground = pairing.isPureTransparentBackground
                                        )
                                    } else {
                                        editingTemplate.copy(animatedBackground = type)
                                    }
                                    onUpdate(updated)
                                },
                                label = { Text(type.displayNameAr, fontSize = 10.sp) }
                            )
                        }
                    }
                    // v1.9.2 (#8): when Custom Video is the selected type,
                    // its file controls come FIRST under the chips — the
                    // primary action (choose/replace the file) was buried at
                    // the bottom of this section, below the sliders.
                    if (editingTemplate.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO) {
                        if (editingTemplate.customVideoId.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "✓ فيديو مخصص محدد (Custom video set)",
                                        fontSize = 11.sp,
                                        color = bento.textSecondary
                                    )
                                    // v1.8: show the opaque video ID and its local URL.
                                    Text(
                                        "ID: ${editingTemplate.customVideoId}",
                                        fontSize = 10.sp,
                                        color = bento.textSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        "URL: /video?id=${editingTemplate.customVideoId}",
                                        fontSize = 10.sp,
                                        color = bento.textSecondary
                                    )
                                }
                                TextButton(onClick = {
                                    onUpdate(editingTemplate.copy(customVideoId = "", animatedBackground = AnimatedBackgroundType.NONE))
                                }) {
                                    Text("إزالة (Remove)", fontSize = 11.sp, color = bento.primary)
                                }
                            }
                            // v1.8: Muted toggle for custom video (default muted for church production).
                            Spacer(modifier = Modifier.height(4.dp))
                            FeatureToggleRow(
                                "كتم صوت الفيديو (Mute video audio)",
                                editingTemplate.customVideoMuted
                            ) {
                                onUpdate(editingTemplate.copy(customVideoMuted = it))
                            }
                        } else if (editingTemplate.customVideoUrl.isNotEmpty()) {
                            Text(
                                "⚠ تعذر العثور على الفيديو المحفوظ — اختر ملفاً جديداً",
                                fontSize = 11.sp,
                                color = bento.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Button(
                            onClick = { launcher.launch("video/*") },
                            enabled = !isImportingVideo,
                            colors = ButtonDefaults.buttonColors(containerColor = bento.primary)
                        ) {
                            Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isImportingVideo) "جاري الاستيراد..." else "اختيار ملف فيديو مخصص (MP4)", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (editingTemplate.animatedBackground != AnimatedBackgroundType.NONE) {
                        // v2.0: Video loop mode — v1.9.2 (#8): for Custom
                        // Video it now sits right under the file controls,
                        // before Opacity/Speed (was after them).
                        if (editingTemplate.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO) {
                            Text(
                                "طريقة تكرار الفيديو (Video loop)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = bento.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VideoLoopMode.values().forEach { mode ->
                                    FilterChip(
                                        selected = editingTemplate.videoLoopMode == mode,
                                        onClick = { onUpdate(editingTemplate.copy(videoLoopMode = mode)) },
                                        label = { Text(mode.displayName, fontSize = 12.sp) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        // v1.8: opacity slider for ALL animated backgrounds (not just custom video).
                        SliderWithLabel(
                            label = "شفافية الحركة (Animation Opacity)",
                            value = editingTemplate.animatedBackgroundOpacity,
                            range = 0f..1f,
                            onValueChange = { onUpdate(editingTemplate.copy(animatedBackgroundOpacity = it)) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // v1.8: Motion speed slider (0 to 3.0x) — right after opacity.
                        SliderWithLabel(
                            "سرعة الحركة (Motion speed)",
                            editingTemplate.motionSpeed,
                            0f..3f
                        ) {
                            onUpdate(editingTemplate.copy(motionSpeed = it))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // v1.9.2 (#8): the loop chips, the set-video info and
                        // the file picker for Custom Video moved ABOVE the
                        // sliders (right under the type chips).
                        if (editingTemplate.animatedBackground != AnimatedBackgroundType.CUSTOM_VIDEO) {
                            Button(
                                onClick = { launcher.launch("video/*") },
                                enabled = !isImportingVideo,
                                colors = ButtonDefaults.buttonColors(containerColor = bento.primary)
                            ) {
                                Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isImportingVideo) "جاري الاستيراد..." else "اختيار ملف فيديو مخصص (MP4)", fontSize = 12.sp)
                            }
                        }
                    }

                    FeatureToggleRow("خلفية شفافة تماماً 100% Alpha", editingTemplate.isPureTransparentBackground) { onUpdate(editingTemplate.copy(isPureTransparentBackground = it)) }
                
                    if (!editingTemplate.isPureTransparentBackground) {
                        SliderWithLabel("درجة شفافية الكارت (Background Opacity)", editingTemplate.bgOpacity, 0f..1f) { onUpdate(editingTemplate.copy(bgOpacity = it)) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ColorGradePickerRow("لون كارت الخلفية (Card Color)", editingTemplate.bgColorHex, { onUpdate(editingTemplate.copy(bgColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                            }
                            // v1.8: Quick transparent option for card color.
                            TextButton(
                                onClick = { onUpdate(editingTemplate.copy(bgColorHex = "#00000000", bgOpacity = 0f)) }
                            ) {
                                Text("شفاف", fontSize = 11.sp, color = bento.primary)
                            }
                        }
                    }

                    // 2026-10-28 gradient card: OFF by default, so every
                    // existing style (including ROYAL's own gradient)
                    // is unchanged. When ON, the two colours + angle +
                    // animation + speed drive HTTP (CSS gradient) and
                    // NDI (native gradient) with the same parameters;
                    // the gradient sits above a motion video and the
                    // text sits above it (layer order unchanged).
                    // Angle uses the CSS convention: 90 = left-to-right,
                    // 270 = right-to-left, 0 = bottom-to-top.
                    FeatureToggleRow("بطاقة متدرجة اللونين (Gradient card)", editingTemplate.gradientEnabled) { onUpdate(editingTemplate.copy(gradientEnabled = it)) }
                    if (editingTemplate.gradientEnabled) {
                        ColorGradePickerRow("اللون الثاني (Second color)", editingTemplate.gradientColorHex2, { onUpdate(editingTemplate.copy(gradientColorHex2 = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                        SliderWithLabel("زاوية التدرج (Angle, 0-360°)", editingTemplate.gradientAngleDeg.toFloat(), 0f..360f) { onUpdate(editingTemplate.copy(gradientAngleDeg = it.toInt())) }
                        val gradientAnims = listOf(
                            GradientAnimationType.NONE to "ثابت (None)",
                            GradientAnimationType.SLIDE to "انزلاق يمين-يسار (Slide)",
                            GradientAnimationType.ROTATE to "تدوير الزاوية ببطء (Rotate)",
                            GradientAnimationType.PULSE to "نبض (Pulse)"
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            gradientAnims.forEach { (anim, label) ->
                                val selected = editingTemplate.gradientAnimation == anim
                                Surface(shape = RoundedCornerShape(999.dp), color = if (selected) bento.primaryContainer else bento.card, border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) bento.primary else bento.border), onClick = { onUpdate(editingTemplate.copy(gradientAnimation = anim)) }) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                        if (editingTemplate.gradientAnimation != GradientAnimationType.NONE) {
                            SliderWithLabel("سرعة الحركة (Speed)", editingTemplate.gradientSpeed, 0f..3f) { onUpdate(editingTemplate.copy(gradientSpeed = it)) }
                        }
                        Text("اللون الأول هو لون الكارت أعلاه، والشفافية من منزلق الشفافية.", fontSize = 11.sp, color = bento.textSecondary)
                    }

                    // v1.9.2 (#9): accents group — the border flag comes
                    // FIRST and the color follows it, shown only while the
                    // flag is on (the picker used to render unconditionally,
                    // above its own toggle, inviting a dead edit). Same
                    // gated pattern as the Card Glow color in Shadow & Glow.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureToggleRow("إظهار الحدود الملونة", editingTemplate.showAccentBorder) { onUpdate(editingTemplate.copy(showAccentBorder = it)) }
                    }
                    if (editingTemplate.showAccentBorder) {
                        ColorGradePickerRow("لون إطار التمييز (Accent Color)", editingTemplate.accentColorHex, { onUpdate(editingTemplate.copy(accentColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                    }

                    Row(Modifier.fillMaxWidth().then(anchorModifier("bg-emblem")), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        // v1.7: free emblem — the user types any emoji/symbol with the
                        // system keyboard. Empty = no emblem (hint behind the (i)).
                        OutlinedTextField(
                            value = editingTemplate.emblem,
                            onValueChange = { onUpdate(editingTemplate.copy(emblem = it)) },
                            label = { Text("رمز / إيموجي (Emblem)", fontSize = 10.sp) },
                            placeholder = { Text("✝", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        InfoPopupButton("رمز / إيموجي (Emblem)", "اتركه فارغاً للإخفاء")
                    }
                    // Independent text shadow and card glow (v1.6). Card glow only
                    // applies where a card exists — not on full-bleed Full Show.
                    // v1.9: verse-change transition (HTTP overlays animate with this;
                    // NDI applies the frame immediately).
                    Text("حركة انتقال الآية (Transition)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary, modifier = anchorModifier("bg-transition"))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        TransitionType.entries.forEach { t ->
                            FilterChip(
                                selected = editingTemplate.transition == t,
                                onClick = { onUpdate(editingTemplate.copy(transition = t)) },
                                label = { Text(t.displayName, fontSize = 10.sp) }
                            )
                        }
                    }
                    SliderWithLabel("مدة الانتقال (Duration ms)", editingTemplate.transitionDurationMs.toFloat(), 0f..2000f) { onUpdate(editingTemplate.copy(transitionDurationMs = it.toInt())) }
                    Spacer(Modifier.height(4.dp))
                    // v1.9: stream background — what fills the space around the card
                    // (chroma/luma keys, dark studio, or full transparency).
                    // v1.9.1: this setting is read ONLY by the MJPEG /stream
                    // renderer, which always renders the LOWER THIRD template —
                    // so the chips live in the Lower tab only. In the Full Show
                    // tab this is an explanatory note instead of a dead control
                    // (Ashraf's bug report: changing it in Full changed nothing).
                    Row(modifier = anchorModifier("bg-stream"), verticalAlignment = Alignment.CenterVertically) {
                        Text("خلفية البث (Stream Background)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary, modifier = Modifier.weight(1f))
                        InfoPopupButton("خلفية البث (Stream Background)", if (editorTab == 0) "تؤثر فقط في بث MJPEG ‏(/stream) حول تصميم الثلث السفلي — لا تؤثر في NDI ولا في صفحات /lower و/full." else "تُضبط خلفية البث من تبويب الثلث السفلي (Lower Third) — فبث MJPEG ‏(/stream) يعرض دائماً تصميم الثلث السفلي.")
                    }
                    Spacer(Modifier.height(4.dp))
                    if (editorTab == 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StreamBackgroundMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = editingTemplate.streamBackgroundMode == mode,
                                    onClick = { onUpdate(editingTemplate.copy(streamBackgroundMode = mode)) },
                                    label = { Text(mode.displayNameAr, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                
                }
    }

    val shadowSectionBlock: @Composable () -> Unit = {
CollapsibleSection(
                    modifier = anchorModifier("shadow"),
                    title = "الظل والتوهج (Shadow & Glow)",
                    initiallyExpanded = editingTemplate.textShadowEnabled || editingTemplate.cardGlowEnabled,
                    expanded = sectionExpanded.getOrPut("shadow") { editingTemplate.textShadowEnabled || editingTemplate.cardGlowEnabled },
                    onExpandedChange = { setSectionExpanded("shadow", it) },
                    summary = "Shadow " + (if (editingTemplate.textShadowEnabled) "on" else "off") + " \u00b7 Glow " + (if (editingTemplate.cardGlowEnabled) "on" else "off")
                ) {
                    FeatureToggleRow("ظل النص (Text Shadow)", editingTemplate.textShadowEnabled, modifier = anchorModifier("shadow-text")) { onUpdate(editingTemplate.copy(textShadowEnabled = it)) }
                    if (editingTemplate.textShadowEnabled) {
                        ColorGradePickerRow("لون ظل النص (Text Shadow Color)", editingTemplate.textShadowColorHex, { onUpdate(editingTemplate.copy(textShadowColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                        // v1.8: shadow thickness (blur), distance (offset), and 8-direction compass.
                        SliderWithLabel("سماكة الظل (Thickness)", editingTemplate.textShadowBlurDp, 0f..32f) {
                            onUpdate(editingTemplate.copy(textShadowBlurDp = it))
                        }
                        SliderWithLabel("مسافة الظل (Distance)", editingTemplate.textShadowOffsetDp, 0f..32f) {
                            onUpdate(editingTemplate.copy(textShadowOffsetDp = it))
                        }
                        Text("اتجاه الظل (Direction)", fontSize = 11.sp, color = bento.textSecondary)
                        // 8-direction compass: 0°=→, 45°=↘, 90°=↓, 135°=↙, 180°=←, 225°=↖, 270°=↑, 315°=↗
                        // v1.8: Constrained to max 200dp so it doesn't blow up on tablets.
                        val directions = listOf(
                            315 to "↗", 0 to "→", 45 to "↘",
                            270 to "↑", null to "•", 90 to "↓",
                            225 to "↖", 180 to "←", 135 to "↙"
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.widthIn(max = 200.dp)
                            ) {
                            for (row in 0..2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (col in 0..2) {
                                        val (angle, symbol) = directions[row * 3 + col]
                                        val selected = if (angle == null) false else editingTemplate.textShadowAngleDeg == angle
                                        // Center button clears to default (90° down).
                                        Box(
                                            modifier = Modifier.weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) bento.primary else bento.surfaceVariant)
                                                .clickable {
                                                    if (angle != null) onUpdate(editingTemplate.copy(textShadowAngleDeg = angle))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                symbol,
                                                fontSize = 20.sp,
                                                color = if (selected) bento.onPrimary else bento.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        } // Close Box (v1.8: constrains compass size on tablets)
                    }
                    if (!editingTemplate.isFullScreen) {
                        FeatureToggleRow("توهج الكارت (Card Glow)", editingTemplate.cardGlowEnabled, modifier = anchorModifier("shadow-glow")) { onUpdate(editingTemplate.copy(cardGlowEnabled = it)) }
                        if (editingTemplate.cardGlowEnabled) {
                            ColorGradePickerRow("لون توهج الكارت (Card Glow Color)", editingTemplate.cardGlowColorHex, { onUpdate(editingTemplate.copy(cardGlowColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                        }
                    }

                }
    }

    val sheetBlock: @Composable () -> Unit = {
        // 5. Detailed Independent Configuration Controls
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Style Preset Selector
                // Primary Typography (Arabic Verse & Citation)
                arabicSectionBlock()
                HorizontalDivider(color = bento.borderSubtle)
                languageSectionBlock()
                englishSectionBlock()
                HorizontalDivider(color = bento.borderSubtle)
                // Background layers: motion backgrounds (Layer 1, bottom) + card/transparency/accents (Layer 2, above video)
                bgSectionBlock()
                shadowSectionBlock()
            }
            Spacer(Modifier.height(100.dp))
        }
    }
    // Wide two-pane layout: the anchor index under the preview. Tapping
    // a row jumps the right panel to that section (or sub-group) and
    // flashes it; the right panel always shows every control — anchors
    // only point, they never hide anything.
    val anchorSections: List<Pair<String, Pair<String, List<Pair<String, String>>>>> = listOf(
        "frame" to ("الإطار والحشوة (Frame & Padding)" to listOf(
            "frame-align" to "محاذاة النص (Alignment)",
            "frame-position" to "الموضع والهوامش (Position & Margins)",
            "frame-padding" to "حشوة البطاقة (Card Padding)",
            "frame-autofit" to "ملاءمة تلقائية (Auto-fit)"
        )),
        "alpha" to ("شفافية 100% (Alpha)" to emptyList()),
        "arabic" to ("تنسيق الخط العربي (Arabic Typography)" to listOf(
            "arabic-citation" to "الشاهد (Citation)",
            "arabic-verse" to "الآية (Verse)"
        )),
        "language" to ("لغة العرض (Language)" to emptyList()),
        "english" to ("تنسيق الخط الإنجليزي (English Typography)" to listOf(
            "english-citation" to "الشاهد الإنجليزي (English Citation)",
            "english-verse" to "الآية الإنجليزية (English Verse)"
        )),
        "bg" to ("الخلفية والمؤثرات البصرية (Background & Effects)" to listOf(
            "bg-motion" to "الخلفيات المتحركة (Motion Backgrounds)",
            "bg-emblem" to "رمز / إيموجي (Emblem)",
            "bg-transition" to "حركة انتقال الآية (Transition)",
            "bg-stream" to "خلفية البث (Stream Background)"
        )),
        "shadow" to ("الظل والتوهج (Shadow & Glow)" to listOf(
            "shadow-text" to "ظل النص (Text Shadow)",
            "shadow-glow" to "توهج الكارت (Card Glow)"
        ))
    )
    val anchorsBlock: @Composable () -> Unit = {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("الانتقال السريع (Jump to)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = bento.textPrimary, modifier = Modifier.padding(bottom = 4.dp))
                anchorSections.forEach { (sectionId, section) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (highlightedAnchor == sectionId) bento.primaryContainer else Color.Transparent)
                            .clickable { jumpToAnchor(sectionId) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(section.first, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                    }
                    section.second.forEach { (subId, subTitle) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (highlightedAnchor == subId) bento.primaryContainer else Color.Transparent)
                                .clickable { jumpToAnchor(subId) }
                                .padding(start = 16.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(subTitle, fontSize = 11.sp, color = bento.textSecondary)
                        }
                    }
                }
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(bento.bg)) {
        // Screen-size gate (see LayoutBreakpoints): content size is
        // ~567dp tall on tablet landscape and would hide the anchors.
        val screenConfig = LocalConfiguration.current
        if (isWideLayout(screenConfig.screenWidthDp.dp, screenConfig.screenHeightDp.dp)) {
            // Wide two-pane layout: tabs + style bar full-width, then
            // left = title + preview + anchors, right = all controls.
            // Each panel scrolls on its own; scrolling the right panel
            // never moves the preview.
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) { tabSwitcherBlock() }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 14.dp)) {
                    // Batch item 6: the Save/Update buttons are gone — saving
                    // lives in the Modified menu (tap the Modified chip in
                    // the app-bar line).
                    styleCardsRow()
                }
                if (showHowItWorks) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) { tipsBlock() }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.weight(0.44f).fillMaxHeight().padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        editorTitleBlock()
                        Spacer(Modifier.height(12.dp))
                        previewBlock(onPreviewClick = { jumpToAnchor("frame") })
                        Spacer(Modifier.height(16.dp))
                        anchorsBlock()
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(bento.borderSubtle))
                    // Right panel: one LazyColumn item per section, keyed
                    // by anchor id — jumps are scrollToItem by key (see
                    // jumpToAnchor). Each sheet section gets its own card
                    // (same 20dp shape, card color, 16dp padding as the
                    // old shared sheet card); the compact layout still
                    // uses the shared sheetBlock() unchanged.
                    LazyColumn(
                        Modifier.weight(0.56f).fillMaxHeight().padding(14.dp),
                        state = rightLazyListState,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item("frame") { frameSectionBlock() }
                        item("alpha") {
                            Box(modifier = anchorModifier("alpha")) {
                                FeatureToggleRow("خلفية شفافة تماماً 100% Alpha", editingTemplate.isPureTransparentBackground) { onUpdate(editingTemplate.copy(isPureTransparentBackground = it)) }
                            }
                        }
                        item("arabic") { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { arabicSectionBlock() } } }
                        item("language") { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { languageSectionBlock() } } }
                        item("english") { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { englishSectionBlock() } } }
                        item("bg") { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { bgSectionBlock() } } }
                        item("shadow") { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { shadowSectionBlock() } } }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                headerBlock()
                stageBlock()
                // Compact Jump-to index: the same anchorsBlock as wide.
                // Compact has no LazyColumn, so a tap expands the section
                // and flashes its border (jumpToAnchor) but does not scroll
                // to it — scroll-to jumps stay wide-only. This still fixes
                // the reported findability problem (collapsed Background
                // hiding gradient/motion/stream) without a LazyColumn
                // refactor, which is follow-up, not a minor fix.
                anchorsBlock()
                Spacer(Modifier.height(12.dp))
                sheetBlock()
            }
        }
    }
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("حفظ القالب") },
                text = { OutlinedTextField(value = newTemplateName, onValueChange = { newTemplateName = it }, label = { Text("اسم القالب") }, singleLine = true) },
                confirmButton = { Button(onClick = { if (newTemplateName.isNotBlank()) { onSaveAsNew(newTemplateName, editingTemplate, editorTab == 1); showSaveDialog = false } }) { Text("حفظ") } },
                dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("إلغاء") } }
            )
        }

        // 2026-10-28 design menu: the design button opens ONE menu
        // with every design action — Save current (Update), Save as
        // new, Duplicate, Rename, Export (working design, not stale
        // saved values), Import new, Update from import, Delete.
        // Delete is guarded: factory styles hide it (they are
        // re-supplied on launch, like built-in themes) and every
        // delete goes through the confirmation dialog, which keeps
        // the working values as unsaved edits. No separate storage
        // buttons exist anymore.
        if (showModifiedMenu) {
            val designMenuName = templates.firstOrNull { it.id == highlightedId }?.name ?: editingTemplate.name
            val designMenuTitle = if (tabIsDirty) "معدّل · $designMenuName (Modified · $designMenuName)" else designMenuName
            val highlightedTpl = templates.firstOrNull { it.id == highlightedId }
            AlertDialog(
                onDismissRequest = { onShowModifiedMenuChange(false) },
                title = { Text(designMenuTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (highlightedId != null) {
                            Button(
                                onClick = {
                                    onShowModifiedMenuChange(false)
                                    if (highlightedStyleIsFactory) showFactoryUpdateDialog = true
                                    else onUpdateStyleInPlace(editorTab == 1)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ الحالي «$designMenuName» (Save current / Update)", fontSize = 13.sp)
                            }
                        }
                        Button(
                            onClick = {
                                onShowModifiedMenuChange(false)
                                newTemplateName = "${editingTemplate.name} النسخة"
                                showSaveDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حفظ كنمط جديد (Save as new style)", fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                onShowModifiedMenuChange(false)
                                onSaveAsNew("${editingTemplate.name} النسخة", editingTemplate, editorTab == 1)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("نسخ (Duplicate)", fontSize = 13.sp)
                        }
                        if (highlightedId != null) {
                            Button(
                                onClick = {
                                    onShowModifiedMenuChange(false)
                                    renameInput = designMenuName
                                    showRenameDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("إعادة تسمية (Rename)", fontSize = 13.sp)
                            }
                        }
                        TextButton(
                            onClick = {
                                onShowModifiedMenuChange(false)
                                onExportWorkingTemplate(editorTab == 1)?.let { json ->
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Bible NDI style: ${editingTemplate.name}")
                                        putExtra(Intent.EXTRA_TEXT, json)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "تصدير القالب (Export style)"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تصدير / مشاركة (Export / Share)", fontSize = 13.sp)
                        }
                        TextButton(
                            onClick = {
                                onShowModifiedMenuChange(false)
                                onImportTargetTplChange(null); onShowImportDialogChange(true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("استيراد قالب جديد (Import new style)", fontSize = 13.sp)
                        }
                        if (highlightedTpl != null) {
                            TextButton(
                                onClick = {
                                    onShowModifiedMenuChange(false)
                                    onImportTargetTplChange(highlightedTpl); onShowImportDialogChange(true)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("تحديث باستيراد JSON (Update from import)", fontSize = 13.sp)
                            }
                            if (!highlightedStyleIsFactory) {
                                TextButton(
                                    onClick = {
                                        onShowModifiedMenuChange(false)
                                        templateToDelete = highlightedTpl
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("حذف (Delete)", fontSize = 13.sp, color = bento.liveRed)
                                }
                            } else {
                                Text("الأنماط الجاهزة لا تُحذف (تُجدَّد مع كل تشغيل).", fontSize = 11.sp, color = bento.textSecondary)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { onShowModifiedMenuChange(false) }) { Text("إلغاء") } }
            )
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("إعادة تسمية النمط (Rename style)") },
                text = { OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }, label = { Text("اسم النمط") }, singleLine = true) },
                confirmButton = {
                    Button(onClick = {
                        val id = highlightedId
                        if (id != null && renameInput.isNotBlank()) {
                            onRenameTemplate(id, renameInput.trim(), editorTab == 1)
                        }
                        showRenameDialog = false
                    }) { Text("حفظ") }
                },
                dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("إلغاء") } }
            )
        }

        // v1.9.1: name dialog for a picked font file, then the ViewModel
        // imports it into private storage.
        pendingFontUri?.let { uri ->
            AlertDialog(
                onDismissRequest = { pendingFontUri = null },
                title = { Text("استيراد خط (Import font)") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = fontNameInput,
                            onValueChange = { fontNameInput = it },
                            label = { Text("اسم الخط (Font name)") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "سيظهر الخط بهذا الاسم في قائمتي الخطوط العربية والإنجليزية، ويعمل في المعاينة وNDI وصفحات HTTP.",
                            fontSize = 11.sp, color = bento.textSecondary
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onImportCustomFont(uri.toString(), fontNameInput)
                        pendingFontUri = null
                    }) { Text("استيراد") }
                },
                dismissButton = { TextButton(onClick = { pendingFontUri = null }) { Text("إلغاء") } }
            )
        }

        // v1.9.1: delete an imported font (styles using it fall back to the
        // system typeface until another font is picked).
        fontToDelete?.let { font ->
            AlertDialog(
                onDismissRequest = { fontToDelete = null },
                title = { Text("حذف الخط «${font.name}»؟") },
                text = { Text("سيتم حذف ملف الخط من الجهاز. الأنماط التي تستخدمه ستعود إلى خط النظام حتى تختار خطاً آخر.") },
                confirmButton = {
                    Button(onClick = { onDeleteCustomFont(font); fontToDelete = null }) { Text("حذف") }
                },
                dismissButton = { TextButton(onClick = { fontToDelete = null }) { Text("إلغاء") } }
            )
        }

        // v1.9.1: updating a FACTORY style — the app re-supplies shipped
        // styles on every launch, so the edit is saved as the user's own
        // copy under the same name (and the highlight moves to the copy).
        if (showFactoryUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showFactoryUpdateDialog = false },
                title = { Text("تحديث نمط جاهز (Factory style)") },
                text = {
                    Text("هذا النمط من الأنماط الجاهزة التي يجدّدها التطبيق مع كل تشغيل، فلا يمكن الكتابة فوقه مباشرة. سيتم حفظ تعديلاتك كنسختك الخاصة بنفس الاسم، وستصبح هي النمط المحدد.")
                },
                confirmButton = {
                    Button(onClick = {
                        onUpdateStyleInPlace(editorTab == 1)
                        showFactoryUpdateDialog = false
                    }) { Text("حفظ كنسختي") }
                },
                dismissButton = { TextButton(onClick = { showFactoryUpdateDialog = false }) { Text("إلغاء") } }
            )
        }

        // Long-press style menu: export / update-from-import / delete (full user control).
        styleMenuTpl?.let { tpl ->
            AlertDialog(
                onDismissRequest = { styleMenuTpl = null },
                title = { Text("\"${tpl.name}\"", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                onExportTemplate(tpl.id)?.let { json ->
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Bible NDI style: ${tpl.name}")
                                        putExtra(Intent.EXTRA_TEXT, json)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "تصدير القالب (Export style)"))
                                }
                                styleMenuTpl = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تصدير / مشاركة (Export / Share)", fontSize = 13.sp) }
                        TextButton(
                            onClick = { onImportTargetTplChange(tpl); onShowImportDialogChange(true); styleMenuTpl = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تحديث باستيراد JSON (Update from import)", fontSize = 13.sp) }
                        // Factory styles cannot be deleted (they are
                        // re-supplied from code on every launch, so a
                        // delete would silently undo itself) — the menu
                        // hides Delete for them, like built-in themes.
                        if (!TemplateRepository.isFactoryStyleId(tpl.id)) {
                            TextButton(
                                onClick = { templateToDelete = tpl; styleMenuTpl = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("حذف (Delete)", fontSize = 13.sp, color = bento.liveRed) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { styleMenuTpl = null }) { Text("إلغاء") } }
            )
        }

        // Import dialog: paste JSON or pick a .json file. Target null = new style,
        // otherwise the target style is updated in place (id preserved).
        if (showImportDialog) {
            val targetName = importTargetTpl?.name
            AlertDialog(
                onDismissRequest = { onShowImportDialogChange(false); onImportTargetTplChange(null) },
                title = {
                    Text(
                        if (targetName == null) "استيراد قالب جديد (Import new style)" else "تحديث \"$targetName\" بالاستيراد",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "الصق JSON القالب أدناه أو اختر ملف .json:",
                            fontSize = 12.sp, color = bento.textSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = importText,
                            onValueChange = { importText = it; importError = null },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            placeholder = { Text("{\"name\": ...}", fontSize = 11.sp) },
                            textStyle = TextStyle(fontSize = 11.sp)
                        )
                        if (importError != null) {
                            Text(importError!!, fontSize = 12.sp, color = bento.liveRed, modifier = Modifier.padding(top = 4.dp))
                        }
                        TextButton(onClick = { jsonPicker.launch(arrayOf("application/json", "text/plain")) }) {
                            Text("اختر ملف JSON (Choose file)", fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val target = importTargetTpl
                        val ok = if (target != null) {
                            onUpdateTemplateFromJson(target.id, importText, editorTab == 1)
                        } else {
                            onImportNewTemplate(importText, editorTab == 1)
                        }
                        if (ok) {
                            onShowImportDialogChange(false)
                            onImportTargetTplChange(null)
                        } else {
                            importError = "JSON غير صالح — تحقق من النص وحاول مجدداً (invalid style JSON)"
                        }
                    }) { Text("استيراد (Import)") }
                },
                dismissButton = {
                    TextButton(onClick = { onShowImportDialogChange(false); onImportTargetTplChange(null) }) { Text("إلغاء") }
                }
            )
        }

        // Long-press delete confirmation (deletes from the style's own tab collection).
        templateToDelete?.let { tpl ->
            AlertDialog(
                onDismissRequest = { templateToDelete = null },
                title = { Text("حذف القالب") },
                text = { Text("هل تريد حذف القالب \"${tpl.name}\"؟ لا يمكن التراجع عن الحذف. المظهر الحالي يبقى كما هو، كتعديلات غير محفوظة.") },
                confirmButton = {
                    Button(
                        onClick = { onDeleteTemplate(tpl.id, tpl.isFullScreen); templateToDelete = null },
                        colors = ButtonDefaults.buttonColors(containerColor = bento.liveRed)
                    ) { Text("حذف") }
                },
                dismissButton = { TextButton(onClick = { templateToDelete = null }) { Text("إلغاء") } }
            )
        }
}

    /** Cache of Compose FontFamilies built from bundled asset fonts (editor + preview). */
    private val editorFontFamilyCache = ConcurrentHashMap<String, FontFamily>()

    /**
     * Compose [FontFamily] for a bundled Arabic font. Returns [FontFamily.Default]
     * for "System" or unknown families.
     */
    private fun bundledFontFamily(context: Context, family: String): FontFamily {
        if (family == ArabicFonts.SYSTEM) return FontFamily.Default
        val bundled = BundledFonts.find(family)
        if (bundled != null) {
            return editorFontFamilyCache.getOrPut(family) {
                try {
                    FontFamily(android.graphics.Typeface.createFromAsset(context.assets, bundled.asset400))
                } catch (_: Exception) {
                    FontFamily.Default
                }
            }
        }
        // v1.9.1: user-imported font from private storage. Deliberately NOT
        // cached: the file under a name can change (delete + re-import).
        val custom = com.arabicchristianmedia.model.CustomFonts.find(family)
        val file = custom?.let { com.arabicchristianmedia.server.FontStore(context).fileFor(it) }
        return if (file != null) {
            try {
                FontFamily(android.graphics.Typeface.createFromFile(file))
            } catch (_: Exception) {
                FontFamily.Default
            }
        } else {
            FontFamily.Default
        }
    }

    /**
     * v1.9.1: the imported-fonts section shared by both font dropdowns —
     * the user's own fonts (each rendered in its typeface, with a delete
     * button) plus the "import a font" action that opens the file picker.
     */
    @Composable
    private fun CustomFontMenuItems(
        customFonts: List<com.arabicchristianmedia.model.CustomFont>,
        onSelect: (String) -> Unit,
        onImportFont: () -> Unit,
        onDeleteCustomFont: (com.arabicchristianmedia.model.CustomFont) -> Unit,
        closeMenu: () -> Unit
    ) {
        val context = LocalContext.current
        if (customFonts.isNotEmpty()) {
            DropdownMenuItem(
                text = { Text("خطوطي المستوردة (My fonts)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalBentoColors.current.textSecondary) },
                onClick = {},
                enabled = false
            )
            customFonts.forEach { font ->
                val itemFamily = remember(font.name) { bundledFontFamily(context, font.name) }
                DropdownMenuItem(
                    text = { Text(font.name, fontFamily = itemFamily, fontSize = 14.sp) },
                    onClick = { onSelect(font.name); closeMenu() },
                    trailingIcon = {
                        IconButton(onClick = { closeMenu(); onDeleteCustomFont(font) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف الخط (Delete font)",
                                tint = LocalBentoColors.current.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
        DropdownMenuItem(
            text = { Text("استيراد خط… (Import font…)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
            onClick = { closeMenu(); onImportFont() },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = LocalBentoColors.current.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }

    /**
     * Dropdown listing every bundled Arabic font (plus System), each item rendered
     * in its own typeface so the user can see the actual look before choosing.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FontDropdown(
        selected: String,
        onSelect: (String) -> Unit,
        customFonts: List<com.arabicchristianmedia.model.CustomFont>,
        onImportFont: () -> Unit,
        onDeleteCustomFont: (com.arabicchristianmedia.model.CustomFont) -> Unit
    ) {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val selectedFamily = remember(selected) { bundledFontFamily(context, selected) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = ArabicFonts.arabicName(selected)?.let { "$it · $selected" } ?: selected,
                onValueChange = {},
                readOnly = true,
                label = { Text("الخط (Font)", fontSize = 10.sp) },
                textStyle = TextStyle(fontFamily = selectedFamily, fontSize = 14.sp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ArabicFonts.displayNames.forEach { name ->
                    val itemFamily = remember(name) { bundledFontFamily(context, name) }
                    DropdownMenuItem(
                        text = {
                            val arName = ArabicFonts.arabicName(name)
                            if (arName != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(arName, fontFamily = itemFamily, fontSize = 15.sp)
                                    Text(name, fontSize = 11.sp, color = LocalBentoColors.current.textSecondary)
                                }
                            } else {
                                Text(name, fontFamily = itemFamily, fontSize = 14.sp)
                            }
                        },
                        onClick = { onSelect(name); expanded = false }
                    )
                }
                CustomFontMenuItems(
                    customFonts = customFonts,
                    onSelect = onSelect,
                    onImportFont = onImportFont,
                    onDeleteCustomFont = onDeleteCustomFont,
                    closeMenu = { expanded = false }
                )
            }
        }
    }

    /**
     * Dropdown listing every bundled Latin (English) font (plus System), each item
     * rendered in its own typeface. Mirrors [FontDropdown] for the Arabic font.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LatinFontDropdown(
        selected: String,
        onSelect: (String) -> Unit,
        customFonts: List<com.arabicchristianmedia.model.CustomFont>,
        onImportFont: () -> Unit,
        onDeleteCustomFont: (com.arabicchristianmedia.model.CustomFont) -> Unit
    ) {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val selectedFamily = remember(selected) { bundledFontFamily(context, selected) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text("الخط الإنجليزي (English Font)", fontSize = 10.sp) },
                textStyle = TextStyle(fontFamily = selectedFamily, fontSize = 14.sp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                singleLine = true
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                LatinFonts.displayNames.forEach { name ->
                    val itemFamily = remember(name) { bundledFontFamily(context, name) }
                    DropdownMenuItem(
                        text = { Text(name, fontFamily = itemFamily, fontSize = 14.sp) },
                        onClick = { onSelect(name); expanded = false }
                    )
                }
                CustomFontMenuItems(
                    customFonts = customFonts,
                    onSelect = onSelect,
                    onImportFont = onImportFont,
                    onDeleteCustomFont = onDeleteCustomFont,
                    closeMenu = { expanded = false }
                )
            }
        }
    }

    @Composable
    fun StyleToggle(label: String, active: Boolean, onToggle: (Boolean) -> Unit) {    val bento = LocalBentoColors.current
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) bento.primary else bento.surfaceVariant)
                .clickable { onToggle(!active) },
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (active) bento.onPrimary else bento.textPrimary)
        }
    }

    /**
     * v1.9: Collapsible section for the editor. Groups related settings under an
     * expandable header to reduce scrolling and improve organization.
     */
    @Composable
    fun CollapsibleSection(
        title: String,
        initiallyExpanded: Boolean = true,
        syncKey: Any? = null,
        summary: String = "",
        modifier: Modifier = Modifier,
        expanded: Boolean? = null,
        onExpandedChange: ((Boolean) -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        val bento = LocalBentoColors.current
        // v1.9: syncKey re-seeds the expanded state when the template (or its
        // language mode) changes — e.g. English Typography auto-expands when
        // the style has English. Manual toggles are preserved otherwise.
        var internalExpanded by remember(syncKey) { mutableStateOf(initiallyExpanded) }
        // Controlled mode (expanded != null): the caller owns the state
        // (Editor sections, hoisted so tab switches + anchor jumps can
        // drive it). Uncontrolled sites keep the old remember behavior.
        val isExpanded = expanded ?: internalExpanded
        Column(modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (onExpandedChange != null) onExpandedChange(!isExpanded) else internalExpanded = !isExpanded }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Round 2 (#10): the spec-sheet header - an (i) affordance,
                // the title, and (when collapsed) a one-line summary of the
                // section's current state, so the whole style can be audited
                // without opening a single section.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = bento.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                        if (!isExpanded && summary.isNotEmpty()) {
                            Text(summary, fontSize = 11.sp, color = bento.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(
                    if (isExpanded) "▼" else "▶",
                    fontSize = 12.sp,
                    color = bento.textSecondary
                )
            }
            if (isExpanded) {
                content()
            }
            HorizontalDivider(color = bento.borderSubtle, modifier = Modifier.padding(vertical = 4.dp))
        }
    }

// One (i) component for the whole app (item 4; reused by the item-14
// sweep): an info icon that opens one popup style — a dialog with the
// subject as title and the explanation as body.
@Composable
fun InfoPopupButton(infoTitle: String, infoText: String) {
    var showInfo by remember { mutableStateOf(false) }
    val bento = LocalBentoColors.current
    IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Default.Info, infoTitle, tint = bento.primary, modifier = Modifier.size(18.dp))
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(infoTitle) },
            text = { Text(infoText) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("تم") } }
        )
    }
}

@Composable
fun SliderWithLabel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit) {
    val bento = LocalBentoColors.current
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = bento.textSecondary)
            // Opacity-style 0..1 ranges read better as a percentage.
            // Other ranges show 2 decimals for fine control (e.g., motion speed 0.00-3.00).
            val readout = if (range.endInclusive <= 1f) "${(value * 100).toInt()}%"
                          else "%.2f".format(value)
            Text(readout, fontSize = 11.sp, color = bento.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = bento.primary, activeTrackColor = bento.primary))
    }
}

@Composable
fun EditorTabItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bento = LocalBentoColors.current
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) bento.primary else Color.Transparent).clickable { onClick() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) bento.onPrimary else bento.textSecondary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ColorGradePickerRow(
    label: String,
    selectedHex: String,
    onColorSelected: (String) -> Unit,
    recentColors: List<String>,
    onRemoveRecentColor: (String) -> Unit,
    onAddRecentColor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bento = LocalBentoColors.current
    var showGradeDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // v1.7: the label flexes (weight) and ellipsizes; the Color Grade
        // action collapses to an icon on narrow widths so the bilingual label
        // can never be crushed into a letter-by-letter strip under large fonts.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compactAction = maxWidth < 360.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = bento.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(try { Color(android.graphics.Color.parseColor(selectedHex)) } catch (e: Exception) { Color.White })
                            .border(1.dp, bento.border, CircleShape)
                    )
                    Text(
                        text = selectedHex.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.primary,
                        maxLines = 1
                    )

                    if (compactAction) {
                        IconButton(
                            onClick = { showGradeDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = "درجات الألوان (Color Grade)", tint = bento.primary, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = bento.primaryContainer,
                            modifier = Modifier.clickable { showGradeDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = bento.onPrimaryContainer, modifier = Modifier.size(12.dp))
                                Text("درجات الألوان (Color Grade)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = bento.onPrimaryContainer, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Recent Colors row with (x) close button on each chip.
        // 2026-10-28: a Clear (checkerboard) chip is ALWAYS first,
        // so transparency is one tap away without opening the modal.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item(key = "clear") {
                val clearSelected = selectedHex.equals("#00000000", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bento.surfaceVariant,
                    border = BorderStroke(if (clearSelected) 2.dp else 1.dp, if (clearSelected) bento.primary else bento.border),
                    modifier = Modifier.clickable { onColorSelected("#00000000") }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp).clip(CircleShape).border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)) {
                            val cell = size.width / 4f
                            for (row in 0 until 4) {
                                for (col in 0 until 4) {
                                    if ((row + col) % 2 == 0) {
                                        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell), size = androidx.compose.ui.geometry.Size(cell, cell))
                                    } else {
                                        drawRect(Color(0xFFCBD5E1), topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell), size = androidx.compose.ui.geometry.Size(cell, cell))
                                    }
                                }
                            }
                        }
                        Text("شفاف (Clear)", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = bento.textSecondary, maxLines = 1)
                    }
                }
            }
            items(recentColors, key = { it }) { hex ->
                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bento.surfaceVariant,
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) bento.primary else bento.border),
                    modifier = Modifier.clickable { onColorSelected(hex) }
                ) {
                    // v1.7: wider chip, larger swatch, hex readout. The whole
                    // body applies the color; the X is a separate hit target.
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Transparent recent colours show the checkerboard,
                        // not a solid (invisible) swatch.
                        if (hex.equals("#00000000", ignoreCase = true)) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp).clip(CircleShape).border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)) {
                                val cell = size.width / 4f
                                for (row in 0 until 4) {
                                    for (col in 0 until 4) {
                                        if ((row + col) % 2 == 0) {
                                            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell), size = androidx.compose.ui.geometry.Size(cell, cell))
                                        } else {
                                            drawRect(Color(0xFFCBD5E1), topLeft = androidx.compose.ui.geometry.Offset(col * cell, row * cell), size = androidx.compose.ui.geometry.Size(cell, cell))
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray })
                                    .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                            )
                        }
                        Text(
                            text = hex.uppercase().removePrefix("#"),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bento.textSecondary,
                            maxLines = 1
                        )
                        IconButton(
                            onClick = { onRemoveRecentColor(hex) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "حذف", tint = bento.textSecondary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }

    if (showGradeDialog) {
        ColorGradeSpectrumModal(
            currentHex = selectedHex,
            onDismiss = { showGradeDialog = false },
            onSelectColor = { newHex ->
                onColorSelected(newHex)
                onAddRecentColor(newHex)
                showGradeDialog = false
            }
        )
    }
}

@Composable
fun ColorGradeSpectrumModal(
    currentHex: String,
    onDismiss: () -> Unit,
    onSelectColor: (String) -> Unit
) {
    var hexInput by remember { mutableStateOf(currentHex) }
    // Deep-test fix: an invalid hex used to silently become #FFFFFF on
    // confirm. Now it shows an error and keeps the previous colour.
    var hexError by remember { mutableStateOf(false) }
    var hueValue by remember { mutableFloatStateOf(0f) }
    // 2026-10-28 full spectrum: saturation + brightness/value join
    // hue, so white (sat 0 / value 1) and black (value 0) are
    // reachable from the sliders, not only via presets/hex.
    var satValue by remember { mutableFloatStateOf(0.85f) }
    var valValue by remember { mutableFloatStateOf(0.95f) }
    // 2026-10-28: opacity rides the hex alpha channel (#AARRGGBB,
    // which parseColor already accepts). At 100% the hex stays
    // 6-digit, so every existing colour is byte-identical; below
    // 100% the slider rewrites only the alpha prefix, never the RGB.
    var opacityValue by remember { mutableFloatStateOf(1f) }
    fun withOpacity(rgbHex: String): String {
        val rgb = rgbHex.removePrefix("#").takeLast(6)
        return if (opacityValue >= 0.999f) "#$rgb"
               else String.format("#%02X%s", (opacityValue * 255).toInt().coerceIn(0, 255), rgb)
    }
    val bento = LocalBentoColors.current
    // Seed the sliders from the current colour when it parses.
    androidx.compose.runtime.LaunchedEffect(currentHex) {
        try {
            val parsed = android.graphics.Color.parseColor(currentHex)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(parsed, hsv)
            hueValue = hsv[0]; satValue = hsv[1]; valValue = hsv[2]
            opacityValue = android.graphics.Color.alpha(parsed) / 255f
        } catch (e: Exception) {
            // Keep defaults; the hex field stays authoritative.
        }
    }

    val presetGrades = listOf(
        "#FBBF24", "#F59E0B", "#D97706", "#B45309", "#78350F",
        "#EF4444", "#DC2626", "#B91C1C", "#991B1B", "#881337",
        "#A855F7", "#9333EA", "#7E22CE", "#6B21A8", "#581C87",
        "#3B82F6", "#2563EB", "#1D4ED8", "#1E40AF", "#06B6D4",
        "#10B981", "#059669", "#047857", "#0D9488", "#115E59",
        "#FFFFFF", "#F8FAFC", "#F1F5F9", "#E2E8F0", "#CBD5E1",
        "#94A3B8", "#64748B", "#475569", "#1E293B", "#0F172A", "#000000"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Palette, null, tint = bento.primary)
                Text("لوحة درجات الألوان (Color Grade Spectrum)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("اللون المختار:", fontSize = 12.sp, color = bento.textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(try { Color(android.graphics.Color.parseColor(hexInput)) } catch (e: Exception) { Color.White })
                                .border(2.dp, bento.primary, CircleShape)
                        )
                        Text(hexInput.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = bento.textPrimary)
                    }
                }

                Text("درجات الألوان المجهزة (Preset Grades)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                Box(modifier = Modifier.height(130.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 34.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(presetGrades.size) { index ->
                            val hex = presetGrades[index]
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(if (hexInput.equals(hex, true)) 3.dp else 1.dp, if (hexInput.equals(hex, true)) bento.primary else Color.Gray.copy(alpha = 0.3f), CircleShape)
                                    .clickable { hexInput = withOpacity(hex) }
                            )
                        }
                    }
                }

                Text("شريط الطيف الضوئي (Hue Spectrum Cycle)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            )
                        )
                )
                Slider(
                    value = hueValue,
                    onValueChange = { h ->
                        hueValue = h
                        val hsv = floatArrayOf(h, satValue, valValue)
                        val colorInt = android.graphics.Color.HSVToColor(hsv)
                        hexInput = withOpacity(String.format("#%06X", 0xFFFFFF and colorInt))
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = bento.primary, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent)
                )

                Text("التشبع (Saturation)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                Slider(
                    value = satValue,
                    onValueChange = { s ->
                        satValue = s
                        val hsv = floatArrayOf(hueValue, s, valValue)
                        val colorInt = android.graphics.Color.HSVToColor(hsv)
                        hexInput = withOpacity(String.format("#%06X", 0xFFFFFF and colorInt))
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = bento.primary)
                )
                Text("السطوع (Brightness)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                Slider(
                    value = valValue,
                    onValueChange = { v ->
                        valValue = v
                        val hsv = floatArrayOf(hueValue, satValue, v)
                        val colorInt = android.graphics.Color.HSVToColor(hsv)
                        hexInput = withOpacity(String.format("#%06X", 0xFFFFFF and colorInt))
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = bento.primary)
                )

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input -> hexInput = input; hexError = false },
                    label = { Text("رمز اللون (Hex Code e.g. #FBBF24)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = hexError,
                    supportingText = if (hexError) {
                        { Text("رمز لون غير صالح — بقي اللون السابق (Invalid hex — previous colour kept)", color = bento.liveRed) }
                    } else null
                )

                Text("الشفافية (Opacity)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                Slider(
                    value = opacityValue,
                    onValueChange = { o ->
                        opacityValue = o
                        // Re-apply the alpha prefix to the current RGB.
                        val rgb = hexInput.removePrefix("#").takeLast(6)
                        hexInput = if (o >= 0.999f) "#$rgb"
                                   else String.format("#%02X%s", (o * 255).toInt().coerceIn(0, 255), rgb)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = bento.primary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatted = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                    try {
                        android.graphics.Color.parseColor(formatted)
                        hexError = false
                        onSelectColor(formatted)
                    } catch (e: Exception) {
                        // Keep the previous colour and stay in the dialog;
                        // silently switching to white hid a typo.
                        hexError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = bento.primary)
            ) {
                Text("تأكيد الاختيار")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 2026-10-28: Clear is always present in the modal too —
                // it returns the transparent hex, which downstream code
                // already parses as fully transparent.
                TextButton(onClick = { onSelectColor("#00000000") }) { Text("شفاف (Clear)") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}

@Composable
fun FeatureToggleRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    val bento = LocalBentoColors.current
    // Deep-test fix: the whole row toggles, not only the Switch thumb.
    // A tap on the Switch itself still fires only the Switch (the inner
    // clickable wins), so there is no double toggle.
    Row(modifier = modifier.then(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = bento.textPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = bento.primary))
    }
}

/**
 * v1.9.1: one drag handle for the visual box editor — a 28dp touch target
 * with a 14dp dot. [onDrag] receives the drag delta in stage px.
 */
@Composable
fun BoxEditHandle(xPx: Float, yPx: Float, color: Color, onDrag: (Float, Float) -> Unit) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - 14.dp.toPx()).roundToInt(), (yPx - 14.dp.toPx()).roundToInt()) }
            .size(28.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag(dragAmount.x, dragAmount.y)
                }
            }
            .padding(7.dp)
            .background(color, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
    )
}

/**
 * v1.9.1: the visual text-box editor (Ashraf's ask): the auto-fit box drawn
 * over the preview stage in TRUE frame fractions — Lower: width from the
 * horizontal margins, height = boxHeightPercent, anchored at
 * positionBottomPercent; Full: full width, height centered. Dragging an
 * edge edits the same template fields the sliders edit (they stay in sync);
 * the chip reads the box in % of frame and px at 1080p.
 */
@Composable
fun BoxEditOverlay(template: LowerThirdTemplate, stageW: Int, stageH: Int, onTemplateChange: (LowerThirdTemplate) -> Unit) {
    val bento = LocalBentoColors.current
    val density = LocalDensity.current
    val w = stageW.toFloat()
    val h = stageH.toFloat()
    // v1.9.2 (#7): ONE box model for both feeds (Full Show included) —
    // margin insets the sides, Bottom Position anchors the bottom edge,
    // and the height grows upward from there, clamped inside the stage.
    // Full gains the side (margin) and bottom (position) handles it
    // never had; its old centered region + bottom-height handle are gone.
    val left = w * template.horizontalMarginPercent / 100f
    val right = w * (100 - template.horizontalMarginPercent) / 100f
    val boxH = h * template.boxHeightPercent / 100f
    val top = h - h * template.positionBottomPercent / 100f - boxH
    val midX = (left + right) / 2f
    val midY = top + boxH / 2f
    val accent = bento.primary

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { (right - left).toDp() }, with(density) { boxH.toDp() })
            .border(2.dp, accent, RoundedCornerShape(6.dp))
    )

    val wPct = 100 - 2 * template.horizontalMarginPercent
    val label = "Box $wPct% × ${template.boxHeightPercent}%" +
        " · B ${template.positionBottomPercent}%" +
        " — ${wPct * 1920 / 100}×${template.boxHeightPercent * 1080 / 100} px @1080p"
    Box(
        modifier = Modifier
            .offset { IntOffset(8, 8) }
            .background(Color(0xDD0F172A), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }

    BoxEditHandle(left, midY, accent) { dx, _ ->
        val m = (template.horizontalMarginPercent + dx / w * 100f).coerceIn(0f, 30f)
        onTemplateChange(template.copy(horizontalMarginPercent = m.toInt()))
    }
    BoxEditHandle(right, midY, accent) { dx, _ ->
        val m = (template.horizontalMarginPercent - dx / w * 100f).coerceIn(0f, 30f)
        onTemplateChange(template.copy(horizontalMarginPercent = m.toInt()))
    }
    BoxEditHandle(midX, top + boxH, accent) { _, dy ->
        val b = (template.positionBottomPercent - dy / h * 100f).coerceIn(0f, 30f)
        onTemplateChange(template.copy(positionBottomPercent = b.toInt()))
    }
    // Top edge drags the box height; the bottom stays anchored, and the
    // height clamps so the top edge never leaves the stage (the same
    // clamp the renderers apply).
    BoxEditHandle(midX, top, accent) { _, dy ->
        val nh = (template.boxHeightPercent - dy / h * 100f)
            .coerceIn(10f, (100f - template.positionBottomPercent).coerceAtLeast(10f))
        onTemplateChange(template.copy(boxHeightPercent = nh.toInt()))
    }
}

@Composable
fun BroadcastPreviewViewport(template: LowerThirdTemplate, verse: BibleVerse, previewBg: PreviewBackground, boxEditEnabled: Boolean = false, onTemplateChange: (LowerThirdTemplate) -> Unit = {}) {
    val bento = LocalBentoColors.current
    val context = LocalContext.current
    // v1.9.1: stage size (px) for the visual box editor overlay.
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    // v1.9.2 (#7c): the preview fit is computed further below, once the
    // stage metrics exist — it mirrors the renderers' inputs (real content
    // width, real box height) instead of the old fixed 45-chars-per-line /
    // 6-or-10-line budget, which over-shrank verses on air.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // v1.9: cap preview width on wide screens so it doesn't stretch edge-to-edge.
            .widthIn(max = 720.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, bento.border, RoundedCornerShape(14.dp))
            .onSizeChanged { stageSize = it }
    ) {
        // Background Simulation
        if (previewBg == PreviewBackground.CHECKERBOARD_TRANSPARENT) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val squareSize = 20f
                val cols = (size.width / squareSize).toInt() + 1
                val rows = (size.height / squareSize).toInt() + 1
                for (r in 0..rows) {
                    for (c in 0..cols) {
                        val color = if ((r + c) % 2 == 0) Color(0xFFE2E8F0) else Color(0xFFCBD5E1)
                        drawRect(
                            color = color,
                            topLeft = Offset(c * squareSize, r * squareSize),
                            size = Size(squareSize, squareSize)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when (previewBg) {
                            PreviewBackground.SIMULATED_STUDIO_CAMERA -> Color(0xFF0F172A)
                            PreviewBackground.PURE_BLACK -> Color(0xFF000000)
                            PreviewBackground.CHROMA_GREEN -> Color(0xFF00FF00)
                            else -> Color(0xFF0F172A)
                        }
                    )
            )
        }
        
        val isFS = template.isFullScreen
        // Round 2 (#2/#6): the preview now uses the SAME proportional
        // system as the outputs. The stage stands for a 1920px frame and
        // design sizes render at sp x 2 px there, so one design unit is
        // stageWidthDp / 960 dp. Was: fixed sp factors tuned for one
        // 720dp stage (wrong on any other stage size or orientation -
        // the same verse wrapped 2 lines in portrait, 1 in landscape,
        // 3 on air) and a card placed by a 0.4dp-per-% approximation
        // (the verse drew outside the Edit Box outline). dp units also
        // keep the system font-scale setting from inflating the preview.
        val density = LocalDensity.current
        val stageWDp = if (stageSize.width > 0) stageSize.width / density.density else 720f
        val stageHDp = if (stageSize.height > 0) stageSize.height / density.density else 405f
        val stageUnit = stageWDp / 960f
        // v1.9.2 (#7): BOTH feeds place the card by the slider fractions —
        // Full Show no longer zeroes its margin/bottom in the preview.
        val cardSidePad = if (stageSize.width > 0) (stageWDp * template.horizontalMarginPercent / 100f).dp else (template.horizontalMarginPercent * 0.4f).dp
        val cardBottomPad = if (stageSize.height > 0) (stageHDp * template.positionBottomPercent / 100f).dp else (template.positionBottomPercent * 0.4f).dp
        // v1.9.2 (#7c): preview fit on the renderers' own inputs. Content
        // height grows ~quadratically with font scale (halving the font
        // doubles the chars per line AND halves the line height), so the
        // fitting scale is sqrt(available / height-at-scale-1) — the old
        // linear estimate (fixed 45 chars/line, 6/10 line budget) shrank
        // verses far below what NDI/HTTP actually render. The CAP applies
        // first (short verse renders at min(size, cap)); overflow shrinks
        // below the cap toward the same 10sp hard floor.
        val previewFitScale = remember(template, verse, stageSize) {
            if (!template.autoFitText || template.verseFontSize <= 0) 1f
            else {
                val capRatio = (template.autoFitMaxVerseSp.toFloat() / template.verseFontSize).coerceIn(0.05f, 1f)
                val hardFloor = (10f / template.verseFontSize).coerceIn(0.05f, 1f)
                val verseDp = template.verseFontSize * stageUnit
                val contentWDp = (stageWDp * (1f - 2f * template.horizontalMarginPercent / 100f) -
                    (template.cardPaddingLeftDp + template.cardPaddingRightDp) * stageUnit * 0.5f).coerceAtLeast(80f)
                val charsPerLine = (contentWDp / (0.5f * verseDp)).coerceAtLeast(6f)
                val totalChars = verse.arabicText.length +
                    (if (template.languageMode != com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY) verse.englishText.length else 0)
                val linesAt1 = totalChars / charsPerLine
                val heightAt1 = linesAt1 * verseDp * 1.25f + template.referenceFontSize * stageUnit * 1.3f
                val boxHDp = stageHDp * minOf(template.boxHeightPercent, 100 - template.positionBottomPercent).coerceAtLeast(0) / 100f
                val availH = (boxHDp - (template.cardPaddingTopDp + template.cardPaddingBottomDp) * stageUnit * 0.5f).coerceAtLeast(20f)
                when {
                    heightAt1 * capRatio * capRatio <= availH -> capRatio
                    heightAt1 <= 0f -> capRatio
                    else -> kotlin.math.sqrt(availH / heightAt1).coerceIn(hardFloor, capRatio)
                }
            }
        }
        val bgCol = try { Color(android.graphics.Color.parseColor(template.bgColorHex)) } catch (e: Exception) { Color.Black }
        val textCol = try { Color(android.graphics.Color.parseColor(template.textColorHex)) } catch (e: Exception) { Color.White }
        val refCol = try { Color(android.graphics.Color.parseColor(template.referenceColorHex)) } catch (e: Exception) { Color.Yellow }
        val secTextCol = try { Color(android.graphics.Color.parseColor(template.secondaryTextColorHex)) } catch (e: Exception) { Color.Gray }
        val secRefCol = try { Color(android.graphics.Color.parseColor(template.secondaryReferenceColorHex)) } catch (e: Exception) { Color.Gray }
        // Independent text shadow (v1.8): user-controlled thickness (blur),
        // distance (offset) and compass direction — mirrors NDI canvas + HTTP.
        val shadowCol = try { Color(android.graphics.Color.parseColor(template.textShadowColorHex)) } catch (e: Exception) { Color.Black }
        val shadowAngleRad = Math.toRadians(template.textShadowAngleDeg.toDouble())
        val previewShadow = if (template.textShadowEnabled) Shadow(
            color = shadowCol,
            offset = Offset(
                (kotlin.math.cos(shadowAngleRad) * template.textShadowOffsetDp * stageUnit * 0.5f).toFloat(),
                (kotlin.math.sin(shadowAngleRad) * template.textShadowOffsetDp * stageUnit * 0.5f).toFloat()
            ),
            blurRadius = template.textShadowBlurDp * stageUnit * 0.5f
        ) else null
        // Bundled Arabic font for a true WYSIWYG preview (same TTFs the NDI canvas uses).
        val previewFontFamily = remember(template.fontFamily) { bundledFontFamily(context, template.fontFamily) }
        // Independent card glow (v1.6): colored halo behind the card, only where a card exists.
        // Skipped for transparent styles and full-bleed Full Show — mirrors the
        // NDI canvas renderer and the HTML overlay (which force no glow there).
        val glowCol = try { Color(android.graphics.Color.parseColor(template.cardGlowColorHex)) } catch (e: Exception) { Color.Black }
        val hasVisibleCard = !template.isPureTransparentBackground &&
                template.style != com.arabicchristianmedia.model.TemplateStyle.TRANSPARENT_OUTLINE
        // v1.9.2 (#3): visible-EDGE rule, same as NDI/HTTP — the shadow
        // shows whenever the card is inset from the frame on any side
        // (Full Show included), not never-on-Full as before.
        val cardHasVisibleEdge = template.horizontalMarginPercent > 0 || template.positionBottomPercent > 0 ||
            ((isFS || template.autoFitText) && template.boxHeightPercent < 100)
        val cardGlowModifier = if (template.cardGlowEnabled && cardHasVisibleEdge && hasVisibleCard) {
            Modifier.drawBehind {
                // Soft layered halo in the user's glow color (drawn outside the card bounds).
                drawRoundRect(
                    color = glowCol.copy(alpha = 0.30f),
                    topLeft = Offset(-22f, -22f),
                    size = Size(size.width + 44f, size.height + 44f),
                    cornerRadius = CornerRadius(28f, 28f)
                )
                drawRoundRect(
                    color = glowCol.copy(alpha = 0.15f),
                    topLeft = Offset(-40f, -40f),
                    size = Size(size.width + 80f, size.height + 80f),
                    cornerRadius = CornerRadius(40f, 40f)
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = cardSidePad, end = cardSidePad, bottom = cardBottomPad),
            // v1.9.2 (#7): bottom-anchored for BOTH feeds — the box hangs
            // from its Bottom Position like the renderers' box.
            contentAlignment = Alignment.BottomCenter
        ) {
            val cardBg = when {
                template.isPureTransparentBackground || template.style == TemplateStyle.TRANSPARENT_OUTLINE -> Color(0xFF1E293B).copy(alpha = 0.45f)
                else -> bgCol.copy(alpha = template.bgOpacity)
            }

            Surface(
                // v1.9.2 (#7): the corner radius applies to Full Show too.
                shape = RoundedCornerShape((template.cornerRadiusDp * stageUnit).dp),
                color = cardBg,
                border = if (template.showAccentBorder) BorderStroke(1.5.dp, try { Color(android.graphics.Color.parseColor(template.accentColorHex)) } catch (e: Exception) { Color.Yellow }) else null,
                shadowElevation = if (template.cardGlowEnabled && cardHasVisibleEdge && hasVisibleCard) 12.dp else 0.dp,
                // Round 2 (#2): no extra fixed inset - the card edges now
                // coincide with the box outline's true fractions.
                // v1.9.2 (#7): the DRAWN card is the slider box for Full
                // (always) and for Lower under auto-fit — an explicit stage
                // height, bottom-anchored by the outer Box. Lower with
                // auto-fit OFF keeps its content-hugging card.
                modifier = cardGlowModifier.then(
                    if (isFS || template.autoFitText)
                        // Clamped like the renderers: the box top never
                        // leaves the frame (height + bottom <= 100%).
                        Modifier.fillMaxWidth().height((stageHDp * minOf(template.boxHeightPercent, 100 - template.positionBottomPercent).coerceAtLeast(0) / 100f).dp)
                    else Modifier.fillMaxWidth()
                )
            ) {
                Column(
                    // v1.9: preview honors the style's per-side card padding
                    // (was hardcoded 14/10dp), like NDI and HTTP.
                    modifier = (if (isFS) Modifier.fillMaxSize() else Modifier).padding(
                        start = (template.cardPaddingLeftDp * stageUnit * 0.5f).dp,
                        end = (template.cardPaddingRightDp * stageUnit * 0.5f).dp,
                        top = (template.cardPaddingTopDp * stageUnit * 0.5f).dp,
                        bottom = (template.cardPaddingBottomDp * stageUnit * 0.5f).dp
                    ),
                    verticalArrangement = if (isFS) Arrangement.Center else Arrangement.Top,
                    horizontalAlignment = when (template.alignment) {
                        BroadcastTextAlignment.CENTER -> Alignment.CenterHorizontally
                        BroadcastTextAlignment.LEFT -> Alignment.Start
                        else -> Alignment.End
                    }
                ) {
                    // Arabic Citation & user Emblem (v1.7: free emoji/symbol)
                    // RTL row: the emblem sits before (right of) the Arabic citation,
                    // matching NDI and HTTP.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (template.emblem.isNotEmpty()) {
                            Text(
                                text = template.emblem,
                                // Sizes are computed in dp (stage-proportional, immune to the
                                // system font scale); Text wants TextUnit, so convert via
                                // density - dp.toSp() cancels fontScale back out.
                                fontSize = with(density) { (template.referenceFontSize * previewFitScale * stageUnit).dp.toSp() },
                                // v1.8: emblem uses citation styling (was accent).
                                color = refCol
                            )
                        }
                        Text(
                            text = if (template.languageMode != com.arabicchristianmedia.model.LanguageMode.ENGLISH_ONLY)
                                verse.getFormattedArabicCitation(template.useEasternArabicNumerals)
                            else verse.getFormattedEnglishCitation(),
                            fontSize = with(density) { (template.referenceFontSize * previewFitScale * stageUnit).dp.toSp() },
                            fontWeight = if (template.referenceIsBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (template.referenceIsItalic) FontStyle.Italic else FontStyle.Normal,
                            fontFamily = previewFontFamily,
                            color = refCol,
                            style = TextStyle(shadow = previewShadow)
                        )
                    }
                    }

                    // v1.8: Arabic verse hidden in ENGLISH_ONLY mode.
                    if (template.languageMode != com.arabicchristianmedia.model.LanguageMode.ENGLISH_ONLY) {
                    // Arabic Verse Text
                    Text(
                        text = verse.arabicText,
                        fontSize = with(density) { (template.verseFontSize * previewFitScale * stageUnit).dp.toSp() },
                        lineHeight = with(density) { (template.verseFontSize * 1.31f * previewFitScale * stageUnit).dp.toSp() },
                        fontWeight = if (template.verseIsBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (template.verseIsItalic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = previewFontFamily,
                        color = textCol,
                        style = TextStyle(shadow = previewShadow),
                        textAlign = when (template.alignment) {
                            BroadcastTextAlignment.CENTER -> TextAlign.Center
                            BroadcastTextAlignment.LEFT -> TextAlign.Left
                            else -> TextAlign.Right
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    }
                    
                    // v1.8: three-way language mode; English left unless centered.
                    if (template.languageMode != com.arabicchristianmedia.model.LanguageMode.ARABIC_ONLY) {
                        Spacer(Modifier.height((template.bilingualSpacing * stageUnit * 0.5f).dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = verse.englishText,
                                fontSize = with(density) { (template.secondaryVerseFontSize * 0.95f * previewFitScale * stageUnit).dp.toSp() },
                                fontWeight = if (template.secondaryVerseIsBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (template.secondaryVerseIsItalic) FontStyle.Italic else FontStyle.Normal,
                                color = secTextCol,
                                style = TextStyle(shadow = previewShadow),
                                textAlign = when (template.alignment) {
                                    BroadcastTextAlignment.CENTER -> TextAlign.Center
                                    else -> TextAlign.Left
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            // v1.9: English citation on its own line (matches NDI + HTTP).
                            // BOTH mode only — in ENGLISH_ONLY it's already in the main citation row.
                            if (template.languageMode == com.arabicchristianmedia.model.LanguageMode.BOTH) {
                                Text(
                                    text = "(${verse.getFormattedEnglishCitation()})",
                                    fontSize = with(density) { (template.secondaryReferenceFontSize * 0.9f * previewFitScale * stageUnit).dp.toSp() },
                                    fontWeight = if (template.secondaryReferenceIsBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (template.secondaryReferenceIsItalic) FontStyle.Italic else FontStyle.Normal,
                                    fontFamily = previewFontFamily,
                                    color = secRefCol,
                                    style = TextStyle(shadow = previewShadow),
                                    textAlign = when (template.alignment) {
                                        BroadcastTextAlignment.CENTER -> TextAlign.Center
                                        else -> TextAlign.Left
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // v1.9.1: visual text-box editor — outline + drag handles + readout,
        // drawn over the stage when enabled (Editor's Edit Box toggle).
        if (boxEditEnabled && stageSize.width > 0 && stageSize.height > 0) {
            BoxEditOverlay(template, stageSize.width, stageSize.height, onTemplateChange)
        }
    }
}

@Preview(name = "Lower Third Design", showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
fun PreviewLowerThirdDesign() {
    val sampleTemplate = TemplateRepository.DEFAULT_TEMPLATES[1]
    val sampleVerse = BibleVerse(
        id = "jhn_3_16", bookId = "jhn", bookArabicName = "إنجيل يوحنا", bookEnglishName = "John", chapter = 3, verse = 16,
        arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ، لِكَيْ لاَ يَهْلِكَ كُلُّ مَنْ يُؤْمِنُ بِهِ، بَلْ تَكُونُ لَهُ الْحَيَاةُ الأَبَدِيَّةُ.",
        englishText = "For God so loved the world that He gave His only begotten Son, that whoever believes in Him should not perish but have everlasting life."
    )
    
    MyApplicationTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
            BroadcastPreviewViewport(
                template = sampleTemplate.copy(languageMode = com.arabicchristianmedia.model.LanguageMode.BOTH),
                verse = sampleVerse,
                previewBg = PreviewBackground.SIMULATED_STUDIO_CAMERA
            )
        }
    }
}
