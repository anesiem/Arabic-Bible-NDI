package com.arabicchristianmedia.data

import android.content.Context
import com.arabicchristianmedia.model.BibleBook
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.Testament
import java.text.Normalizer

object BibleRepository {

    val allBooks: List<BibleBook> = listOf(
        // Old Testament
        BibleBook("gen", "سفر التكوين", "Genesis", "تك", "Gen", Testament.OLD_TESTAMENT, 50),
        BibleBook("exo", "سفر الخروج", "Exodus", "خر", "Exo", Testament.OLD_TESTAMENT, 40),
        BibleBook("lev", "سفر اللاويين", "Leviticus", "لا", "Lev", Testament.OLD_TESTAMENT, 27),
        BibleBook("num", "سفر العدد", "Numbers", "عد", "Num", Testament.OLD_TESTAMENT, 36),
        BibleBook("deu", "سفر التثنية", "Deuteronomy", "تث", "Deu", Testament.OLD_TESTAMENT, 34),
        BibleBook("jos", "سفر يشوع", "Joshua", "يش", "Jos", Testament.OLD_TESTAMENT, 24),
        BibleBook("jdg", "سفر القضاة", "Judges", "قض", "Jdg", Testament.OLD_TESTAMENT, 21),
        BibleBook("rut", "سفر راعوث", "Ruth", "را", "Rut", Testament.OLD_TESTAMENT, 4),
        BibleBook("1sa", "سفر صموئيل الأول", "1 Samuel", "١صم", "1Sam", Testament.OLD_TESTAMENT, 31),
        BibleBook("2sa", "سفر صموئيل الثاني", "2 Samuel", "٢صم", "2Sam", Testament.OLD_TESTAMENT, 24),
        BibleBook("1ki", "سفر الملوك الأول", "1 Kings", "١مل", "1Ki", Testament.OLD_TESTAMENT, 22),
        BibleBook("2ki", "سفر الملوك الثاني", "2 Kings", "٢مل", "2Ki", Testament.OLD_TESTAMENT, 25),
        BibleBook("1ch", "سفر أخبار الأيام الأول", "1 Chronicles", "١أخ", "1Ch", Testament.OLD_TESTAMENT, 29),
        BibleBook("2ch", "سفر أخبار الأيام الثاني", "2 Chronicles", "٢أخ", "2Ch", Testament.OLD_TESTAMENT, 36),
        BibleBook("ezr", "سفر عزرا", "Ezra", "عز", "Ezr", Testament.OLD_TESTAMENT, 10),
        BibleBook("neh", "سفر نحميا", "Nehemiah", "نح", "Neh", Testament.OLD_TESTAMENT, 13),
        BibleBook("est", "سفر أستير", "Esther", "أس", "Est", Testament.OLD_TESTAMENT, 10),
        BibleBook("job", "سفر أيوب", "Job", "أي", "Job", Testament.OLD_TESTAMENT, 42),
        BibleBook("psa", "سفر المزامير", "Psalms", "مز", "Psa", Testament.OLD_TESTAMENT, 150),
        BibleBook("pro", "سفر الأمثال", "Proverbs", "أم", "Pro", Testament.OLD_TESTAMENT, 31),
        BibleBook("ecc", "سفر الجامعة", "Ecclesiastes", "جا", "Ecc", Testament.OLD_TESTAMENT, 12),
        BibleBook("sng", "سفر نشيد الأنشاد", "Song of Solomon", "نش", "Sng", Testament.OLD_TESTAMENT, 8),
        BibleBook("isa", "سفر إشعياء", "Isaiah", "إش", "Isa", Testament.OLD_TESTAMENT, 66),
        BibleBook("jer", "سفر إرميا", "Jeremiah", "إر", "Jer", Testament.OLD_TESTAMENT, 52),
        BibleBook("lam", "سفر مراثي إرميا", "Lamentations", "مرا", "Lam", Testament.OLD_TESTAMENT, 5),
        BibleBook("ezk", "سفر حزقيال", "Ezekiel", "حز", "Ezk", Testament.OLD_TESTAMENT, 48),
        BibleBook("dan", "سفر دانيال", "Daniel", "دا", "Dan", Testament.OLD_TESTAMENT, 12),
        BibleBook("hos", "سفر هوشع", "Hosea", "هو", "Hos", Testament.OLD_TESTAMENT, 14),
        BibleBook("jol", "سفر يوئيل", "Joel", "يوئيل", "Jol", Testament.OLD_TESTAMENT, 3),
        BibleBook("amo", "سفر عاموس", "Amos", "عا", "Amo", Testament.OLD_TESTAMENT, 9),
        BibleBook("oba", "سفر عوبديا", "Obadiah", "عو", "Oba", Testament.OLD_TESTAMENT, 1),
        BibleBook("jon", "سفر يونان", "Jonah", "يون", "Jon", Testament.OLD_TESTAMENT, 4),
        BibleBook("mic", "سفر ميخا", "Micah", "مي", "Mic", Testament.OLD_TESTAMENT, 7),
        BibleBook("nam", "سفر ناحوم", "Nahum", "نا", "Nam", Testament.OLD_TESTAMENT, 3),
        BibleBook("hab", "سفر حبقوق", "Habakkuk", "حب", "Hab", Testament.OLD_TESTAMENT, 3),
        BibleBook("zep", "سفر صفنيا", "Zephaniah", "صف", "Zep", Testament.OLD_TESTAMENT, 3),
        BibleBook("hag", "سفر حجي", "Haggai", "حج", "Hag", Testament.OLD_TESTAMENT, 2),
        BibleBook("zec", "سفر زكريا", "Zechariah", "زك", "Zec", Testament.OLD_TESTAMENT, 14),
        BibleBook("mal", "سفر ملاخي", "Malachi", "مل", "Mal", Testament.OLD_TESTAMENT, 4),

        // New Testament
        BibleBook("mat", "إنجيل متى", "Matthew", "مت", "Mat", Testament.NEW_TESTAMENT, 28),
        BibleBook("mrk", "إنجيل مرقس", "Mark", "مر", "Mrk", Testament.NEW_TESTAMENT, 16),
        BibleBook("luk", "إنجيل لوقا", "Luke", "لو", "Luk", Testament.NEW_TESTAMENT, 24),
        BibleBook("jhn", "إنجيل يوحنا", "John", "يو", "Jhn", Testament.NEW_TESTAMENT, 21),
        BibleBook("act", "سفر أعمال الرسل", "Acts", "أع", "Act", Testament.NEW_TESTAMENT, 28),
        BibleBook("rom", "رسالة رومية", "Romans", "رو", "Rom", Testament.NEW_TESTAMENT, 16),
        BibleBook("1co", "رسالة كورنثوس الأولى", "1 Corinthians", "١كو", "1Co", Testament.NEW_TESTAMENT, 16),
        BibleBook("2co", "رسالة كورنثوس الثانية", "2 Corinthians", "٢كو", "2Co", Testament.NEW_TESTAMENT, 13),
        BibleBook("gal", "رسالة غلاطية", "Galatians", "غل", "Gal", Testament.NEW_TESTAMENT, 6),
        BibleBook("eph", "رسالة أفسس", "Ephesians", "أف", "Eph", Testament.NEW_TESTAMENT, 6),
        BibleBook("php", "رسالة فيلبي", "Philippians", "في", "Php", Testament.NEW_TESTAMENT, 4),
        BibleBook("col", "رسالة كولوسي", "Colossians", "كو", "Col", Testament.NEW_TESTAMENT, 4),
        BibleBook("1th", "رسالة تسالونيكي الأولى", "1 Thessalonians", "١تس", "1Th", Testament.NEW_TESTAMENT, 5),
        BibleBook("2th", "رسالة تسالونيكي الثانية", "2 Thessalonians", "٢تس", "2Th", Testament.NEW_TESTAMENT, 3),
        BibleBook("1ti", "رسالة تيموثاوس الأولى", "1 Timothy", "١تي", "1Ti", Testament.NEW_TESTAMENT, 6),
        BibleBook("2ti", "رسالة تيموثاوس الثانية", "2 Timothy", "٢تي", "2Ti", Testament.NEW_TESTAMENT, 4),
        BibleBook("tit", "رسالة تيطس", "Titus", "تي", "Tit", Testament.NEW_TESTAMENT, 3),
        BibleBook("phm", "رسالة فليمون", "Philemon", "فل", "Phm", Testament.NEW_TESTAMENT, 1),
        BibleBook("heb", "رسالة العبرانيين", "Hebrews", "عب", "Heb", Testament.NEW_TESTAMENT, 13),
        BibleBook("jas", "رسالة يعقوب", "James", "يع", "Jas", Testament.NEW_TESTAMENT, 5),
        BibleBook("1pe", "رسالة بطرس الأولى", "1 Peter", "١بط", "1Pe", Testament.NEW_TESTAMENT, 5),
        BibleBook("2pe", "رسالة بطرس الثانية", "2 Peter", "٢بط", "2Pe", Testament.NEW_TESTAMENT, 3),
        BibleBook("1jn", "رسالة يوحنا الأولى", "1 John", "١يو", "1Jn", Testament.NEW_TESTAMENT, 5),
        BibleBook("2jn", "رسالة يوحنا الثانية", "2 John", "٢يو", "2Jn", Testament.NEW_TESTAMENT, 1),
        BibleBook("3jn", "رسالة يوحنا الثالثة", "3 John", "٣يو", "3Jn", Testament.NEW_TESTAMENT, 1),
        BibleBook("jud", "رسالة يهوذا", "Jude", "يه", "Jud", Testament.NEW_TESTAMENT, 1),
        BibleBook("rev", "سفر الرؤيا", "Revelation", "رؤ", "Rev", Testament.NEW_TESTAMENT, 22)
    )

    private var dbHelper: BibleDatabaseHelper? = null
    private var appContext: Context? = null

    private const val PREFS_NAME = "bible_prefs"
    private const val KEY_TASHKEEL_ENABLED = "arabic_tashkeel_enabled"
    private const val KEY_ENGLISH_VERSION = "english_bible_version"

    fun initialize(context: Context) {
        if (dbHelper == null) {
            dbHelper = BibleDatabaseHelper(context.applicationContext)
        }
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    /** True when the user has enabled tashkeel (diacritics) for Arabic text. */
    fun isTashkeelEnabled(): Boolean {
        // One-update item 13: Tashkeel defaults ON.
        return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_TASHKEEL_ENABLED, true) ?: true
    }

    /** Persist the tashkeel preference. */
    fun setTashkeelEnabled(enabled: Boolean) {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_TASHKEEL_ENABLED, enabled)?.apply()
    }

    /** Returns the user's English version preference ("asv", "kjv", "web"). */
    fun getEnglishVersion(): String {
        return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_ENGLISH_VERSION, "asv") ?: "asv"
    }

    /** Returns the English DB file name based on the user's version preference. */
    fun getEnglishDbName(): String {
        val version = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_ENGLISH_VERSION, "asv") ?: "asv"
        return when (version) {
            "kjv" -> BibleDatabaseHelper.ENGLISH_KJV_DB
            "web" -> BibleDatabaseHelper.ENGLISH_WEB_DB
            else -> BibleDatabaseHelper.ENGLISH_DB // asv
        }
    }

    /** Persist the English Bible version preference ("asv", "kjv", "web"). */
    fun setEnglishVersion(version: String) {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.putString(KEY_ENGLISH_VERSION, version)?.apply()
    }

    fun getBookById(id: String): BibleBook? {
        return allBooks.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun getVerses(bookId: String, chapter: Int): List<BibleVerse> {
        val helper = dbHelper ?: return emptyList()
        val book = getBookById(bookId) ?: return emptyList()
        val bookNum = allBooks.indexOf(book) + 1

        val arDb = helper.openDatabase(BibleDatabaseHelper.ARABIC_DB)
        val enDb = helper.openDatabase(getEnglishDbName())

        val verses = mutableListOf<BibleVerse>()

        val enMap = mutableMapOf<Int, String>()
        enDb?.let { db ->
            val cursor = db.rawQuery(
                "SELECT verseNum, word FROM words WHERE bookNum = ? AND chNum = ? ORDER BY verseNum",
                arrayOf(bookNum.toString(), chapter.toString())
            )
            if (cursor.moveToFirst()) {
                do {
                    enMap[cursor.getInt(0)] = cursor.getString(1)
                } while (cursor.moveToNext())
            }
            cursor.close()
        }

        arDb?.let { db ->
            // When tashkeel is enabled, prefer tashkeelWord; fall back to word
            // for verses where tashkeel is missing (e.g. psalm headings).
            val wordColumn = if (isTashkeelEnabled()) {
                "COALESCE(NULLIF(tashkeelWord, ''), word)"
            } else {
                "word"
            }
            val cursor = db.rawQuery(
                "SELECT verseNum, $wordColumn FROM words WHERE bookNum = ? AND chNum = ? ORDER BY verseNum",
                arrayOf(bookNum.toString(), chapter.toString())
            )
            if (cursor.moveToFirst()) {
                do {
                    val verseNum = cursor.getInt(0)
                    val arText = cursor.getString(1)
                    val enText = enMap[verseNum] ?: ""
                    verses.add(
                        BibleVerse(
                            id = "${bookId}_${chapter}_$verseNum",
                            bookId = bookId,
                            bookArabicName = book.arabicName,
                            bookEnglishName = book.englishName,
                            chapter = chapter,
                            verse = verseNum,
                            arabicText = arText,
                            englishText = enText
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
        }

        if (verses.isEmpty()) {
            if (bookId == "jhn" && chapter == 3) {
                return listOf(
                    BibleVerse(
                        id = "jhn_3_16",
                        bookId = "jhn",
                        bookArabicName = "إنجيل يوحنا",
                        bookEnglishName = "John",
                        chapter = 3,
                        verse = 16,
                        arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ، لِكَيْ لاَ يَهْلِكَ كُلُّ مَنْ يُؤْمِنُ بِهِ، بَلْ تَكُونُ لَهُ الْحَيَاةُ الأَبَدِيَّةُ.",
                        englishText = "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life."
                    ),
                    BibleVerse(
                        id = "jhn_3_17",
                        bookId = "jhn",
                        bookArabicName = "إنجيل يوحنا",
                        bookEnglishName = "John",
                        chapter = 3,
                        verse = 17,
                        arabicText = "لأَنَّهُ لَمْ يُرْسِلِ اللهُ ابْنَهُ إِلَى الْعَالَمِ لِيَدِينَ الْعَالَمَ، بَلْ لِيَخْلُصَ بِهِ الْعَالَمُ.",
                        englishText = "For God sent not his Son into the world to condemn the world; but that the world through him might be saved."
                    )
                )
            } else if (bookId == "psa" && chapter == 23) {
                return listOf(
                    BibleVerse(
                        id = "psa_23_1",
                        bookId = "psa",
                        bookArabicName = "سفر المزامير",
                        bookEnglishName = "Psalms",
                        chapter = 23,
                        verse = 1,
                        arabicText = "اَلرَّبُّ رَاعِيَّ فَلاَ يَنُقُصُنِي شَيْءٌ.",
                        englishText = "The LORD is my shepherd; I shall not want."
                    )
                )
            }
        }

        return verses
    }

    /**
     * Normalize Arabic text for search matching — variant-insensitive.
     * A word can be written several ways and still be correct, so the
     * search treats these groups as the same letter: ي/ى, ة/ه,
     * ا/إ/أ/ٱ/آ (plus the Quranic alef forms ٲ/ٳ), and the hamza seats
     * ؤ/ئ/ء (Policy B, seat-preserving). The DB's searchWord column is
     * built with this SAME function (identical on index + query), so a
     * query in any spelling meets the stored form. Display text is
     * NEVER touched — only the search key.
     *
     * Pipeline order (research §5 — do not reorder):
     * 1. NFKC (presentation forms → base letters; search key only),
     * 2. NFC (compose decomposed alef+hamza so later steps see single
     *    code points), 3. map dagger/small letters BEFORE stripping,
     * 4. strip marks + fold letters, 5. collapse doubled و/ي from the
     *    hamza fold. Tier 1 is the settled Lucene/CAMeL core; Tier 2
     *    is recall extensions.
     *
     * Over-normalization caution: every fold merges some genuinely
     * different words (على vs علي, حماة vs حماه, سأل→سال). Mitigation:
     * the search ranks exact (unnormalized) matches first. Do NOT fold
     * dialectal sound mergers (ث→ت, ذ→د/ز, ظ→ز, ق→ك/ء, ج→گ), ة→ت,
     * a trailing ا, or aggressive stemming.
     *
     * Known gap (state plainly): under Policy B, مسئول folds to مسيول,
     * NOT مسول — the seat-swap case still needs query expansion.
     * v1.9.19: full table (13 tiered groups) replaces the v1.9.5
     * 5-fold version; dagger alef is now MAPPED to ا, never stripped.
     */
    fun normalizeForSearch(text: String): String {
        // 1. NFKC — fold presentation forms (U+FB50–FDFF, U+FE70–FEFF)
        //    to base letters. Search key only: NFKC expands ﷺ to a phrase
        //    and must never be applied to display text.
        // 2. NFC — compose decomposed alef+hamza (ا + U+0654 → أ) so
        //    later steps see single code points.
        var s = Normalizer.normalize(text, Normalizer.Form.NFKC)
        s = Normalizer.normalize(s, Normalizer.Form.NFC)
        // 3. Map letter-like marks BEFORE stripping diacritics: dagger
        //    alef → ا (MAP, never strip — stripping deletes a pronounced
        //    letter: هٰذا → هذ instead of هذا); small high waw/yeh → و/ي
        //    (pronounced letters, not diacritics).
        s = s.map { c ->
            when (c) {
                '\u0670' -> 'ا' // dagger alef
                '\u06E5', '\u08F3', '\u08D3' -> 'و' // small high waw
                '\u06E6', '\u06E7', '\u08C9', '\u08CA', '\u08CB' -> 'ي' // small high yeh
                else -> c
            }
        }.joinToString("")
        // 4. Strip marks and 5. fold letters, in one pass.
        val sb = StringBuilder(s.length)
        for (c in s) {
            val code = c.code
            // Strip: harakat (U+064B–U+065F, incl. madda/hamza-above/below —
            // a remaining U+0654/U+0655 after NFC is a decomposed hamza on
            // و/ي, and deleting it is exactly the seat-preserving fold),
            // tatweel U+0640, Quranic annotation marks, invisible/bidi
            // controls (copy-paste inserts these inside words).
            if (code in 0x064B..0x065F || code == 0x0640 ||
                code in 0x0610..0x061A || code in 0x06D6..0x06DC || code in 0x06DF..0x06ED ||
                code in 0x08D4..0x08E2 ||
                code in 0x200B..0x200F || code == 0x061C || code in 0x202A..0x202E ||
                code in 0x2066..0x2069 || code == 0xFEFF) {
                continue
            }
            // Fold letters. Tier 1 (Lucene/CAMeL core) first, then Tier 2
            // recall extensions. Unicode escapes are deliberate: several
            // of these look identical to their canonical form (Farsi yeh
            // ی U+06CC vs Arabic yeh ي U+064A) and must not be confused.
            when (c) {
                // Tier 1: alef forms → ا
                'أ', 'إ', 'آ', '\u0671', '\u0672', '\u0673' -> sb.append('ا')
                // Tier 1: alef maksura → ي ; teh marbuta → ه
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                // Tier 2: hamza Policy B (seat-preserving) — ؤ→و, ئ→ي,
                // standalone ء deleted (مؤمن→مومن).
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                'ء' -> { /* delete */ }
                // Tier 2: Persian-keyboard lookalikes (Arabic-only app).
                '\u06CC' -> sb.append('ي') // Farsi yeh
                '\u06A9' -> sb.append('ك') // keheh
                // Tier 2: Urdu goal forms → ه
                '\u06C1', '\u06C3', '\u06C2', '\u06C0' -> sb.append('ه')
                // Tier 3: Urdu yeh forms → ي
                '\u06D2', '\u06D3', '\u06CD' -> sb.append('ي')
                else -> sb.append(c)
            }
        }
        // 6. Collapse runs of doubled و/ي produced by the hamza fold
        // (رئيس→رييس→ريس, مسؤول→مسوول→مسول). Recall extension, not a
        // standard analyzer rule — see the over-normalization note above.
        return collapseDoubledWawYeh(sb.toString())
    }

    /**
     * Collapse runs of identical و or ي to a single letter. Recall
     * extension for the hamza fold (Policy B): ئ→ي and ؤ→و can create a
     * doubled letter that the user typed as a single one. Only و/ي are
     * collapsed — other doubled letters are left alone.
     */
    private fun collapseDoubledWawYeh(text: String): String {
        if (text.length < 2) return text
        val sb = StringBuilder(text.length)
        var prev = '\u0000'
        for (c in text) {
            if ((c == 'و' || c == 'ي') && c == prev) continue
            sb.append(c)
            prev = c
        }
        return sb.toString()
    }

    /**
     * Strip Arabic diacritics (tashkeel) and tatweel (kashida) from text for
     * search normalization. Allows users to find verses whether they type
     * with or without marks, or paste text with elongation.
     * @deprecated Use normalizeForSearch() which also normalizes letter variants.
     */
    fun stripTashkeel(text: String): String {
        return normalizeForSearch(text)
    }

    fun searchVerses(query: String): List<BibleVerse> {
        // v1.9.19: normalizeForSearch() is the full variant-insensitive
        // table (13 tiered groups). The match runs against the
        // search-only searchWord column, rebuilt with the SAME function
        // — the v1.9.5 index was built with a weaker normalizer, so a
        // query for يتامى (normalized to يتامي) found nothing. word
        // itself is NEVER touched, so it can still be updated/corrected
        // later. Display text comes from word, not searchWord.
        val q = normalizeForSearch(query.trim())
        if (q.isEmpty()) return emptyList()
        val helper = dbHelper ?: return emptyList()

        val arDb = helper.openDatabase(BibleDatabaseHelper.ARABIC_DB)
        val results = mutableListOf<BibleVerse>()

        arDb?.let { db ->
            val cursor = db.rawQuery(
                "SELECT bookNum, chNum, verseNum, word FROM words WHERE searchWord LIKE ? LIMIT 100",
                arrayOf("%$q%")
            )
            if (cursor.moveToFirst()) {
                do {
                    val bNum = cursor.getInt(0)
                    val cNum = cursor.getInt(1)
                    val vNum = cursor.getInt(2)
                    val arText = cursor.getString(3)
                    val book = allBooks.getOrNull(bNum - 1) ?: continue
                    results.add(
                        BibleVerse(
                            id = "${book.id}_${cNum}_$vNum",
                            bookId = book.id,
                            bookArabicName = book.arabicName,
                            bookEnglishName = book.englishName,
                            chapter = cNum,
                            verse = vNum,
                            arabicText = arText,
                            englishText = ""
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return results
    }

    /**
     * Count of verses matching a search query (v1.9.19 item 4). Same
     * normalized pattern as [searchVerses], but returns one integer —
     * no verse objects are loaded. The count is the TRUE total
     * ([searchVerses] caps its list at 100), so the UI can show e.g.
     * "150 verses" while listing the first 100.
     */
    fun searchVersesCount(query: String): Int {
        val q = normalizeForSearch(query.trim())
        if (q.isEmpty()) return 0
        val helper = dbHelper ?: return 0
        val arDb = helper.openDatabase(BibleDatabaseHelper.ARABIC_DB)
        var count = 0
        arDb?.let { db ->
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM words WHERE searchWord LIKE ?",
                arrayOf("%$q%")
            )
            if (cursor.moveToFirst()) count = cursor.getInt(0)
            cursor.close()
        }
        return count
    }

    fun addCustomVerse(verse: BibleVerse) {}
}
