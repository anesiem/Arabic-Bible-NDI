package com.example.data

import android.content.Context
import com.example.model.BibleBook
import com.example.model.BibleVerse
import com.example.model.Testament

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

    fun initialize(context: Context) {
        if (dbHelper == null) {
            dbHelper = BibleDatabaseHelper(context.applicationContext)
        }
    }

    fun getBookById(id: String): BibleBook? {
        return allBooks.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun getVerses(bookId: String, chapter: Int): List<BibleVerse> {
        val helper = dbHelper ?: return emptyList()
        val book = getBookById(bookId) ?: return emptyList()
        val bookNum = allBooks.indexOf(book) + 1

        val arDb = helper.openDatabase(BibleDatabaseHelper.ARABIC_DB)
        val enDb = helper.openDatabase(BibleDatabaseHelper.ENGLISH_DB)

        val verses = mutableListOf<BibleVerse>()

        try {
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
                val cursor = db.rawQuery(
                    "SELECT verseNum, word FROM words WHERE bookNum = ? AND chNum = ? ORDER BY verseNum",
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
        } finally {
            arDb?.close()
            enDb?.close()
        }

        return verses
    }

    fun searchVerses(query: String): List<BibleVerse> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val helper = dbHelper ?: return emptyList()

        val arDb = helper.openDatabase(BibleDatabaseHelper.ARABIC_DB)
        val results = mutableListOf<BibleVerse>()

        try {
            arDb?.let { db ->
                val cursor = db.rawQuery(
                    "SELECT bookNum, chNum, verseNum, word FROM words WHERE word LIKE ? LIMIT 100",
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
        } finally {
            arDb?.close()
        }
        return results
    }

    fun addCustomVerse(verse: BibleVerse) {}
}
