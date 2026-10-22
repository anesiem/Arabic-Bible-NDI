package com.arabicchristianmedia.model

/**
 * v1.9.1 sermon list model.
 *
 * The working sermon list is a list of [SermonEntry] — a canonical database
 * verse plus the share-group it arrived in. Grouping is PER REFERENCE
 * (Ashraf's ruling): every reference the scanner finds in a shared note —
 * including its whole range expansion — is one group, and groups alternate
 * between two shades so the operator can see where one selection ends and
 * the next begins. Only references are persisted (see SermonStore); the
 * verse text is always re-expanded from the database so the tashkeel
 * setting applies fresh.
 */
data class SermonRef(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val groupId: Long,
    val shade: Int
)

data class SermonEntry(
    val verse: BibleVerse,
    val groupId: Long,
    val shade: Int
) {
    /** Identity of the verse itself (book:chapter:verse) for dedupe/lookup. */
    val key: String get() = "${verse.bookId}:${verse.chapter}:${verse.verse}"

    fun toRef(): SermonRef = SermonRef(verse.bookId, verse.chapter, verse.verse, groupId, shade)
}

/** A named, saved sermon list (the user's library of services). */
data class SavedSermon(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val entries: List<SermonRef>
)

/**
 * Pure shade-assignment rules for sermon groups (unit-tested).
 *
 * Shades are 0/1 and alternate per group in display order. A batch placed
 * on TOP of an existing list must satisfy one extra constraint: the batch's
 * LAST group (the one that ends up adjacent to the existing top group)
 * must differ from the existing top group's shade. Existing groups are
 * never recolored.
 */
/**
 * v1.9.1: user-selectable alternating row colors for the sermon list —
 * curated pairs, one set per screen mode (the stock theme pair swung
 * between a saturated violet and a bright lavender in dark mode, which
 * was harsh on the eyes). The user picks a pair per mode in the Sermon
 * tab's palette dialog; defaults are D1 / L1. Colors are ARGB longs.
 */
data class SermonShadePair(
    val id: String,
    val displayName: String,
    val shadeA: Long,
    val shadeB: Long
)

object SermonShadePairs {
    val DARK: List<SermonShadePair> = listOf(
        SermonShadePair("d1", "Dusty Violet / Slate", 0xFF3B3052, 0xFF2B3A42),
        SermonShadePair("d2", "Plum / Smoked Olive", 0xFF413147, 0xFF37402F),
        SermonShadePair("d3", "Midnight / Deep Teal", 0xFF2E3A55, 0xFF274743),
        SermonShadePair("d4", "Espresso / Aubergine", 0xFF40342C, 0xFF382E47)
    )
    val LIGHT: List<SermonShadePair> = listOf(
        SermonShadePair("l1", "Lavender / Mist Blue", 0xFFEAE3F6, 0xFFDFE9F3),
        SermonShadePair("l2", "Blush / Sage", 0xFFF6E3E6, 0xFFE3EFE2),
        SermonShadePair("l3", "Sand / Sky", 0xFFF3ECDC, 0xFFE0EBF5),
        SermonShadePair("l4", "Lilac / Mint", 0xFFEDE4F7, 0xFFE0F2EA)
    )

    fun dark(id: String?): SermonShadePair = DARK.firstOrNull { it.id == id } ?: DARK[0]
    fun light(id: String?): SermonShadePair = LIGHT.firstOrNull { it.id == id } ?: LIGHT[0]
}

object SermonGrouping {

    /**
     * Shades for a fresh batch of [groupCount] groups (in display order)
     * prepended above a list whose current top group has [existingTopShade]
     * (null when the list is empty).
     */
    fun shadesForNewBatch(existingTopShade: Int?, groupCount: Int): List<Int> {
        if (groupCount <= 0) return emptyList()
        val lastParity = (groupCount - 1) % 2
        val base = if (existingTopShade == null) 0
                   else (existingTopShade xor lastParity xor 1)
        return (0 until groupCount).map { i -> base xor (i % 2) }
    }

    /**
     * Shades for groups merged in from a saved sermon. The incoming groups
     * keep their stored RELATIVE alternation pattern; the whole set is
     * flipped if needed so its last group differs from the existing top
     * group's shade. Returns one shade per incoming group, in stored order.
     */
    fun reshadeForMerge(existingTopShade: Int?, storedGroupShades: List<Int>): List<Int> {
        if (storedGroupShades.isEmpty()) return emptyList()
        val origin = storedGroupShades.first()
        val relative = storedGroupShades.map { it xor origin }
        if (existingTopShade == null) return storedGroupShades
        val lastRelative = relative.last()
        // Want: base xor lastRelative != existingTopShade
        val base = existingTopShade xor lastRelative xor 1
        return relative.map { it xor base }
    }
}
