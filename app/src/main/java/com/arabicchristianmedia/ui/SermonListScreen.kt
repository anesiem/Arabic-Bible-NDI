package com.arabicchristianmedia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.ui.theme.LocalBentoColors

/**
 * v2.0: the sermon playlist — built from text shared from other apps
 * (Google Keep notes, Bible apps) via ACTION_SEND / ACTION_PROCESS_TEXT.
 * References-only: every row is a canonical database verse. The first row
 * goes on air automatically on arrival; tapping any row puts that verse
 * live on both feeds (same path as /api/trigger).
 */
@Composable
fun SermonListScreen(
    uiState: BibleNdiUiState,
    onShowVerse: (BibleVerse) -> Unit,
    onClear: () -> Unit
) {
    val bento = LocalBentoColors.current
    val playlist = uiState.sermonPlaylist

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "قائمة الخدمة (Sermon list)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = bento.textPrimary
                    )
                    Text(
                        text = if (playlist.isEmpty()) "لا توجد آيات بعد"
                               else "${playlist.size} آية — اضغط أي آية لعرضها على الهواء",
                        fontSize = 12.sp,
                        color = bento.textSecondary
                    )
                }
                if (playlist.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "مسح القائمة (Clear list)",
                            tint = bento.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (playlist.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = bento.textSecondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "شارك ملاحظة من Google Keep أو من تطبيق الكتاب المقدس إلى «Bible NDI» — ستظهر مراجعها هنا بالترتيب، والآية الأولى تذهب للبث فوراً.",
                        fontSize = 14.sp,
                        color = bento.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(playlist, key = { _, v -> v.id }) { index, verse ->
                        val isLive = uiState.activeVerse?.id == verse.id && uiState.isLiveOnAir
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = bento.card),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isLive) 2.dp else 1.dp,
                                if (isLive) bento.primary else bento.border
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { onShowVerse(verse) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLive) bento.primary.copy(alpha = 0.25f)
                                            else bento.primaryContainer,
                                    modifier = Modifier.padding(end = 10.dp)
                                ) {
                                    Text(
                                        text = ArabicTextFormatter.toEasternArabicDigits(index + 1),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bento.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = verse.getFormattedArabicCitation(true),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = bento.primary
                                        )
                                        if (isLive) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = bento.primary
                                            ) {
                                                Text(
                                                    text = "على الهواء • LIVE",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = bento.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = verse.arabicText,
                                        fontSize = 15.sp,
                                        lineHeight = 24.sp,
                                        color = bento.textPrimary,
                                        textAlign = TextAlign.Right,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
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
}
