package com.example.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ArabicTextFormatter
import com.example.data.BibleRepository
import com.example.data.TemplateRepository
import com.example.model.BibleVerse
import com.example.model.BroadcastTextAlignment
import com.example.model.LowerThirdTemplate
import com.example.model.TemplateStyle
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme

class FullscreenOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val templateRepo = TemplateRepository(this)
        val activeTplId = templateRepo.getActiveTemplateId()
        val template = templateRepo.getAllTemplates().firstOrNull { it.id == activeTplId }
            ?: TemplateRepository.DEFAULT_TEMPLATES[0]

        val defaultVerse = BibleRepository.getVerses("jhn", 3).firstOrNull { it.verse == 16 }
            ?: BibleRepository.getVerses("jhn", 3).first()

        setContent {
            FullscreenOverlayContent(
                template = template,
                verse = defaultVerse,
                onClose = { finish() }
            )
        }
    }
}

@Composable
fun FullscreenOverlayContent(
    template: LowerThirdTemplate,
    verse: BibleVerse,
    onClose: () -> Unit
) {
    var chromaKeyMode by remember { mutableStateOf(false) }

    val bgCol = try { Color(android.graphics.Color.parseColor(template.bgColorHex)) } catch (e: Exception) { Color.Black }
    val accentCol = try { Color(android.graphics.Color.parseColor(template.accentColorHex)) } catch (e: Exception) { Color(0xFFE5A93C) }
    val textCol = try { Color(android.graphics.Color.parseColor(template.textColorHex)) } catch (e: Exception) { Color.White }
    val refCol = try { Color(android.graphics.Color.parseColor(template.referenceColorHex)) } catch (e: Exception) { Color(0xFFF4D06F) }

    val formattedVerse = ArabicTextFormatter.prepareForBroadcast(
        verse.arabicText,
        template.useEasternArabicNumerals,
        template.useArabicPunctuation
    )
    val formattedCitation = verse.getFormattedArabicCitation(template.useEasternArabicNumerals)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (chromaKeyMode) Color(0xFF00FF00) else Color.Black)
    ) {
        // Exit & Chroma Toggle Controls (Top bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x66000000),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { chromaKeyMode = !chromaKeyMode }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (chromaKeyMode) "Chroma Green Active" else "Pure Black Background",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Lower Third Positioned at Bottom or Full Show Projector Centered
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (template.isFullScreen) 0.dp else (template.horizontalMarginPercent * 4).dp,
                        end = if (template.isFullScreen) 0.dp else (template.horizontalMarginPercent * 4).dp,
                        bottom = if (template.isFullScreen) 0.dp else (template.positionBottomPercent * 5).dp
                    ),
                contentAlignment = if (template.isFullScreen) Alignment.Center else Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(if (template.isFullScreen) 0.dp else template.cornerRadiusDp.dp),
                    color = if (template.style == TemplateStyle.TRANSPARENT_OUTLINE) Color.Transparent else bgCol.copy(alpha = template.bgOpacity),
                    border = if (template.showAccentBorder && template.style != TemplateStyle.TRANSPARENT_OUTLINE && !template.isFullScreen) {
                        BorderStroke(2.dp, accentCol)
                    } else null,
                    shadowElevation = if (template.showDropShadow && !template.isFullScreen) 16.dp else 0.dp,
                    modifier = if (template.isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = if (template.isFullScreen) Modifier.fillMaxSize().padding(48.dp) else Modifier.padding(24.dp),
                        verticalArrangement = if (template.isFullScreen) Arrangement.Center else Arrangement.Top,
                        horizontalAlignment = if (template.isFullScreen) {
                            when (template.alignment) {
                                BroadcastTextAlignment.CENTER -> Alignment.CenterHorizontally
                                BroadcastTextAlignment.LEFT -> Alignment.Start
                                else -> Alignment.End
                            }
                        } else Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (template.showCrossEmblem) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(accentCol)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = formattedCitation,
                                fontSize = template.referenceFontSize.sp,
                                fontWeight = FontWeight.Bold,
                                color = refCol
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = formattedVerse,
                            fontSize = template.verseFontSize.sp,
                            lineHeight = (template.verseFontSize * 1.5).sp,
                            fontWeight = FontWeight.Medium,
                            color = textCol,
                            textAlign = when (template.alignment) {
                                BroadcastTextAlignment.CENTER -> TextAlign.Center
                                BroadcastTextAlignment.LEFT -> TextAlign.Left
                                else -> TextAlign.Right
                            }
                        )

                        if (template.bilingualMode) {
                            Spacer(modifier = Modifier.height(6.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = "${verse.englishText} (${verse.getFormattedEnglishCitation()})",
                                    fontSize = (template.verseFontSize * 0.55).sp,
                                    color = Color(0xFFCBD5E1),
                                    textAlign = when (template.alignment) {
                                        BroadcastTextAlignment.CENTER -> TextAlign.Center
                                        BroadcastTextAlignment.LEFT -> TextAlign.Left
                                        else -> TextAlign.Right
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
}

@Preview(showBackground = true, widthDp = 1920, heightDp = 1080)
@Composable
fun FullscreenOverlayContentPreview() {
    MyApplicationTheme {
        FullscreenOverlayContent(
            template = TemplateRepository.DEFAULT_TEMPLATES[0],
            verse = BibleRepository.getVerses("jhn", 3)[15],
            onClose = {}
        )
    }
}
