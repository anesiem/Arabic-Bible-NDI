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
