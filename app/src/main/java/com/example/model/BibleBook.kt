package com.example.model

enum class Testament {
    OLD_TESTAMENT,
    NEW_TESTAMENT
}

data class BibleBook(
    val id: String,
    val arabicName: String,
    val englishName: String,
    val arabicAbbreviation: String,
    val englishAbbreviation: String,
    val testament: Testament,
    val totalChapters: Int
)
