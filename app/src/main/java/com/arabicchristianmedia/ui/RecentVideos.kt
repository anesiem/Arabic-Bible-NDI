package com.arabicchristianmedia.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicchristianmedia.ui.theme.LocalBentoColors

/**
 * Follow-up Phase 4 (item 6): recent videos, re-implemented cleanly after
 * the f949801 revert (brace mismatch in the old inline block).
 *
 * Same pattern as the editor's recent colors: video IDs are stored in
 * SharedPreferences (most recent first, capped at [MAX_RECENT]), tap a
 * card to re-apply the video, X to remove it. No real thumbnails are
 * available offline without a media stack, so cards show a video
 * placeholder icon — they follow the app's card pattern (surface +
 * selected border) so they look native either way.
 *
 * WIRING (2 lines in TemplateEditorScreen.kt — NOT done here because the
 * file is owned by another phase):
 *   1. After a successful video import: `RecentVideosStore.add(context, id)`
 *   2. Under the video file buttons:
 *      `RecentVideosRow(context = context, selectedVideoId = editingTemplate.customVideoId,
 *        onSelect = { id -> onUpdate(editingTemplate.copy(customVideoId = id,
 *          customVideoUrl = "", animatedBackground = AnimatedBackgroundType.CUSTOM_VIDEO)) })`
 */
object RecentVideosStore {

    private const val PREFS = "editor_recent_videos"
    private const val KEY = "recent_videos"

    /** Max recent videos kept. */
    const val MAX_RECENT = 8

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Current list, most-recent first (empty when nothing stored). */
    fun get(context: Context): List<String> =
        prefs(context).getStringSet(KEY, null)?.toList()?.take(MAX_RECENT) ?: emptyList()

    /** Records a video ID: deduped, moved to front, capped at [MAX_RECENT]. */
    fun add(context: Context, id: String) {
        if (id.isEmpty()) return
        val current = get(context)
        if (current.contains(id)) return
        val updated = (listOf(id) + current).take(MAX_RECENT)
        prefs(context).edit().putStringSet(KEY, updated.toSet()).apply()
    }

    /** Removes a video ID from recents (the stored file itself is untouched). */
    fun remove(context: Context, id: String) {
        val updated = get(context).filterNot { it == id }
        prefs(context).edit().putStringSet(KEY, updated.toSet()).apply()
    }
}

/**
 * Horizontal row of recent-video cards: tap to re-apply, X to remove.
 * Renders nothing when there are no recent videos. The card follows the
 * editor's card pattern: Surface, 72dp square, primary border when it is
 * the currently selected video.
 */
@Composable
fun RecentVideosRow(
    context: Context,
    selectedVideoId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var recentVideos by remember { mutableStateOf(RecentVideosStore.get(context)) }
    if (recentVideos.isEmpty()) return
    val bento = LocalBentoColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "فيديوهات أخيرة (Recent videos)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = bento.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(recentVideos, key = { it }) { videoId ->
                val isSelected = selectedVideoId == videoId
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            RecentVideosStore.add(context, videoId)
                            recentVideos = RecentVideosStore.get(context)
                            onSelect(videoId)
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = bento.surfaceVariant,
                    border = if (isSelected) BorderStroke(2.dp, bento.primary) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = bento.textSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        // X to remove from recents.
                        IconButton(
                            onClick = {
                                RecentVideosStore.remove(context, videoId)
                                recentVideos = RecentVideosStore.get(context)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = bento.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
