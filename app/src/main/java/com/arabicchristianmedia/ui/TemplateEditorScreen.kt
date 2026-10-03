package com.arabicchristianmedia.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.model.*
import com.arabicchristianmedia.server.VideoStore
import com.arabicchristianmedia.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class PreviewBackground(val displayNameAr: String) {
    CHECKERBOARD_TRANSPARENT("شفاف مفرغ (100% Transparent Alpha)"),
    SIMULATED_STUDIO_CAMERA("استوديو داكن (Studio Dark)"),
    PURE_BLACK("أسود معتم (Pure Black)"),
    CHROMA_GREEN("كروما خضراء (Chroma Green)")
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
    onResetDefaults: (Boolean) -> Unit,
    onDeleteTemplate: (String, Boolean) -> Unit,
    onExportTemplate: (String) -> String?,
    onUpdateTemplateFromJson: (String, String, Boolean) -> Boolean,
    onImportNewTemplate: (String, Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    var editorTab by remember { mutableIntStateOf(0) } // 0: Lower Third, 1: Full Show
    var showSaveDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<LowerThirdTemplate?>(null) }
    var styleMenuTpl by remember { mutableStateOf<LowerThirdTemplate?>(null) } // long-press actions menu
    var importTargetTpl by remember { mutableStateOf<LowerThirdTemplate?>(null) } // null = import as new
    var showImportDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var previewBg by remember { mutableStateOf(PreviewBackground.SIMULATED_STUDIO_CAMERA) }
    val context = LocalContext.current

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

    val displayVerse = activeVerse ?: BibleVerse(
        id = "sample", bookId = "jhn", bookArabicName = "إنجيل يوحنا", bookEnglishName = "John", chapter = 3, verse = 16,
        arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ، لِكَيْ لاَ يَهْلِكَ كُلُّ مَنْ يُؤْمِنُ بِهِ، بَلْ تَكُونُ لَهُ الْحَيَاةُ الأَبَدِيَّةُ.",
        englishText = "For God so loved the world that He gave His only begotten Son, that whoever believes in Him should not perish but have everlasting life."
    )

    Column(modifier = modifier.fillMaxSize().background(bento.bg).padding(14.dp).verticalScroll(rememberScrollState())) {
        // 1. Header & Quick Actions (responsive: compact icon actions on narrow
        // screens so the title never crushes the buttons under large fonts)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compactHeader = maxWidth < 420.dp
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("محرر القوالب والتصاميم (Template Editor)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("تخصيص كامل وشامل لطبقات البث والعرض المباشر", fontSize = 12.sp, color = bento.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onResetDefaults(editorTab == 1) }) { Icon(Icons.Default.Refresh, "إعادة تعيين", tint = bento.primary) }
                    if (compactHeader) {
                        IconButton(onClick = { newTemplateName = "${editingTemplate.name} النسخة"; showSaveDialog = true }) {
                            Icon(Icons.Default.Add, "حفظ كقالب جديد", tint = bento.primary)
                        }
                    } else {
                        Button(onClick = { newTemplateName = "${editingTemplate.name} النسخة"; showSaveDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = bento.primary)) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("حفظ كقالب جديد", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 2. Tab Switcher: Lower Third vs Full Show (Projector)
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bento.surfaceVariant).padding(4.dp)) {
            EditorTabItem("طبقة البث السفلى (Lower Third)", editorTab == 0, { editorTab = 0 }, Modifier.weight(1f))
            EditorTabItem("العرض الكامل (Full Show Projector)", editorTab == 1, { editorTab = 1 }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        // 2b. Workflow tips: how the style system works (each tab is independent).
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = bento.primaryContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("كيف يعمل المحرر (How it works)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = bento.onPrimaryContainer)
                val tips = listOf(
                    "اختر نمطاً من القائمة لتطبيقه فوراً على المعاينة والبث.",
                    "أي تعديل يظهر مباشرة ويُحفظ تلقائياً — لا يوجد زر حفظ.",
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

        Spacer(Modifier.height(12.dp))

        // 3. Compact Live Preview Card with Functional Background Modes
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
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
                BroadcastPreviewViewport(editingTemplate, displayVerse, previewBg)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 4. Style Selector (per-tab collection; highlight = selected style id, NOT the working template id)
        val highlightedId = if (editorTab == 0) highlightedLowerStyleId else highlightedShowStyleId
        val tabIsDirty = if (editorTab == 0) lowerWorkingDirty else showWorkingDirty
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("اختر النمط (Select Style)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bento.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tabIsDirty) {
                    Surface(shape = RoundedCornerShape(10.dp), color = bento.primaryContainer) {
                        Text("معدّل (Modified)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bento.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                TextButton(onClick = { importTargetTpl = null; showImportDialog = true }) {
                    Text("استيراد (Import)", fontSize = 11.sp)
                }
            }
        }
        Text("اضغط مطولاً على أي نمط للتصدير / التحديث بالاستيراد / الحذف • التعديل بدون حفظ يمسح التمييز", fontSize = 10.sp, color = bento.textSecondary, modifier = Modifier.padding(top = 2.dp))
        LazyRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Strict partition: each tab shows ONLY its own collection. An empty tab
            // shows an empty-state hint — never the other tab's styles.
            val filteredTemplates = if (editorTab == 1) templates.filter { it.isFullScreen } else templates.filter { !it.isFullScreen }
            if (filteredTemplates.isEmpty()) {
                item {
                    Text(
                        if (editorTab == 1)
                            "لا توجد أنماط للعرض الكامل بعد — عدّل ثم «حفظ كقالب جديد» لإنشاء أول نمط."
                        else
                            "لا توجد أنماط للطبقة السفلى بعد — عدّل ثم «حفظ كقالب جديد» لإنشاء أول نمط.",
                        fontSize = 11.sp,
                        color = bento.textSecondary,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                    )
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
                        Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp)).background(try { Color(android.graphics.Color.parseColor(tpl.bgColorHex)) } catch (e: Exception) { Color.Black }).border(1.dp, bento.border, RoundedCornerShape(8.dp)))
                        Spacer(Modifier.height(6.dp))
                        Text(tpl.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 5. Detailed Independent Configuration Controls
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                
                // Style Preset Selector
                Text("المظهر والتأطير (Design Style)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TemplateStyle.entries) { style ->
                        FilterChip(
                            selected = editingTemplate.style == style,
                            onClick = { onUpdate(editingTemplate.copy(style = style)) },
                            label = { Text(style.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                // Alignment
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("محاذاة النص (Alignment)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            BroadcastTextAlignment.entries.forEach { align ->
                                FilterChip(selected = editingTemplate.alignment == align, onClick = { onUpdate(editingTemplate.copy(alignment = align)) }, label = { Text(align.displayName, fontSize = 10.sp) })
                            }
                        }
                    }
                }

                HorizontalDivider(color = bento.borderSubtle)

                // Primary Typography (Arabic Verse & Citation)
                Text("تنسيق الخط العربي (Arabic Typography)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                FontDropdown(
                    selected = editingTemplate.fontFamily,
                    onSelect = { onUpdate(editingTemplate.copy(fontFamily = it)) }
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("الآية:", fontSize = 11.sp, color = bento.textSecondary)
                    StyleToggle("B", editingTemplate.verseIsBold) { onUpdate(editingTemplate.copy(verseIsBold = it)) }
                    StyleToggle("I", editingTemplate.verseIsItalic) { onUpdate(editingTemplate.copy(verseIsItalic = it)) }
                }
                ColorGradePickerRow("لون النص العربي (Verse Color)", editingTemplate.textColorHex, { onUpdate(editingTemplate.copy(textColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                SliderWithLabel("حجم النص العربي", editingTemplate.verseFontSize.toFloat(), 10f..180f) { onUpdate(editingTemplate.copy(verseFontSize = it.toInt())) }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("الشاهد:", fontSize = 11.sp, color = bento.textSecondary)
                    StyleToggle("B", editingTemplate.referenceIsBold) { onUpdate(editingTemplate.copy(referenceIsBold = it)) }
                    StyleToggle("I", editingTemplate.referenceIsItalic) { onUpdate(editingTemplate.copy(referenceIsItalic = it)) }
                }
                ColorGradePickerRow("لون الشاهد العربي (Citation Color)", editingTemplate.referenceColorHex, { onUpdate(editingTemplate.copy(referenceColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                SliderWithLabel("حجم الشاهد العربي", editingTemplate.referenceFontSize.toFloat(), 10f..120f) { onUpdate(editingTemplate.copy(referenceFontSize = it.toInt())) }

                // Bilingual Typography & English Citation Options
                HorizontalDivider(color = bento.borderSubtle)
                FeatureToggleRow("تفعيل النص الإنجليزي (Bilingual Mode)", editingTemplate.bilingualMode) { onUpdate(editingTemplate.copy(bilingualMode = it)) }
                if (editingTemplate.bilingualMode) {
                    Text("تنسيق الخط الإنجليزي (English Typography)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                    
                    // English Verse Controls
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("الآية الإنجليزية:", fontSize = 11.sp, color = bento.textSecondary)
                        StyleToggle("B", editingTemplate.secondaryVerseIsBold) { onUpdate(editingTemplate.copy(secondaryVerseIsBold = it)) }
                        StyleToggle("I", editingTemplate.secondaryVerseIsItalic) { onUpdate(editingTemplate.copy(secondaryVerseIsItalic = it)) }
                    }
                    ColorGradePickerRow("لون النص الإنجليزي (English Verse Color)", editingTemplate.secondaryTextColorHex, { onUpdate(editingTemplate.copy(secondaryTextColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                    SliderWithLabel("حجم النص الإنجليزي", editingTemplate.secondaryVerseFontSize.toFloat(), 8f..120f) { onUpdate(editingTemplate.copy(secondaryVerseFontSize = it.toInt())) }

                    // English Citation Controls
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("الشاهد الإنجليزي:", fontSize = 11.sp, color = bento.textSecondary)
                        StyleToggle("B", editingTemplate.secondaryReferenceIsBold) { onUpdate(editingTemplate.copy(secondaryReferenceIsBold = it)) }
                        StyleToggle("I", editingTemplate.secondaryReferenceIsItalic) { onUpdate(editingTemplate.copy(secondaryReferenceIsItalic = it)) }
                    }
                    ColorGradePickerRow("لون الشاهد الإنجليزي (English Citation Color)", editingTemplate.secondaryReferenceColorHex, { onUpdate(editingTemplate.copy(secondaryReferenceColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                    SliderWithLabel("حجم الشاهد الإنجليزي", editingTemplate.secondaryReferenceFontSize.toFloat(), 8f..100f) { onUpdate(editingTemplate.copy(secondaryReferenceFontSize = it.toInt())) }

                    SliderWithLabel("المسافة الفاصلة بين اللغتين", editingTemplate.bilingualSpacing.toFloat(), 0f..150f) { onUpdate(editingTemplate.copy(bilingualSpacing = it.toInt())) }
                }

                // Background, Transparency & Accents
                HorizontalDivider(color = bento.borderSubtle)
                Text("الخلفية والمؤثرات البصرية (Background & Effects)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                
                FeatureToggleRow("خلفية شفافة تماماً 100% Alpha", editingTemplate.isPureTransparentBackground) { onUpdate(editingTemplate.copy(isPureTransparentBackground = it)) }
                
                if (!editingTemplate.isPureTransparentBackground) {
                    SliderWithLabel("درجة شفافية الكارت (Background Opacity)", editingTemplate.bgOpacity, 0f..1f) { onUpdate(editingTemplate.copy(bgOpacity = it)) }
                    ColorGradePickerRow("لون كارت الخلفية (Card Color)", editingTemplate.bgColorHex, { onUpdate(editingTemplate.copy(bgColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                }

                ColorGradePickerRow("لون إطار التمييز (Accent Color)", editingTemplate.accentColorHex, { onUpdate(editingTemplate.copy(accentColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // v1.7: free emblem — the user types any emoji/symbol with the
                    // system keyboard. Empty = no emblem.
                    OutlinedTextField(
                        value = editingTemplate.emblem,
                        onValueChange = { onUpdate(editingTemplate.copy(emblem = it)) },
                        label = { Text("رمز / إيموجي (Emblem) — اتركه فارغاً للإخفاء", fontSize = 10.sp) },
                        placeholder = { Text("✝", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureToggleRow("إظهار الحدود الملونة", editingTemplate.showAccentBorder) { onUpdate(editingTemplate.copy(showAccentBorder = it)) }
                }
                // Independent text shadow and card glow (v1.6). Card glow only
                // applies where a card exists — not on full-bleed Full Show.
                Text("الظل والتوهج (Shadow & Glow)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                FeatureToggleRow("ظل النص (Text Shadow)", editingTemplate.textShadowEnabled) { onUpdate(editingTemplate.copy(textShadowEnabled = it)) }
                if (editingTemplate.textShadowEnabled) {
                    ColorGradePickerRow("لون ظل النص (Text Shadow Color)", editingTemplate.textShadowColorHex, { onUpdate(editingTemplate.copy(textShadowColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                }
                if (!editingTemplate.isFullScreen) {
                    FeatureToggleRow("توهج الكارت (Card Glow)", editingTemplate.cardGlowEnabled) { onUpdate(editingTemplate.copy(cardGlowEnabled = it)) }
                    if (editingTemplate.cardGlowEnabled) {
                        ColorGradePickerRow("لون توهج الكارت (Card Glow Color)", editingTemplate.cardGlowColorHex, { onUpdate(editingTemplate.copy(cardGlowColorHex = it)) }, recentColors, onRemoveRecentColor, onAddRecentColor)
                    }
                }

                // Animated Motion Backgrounds
                Text("الخلفيات المتحركة (Motion Video Backgrounds)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AnimatedBackgroundType.entries) { type ->
                        FilterChip(selected = editingTemplate.animatedBackground == type, onClick = { onUpdate(editingTemplate.copy(animatedBackground = type)) }, label = { Text(type.displayNameAr, fontSize = 10.sp) })
                    }
                }
                if (editingTemplate.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO) {
                    // v1.7: opacity slider works ONLY when the custom-video flag is ON.
                    SliderWithLabel(
                        label = "شفافية الفيديو (Video Opacity)",
                        value = editingTemplate.animatedBackgroundOpacity,
                        range = 0f..1f,
                        onValueChange = { onUpdate(editingTemplate.copy(animatedBackgroundOpacity = it)) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (editingTemplate.customVideoId.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "✓ فيديو مخصص محدد (Custom video set)",
                                fontSize = 11.sp,
                                color = bento.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                onUpdate(editingTemplate.copy(customVideoId = "", animatedBackground = AnimatedBackgroundType.NONE))
                            }) {
                                Text("إزالة (Remove)", fontSize = 11.sp, color = bento.primary)
                            }
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
                }
            }
        }
        Spacer(Modifier.height(100.dp))
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
                        onClick = { importTargetTpl = tpl; showImportDialog = true; styleMenuTpl = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تحديث باستيراد JSON (Update from import)", fontSize = 13.sp) }
                    TextButton(
                        onClick = { templateToDelete = tpl; styleMenuTpl = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("حذف (Delete)", fontSize = 13.sp, color = bento.liveRed) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { styleMenuTpl = null }) { Text("إلغاء") } }
        )
    }

    // Import dialog: paste JSON or pick a .json file. Target null = new style,
    // otherwise the target style is updated in place (id preserved).
    if (showImportDialog) {
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
        val targetName = importTargetTpl?.name
        AlertDialog(
            onDismissRequest = { showImportDialog = false; importTargetTpl = null },
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
                        showImportDialog = false
                        importTargetTpl = null
                    } else {
                        importError = "JSON غير صالح — تحقق من النص وحاول مجدداً (invalid style JSON)"
                    }
                }) { Text("استيراد (Import)") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; importTargetTpl = null }) { Text("إلغاء") }
            }
        )
    }

    // Long-press delete confirmation (deletes from the style's own tab collection).
    templateToDelete?.let { tpl ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("حذف القالب") },
            text = { Text("هل تريد حذف القالب \"${tpl.name}\"؟ لا يمكن التراجع عن الحذف.") },
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
    return editorFontFamilyCache.getOrPut(family) {
        val bundled = ArabicFonts.find(family) ?: return FontFamily.Default
        try {
            FontFamily(android.graphics.Typeface.createFromAsset(context.assets, bundled.asset400))
        } catch (_: Exception) {
            FontFamily.Default
        }
    }
}

/**
 * Dropdown listing every bundled Arabic font (plus System), each item rendered
 * in its own typeface so the user can see the actual look before choosing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontDropdown(selected: String, onSelect: (String) -> Unit) {
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
                    text = { Text(name, fontFamily = itemFamily, fontSize = 14.sp) },
                    onClick = { onSelect(name); expanded = false }
                )
            }
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
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (active) Color.White else bento.textPrimary)
    }
}

@Composable
fun SliderWithLabel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val bento = LocalBentoColors.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = bento.textSecondary)
            // Opacity-style 0..1 ranges read better as a percentage.
            val readout = if (range.endInclusive <= 1f) "${(value * 100).toInt()}%" else "${value.toInt()}"
            Text(readout, fontSize = 11.sp, color = bento.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = bento.primary, activeTrackColor = bento.primary))
    }
}

@Composable
fun EditorTabItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bento = LocalBentoColors.current
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) bento.primary else Color.Transparent).clickable { onClick() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else bento.textSecondary, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
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

        // Recent Colors row with (x) close button on each chip
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
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
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray })
                                .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                        )
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
    var hueValue by remember { mutableFloatStateOf(0f) }
    val bento = LocalBentoColors.current

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
                                    .clickable { hexInput = hex }
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
                        val hsv = floatArrayOf(h, 0.85f, 0.95f)
                        val colorInt = android.graphics.Color.HSVToColor(hsv)
                        hexInput = String.format("#%06X", 0xFFFFFF and colorInt)
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = bento.primary, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent)
                )

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input -> hexInput = input },
                    label = { Text("رمز اللون (Hex Code e.g. #FBBF24)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatted = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                    try {
                        android.graphics.Color.parseColor(formatted)
                        onSelectColor(formatted)
                    } catch (e: Exception) {
                        onSelectColor("#FFFFFF")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = bento.primary)
            ) {
                Text("تأكيد الاختيار")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun FeatureToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val bento = LocalBentoColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = bento.textPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = bento.primary))
    }
}

@Composable
fun BroadcastPreviewViewport(template: LowerThirdTemplate, verse: BibleVerse, previewBg: PreviewBackground) {
    val bento = LocalBentoColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, bento.border, RoundedCornerShape(14.dp))
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
                (kotlin.math.cos(shadowAngleRad) * template.textShadowOffsetDp).toFloat(),
                (kotlin.math.sin(shadowAngleRad) * template.textShadowOffsetDp).toFloat()
            ),
            blurRadius = template.textShadowBlurDp
        ) else null
        // Bundled Arabic font for a true WYSIWYG preview (same TTFs the NDI canvas uses).
        val previewFontFamily = remember(template.fontFamily) { bundledFontFamily(context, template.fontFamily) }
        // Independent card glow (v1.6): colored halo behind the card, only where a card exists.
        // Skipped for transparent styles and full-bleed Full Show — mirrors the
        // NDI canvas renderer and the HTML overlay (which force no glow there).
        val glowCol = try { Color(android.graphics.Color.parseColor(template.cardGlowColorHex)) } catch (e: Exception) { Color.Black }
        val hasVisibleCard = !template.isPureTransparentBackground &&
                template.style != com.arabicchristianmedia.model.TemplateStyle.TRANSPARENT_OUTLINE
        val cardGlowModifier = if (template.cardGlowEnabled && !isFS && hasVisibleCard) {
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
                .padding(
                    start = if (isFS) 0.dp else (template.horizontalMarginPercent * 0.4f).dp,
                    end = if (isFS) 0.dp else (template.horizontalMarginPercent * 0.4f).dp,
                    bottom = if (isFS) 0.dp else (template.positionBottomPercent * 0.4f).dp
                ),
            contentAlignment = if (isFS) Alignment.Center else Alignment.BottomCenter
        ) {
            val cardBg = when {
                template.isPureTransparentBackground || template.style == TemplateStyle.TRANSPARENT_OUTLINE -> Color(0xFF1E293B).copy(alpha = 0.45f)
                else -> bgCol.copy(alpha = template.bgOpacity.coerceAtLeast(0.3f))
            }

            Surface(
                shape = RoundedCornerShape(if (isFS) 0.dp else (template.cornerRadiusDp * 0.6f).dp),
                color = cardBg,
                border = if (template.showAccentBorder) BorderStroke(1.5.dp, try { Color(android.graphics.Color.parseColor(template.accentColorHex)) } catch (e: Exception) { Color.Yellow }) else BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                shadowElevation = if (template.cardGlowEnabled && !isFS && hasVisibleCard) 12.dp else 0.dp,
                modifier = cardGlowModifier.then(if (isFS) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            ) {
                Column(
                    modifier = if (isFS) Modifier.fillMaxSize().padding(14.dp) else Modifier.padding(10.dp),
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
                                fontSize = 14.sp,
                                // v1.8: emblem uses citation styling (was accent).
                                color = refCol
                            )
                        }
                        Text(
                            text = if (template.languageMode != com.arabicchristianmedia.model.LanguageMode.ENGLISH_ONLY)
                                verse.getFormattedArabicCitation(template.useEasternArabicNumerals)
                            else verse.getFormattedEnglishCitation(),
                            fontSize = (template.referenceFontSize * 0.42f).sp,
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
                        fontSize = (template.verseFontSize * 0.42f).sp,
                        lineHeight = (template.verseFontSize * 0.55f).sp,
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
                        Spacer(Modifier.height((template.bilingualSpacing * 0.2f).dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = buildAnnotatedString {
                                    append(verse.englishText)
                                    withStyle(
                                        SpanStyle(
                                            color = secRefCol,
                                            fontSize = (template.secondaryReferenceFontSize * 0.38f).sp,
                                            fontWeight = if (template.secondaryReferenceIsBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (template.secondaryReferenceIsItalic) FontStyle.Italic else FontStyle.Normal
                                        )
                                    ) {
                                        append(" (${verse.getFormattedEnglishCitation()})")
                                    }
                                },
                                fontSize = (template.secondaryVerseFontSize * 0.4f).sp,
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
                        }
                    }
                }
            }
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
