package com.arabicchristianmedia.model

/**
 * One-update item 21: the 66 books in 10 Bible-study groups for the
 * Library book picker. Counts: Pentateuch 5, Historical 12,
 * Poetry/Wisdom 5, Major Prophets 5, Minor Prophets 12, Gospels 4,
 * Acts 1, Pauline 13, General 8, Revelation 1 = 66.
 *
 * [shortDisplayName] strips the leading "سفر "/"إنجيل "/"رسالة " for the
 * compact chips — display only; citations keep the full [BibleBook.arabicName].
 */
data class BibleBookGroup(
    val id: String,
    val arabicName: String,
    val bookIds: List<String>
)

object BibleBookGroups {
    val PENTATEUCH = BibleBookGroup(
        "pentateuch", "أسفار موسى الخمسة",
        listOf("gen", "exo", "lev", "num", "deu")
    )
    val HISTORICAL = BibleBookGroup(
        "historical", "الأسفار التاريخية",
        listOf("jos", "jdg", "rut", "1sa", "2sa", "1ki", "2ki", "1ch", "2ch", "ezr", "neh", "est")
    )
    val POETRY_WISDOM = BibleBookGroup(
        "poetry", "الشعر والحكمة",
        listOf("job", "psa", "pro", "ecc", "sng")
    )
    val MAJOR_PROPHETS = BibleBookGroup(
        "major_prophets", "الأنبياء الكبار",
        listOf("isa", "jer", "lam", "ezk", "dan")
    )
    val MINOR_PROPHETS = BibleBookGroup(
        "minor_prophets", "الأنبياء الصغار",
        listOf("hos", "jol", "amo", "oba", "jon", "mic", "nam", "hab", "zep", "hag", "zec", "mal")
    )
    val GOSPELS = BibleBookGroup(
        "gospels", "الأناجيل",
        listOf("mat", "mrk", "luk", "jhn")
    )
    val ACTS = BibleBookGroup(
        "acts", "أعمال الرسل",
        listOf("act")
    )
    val PAULINE = BibleBookGroup(
        "pauline", "رسائل بولس",
        listOf("rom", "1co", "2co", "gal", "eph", "php", "col", "1th", "2th", "1ti", "2ti", "tit", "phm")
    )
    val GENERAL = BibleBookGroup(
        "general", "الرسائل العامة",
        listOf("heb", "jas", "1pe", "2pe", "1jn", "2jn", "3jn", "jud")
    )
    val REVELATION = BibleBookGroup(
        "revelation", "الرؤيا",
        listOf("rev")
    )

    val ALL: List<BibleBookGroup> = listOf(
        PENTATEUCH, HISTORICAL, POETRY_WISDOM, MAJOR_PROPHETS, MINOR_PROPHETS,
        GOSPELS, ACTS, PAULINE, GENERAL, REVELATION
    )

    /** Group id for a book id, or null when unknown. */
    fun groupOf(bookId: String): BibleBookGroup? =
        ALL.firstOrNull { bookId in it.bookIds }

    /**
     * One-update item 3: chapter-band alternation, pure and unit-testable.
     * The band flips when the (book, chapter) changes — not per row — so
     * all verses of one chapter share a band. Returns per-verse bandA flags.
     */
    fun chapterBands(verses: List<BibleVerse>): List<Boolean> {
        var bandA = true
        var prevKey: Pair<String, Int>? = null
        return verses.map { verse ->
            val key = verse.bookId to verse.chapter
            if (prevKey != null && key != prevKey) bandA = !bandA
            prevKey = key
            bandA
        }
    }

    /** Short chip label: strip the leading سفر/إنجيل/رسالة — display only. */
    fun shortDisplayName(arabicName: String): String {
        for (prefix in listOf("سفر ", "إنجيل ", "رسالة ")) {
            if (arabicName.startsWith(prefix)) return arabicName.removePrefix(prefix)
        }
        return arabicName
    }
}
