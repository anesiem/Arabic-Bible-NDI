package com.arabicchristianmedia.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Item 10: recents persistence round-trips — references only (book /
 * chapter / verse), empty by default, and cleared by the user reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentStoreTest {

    private lateinit var store: RecentStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("recent_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = RecentStore(context)
    }

    @Test
    fun recentsRoundTripRefs() {
        val refs = listOf(
            RecentRef("jhn", 3, 16),
            RecentRef("psa", 23, 1),
            RecentRef("rom", 8, 28)
        )
        store.save(refs)
        assertEquals("recents must round-trip exactly", refs, store.load())
    }

    @Test
    fun recentsDefaultToEmptyAndClear() {
        assertEquals("fresh store has empty recents", emptyList<RecentRef>(), store.load())
        store.save(listOf(RecentRef("gen", 1, 1)))
        store.clear()
        assertEquals("clearing removes all recents", emptyList<RecentRef>(), store.load())
    }
}
