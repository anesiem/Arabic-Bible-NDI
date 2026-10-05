package com.arabicchristianmedia.server

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * v1.7: private storage for user-picked background videos.
 *
 * Security model: templates reference videos by opaque ID only. The HTTP
 * server serves files by ID from this allowlist — never by raw path — so a
 * LAN client cannot request arbitrary files (the old `/ndi/video_file?path=`
 * endpoint did exactly that). IDs are validated against a strict pattern to
 * block path traversal.
 */
class VideoStore(private val context: Context) {

    companion object {
        private const val TAG = "VideoStore"
        private val ID_PATTERN = Regex("^[0-9a-fA-F-]{1,64}$")
    }

    fun backgroundsDir(): File =
        File(context.filesDir, "backgrounds").apply { mkdirs() }

    /**
     * Copies a user-picked video (content:// URI) into private storage.
     * Returns the new video ID, or null on failure.
     */
    fun importFromUri(uri: Uri): String? {
        return try {
            val id = UUID.randomUUID().toString()
            val dest = File(backgroundsDir(), "$id.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (dest.length() == 0L) {
                dest.delete()
                return null
            }
            Log.i(TAG, "Imported video $id (${dest.length()} bytes)")
            id
        } catch (e: Exception) {
            Log.w(TAG, "Video import failed: ${e.message}")
            null
        }
    }

    /**
     * Best-effort migration for legacy templates whose customVideoUrl holds a
     * raw content:// URI or file path (pre-v1.7, no persistable permission, so
     * these usually die on restart — null then means "video missing").
     */
    fun importLegacyUrl(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val uri = Uri.parse(url)
            if (url.startsWith("content://")) {
                importFromUri(uri)
            } else {
                val src = File(url)
                if (!src.isFile || !src.canRead()) return null
                val id = UUID.randomUUID().toString()
                val dest = File(backgroundsDir(), "$id.mp4")
                src.copyTo(dest, overwrite = true)
                id
            }
        } catch (e: Exception) {
            Log.w(TAG, "Legacy video migration failed: ${e.message}")
            null
        }
    }

    /**
     * Resolves a video ID to its private file. Returns null for invalid IDs
     * or missing files (never throws, never escapes [backgroundsDir]).
     */
    fun fileFor(id: String): File? {
        if (!ID_PATTERN.matches(id)) return null
        return try {
            val dir = backgroundsDir()
            val file = File(dir, "$id.mp4")
            // Canonical-path check: the resolved file must stay inside dir.
            if (file.canonicalPath.startsWith(dir.canonicalPath + File.separator) && file.isFile) file
            else null
        } catch (e: Exception) {
            null
        }
    }

    fun deleteVideo(id: String) {
        try { fileFor(id)?.delete() } catch (e: Exception) {}
    }

    /**
     * v1.8: list all stored background videos (for /api/videos).
     * Returns (id, file) pairs sorted by name.
     */
    fun listVideos(): List<Pair<String, File>> {
        return try {
            val dir = backgroundsDir()
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".mp4") }
                ?: return emptyList()
            files.mapNotNull { f ->
                val id = f.name.removeSuffix(".mp4")
                if (ID_PATTERN.matches(id)) id to f else null
            }.sortedBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
