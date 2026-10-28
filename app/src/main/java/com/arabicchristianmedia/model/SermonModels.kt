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

    /** R1: the ONE band-pair resolution — every list uses this. */
    fun forMode(isDark: Boolean, darkId: String?, lightId: String?): SermonShadePair =
        if (isDark) dark(darkId) else light(lightId)
}

/**
 * Sermon step result (sermon APIs, next/prev).
 *
 * Design choice, documented here because it is a behaviour contract:
 * stepping PAST either end does NOT wrap silently — it returns
 * [error] ("End of sermon list" / "Already at the first verse") and
 * leaves the live verse unchanged. A presenter that wraps by accident
 * restarts the service; a presenter that stops at the end is obvious.
 * An empty list is a separate error. When the live verse is not in the
 * list at all (e.g. the operator triggered a Library verse), next starts
 * at the first sermon verse and prev at the last — entering the list,
 * not failing.
 */
data class SermonStepOutcome(
    val entry: SermonEntry?,
    val index: Int,
    val total: Int,
    val error: String?
)

object SermonNavigator {

    /** First index whose verse matches [activeVerse], or -1 when not in the list. */
    fun currentIndex(entries: List<SermonEntry>, activeVerse: BibleVerse?): Int {
        if (activeVerse == null) return -1
        return entries.indexOfFirst { it.verse.id == activeVerse.id }
    }

    /**
     * Pure step logic, unit-tested: [currentIndex] is -1 when the live
     * verse is not in the list. Returns the target index, or null when
     * stepping would leave the list (empty list, or already at an end).
     */
    fun stepIndex(currentIndex: Int, total: Int, step: Int): Int? {
        if (total <= 0) return null
        if (currentIndex < 0) return if (step > 0) 0 else total - 1
        val target = currentIndex + step
        return if (target in 0 until total) target else null
    }

    fun step(entries: List<SermonEntry>, activeVerse: BibleVerse?, step: Int): SermonStepOutcome {
        if (entries.isEmpty()) {
            return SermonStepOutcome(null, -1, 0, "Sermon list is empty")
        }
        val current = currentIndex(entries, activeVerse)
        val target = stepIndex(current, entries.size, step)
        if (target == null) {
            val error = if (step > 0) "End of sermon list"
                else "Already at the first verse of the sermon list"
            return SermonStepOutcome(null, current, entries.size, error)
        }
        return SermonStepOutcome(entries[target], target, entries.size, null)
    }
}

/** Result of creating a sermon from shared text / a refs list via API. */
data class SermonCreateResult(
    val id: String?,
    val name: String?,
    val entries: List<SermonEntry>,
    val parsedCount: Int,
    /** Segments that looked like a reference but did not expand to a verse. */
    val invalidRefs: List<String>
)

/** Result of a backup import, dry-run or applied. */
data class BackupImportResult(
    val applied: Boolean,
    val sermons: Int,
    val templates: Int,
    val recents: Int,
    val themeId: String?,
    val invalid: List<String>
)

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
