package com.arabicchristianmedia.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arabicchristianmedia.model.SavedSermon
import com.arabicchristianmedia.model.SermonRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.9.1: sermon persistence round-trips — working list (refs + group +
 * shade), saved sermons library, and the current-sermon pointer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SermonStoreTest {

    private lateinit var store: SermonStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sermon_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = SermonStore(context)
    }

    @Test
    fun workingListRoundTripsRefsGroupsAndShades() {
        val refs = listOf(
            SermonRef("heb", 7, 3, groupId = 10L, shade = 0),
            SermonRef("heb", 7, 4, groupId = 10L, shade = 0),
            SermonRef("heb", 7, 5, groupId = 10L, shade = 0),
            SermonRef("heb", 7, 16, groupId = 11L, shade = 1),
            SermonRef("jhn", 3, 16, groupId = 12L, shade = 0)
        )
        store.saveWorking(refs)
        assertEquals("working list must round-trip exactly", refs, store.loadWorking())
    }

    @Test
    fun workingListDefaultsToEmptyAndClears() {
        assertEquals("fresh store has an empty working list", emptyList<SermonRef>(), store.loadWorking())
        store.saveWorking(listOf(SermonRef("gen", 1, 1, 1L, 0)))
        store.saveWorking(emptyList())
        assertEquals("saving an empty list clears the working list", emptyList<SermonRef>(), store.loadWorking())
    }

    @Test
    fun savedSermonsRoundTripWithEntries() {
        val saved = listOf(
            SavedSermon(
                id = "sermon_1", name = "عظة الأحد", updatedAt = 12345L,
                entries = listOf(
                    SermonRef("heb", 9, 7, groupId = 3L, shade = 1),
                    SermonRef("heb", 9, 8, groupId = 3L, shade = 1)
                )
            ),
            SavedSermon(
                id = "sermon_2", name = "Wednesday", updatedAt = 999L,
                entries = emptyList()
            )
        )
        store.saveSaved(saved)
        assertEquals("saved sermons must round-trip exactly", saved, store.loadSaved())
    }

    @Test
    fun currentSermonPointerRoundTrips() {
        assertNull("fresh store has no current sermon", store.getCurrentId())
        store.setCurrent("sermon_1", "عظة الأحد")
        assertEquals("current id round-trips", "sermon_1", store.getCurrentId())
        assertEquals("current name round-trips", "عظة الأحد", store.getCurrentName())
        store.setCurrent(null, null)
        assertNull("clearing the pointer nulls the id", store.getCurrentId())
        assertNull("clearing the pointer nulls the name", store.getCurrentName())
    }
}
