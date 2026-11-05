package com.arabicchristianmedia.server

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Item 54 (1.9.8): private storage for user-picked logo images.
 *
 * Same security model as VideoStore: templates reference logos by opaque ID
 * only. The HTTP server serves files by ID from this allowlist — never by
 * raw path — so a LAN client cannot request arbitrary files. IDs are
 * validated against a strict pattern to block path traversal.
 *
 * The original image extension is preserved (.png/.jpg/.jpeg/.webp) so the
 * HTTP route can send the right Content-Type; fileFor() tries each known
 * extension in turn.
 */
class LogoStore(private val context: Context) {

    companion object {
        private const val TAG = "LogoStore"
        private val ID_PATTERN = Regex("^[0-9a-fA-F-]{1,64}$")
        private val EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")
    }

    fun logosDir(): File =
        File(context.filesDir, "logos").apply { mkdirs() }

    /**
     * Copies a user-picked logo image (content:// URI) into private storage.
     * Returns the new logo ID, or null on failure.
     */
    fun importFromUri(uri: Uri): String? {
        val ext = when (context.contentResolver.getType(uri)?.lowercase()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        return try {
            context.contentResolver.openInputStream(uri)?.let { importFromInputStream(it, ext) }
        } catch (e: Exception) {
            Log.w(TAG, "Logo import failed: ${e.message}")
            null
        }
    }

    /**
     * Stores image data from any input stream (asset, decoded Base64, …).
     * Returns the new logo ID, or null on failure. The stream is closed.
     */
    fun importFromInputStream(input: java.io.InputStream, ext: String = "png"): String? {
        return try {
            val safeExt = ext.lowercase().takeIf { it in EXTENSIONS } ?: "png"
            val id = UUID.randomUUID().toString()
            val dest = File(logosDir(), "$id.$safeExt")
            input.use { inputStream ->
                dest.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
            }
            if (dest.length() == 0L) {
                dest.delete()
                return null
            }
            Log.i(TAG, "Imported logo $id (${dest.length()} bytes)")
            id
        } catch (e: Exception) {
            Log.w(TAG, "Logo import failed: ${e.message}")
            null
        }
    }

    /**
     * Stores raw image bytes (e.g. decoded from an imported template's
     * embedded "logoData"). Extension is detected from magic bytes.
     * Returns the new logo ID, or null on failure.
     */
    fun importFromBytes(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val ext = when {
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
                && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
                && bytes[2] == 0xFF.toByte() -> "jpg"
            bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte()
                && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
                && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte()
                && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "webp"
            else -> "png"
        }
        return importFromInputStream(bytes.inputStream(), ext)
    }

    /**
     * Resolves a logo ID to its private file. Returns null for invalid IDs
     * or missing files (never throws, never escapes [logosDir]).
     */
    fun fileFor(id: String): File? {
        if (!ID_PATTERN.matches(id)) return null
        return try {
            val dir = logosDir()
            EXTENSIONS.firstNotNullOfOrNull { ext ->
                val file = File(dir, "$id.$ext")
                // Canonical-path check: the resolved file must stay inside dir.
                if (file.canonicalPath.startsWith(dir.canonicalPath + File.separator) && file.isFile) file
                else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteLogo(id: String) {
        try { fileFor(id)?.delete() } catch (e: Exception) {}
    }

    /** Content-Type for a stored logo file, by extension. */
    fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }
}
