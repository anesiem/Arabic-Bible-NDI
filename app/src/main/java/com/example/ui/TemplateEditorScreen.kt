package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArabicTextFormatter
import com.example.data.TemplateRepository
import com.example.model.*
import com.example.ui.theme.*

enum class PreviewBackground(val displayNameAr: String) {
    CHECKERBOARD_TRANSPARENT("شفاف مفرغ (100% Transparent Alpha)"),
    SIMULATED_STUDIO_CAMERA("استوديو داكن (Studio Dark)"),
    PURE_BLACK("أسود معتم (Pure Black)"),
    CHROMA_GREEN("كروما خضراء (Chroma Green)")
}

@Composable
fun TemplateEditorScreen(
    currentTemplate: LowerThirdTemplate,
    activeShowTemplate: LowerThirdTemplate,
    templates: List<LowerThirdTemplate>,
    activeVerse: BibleVerse?,
    onSelectTemplate: (LowerThirdTemplate) -> Unit,
    onUpdateTemplate: (LowerThirdTemplate) -> Unit,
    onUpdateShowTemplate: (LowerThirdTemplate) -> Unit,
    onSaveAsNew: (String, LowerThirdTemplate) -> Unit,
    onResetDefaults: () -> Unit,
    onToggleNdiSource: (String) -> Unit,
    ndiLowerThirdActive: Boolean,
    ndiFullShowActive: Boolean,
    modifier: Modifier = Modifier
) {
    var editorTab by remember { mutableIntStateOf(0) } // 0: Lower Third, 1: Full Show
    var showSaveDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var previewBg by remember { mutableStateOf(PreviewBackground.SIMULATED_STUDIO_CAMERA) }

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

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onUpdate(editingTemplate.copy(customVideoUrl = it.toString(), animatedBackground = AnimatedBackgroundType.CUSTOM_VIDEO)) }
    }

    val displayVerse = activeVerse ?: BibleVerse(
        id = "sample", bookId = "jhn", bookArabicName = "إنجيل يوحنا", bookEnglishName = "John", chapter = 3, verse = 16,
        arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ، لِكَيْ لاَ يَهْلِكَ كُلُّ مَنْ يُؤْمِنُ بِهِ، بَلْ تَكُونُ لَهُ الْحَيَاةُ الأَبَدِيَّةُ.",
        englishText = "For God so loved the world that He gave His only begotten Son, that whoever believes in Him should not perish but have everlasting life."
    )

    Column(modifier = modifier.fillMaxSize().background(bento.bg).padding(14.dp).verticalScroll(rememberScrollState())) {
        // 1. Header & Quick Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("محرر القوالب والتصاميم (Template Editor)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                Text("تخصيص كامل وشامل لطبقات البث والعرض المباشر", fontSize = 12.sp, color = bento.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onResetDefaults) { Icon(Icons.Default.Refresh, "إعادة تعيين", tint = bento.primary) }
                Button(onClick = { newTemplateName = "${editingTemplate.name} النسخة"; showSaveDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = bento.primary)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("حفظ كقالب جديد", fontSize = 12.sp)
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

        // 4. Base Template Selector (Effecting ONLY current tab mode)
        Text("اختر نمط القالب المبدئي (Select Base Style)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = bento.textPrimary)
        LazyRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val filteredTemplates = if (editorTab == 1) templates.filter { it.isFullScreen } else templates.filter { !it.isFullScreen }
            val listToShow = if (filteredTemplates.isEmpty()) templates else filteredTemplates
            items(listToShow) { tpl ->
                Card(
                    onClick = {
                        if (editorTab == 0) {
                            onSelectTemplate(tpl.copy(isFullScreen = false))
                        } else {
                            onUpdateShowTemplate(tpl.copy(isFullScreen = true))
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (tpl.id == editingTemplate.id) bento.primaryContainer else bento.card),
                    border = if (tpl.id == editingTemplate.id) BorderStroke(2.dp, bento.primary) else BorderStroke(1.dp, bento.borderSubtle),
                    modifier = Modifier.width(130.dp)
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

                // Alignment & NDI Feed Toggle
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
                    Column(Modifier.weight(0.6f)) {
                        val isActive = if (editorTab == 0) ndiLowerThirdActive else ndiFullShowActive
                        Text("مصدر NDI المباشر", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        SourceToggleChip(if (editorTab == 0) "Lower Third" else "Full Show", isActive, { onToggleNdiSource(if (editorTab == 0) "lower" else "full") })
                    }
                }

                HorizontalDivider(color = bento.borderSubtle)

                // Primary Typography (Arabic Verse & Citation)
                Text("تنسيق الخط العربي (Arabic Typography)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                val arabicFonts = listOf("Amiri", "Cairo", "Noto Naskh Arabic", "System")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(arabicFonts) { font ->
                        FilterChip(selected = editingTemplate.fontFamily == font, onClick = { onUpdate(editingTemplate.copy(fontFamily = font)) }, label = { Text(font, fontSize = 11.sp) })
                    }
                }
                
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
                    FeatureToggleRow("إظهار رمز الصليب", editingTemplate.showCrossEmblem) { onUpdate(editingTemplate.copy(showCrossEmblem = it)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureToggleRow("إظهار الحدود الملونة", editingTemplate.showAccentBorder) { onUpdate(editingTemplate.copy(showAccentBorder = it)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureToggleRow("إظهار ظلال النصوص (Drop Shadow)", editingTemplate.showDropShadow) { onUpdate(editingTemplate.copy(showDropShadow = it)) }
                }

                // Animated Motion Backgrounds
                Text("الخلفيات المتحركة (Motion Video Backgrounds)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AnimatedBackgroundType.entries) { type ->
                        FilterChip(selected = editingTemplate.animatedBackground == type, onClick = { onUpdate(editingTemplate.copy(animatedBackground = type)) }, label = { Text(type.displayNameAr, fontSize = 10.sp) })
                    }
                }
                if (editingTemplate.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO) {
                   Button(onClick = { launcher.launch("video/*") }, colors = ButtonDefaults.buttonColors(containerColor = bento.primary)) {
                       Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(16.dp))
                       Spacer(Modifier.width(6.dp))
                       Text("اختيار ملف فيديو مخصص (MP4)")
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
            confirmButton = { Button(onClick = { if (newTemplateName.isNotBlank()) { onSaveAsNew(newTemplateName, editingTemplate); showSaveDialog = false } }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun StyleToggle(label: String, active: Boolean, onToggle: (Boolean) -> Unit) {
    val bento = LocalBentoColors.current
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
            Text("${value.toInt()}", fontSize = 11.sp, color = bento.primary, fontWeight = FontWeight.Bold)
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
fun SourceToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bento = LocalBentoColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (active) bento.primary else bento.card, border = BorderStroke(1.dp, if (active) bento.primary else bento.border)) {
        Row(Modifier.padding(vertical = 6.dp, horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (active) Icons.Default.Check else Icons.Default.Videocam, null, tint = if (active) Color.White else bento.textSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = if (active) Color.White else bento.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = bento.textSecondary)
            
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
                    color = bento.primary
                )
                
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
                        Text("درجات الألوان (Color Grade)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = bento.onPrimaryContainer)
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
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray })
                                .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                        )
                        IconButton(
                            onClick = { onRemoveRecentColor(hex) },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "حذف", tint = bento.textSecondary, modifier = Modifier.size(10.dp))
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
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = bento.textPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = bento.primary))
    }
}

@Composable
fun BroadcastPreviewViewport(template: LowerThirdTemplate, verse: BibleVerse, previewBg: PreviewBackground) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
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
                modifier = if (isFS) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(horizontal = 8.dp)
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
                    // Arabic Citation & Cross Emblem
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (template.showCrossEmblem) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(try { Color(android.graphics.Color.parseColor(template.accentColorHex)) } catch (e: Exception) { Color.Yellow })
                            )
                        }
                        Text(
                            text = verse.getFormattedArabicCitation(template.useEasternArabicNumerals),
                            fontSize = (template.referenceFontSize * 0.42f).sp,
                            fontWeight = if (template.referenceIsBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (template.referenceIsItalic) FontStyle.Italic else FontStyle.Normal,
                            color = refCol
                        )
                    }

                    // Arabic Verse Text
                    Text(
                        text = verse.arabicText,
                        fontSize = (template.verseFontSize * 0.42f).sp,
                        lineHeight = (template.verseFontSize * 0.55f).sp,
                        fontWeight = if (template.verseIsBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (template.verseIsItalic) FontStyle.Italic else FontStyle.Normal,
                        color = textCol,
                        textAlign = when (template.alignment) {
                            BroadcastTextAlignment.CENTER -> TextAlign.Center
                            BroadcastTextAlignment.LEFT -> TextAlign.Left
                            else -> TextAlign.Right
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // English Bilingual Verse & Citation
                    if (template.bilingualMode) {
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
                                textAlign = when (template.alignment) {
                                    BroadcastTextAlignment.CENTER -> TextAlign.Center
                                    BroadcastTextAlignment.LEFT -> TextAlign.Left
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
                template = sampleTemplate.copy(bilingualMode = true),
                verse = sampleVerse,
                previewBg = PreviewBackground.SIMULATED_STUDIO_CAMERA
            )
        }
    }
}
