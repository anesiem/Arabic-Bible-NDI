package com.arabicchristianmedia.data

import android.content.Context
import android.content.SharedPreferences
import com.arabicchristianmedia.model.SavedSermon
import com.arabicchristianmedia.model.SermonRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * v1.9.1: persistence for the sermon list.
 *
 * Stores REFERENCES ONLY (book/chapter/verse + group + shade) as JSON in
 * SharedPreferences — the working list (restored at launch) and the library
 * of named saved sermons. Verse text is never stored; it is re-expanded
 * from the Bible database on load so the tashkeel setting always applies.
 */
class SermonStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sermon_store", Context.MODE_PRIVATE)

    // ── Working list ────────────────────────────────────────────────────

    fun loadWorking(): List<SermonRef> {
        val json = prefs.getString(KEY_WORKING, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> refFromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveWorking(refs: List<SermonRef>) {
        val arr = JSONArray()
        refs.forEach { arr.put(refToJson(it)) }
        prefs.edit().putString(KEY_WORKING, arr.toString()).apply()
    }

    // ── Saved sermons library ───────────────────────────────────────────

    fun loadSaved(): List<SavedSermon> {
        val json = prefs.getString(KEY_SAVED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val entriesArr = obj.optJSONArray("entries") ?: JSONArray()
                SavedSermon(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    updatedAt = obj.optLong("updatedAt", 0L),
                    entries = (0 until entriesArr.length()).map { j ->
                        refFromJson(entriesArr.getJSONObject(j))
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSaved(list: List<SavedSermon>) {
        val arr = JSONArray()
        list.forEach { saved ->
            val entriesArr = JSONArray()
            saved.entries.forEach { entriesArr.put(refToJson(it)) }
            arr.put(
                JSONObject()
                    .put("id", saved.id)
                    .put("name", saved.name)
                    .put("updatedAt", saved.updatedAt)
                    .put("entries", entriesArr)
            )
        }
        prefs.edit().putString(KEY_SAVED, arr.toString()).apply()
    }

    // ── Which saved sermon the working list belongs to ──────────────────

    fun getCurrentId(): String? = prefs.getString(KEY_CURRENT_ID, null)

    fun getCurrentName(): String? = prefs.getString(KEY_CURRENT_NAME, null)

    fun setCurrent(id: String?, name: String?) {
        prefs.edit()
            .putString(KEY_CURRENT_ID, id)
            .putString(KEY_CURRENT_NAME, name)
            .apply()
    }

    // ── JSON helpers ────────────────────────────────────────────────────

    private fun refToJson(ref: SermonRef): JSONObject = JSONObject()
        .put("b", ref.bookId)
        .put("c", ref.chapter)
        .put("v", ref.verse)
        .put("g", ref.groupId)
        .put("s", ref.shade)

    private fun refFromJson(obj: JSONObject): SermonRef = SermonRef(
        bookId = obj.optString("b"),
        chapter = obj.optInt("c"),
        verse = obj.optInt("v"),
        groupId = obj.optLong("g"),
        shade = obj.optInt("s")
    )

    companion object {
        private const val KEY_WORKING = "working_list_json"
        private const val KEY_SAVED = "saved_sermons_json"
        private const val KEY_CURRENT_ID = "current_sermon_id"
        private const val KEY_CURRENT_NAME = "current_sermon_name"
    }
}
