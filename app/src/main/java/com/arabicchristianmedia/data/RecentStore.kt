package com.arabicchristianmedia.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Item 10: persistence for the recents list — the same storage pattern as
 * the sermon list ([SermonStore]): REFERENCES ONLY (book/chapter/verse) as
 * JSON in SharedPreferences. Verse text is never stored; it is re-expanded
 * from the Bible database on load so the tashkeel setting always applies.
 * Recents used to be session-only (lost on process kill); they now survive
 * restarts until the user clears them.
 */
data class RecentRef(
    val bookId: String,
    val chapter: Int,
    val verse: Int
)

class RecentStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("recent_store", Context.MODE_PRIVATE)

    fun load(): List<RecentRef> {
        val json = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecentRef(
                    bookId = obj.optString("b"),
                    chapter = obj.optInt("c"),
                    verse = obj.optInt("v")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(refs: List<RecentRef>) {
        val arr = JSONArray()
        refs.forEach {
            arr.put(
                JSONObject()
                    .put("b", it.bookId)
                    .put("c", it.chapter)
                    .put("v", it.verse)
            )
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_RECENTS).apply()
    }

    companion object {
        private const val KEY_RECENTS = "recent_list_json"
    }
}
