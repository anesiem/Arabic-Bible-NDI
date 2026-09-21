package com.example.data

object ArabicTextFormatter {

    private val EASTERN_ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    private const val RLM = "\u200F" // Right-to-Left Mark
    private const val ARABIC_COMMA = '،' // \u060C
    private const val ARABIC_SEMICOLON = '؛' // \u061B
    private const val ARABIC_QUESTION = '؟' // \u061F

    /**
     * Converts any Latin digits (0-9) in the input to Eastern Arabic-Indic numerals (٠-٩).
     */
    fun toEasternArabicDigits(number: Int): String {
        return toEasternArabicDigits(number.toString())
    }

    /**
     * Replaces 0-9 with ٠-٩ in a given string.
     */
    fun toEasternArabicDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (ch in '0'..'9') {
                sb.append(EASTERN_ARABIC_DIGITS[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Normalizes punctuation to Arabic typographical standards:
     * - Replaces Latin comma (,) with Arabic comma (، \u060C)
     * - Replaces Latin semicolon (;) with Arabic semicolon (؛ \u061B)
     * - Replaces Latin question mark (?) with Arabic question mark (؟ \u061F)
     * - Ensures correct typography spacing (no space before comma, one space after)
     * - Fixes multiple dots
     */
    fun formatArabicPunctuation(text: String): String {
        var formatted = text
            .replace(',', ARABIC_COMMA)
            .replace(';', ARABIC_SEMICOLON)
            .replace('?', ARABIC_QUESTION)

        // Ensure no leading space before Arabic comma or semicolon or dot
        formatted = formatted.replace(Regex("\\s+([$ARABIC_COMMA$ARABIC_SEMICOLON.])"), "$1")

        // Ensure space after Arabic comma and semicolon if followed by a letter or digit
        formatted = formatted.replace(Regex("([$ARABIC_COMMA$ARABIC_SEMICOLON])([^\\s\\d$ARABIC_COMMA$ARABIC_SEMICOLON.\\)\\]»”])"), "$1 $2")

        return formatted
    }

    /**
     * Formats an Arabic Scripture reference with RLM protection, Eastern numerals,
     * and canonical RTL syntax, e.g. "إنجيل يوحنا ٣ : ١٦"
     */
    fun formatCitation(
        bookArabicName: String,
        chapter: Int,
        verse: Int,
        useEasternDigits: Boolean = true
    ): String {
        val chStr = if (useEasternDigits) toEasternArabicDigits(chapter) else chapter.toString()
        val vStr = if (useEasternDigits) toEasternArabicDigits(verse) else verse.toString()
        // Using RLM prevents colons and digits from flipping in mixed LTR/RTL contexts
        return "$RLM$bookArabicName $chStr : $vStr"
    }

    /**
     * Formats verse number in traditional Quranic/Scriptural ornate bracket: ﴿١٦﴾
     */
    fun formatVerseNumberWithOrnateBrackets(verse: Int, useEasternDigits: Boolean = true): String {
        val numStr = if (useEasternDigits) toEasternArabicDigits(verse) else verse.toString()
        return "﴿$numStr﴾"
    }

    /**
     * Formats verse number on the same line as the verse text without brackets, e.g. "٢ هذا جاء"
     */
    fun formatVerseWithInlineNumber(verse: Int, text: String, useEasternDigits: Boolean = true): String {
        val numStr = if (useEasternDigits) toEasternArabicDigits(verse) else verse.toString()
        val normalized = formatArabicPunctuation(text)
        return "$RLM$numStr $normalized"
    }

    /**
     * Prepares Arabic Bible verse text for broadcast lower third:
     * - Applies punctuation normalization
     * - Converts digits if requested
     * - Strips redundant trailing quotes or adds elegant quotation marks
     */
    fun prepareForBroadcast(
        text: String,
        useEasternDigits: Boolean = true,
        useArabicPunctuation: Boolean = true
    ): String {
        var result = text.trim()
        if (useArabicPunctuation) {
            result = formatArabicPunctuation(result)
        }
        if (useEasternDigits) {
            result = toEasternArabicDigits(result)
        }
        return result
    }
}
