package com.arabicchristianmedia.model

/**
 * v2.0: finds every Bible reference inside a free text (e.g. a sermon note
 * shared from Google Keep), in the order the references appear.
 *
 * A reference is recognized ONLY where a book name stands: full names
 * ("رسالة العبرانيين"), the short forms people actually write in notes
 * ("العبرانيين"), English names, and abbreviations. Verse numbers embedded
 * in quoted verse text ("٧ وَأَمَّا...") have no book name in front of
 * them and are ignored — they can never false-trigger.
 *
 * The numbers must be the very next token after the book name (only
 * spaces/punctuation between), and every candidate is validated by
 * [ReferenceParser] (chapter within the book, range order, etc.).
 */
object ReferenceScanner {

    /** "9:7-9" / "9:7" / "9:7, 9" — chapter:verse with an optional
     * same-chapter range end or an explicit verse list. */
    private val VERSE_REF = Regex("""(\d+)\s*:\s*(\d+)(?:\s*-\s*(\d+)|(\s*(?:[,،]\s*\d+)+))?""")

    /** Bare chapter number right at the start of the window (" 23 ...").
     * Only spaces/punctuation may precede the digits — NEVER letters: a
     * letter means the matched name was a prefix of a longer word (the
     * "رؤ" inside "رؤيا" trap) and the digits belong to something else. */
    private val CHAPTER_ONLY = Regex("""^[^\p{L}\d]{0,3}(\d+)""")

    /** How far after a book name to look for its numbers. */
    private const val WINDOW = 48

    private data class Variant(val name: String, val book: BibleBook)
    private data class Occurrence(val start: Int, val end: Int, val variant: Variant)

    fun scan(text: String, books: List<BibleBook>): List<ReferenceParser.ParsedReference> {
        if (text.isBlank() || books.isEmpty()) return emptyList()
        // Eastern digits map 1 char -> 1 char, so positions stay aligned.
        val normalized = ReferenceParser.normalizeDigits(text)

        // Name variants per book. Longest first, so "يوحنا الأولى" beats the
        // "يوحنا" inside it when both start at related positions.
        val variants = books.flatMap { b ->
            listOf(
                b.arabicName,
                ReferenceParser.stripArabicPrefix(b.arabicName),
                // The bare short form people actually write ("رؤيا" for
                // "الرؤيا", "تكوين" for "التكوين").
                ReferenceParser.stripArabicPrefix(b.arabicName).removePrefix("ال"),
                b.arabicAbbreviation,
                b.englishName,
                b.englishAbbreviation
            )
                .filter { it.isNotBlank() }
                .distinct()
                .map { Variant(ReferenceParser.normalizeDigits(it), b) }
        }.sortedByDescending { it.name.length }

        // Every occurrence of every variant in the note.
        val occurrences = mutableListOf<Occurrence>()
        for (v in variants) {
            var from = 0
            while (true) {
                val idx = normalized.indexOf(v.name, from, ignoreCase = true)
                if (idx < 0) break
                occurrences.add(Occurrence(idx, idx + v.name.length, v))
                from = idx + 1
            }
        }
        // Text order; at the same start position the longer name wins.
        val ordered = occurrences.sortedWith(
            compareBy<Occurrence> { it.start }.thenByDescending { it.end }
        )

        val results = mutableListOf<ReferenceParser.ParsedReference>()
        var coveredUntil = -1
        for (occ in ordered) {
            if (occ.start < coveredUntil) continue
            // Word boundary before the name: an abbreviation must not match
            // inside an ordinary word ("عب" inside "الشعب" is not Hebrews).
            if (occ.start > 0 && normalized[occ.start - 1].isLetterOrDigit()) continue
            val windowEnd = minOf(normalized.length, occ.end + WINDOW)
            val window = normalized.substring(occ.end, windowEnd)

            var parsed: ReferenceParser.ParsedReference? = null
            var refEnd = -1

            val m = VERSE_REF.find(window)
            var sawCvForm = false
            if (m != null && window.substring(0, m.range.first).none { it.isLetterOrDigit() }) {
                // The c:v numbers are the next token after the book name.
                // Validate through the parser using the book's real name.
                sawCvForm = true
                parsed = ReferenceParser.parse(occ.variant.book.arabicName + " " + m.value, books)
                if (parsed != null) refEnd = occ.end + m.range.last + 1
            }
            if (parsed == null && !sawCvForm) {
                // Whole-chapter form: a bare number right after the name.
                // It must NEVER fire for a "chapter:verse" token: if a
                // verse reference was present but invalid, the reference
                // is nothing — not the whole chapter (the Rev 1 bug:
                // "رؤيا ١: ١٢" degraded into all of Revelation 1).
                val chM = CHAPTER_ONLY.find(window)
                if (chM != null && !window.substring(chM.range.last + 1).trimStart().startsWith(":")) {
                    parsed = ReferenceParser.parseChapterOnly(
                        occ.variant.book.arabicName + " " + chM.groupValues[1], books
                    )
                    if (parsed != null) refEnd = occ.end + chM.range.last + 1
                }
            }
            if (parsed != null) {
                results.add(parsed)
                coveredUntil = maxOf(coveredUntil, refEnd)
            }
        }
        return results
    }
}
