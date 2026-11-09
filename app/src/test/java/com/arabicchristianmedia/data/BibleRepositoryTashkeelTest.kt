package com.arabicchristianmedia.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v2.0: tashkeel stripping for search normalization.
 */
class BibleRepositoryTashkeelTest {

    @Test
    fun stripTashkeel_removesDiacritics() {
        // v1.9.19: stripTashkeel now delegates to normalizeForSearch,
        // so the standalone ء is ALSO deleted (hamza Policy B) —
        // "البدء" → "البد", not "البدء".
        assertEquals(
            "البد",
            BibleRepository.stripTashkeel("الْبَدْءِ")
        )
    }

    @Test
    fun stripTashkeel_removesTatweel() {
        assertEquals(
            "الله",
            BibleRepository.stripTashkeel("اللـه")
        )
    }

    @Test
    fun stripTashkeel_leavesPlainTextUnchanged() {
        // Text with no diacritics, tatweel, or variant letters is
        // unchanged. (The old input "في البدء خلق الله" no longer
        // qualifies: the ء in "البدء" is deleted by the hamza fold.)
        assertEquals(
            "الله خلق السماوات",
            BibleRepository.stripTashkeel("الله خلق السماوات")
        )
    }

    @Test
    fun stripTashkeel_handlesEmpty() {
        assertEquals("", BibleRepository.stripTashkeel(""))
    }

    @Test
    fun stripTashkeel_fullVerse() {
        // v1.9.19: the ء in "البدء" is deleted by the hamza fold.
        assertEquals(
            "في البد خلق الله السماوات والارض",
            BibleRepository.stripTashkeel("فِي الْبَدْءِ خَلَقَ اللهُ السَّمَاوَاتِ وَالارْضَ")
        )
    }

    // v1.9.19 item 3: variant-insensitive normalization — one test per
    // variant group. The query and the searchWord index are normalized
    // with the SAME function, so any spelling of a word meets the stored
    // form. (The end-to-end check — "يتامى" returns the 6 verses — is
    // verified by the DB rebuild script + device test, not here: the
    // unit-test JVM has no asset DB.)

    @Test
    fun normalize_alefMaksura_bothSpellingsMeet() {
        // The reported bug: "يتامى" returned nothing because the query
        // folded ى→ي but the index still had ى.
        assertEquals("يتامي", BibleRepository.normalizeForSearch("يتامى"))
        assertEquals("يتامي", BibleRepository.normalizeForSearch("يتامي"))
    }

    @Test
    fun normalize_tehMarbuta_bothSpellingsMeet() {
        assertEquals("مدرسه", BibleRepository.normalizeForSearch("مدرسة"))
        assertEquals("مدرسه", BibleRepository.normalizeForSearch("مدرسه"))
    }

    @Test
    fun normalize_alefForms_allToBareAlef() {
        assertEquals("احمد", BibleRepository.normalizeForSearch("أحمد"))
        assertEquals("احمد", BibleRepository.normalizeForSearch("إحمد"))
        assertEquals("احمد", BibleRepository.normalizeForSearch("آحمد"))
        // Alef wasla (U+0671) — was missing before v1.9.19.
        assertEquals("الحمد", BibleRepository.normalizeForSearch("ٱلحمد"))
    }

    @Test
    fun normalize_hamzaPolicyB_seatPreserving() {
        // ؤ→و, ئ→ي, standalone ء deleted (مؤمن→مومن).
        assertEquals("مومن", BibleRepository.normalizeForSearch("مؤمن"))
        assertEquals("مومن", BibleRepository.normalizeForSearch("مومن"))
    }

    @Test
    fun normalize_hamzaPolicyB_collapseDoubledWawYeh() {
        // ئ→ي and ؤ→و can create a doubled letter the user typed as
        // one (رئيس→رييس→ريس, مسؤول→مسوول→مسول).
        assertEquals("ريس", BibleRepository.normalizeForSearch("رئيس"))
        assertEquals("ريس", BibleRepository.normalizeForSearch("ريس"))
        assertEquals("مسول", BibleRepository.normalizeForSearch("مسؤول"))
        assertEquals("مسول", BibleRepository.normalizeForSearch("مسول"))
    }

    @Test
    fun normalize_hamzaSeatSwap_knownGap() {
        // KNOWN GAP (state plainly, do not claim coverage): under
        // Policy B, مسئول folds to مسيول, NOT مسول — the seat-swap case
        // still needs query expansion. This test pins the gap so nobody
        // "fixes" it by accident or claims it works.
        assertEquals("مسيول", BibleRepository.normalizeForSearch("مسئول"))
    }

    @Test
    fun normalize_daggerAlef_mappedNeverStripped() {
        // Stripping the dagger alef deletes a pronounced letter
        // (مٰل would become مل); mapping it to ا is correct.
        assertEquals("مال", BibleRepository.normalizeForSearch("مٰل"))
    }

    @Test
    fun normalize_persianKeyboard_yehAndKaf() {
        // Farsi yeh (U+06CC) and keheh (U+06A9) from Persian keyboards /
        // copy-paste fold to their Arabic forms. Escapes are deliberate:
        // these look identical to ي / ك in most fonts.
        assertEquals("يوسف", BibleRepository.normalizeForSearch("یوسف"))
        assertEquals("كتاب", BibleRepository.normalizeForSearch("کتاب"))
    }

    @Test
    fun normalize_invisibleControls_stripped() {
        // Copy-paste inserts zero-width / bidi controls inside words.
        assertEquals("محمد", BibleRepository.normalizeForSearch("مح\u200Bمد"))
        assertEquals("محمد", BibleRepository.normalizeForSearch("\u200Eمحمد"))
    }

    @Test
    fun normalize_urduGoalForms_toHeh() {
        // Urdu goal forms (U+06C1, U+06C3) fold to ه.
        assertEquals("مدرسه", BibleRepository.normalizeForSearch("مدرس\u06C3"))
        assertEquals("مدرسه", BibleRepository.normalizeForSearch("مدرس\u06C1"))
    }
}
