package com.example.model

enum class BibleVersion(val displayName: String, val languageCode: String) {
    ARABIC_SVD("العربية - سميث وفاندايك (SVD)", "ar"),
    ENGLISH_KJV("English - King James Version (KJV)", "en"),
    ENGLISH_WEB("English - World English Bible (WEB)", "en"),
    DUAL_BILINGUAL("عربي / English Dual", "ar-en")
}

data class BibleVerse(
    val id: String,
    val bookId: String,
    val bookArabicName: String,
    val bookEnglishName: String,
    val chapter: Int,
    val verse: Int,
    val arabicText: String,
    val englishText: String
) {
    fun getFormattedArabicCitation(useEasternNumbers: Boolean = true): String {
        val chStr = if (useEasternNumbers) toEasternDigits(chapter) else chapter.toString()
        val vStr = if (useEasternNumbers) toEasternDigits(verse) else verse.toString()
        return "$bookArabicName $chStr : $vStr"
    }

    fun getFormattedEnglishCitation(): String {
        return "$bookEnglishName $chapter:$verse"
    }

    private fun toEasternDigits(value: Int): String {
        val easternNumerals = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val chars = value.toString().toCharArray()
        for (i in chars.indices) {
            if (chars[i] in '0'..'9') {
                chars[i] = easternNumerals[chars[i] - '0']
            }
        }
        return String(chars)
    }
}
