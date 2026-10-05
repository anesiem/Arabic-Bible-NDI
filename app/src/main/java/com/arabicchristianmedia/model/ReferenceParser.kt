package com.arabicchristianmedia.model

/**
 * Parses Bible references from user input (v1.8).
 *
 * Supports:
 * - English names: "John 3:16", "1 John 1:9"
 * - English abbreviations: "Jn 3:16", "1Jn 1:9", "Gen 1:1"
 * - Arabic names: "يوحنا 3:16", "١ يوحنا ٣:١٦" (Eastern Arabic numerals)
 * - Arabic abbreviations where defined
 *
 * Numeric book addressing (?book=43&chapter=3&verse=16) is handled by the
 * caller — book numbers are 1-indexed into the canonical book list.
 */
object ReferenceParser {

    data class ParsedReference(
        val book: BibleBook,
        val chapter: Int,
        val verse: Int
    )

    /**
     * Parse a free-form reference like "John 3:16" or "يوحنا ٣:١٦".
     * Returns null if the reference cannot be understood.
     */
    fun parse(ref: String, books: List<BibleBook>): ParsedReference? {
        val input = ref.trim()
        if (input.isEmpty()) return null

        // Normalize Eastern Arabic numerals to Western.
        val normalized = normalizeDigits(input)

        // Split into book part and chapter:verse part.
        // The chapter:verse is at the end: "John 3:16" -> book="John", cv="3:16"
        val cvMatch = Regex("""(\d+)\s*:\s*(\d+)\s*$""").find(normalized) ?: return null
        val chapter = cvMatch.groupValues[1].toIntOrNull() ?: return null
        val verse = cvMatch.groupValues[2].toIntOrNull() ?: return null
        if (chapter < 1 || verse < 1) return null

        val bookPart = normalized.substring(0, cvMatch.range.first).trim()
        if (bookPart.isEmpty()) return null

        val book = findBook(bookPart, books) ?: return null
        if (chapter > book.totalChapters) return null

        return ParsedReference(book, chapter, verse)
    }

    /**
     * Find a book by English name, Arabic name, or abbreviation
     * (case-insensitive, punctuation-tolerant).
     */
    fun findBook(name: String, books: List<BibleBook>): BibleBook? {
        val clean = name.trim().lowercase()
            .replace(".", "")
            .replace("ـ", "") // Arabic tatweel
            .trim()
        if (clean.isEmpty()) return null

        // Exact matches first (names and abbreviations).
        books.firstOrNull {
            it.englishName.lowercase() == clean ||
            it.arabicName == name.trim() ||
            it.englishAbbreviation.lowercase() == clean ||
            it.arabicAbbreviation == name.trim()
        }?.let { return it }

        // Prefix match on English name (e.g. "Jn" handled via abbreviation;
        // this catches "Joh" etc.). Require at least 2 chars.
        if (clean.length >= 2 && clean.all { it.isLetter() || it == ' ' }) {
            books.firstOrNull {
                it.englishName.lowercase().startsWith(clean)
            }?.let { return it }
        }

        return null
    }

    /**
     * Convert Eastern Arabic numerals (٠-٩) to Western (0-9).
     */
    fun normalizeDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(when (c) {
                in '٠'..'٩' -> ('0' + (c - '٠'))
                in '۰'..'۹' -> ('0' + (c - '۰')) // Extended Arabic-Indic
                else -> c
            })
        }
        return sb.toString()
    }
}
