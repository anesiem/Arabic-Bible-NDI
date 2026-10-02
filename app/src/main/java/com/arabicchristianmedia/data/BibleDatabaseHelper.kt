package com.arabicchristianmedia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class BibleDatabaseHelper(private val context: Context) {

    companion object {
        const val ARABIC_DB = "arabic.db"
        const val ENGLISH_DB = "english_asv.db"
        /**
         * Bump when the bundled bible/*.db assets change: stale on-device
         * copies are recopied on the next launch.
         */
        const val DATA_VERSION = 1
        private const val PREFS = "bible_db_prefs"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Shared read-only connections, opened lazily once and reused.
     * SQLiteDatabase is thread-safe for concurrent reads; callers must NOT
     * close the returned instance (use [closeAll] at shutdown).
     */
    private val sharedDbs = mutableMapOf<String, SQLiteDatabase>()
    private val dbLock = Any()

    private fun getDatabasePath(name: String): File = context.getDatabasePath(name)

    private fun storedVersion(name: String): Int =
        try { prefs.getInt("db_version_$name", -1) } catch (e: Exception) { -1 }

    private fun storeVersion(name: String, version: Int) {
        try { prefs.edit().putInt("db_version_$name", version).apply() } catch (e: Exception) {}
    }

    /**
     * Ensures a usable database file exists. Copies from assets through a temp
     * file + atomic rename (a failed copy never leaves a corrupt database),
     * recopies when the bundled data version changes, and builds the
     * (bookNum, chNum, verseNum) index once per copy. Safe to call on a
     * background thread; serialized per helper.
     */
    fun ensureDatabase(name: String): Boolean {
        synchronized(dbLock) {
            val dbFile = getDatabasePath(name)
            if (!dbFile.exists() || storedVersion(name) != DATA_VERSION) {
                // Drop any shared connection before replacing the file.
                sharedDbs.remove(name)?.let { try { it.close() } catch (e: Exception) {} }
                if (!copyDatabaseFromAssets(name)) return false
                storeVersion(name, DATA_VERSION)
            }
            return dbFile.exists()
        }
    }

    private fun copyDatabaseFromAssets(name: String): Boolean {
        val dbFile = getDatabasePath(name)
        val parent = dbFile.parentFile ?: return false
        val tmpFile = File(parent, "$name.tmp")
        return try {
            parent.mkdirs()
            tmpFile.delete()
            context.assets.open("bible/$name").use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Atomic publish: readers never see a half-written database.
            if (dbFile.exists() && !dbFile.delete()) {
                Log.w("BibleDatabaseHelper", "Could not delete stale $name before rename")
            }
            if (!tmpFile.renameTo(dbFile)) {
                Log.e("BibleDatabaseHelper", "Failed to rename temp db for $name")
                tmpFile.delete()
                return false
            }
            createIndex(dbFile)
            Log.i("BibleDatabaseHelper", "Database $name copied from assets")
            true
        } catch (e: Exception) {
            Log.e("BibleDatabaseHelper", "Error copying database $name", e)
            try { tmpFile.delete() } catch (ignored: Exception) {}
            false
        }
    }

    /** (bookNum, chNum, verseNum) index: speeds the chapter queries. Built once per copy. */
    private fun createIndex(dbFile: File) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_words_ref ON words (bookNum, chNum, verseNum)")
        } catch (e: Exception) {
            Log.w("BibleDatabaseHelper", "Could not create index on ${dbFile.name}: ${e.message}")
        } finally {
            try { db?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Returns a shared read-only connection, copying the database on first
     * use if needed. The caller must NOT close it.
     */
    fun openDatabase(name: String): SQLiteDatabase? {
        synchronized(dbLock) {
            sharedDbs[name]?.let { db ->
                if (db.isOpen) return db
                sharedDbs.remove(name)
            }
            if (!ensureDatabase(name)) return null
            return try {
                val db = SQLiteDatabase.openDatabase(
                    getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READONLY
                )
                sharedDbs[name] = db
                db
            } catch (e: Exception) {
                Log.e("BibleDatabaseHelper", "Error opening database $name", e)
                null
            }
        }
    }

    /** Close all shared connections (app shutdown). */
    fun closeAll() {
        synchronized(dbLock) {
            sharedDbs.values.forEach { try { it.close() } catch (e: Exception) {} }
            sharedDbs.clear()
        }
    }
}
