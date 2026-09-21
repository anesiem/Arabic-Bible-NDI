package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class BibleDatabaseHelper(private val context: Context) {

    companion object {
        const val ARABIC_DB = "arabic.db"
        const val ENGLISH_DB = "english_asv.db"
    }

    private fun getDatabasePath(name: String): File {
        return context.getDatabasePath(name)
    }

    private fun copyDatabaseFromAssets(name: String) {
        val dbFile = getDatabasePath(name)
        if (!dbFile.exists()) {
            try {
                dbFile.parentFile?.mkdirs()
                context.assets.open("bible/$name").use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i("BibleDatabaseHelper", "Database $name copied from assets to ${dbFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("BibleDatabaseHelper", "Error copying database $name", e)
            }
        }
    }

    fun openDatabase(name: String): SQLiteDatabase? {
        copyDatabaseFromAssets(name)
        return try {
            SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.e("BibleDatabaseHelper", "Error opening database $name", e)
            null
        }
    }
}
