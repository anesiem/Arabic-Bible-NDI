package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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

enum class PreviewBackground {
    CHECKERBOARD_TRANSPARENT,
    SIMULATED_STUDIO_CAMERA,
    PURE_BLACK,
    CHROMA_GREEN
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

    val bento = LocalBentoColors.current
    val editingTemplate = if (editorTab == 0) currentTemplate else activeShowTemplate
    val onUpdate = if (editorTab == 0) onUpdateTemplate else onUpdateShowTemplate

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onUpdate(editingTemplate.copy(customVideoUrl = it.toString(), animatedBackground = AnimatedBackgroundType.CUSTOM_VIDEO)) }
    }

    val displayVerse = activeVerse ?: BibleVerse(
        id = "sample", bookId = "jhn", bookArabicName = "إنجيل يوحنا", bookEnglishName = "John", chapter = 3, verse = 16,
        arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ...",
        englishText = "For God so loved the world that he gave his only begotten Son..."
    )

    Column(modifier = modifier.fillMaxSize().background(bento.bg).padding(16.dp).verticalScroll(rememberScrollState())) {
        // 1. Header & Quick Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Template Editor", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                Text("Design your broadcast and projector visuals", fontSize = 13.sp, color = bento.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onResetDefaults) { Icon(Icons.Default.Refresh, "Reset", tint = bento.primary) }
                Button(onClick = { newTemplateName = "${editingTemplate.name} Copy"; showSaveDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = bento.primary)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save New")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. Tab Switcher
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bento.surfaceVariant).padding(4.dp)) {
            EditorTabItem("Lower Third (Broadcast)", editorTab == 0, { editorTab = 0 }, Modifier.weight(1f))
            EditorTabItem("Full Show (Projector)", editorTab == 1, { editorTab = 1 }, Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        // 3. Live Preview Card
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (editorTab == 0) "Preview: Lower Third" else "Preview: Full Show", fontWeight = FontWeight.SemiBold, color = bento.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PreviewBackground.entries.forEach { bg ->
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(when (bg) {
                                PreviewBackground.CHECKERBOARD_TRANSPARENT -> Color.Gray
                                PreviewBackground.SIMULATED_STUDIO_CAMERA -> Color.DarkGray
                                PreviewBackground.PURE_BLACK -> Color.Black
                                PreviewBackground.CHROMA_GREEN -> Color.Green
                            }).border(width = if (previewBg == bg) 2.dp else 0.dp, color = bento.primary, shape = CircleShape).clickable { previewBg = bg })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                BroadcastPreviewViewport(editingTemplate, displayVerse, previewBg)
            }
        }

        Spacer(Modifier.height(24.dp))

        // 4. Base Template Selector (Filtered by mode)
        Text("Select Base Style", fontWeight = FontWeight.Bold, color = bento.textPrimary)
        LazyRow(modifier = Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val filteredTemplates = if (editorTab == 1) templates.filter { it.isFullScreen } else templates.filter { !it.isFullScreen }
            items(if (filteredTemplates.isEmpty()) templates else filteredTemplates) { tpl ->
                Card(
                    onClick = { onSelectTemplate(tpl) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (tpl.id == editingTemplate.id) bento.primaryContainer else bento.card),
                    border = if (tpl.id == editingTemplate.id) BorderStroke(2.dp, bento.primary) else null,
                    modifier = Modifier.width(140.dp)
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(8.dp)).background(try { Color(android.graphics.Color.parseColor(tpl.bgColorHex)) } catch (e: Exception) { Color.Black }).border(1.dp, bento.border, RoundedCornerShape(8.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(tpl.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 5. Detailed Controls
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = bento.card), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                
                // Alignment & NDI Toggle
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Alignment", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            BroadcastTextAlignment.entries.forEach { align ->
                                FilterChip(selected = editingTemplate.alignment == align, onClick = { onUpdate(editingTemplate.copy(alignment = align)) }, label = { Text(align.name, fontSize = 10.sp) })
                            }
                        }
                    }
                    Column(Modifier.weight(0.6f)) {
                        val isActive = if (editorTab == 0) ndiLowerThirdActive else ndiFullShowActive
                        Text("NDI Feed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                        SourceToggleChip(if (editorTab == 0) "Lower" else "Full", isActive, { onToggleNdiSource(if (editorTab == 0) "lower" else "full") })
                    }
                }

                // Primary Typography (Arabic)
                Text("Arabic Typography", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                val arabicFonts = listOf("Amiri", "Cairo", "Noto Naskh Arabic", "System")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(arabicFonts) { font ->
                        FilterChip(selected = editingTemplate.fontFamily == font, onClick = { onUpdate(editingTemplate.copy(fontFamily = font)) }, label = { Text(font, fontSize = 11.sp) })
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StyleToggle("B", editingTemplate.verseIsBold) { onUpdate(editingTemplate.copy(verseIsBold = it)) }
                    StyleToggle("I", editingTemplate.verseIsItalic) { onUpdate(editingTemplate.copy(verseIsItalic = it)) }
                    ColorPickerRow(editingTemplate.textColorHex, { onUpdate(editingTemplate.copy(textColorHex = it)) }, Modifier.weight(1f))
                }

                SliderWithLabel("Arabic Size", editingTemplate.verseFontSize.toFloat(), 10f..200f) { onUpdate(editingTemplate.copy(verseFontSize = it.toInt())) }
                SliderWithLabel("Citation Size", editingTemplate.referenceFontSize.toFloat(), 10f..100f) { onUpdate(editingTemplate.copy(referenceFontSize = it.toInt())) }

                // Bilingual Typography
                FeatureToggleRow("Bilingual (English) Mode", editingTemplate.bilingualMode) { onUpdate(editingTemplate.copy(bilingualMode = it)) }
                if (editingTemplate.bilingualMode) {
                    HorizontalDivider(color = bento.borderSubtle)
                    Text("English Typography", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StyleToggle("B", editingTemplate.secondaryVerseIsBold) { onUpdate(editingTemplate.copy(secondaryVerseIsBold = it)) }
                        StyleToggle("I", editingTemplate.secondaryVerseIsItalic) { onUpdate(editingTemplate.copy(secondaryVerseIsItalic = it)) }
                        ColorPickerRow(editingTemplate.secondaryTextColorHex, { onUpdate(editingTemplate.copy(secondaryTextColorHex = it)) }, Modifier.weight(1f))
                    }
                    SliderWithLabel("English Size", editingTemplate.secondaryVerseFontSize.toFloat(), 8f..100f) { onUpdate(editingTemplate.copy(secondaryVerseFontSize = it.toInt())) }
                    SliderWithLabel("Bilingual Spacing", editingTemplate.bilingualSpacing.toFloat(), 0f..200f) { onUpdate(editingTemplate.copy(bilingualSpacing = it.toInt())) }
                }

                // Background & Effects
                HorizontalDivider(color = bento.borderSubtle)
                Text("Background & Effects", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = bento.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Opacity", fontSize = 11.sp, color = bento.textSecondary)
                        Slider(value = editingTemplate.bgOpacity, onValueChange = { onUpdate(editingTemplate.copy(bgOpacity = it)) })
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Card Color", fontSize = 11.sp, color = bento.textSecondary)
                        ColorPickerRow(editingTemplate.bgColorHex, { onUpdate(editingTemplate.copy(bgColorHex = it)) })
                    }
                }
                
                // Animated Backgrounds
                Text("Motion Backgrounds", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = bento.textSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AnimatedBackgroundType.entries) { type ->
                        FilterChip(selected = editingTemplate.animatedBackground == type, onClick = { onUpdate(editingTemplate.copy(animatedBackground = type)) }, label = { Text(type.name, fontSize = 10.sp) })
                    }
                }
                if (editingTemplate.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO) {
                   Button(onClick = { launcher.launch("video/*") }) { Text("Pick Video File") }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Template") },
            text = { OutlinedTextField(value = newTemplateName, onValueChange = { newTemplateName = it }, label = { Text("Template Name") }) },
            confirmButton = { Button(onClick = { if (newTemplateName.isNotBlank()) { onSaveAsNew(newTemplateName, editingTemplate); showSaveDialog = false } }) { Text("Save") } }
        )
    }
}

@Composable
fun StyleToggle(label: String, active: Boolean, onToggle: (Boolean) -> Unit) {
    val bento = LocalBentoColors.current
    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (active) bento.primary else bento.surfaceVariant).clickable { onToggle(!active) }, contentAlignment = Alignment.Center) {
        Text(label, fontWeight = FontWeight.Bold, color = if (active) Color.White else bento.textPrimary)
    }
}

@Composable
fun SliderWithLabel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val bento = LocalBentoColors.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = bento.textSecondary)
            Text("${value.toInt()} sp", fontSize = 11.sp, color = bento.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = bento.primary, activeTrackColor = bento.primary))
    }
}

@Composable
fun EditorTabItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bento = LocalBentoColors.current
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) bento.card else Color.Transparent).clickable { onClick() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) bento.textPrimary else bento.textSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun SourceToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    val bento = LocalBentoColors.current
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (active) bento.primary else bento.card, border = BorderStroke(1.dp, if (active) bento.primary else bento.border)) {
        Row(Modifier.padding(vertical = 8.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (active) Icons.Default.Check else Icons.Default.Videocam, null, tint = if (active) Color.White else bento.textSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (active) Color.White else bento.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ColorPickerRow(selectedHex: String, onColorSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = listOf("#FFFFFF", "#FBBF24", "#F59E0B", "#EF4444", "#10B981", "#3B82F6", "#6366F1", "#A855F7", "#000000", "#94A3B8")
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(colors) { hex ->
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex))).border(2.dp, if (selectedHex.equals(hex, true)) Color.Blue else Color.Transparent, CircleShape).clickable { onColorSelected(hex) })
        }
    }
}

@Composable
fun FeatureToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val bento = LocalBentoColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = bento.textPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = bento.primary))
    }
}

@Composable
fun BroadcastPreviewViewport(template: LowerThirdTemplate, verse: BibleVerse, previewBg: PreviewBackground) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))) {
        // Bg Simulation
        Box(modifier = Modifier.fillMaxSize().background(when (previewBg) {
            PreviewBackground.CHECKERBOARD_TRANSPARENT -> Color.DarkGray
            PreviewBackground.SIMULATED_STUDIO_CAMERA -> Color(0xFF0F172A)
            PreviewBackground.PURE_BLACK -> Color.Black
            PreviewBackground.CHROMA_GREEN -> Color.Green
        }))
        
        val isFS = template.isFullScreen
        val bgCol = try { Color(android.graphics.Color.parseColor(template.bgColorHex)) } catch (e: Exception) { Color.Black }
        val textCol = try { Color(android.graphics.Color.parseColor(template.textColorHex)) } catch (e: Exception) { Color.White }
        
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(modifier = Modifier.fillMaxSize().padding(if (isFS) 0.dp else (template.horizontalMarginPercent).dp), contentAlignment = if (isFS) Alignment.Center else Alignment.BottomCenter) {
                Surface(
                    shape = RoundedCornerShape(if (isFS) 0.dp else template.cornerRadiusDp.dp),
                    color = if (template.isPureTransparentBackground) Color.Transparent else bgCol.copy(alpha = template.bgOpacity),
                    modifier = if (isFS) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = if (template.alignment == BroadcastTextAlignment.CENTER) Alignment.CenterHorizontally else Alignment.Start) {
                        Text(verse.getFormattedArabicCitation(true), fontSize = (template.referenceFontSize * 0.5).sp, fontWeight = if (template.referenceIsBold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (template.referenceIsItalic) FontStyle.Italic else FontStyle.Normal, color = try { Color(android.graphics.Color.parseColor(template.referenceColorHex)) } catch (e: Exception) { Color.Yellow })
                        Text(verse.arabicText, fontSize = (template.verseFontSize * 0.5).sp, lineHeight = (template.verseFontSize * 0.7).sp, fontWeight = if (template.verseIsBold) FontWeight.Bold else FontWeight.Normal, fontStyle = if (template.verseIsItalic) FontStyle.Italic else FontStyle.Normal, color = textCol, textAlign = when (template.alignment) {
                            BroadcastTextAlignment.CENTER -> TextAlign.Center
                            BroadcastTextAlignment.LEFT -> TextAlign.Left
                            else -> TextAlign.Right
                        })
                        
                        if (template.bilingualMode) {
                            Spacer(Modifier.height(template.bilingualSpacing.dp / 4))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(verse.englishText ?: "", fontSize = (template.secondaryVerseFontSize * 0.5).sp, color = try { Color(android.graphics.Color.parseColor(template.secondaryTextColorHex)) } catch (e: Exception) { Color.Gray })
                            }
                        }
                    }
                }
            }
        }
    }
}
