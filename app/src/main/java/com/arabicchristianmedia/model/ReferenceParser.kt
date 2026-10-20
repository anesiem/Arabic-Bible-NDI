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
        val verse: Int,
        /** v2.0: last verse of a same-chapter range ("9:7-9"); null = single verse. */
        val endVerse: Int? = null,
        /** v2.0: true when only book+chapter were given ("Psalm 23") — the whole chapter. */
        val wholeChapter: Boolean = false
    )

    /**
     * Parse a free-form reference like "John 3:16" or "يوحنا ٣:١٦".
     * v2.0: also accepts same-chapter verse ranges ("John 3:16-18",
     * "العبرانيين ٩: ٧-٩"). Returns null if the reference cannot be understood.
     * A bare book+chapter ("John 3") is NOT a verse reference and stays null
     * here (the /api/trigger contract); see [parseChapterOnly].
     */
    fun parse(ref: String, books: List<BibleBook>): ParsedReference? {
        val input = ref.trim()
        if (input.isEmpty()) return null

        // Normalize Eastern Arabic numerals to Western.
        val normalized = normalizeDigits(input)

        // v2.0: range form first — "John 3:16-18" -> book="John", cv="3:16-18".
        val rangeMatch = Regex("""(\d+)\s*:\s*(\d+)\s*-\s*(\d+)\s*$""").find(normalized)
        if (rangeMatch != null) {
            val chapter = rangeMatch.groupValues[1].toIntOrNull() ?: return null
            val verse = rangeMatch.groupValues[2].toIntOrNull() ?: return null
            val endVerse = rangeMatch.groupValues[3].toIntOrNull() ?: return null
            if (chapter < 1 || verse < 1 || endVerse < verse) return null
            val bookPart = normalized.substring(0, rangeMatch.range.first).trim()
            if (bookPart.isEmpty()) return null
            val book = findBook(bookPart, books) ?: return null
            if (chapter > book.totalChapters) return null
            return ParsedReference(book, chapter, verse, endVerse = endVerse)
        }

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
     * v2.0: parse a book+chapter reference with NO verse ("المزمور ٢٣",
     * "Psalm 23") as a whole-chapter reference. Used by the share-note
     * scanner, where a bare chapter bullet means "the whole chapter".
     * Deliberately separate from [parse] so the remote API keeps
     * rejecting bare chapters.
     */
    fun parseChapterOnly(ref: String, books: List<BibleBook>): ParsedReference? {
        val input = ref.trim()
        if (input.isEmpty()) return null
        val normalized = normalizeDigits(input)
        val chMatch = Regex("""(\d+)\s*$""").find(normalized) ?: return null
        val chapter = chMatch.groupValues[1].toIntOrNull() ?: return null
        if (chapter < 1) return null
        val bookPart = normalized.substring(0, chMatch.range.first).trim()
        if (bookPart.isEmpty()) return null
        val book = findBook(bookPart, books) ?: return null
        if (chapter > book.totalChapters) return null
        return ParsedReference(book, chapter, verse = 1, wholeChapter = true)
    }

    /**
     * Find a book by English name, Arabic name, or abbreviation
     * (case-insensitive, punctuation-tolerant).
     * v2.0: also matches the name with its leading "سفر"/"رسالة"/"إنجيل"
     * stripped ("رسالة العبرانيين" matches "العبرانيين"), so shared notes
     * and /api/trigger accept the short names people actually write.
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

        // v2.0: prefix-stripped Arabic names ("العبرانيين" for
        // "رسالة العبرانيين"). Exact only — no prefix matching here.
        books.firstOrNull {
            stripArabicPrefix(it.arabicName) == clean
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

    /** v2.0: "رسالة العبرانيين" -> "العبرانيين"; "سفر التكوين" -> "التكوين". */
    fun stripArabicPrefix(arabicName: String): String {
        val t = arabicName.trim()
        for (p in listOf("سفر ", "رسالة ", "إنجيل ")) {
            if (t.startsWith(p) && t.length > p.length) return t.removePrefix(p).trim()
        }
        return t
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
