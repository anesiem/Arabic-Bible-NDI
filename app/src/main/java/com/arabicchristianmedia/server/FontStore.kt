package com.arabicchristianmedia.server

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.arabicchristianmedia.model.CustomFont
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * v1.9.1: private storage for user-imported fonts (mirrors [VideoStore]).
 *
 * The user picks a TTF/OTF through the system file picker; the file is
 * copied into `filesDir/fonts/<id>.<ext>` and its metadata (id, display
 * name, extension) is kept as JSON in SharedPreferences. Templates refer to
 * fonts by NAME only; every surface resolves the name through this store's
 * metadata + [com.arabicchristianmedia.model.CustomFonts]. IDs are
 * validated against a strict pattern so a name can never escape the
 * fonts directory (same allowlist discipline as fonts/videos serving).
 */
class FontStore(private val context: Context) {

    companion object {
        private const val TAG = "FontStore"
        private const val PREFS = "custom_fonts_meta"
        private const val KEY_FONTS = "fonts_json"
        private val ID_PATTERN = Regex("^[0-9a-fA-F-]{1,64}$")
        private val SUPPORTED_EXTS = setOf("ttf", "otf")
    }

    fun fontsDir(): File = File(context.filesDir, "fonts").apply { mkdirs() }

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(): List<CustomFont> {
        val json = prefs.getString(KEY_FONTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CustomFont(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    ext = obj.optString("ext", "ttf")
                )
            }.filter { ID_PATTERN.matches(it.id) && it.name.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveMeta(fonts: List<CustomFont>) {
        val arr = JSONArray()
        fonts.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("ext", it.ext))
        }
        prefs.edit().putString(KEY_FONTS, arr.toString()).apply()
    }

    /**
     * Copies a user-picked font file (content:// URI) into private storage
     * under [displayName]. Returns the stored font, or null on failure
     * (unreadable URI, unsupported type, empty file, duplicate name).
     */
    fun importFromUri(uri: Uri, displayName: String): CustomFont? {
        val name = displayName.trim()
        if (name.isEmpty()) return null
        if (list().any { it.name.equals(name, ignoreCase = true) }) return null
        return try {
            val ext = extensionFor(uri)
            if (ext == null) return null
            val id = UUID.randomUUID().toString()
            val dest = File(fontsDir(), "$id.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (dest.length() == 0L) {
                dest.delete()
                return null
            }
            val font = CustomFont(id, name, ext)
            saveMeta(list() + font)
            Log.i(TAG, "Imported font '$name' ($id.$ext, ${dest.length()} bytes)")
            font
        } catch (e: Exception) {
            Log.w(TAG, "Font import failed: ${e.message}")
            null
        }
    }

    /** Display name the picker reported for [uri] (e.g. "MyFont.ttf"), if any. */
    fun displayNameFor(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extensionFor(uri: Uri): String? {
        val fromName = displayNameFor(uri)
            ?.substringAfterLast('.', "")
            ?.lowercase()
        if (fromName != null && fromName in SUPPORTED_EXTS) return fromName
        val mime = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        return when (mime?.lowercase()) {
            "font/ttf", "application/x-font-ttf", "application/font-sfnt" -> "ttf"
            "font/otf", "application/x-font-otf" -> "otf"
            else -> null
        }
    }

    /** Resolves a stored font to its private file. Null for bad IDs or missing files. */
    fun fileFor(font: CustomFont): File? = fileForId(font.id, font.ext)

    fun fileForId(id: String, ext: String): File? {
        if (!ID_PATTERN.matches(id)) return null
        val safeExt = if (ext.lowercase() in SUPPORTED_EXTS) ext.lowercase() else return null
        return try {
            val dir = fontsDir()
            val file = File(dir, "$id.$safeExt")
            if (file.canonicalPath.startsWith(dir.canonicalPath + File.separator) && file.isFile) file
            else null
        } catch (e: Exception) {
            null
        }
    }

    fun delete(font: CustomFont) {
        try { fileFor(font)?.delete() } catch (e: Exception) {}
        saveMeta(list().filterNot { it.id == font.id })
    }
}
