package com.arabicchristianmedia.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arabicchristianmedia.model.ClearMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Clear Mode JSON migration (2026-10-28 hotfix): an old save without
 * a clearMode key defaults by feed — Full -> verse only, Lower ->
 * all — and an explicit key always wins. Uses the public
 * templateFromJsonString path, so it covers the same parsing the
 * Editor import and the stored templates use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClearModeMigrationTest {

    private lateinit var repo: TemplateRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        repo = TemplateRepository(context)
    }

    private fun oldJson(isFull: Boolean): String =
        """{"id":"old1","name":"Old","isFullScreen":$isFull,"fontFamily":"Amiri","bgColorHex":"#000000","textColorHex":"#FFFFFF","verseFontSize":30,"style":"TRANSPARENT_OUTLINE"}"""

    @Test
    fun oldFullMigratesToVerseOnly() {
        val tpl = repo.templateFromJsonString(oldJson(true))!!
        assertEquals(ClearMode.CLEAR_VERSE_ONLY, tpl.clearMode)
    }

    @Test
    fun oldLowerMigratesToAll() {
        val tpl = repo.templateFromJsonString(oldJson(false))!!
        assertEquals(ClearMode.CLEAR_ALL, tpl.clearMode)
    }

    @Test
    fun explicitKeyWinsOverFeedDefault() {
        val json = """{"id":"old2","name":"Old","isFullScreen":true,"clearMode":"CLEAR_ALL","fontFamily":"Amiri","bgColorHex":"#000000","textColorHex":"#FFFFFF","verseFontSize":30,"style":"TRANSPARENT_OUTLINE"}"""
        val tpl = repo.templateFromJsonString(json)!!
        assertEquals(ClearMode.CLEAR_ALL, tpl.clearMode)
    }

    @Test
    fun clearModeRoundTripsThroughJson() {
        val tpl = repo.templateFromJsonString(oldJson(false))!!.copy(clearMode = ClearMode.CLEAR_VERSE_ONLY)
        val back = repo.templateFromJsonString(repo.templateToJsonString(tpl))!!
        assertEquals(ClearMode.CLEAR_VERSE_ONLY, back.clearMode)
    }
}
