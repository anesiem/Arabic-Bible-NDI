package com.arabicchristianmedia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.ui.theme.LocalBentoColors

/**
 * One-update item 20: the shortcuts help card — the full keyboard map.
 * Opened by the ? key or the top-bar keyboard icon (on every tab).
 * Also documented in README + user docs.
 */
@Composable
fun ShortcutsHelpDialog(onDismiss: () -> Unit) {
    val bento = LocalBentoColors.current
    val rows = listOf(
        "→ / PageDown" to "الآية التالية في العظة (next sermon live)",
        "← / PageUp" to "الآية السابقة في العظة (previous, stops at ends)",
        "↑ / ↓" to "تحريك التحديد في المكتبة/البحث (move selection, focus ring)",
        "Enter" to "عرض المحدد على الهواء (go live on selected)",
        "Space" to "التالي في العظة (next in sermon)",
        "Esc" to "إغلاق ثم مسح → خارج الهواء (close, then Clear → OFF AIR)",
        "C" to "مسح (Clear, per-feed Clear mode)",
        "O" to "تبديل ON AIR",
        "Ctrl+F" to "البحث (search)",
        "Ctrl+1..4" to "التبويبات (Library, Editor, Broadcast, Sermon)",
        "+ / −" to "حجم خط المكتبة (Library font size)",
        "?" to "هذه البطاقة (this help card)",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختصارات لوحة المفاتيح (Keyboard shortcuts)", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "لا تعمل أثناء الكتابة في حقل نص (never while typing).",
                    fontSize = 11.sp, color = bento.textSecondary
                )
                rows.forEach { (key, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            key,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = bento.primary,
                            modifier = Modifier.weight(0.35f)
                        )
                        Text(
                            desc,
                            fontSize = 12.sp, color = bento.textPrimary,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق (Close)") }
        }
    )
}
