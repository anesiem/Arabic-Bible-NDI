package com.arabicchristianmedia.server

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 1.10.0 (item 1): the crash recorder must write the crash, record the
 * restart, and ALWAYS chain to the previous handler — even when the
 * write itself throws (a swallowed crash would leave the process
 * alive-but-broken with no START_STICKY restart).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrashRecorderTest {

    private lateinit var context: Context
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        var calls = 0
        var lastThrowable: Throwable? = null
        var lastThread: Thread? = null
        override fun uncaughtException(t: Thread, e: Throwable) {
            calls++
            lastThread = t
            lastThrowable = e
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        // Isolate the recorder files per test.
        File(context.filesDir, "crash_last.txt").delete()
        File(context.filesDir, "restart_last.txt").delete()
    }

    @After
    fun tearDown() {
        // Never leak the test handler into other test classes.
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    private fun invokeInstalledHandler(throwable: Throwable) {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull("CrashRecorder.install must install a handler", handler)
        handler!!.uncaughtException(Thread.currentThread(), throwable)
    }

    @Test
    fun `crash write round-trips and chains to the previous handler`() {
        val previous = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        CrashRecorder.install(context)

        val boom = RuntimeException("boom-test")
        invokeInstalledHandler(boom)

        assertEquals("the previous handler must be chained", 1, previous.calls)
        assertEquals(boom, previous.lastThrowable)
        val crash = CrashRecorder.readLastCrash(context)
        assertNotNull("the crash file must be written", crash)
        assertEquals(Thread.currentThread().name, crash!!.thread)
        assertTrue("trace must name the exception", crash.trace.contains("boom-test"))
        assertTrue("timestamp must be set", crash.atMs > 0L)
        assertNull("no restart marker before a restart", CrashRecorder.readLastRestart(context))
    }

    @Test
    fun `restart marker records the crash file when one exists`() {
        val previous = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        CrashRecorder.install(context)
        invokeInstalledHandler(RuntimeException("boom-test"))

        CrashRecorder.noteRestart(context)

        val crash = CrashRecorder.readLastCrash(context)
        assertNotNull(crash)
        val restart = CrashRecorder.readLastRestart(context)
        assertNotNull("the restart marker must be written", restart)
        assertTrue(restart!!.wasOsResume)
        assertTrue(restart.crashFilePresent)
        assertEquals(crash!!.atMs, restart.crashAtMs)
        assertTrue(restart.atMs >= crash.atMs)
    }

    @Test
    fun `restart marker records no crash file when none exists`() {
        CrashRecorder.noteRestart(context)

        val restart = CrashRecorder.readLastRestart(context)
        assertNotNull(restart)
        assertTrue(restart!!.wasOsResume)
        assertFalse(restart.crashFilePresent)
        assertEquals(-1L, restart.crashAtMs)
    }

    @Test
    fun `a throwing write still chains to the previous handler`() {
        // A directory at the crash path makes the write throw
        // (FileNotFoundException: Is a directory).
        assertTrue(File(context.filesDir, "crash_last.txt").mkdir())
        val previous = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        CrashRecorder.install(context)

        invokeInstalledHandler(RuntimeException("boom-test"))

        assertEquals("the chain must fire even when the write throws", 1, previous.calls)
        assertNull("no crash file is readable when the write failed", CrashRecorder.readLastCrash(context))
    }

    @Test
    fun `install with no previous handler still records the crash`() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        CrashRecorder.install(context)

        invokeInstalledHandler(RuntimeException("boom-test"))

        val crash = CrashRecorder.readLastCrash(context)
        assertNotNull(crash)
        assertTrue(crash!!.trace.contains("boom-test"))
    }
}
