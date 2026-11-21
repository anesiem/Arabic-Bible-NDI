package com.arabicchristianmedia.server

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 1.10.0 (item 1): passive crash + restart recorder — the diagnosis
 * instrument for the background process deaths (six on 2026-11-20, cause
 * still unknown: kill vs crash unproven, Termux logcat own-UID only).
 *
 * Installed once in [com.arabicchristianmedia.BibleNdiApplication.onCreate]
 * (runs before any Activity or Service, so the handler is in place from
 * the first moment the app can crash). The app is single-process, so one
 * install point covers Activity + Service.
 *
 * Two files in the app's files dir, both small and bounded:
 * - `crash_last.txt`: the LAST uncaught exception (timestamp, thread,
 *   app version, stack trace capped at 100 lines / 8 KB). Overwritten by
 *   the next crash, never deleted — the timestamp is the discriminator.
 * - `restart_last.txt`: written by [noteRestart] on every OS-resume
 *   (START_STICKY restart after a kill), recording whether a crash file
 *   existed and its timestamp.
 *
 * Reading the evidence (via /api/debug "process", or the files via ADB):
 * crash file present with a timestamp just before the restart → CRASH,
 * and the trace head names the cause. Restart marker present with no
 * crash file — or a crash file older than the previous boot → the system
 * KILLED the process.
 *
 * SAFETY RULE: the installed handler ALWAYS chains to the captured
 * default handler, in a `finally` block, and the file write is wrapped
 * in try/catch. A handler that swallowed the exception would leave the
 * process alive-but-broken: no START_STICKY restart, no auto-resume, a
 * dead app wearing a live face.
 */
object CrashRecorder {

    private const val CRASH_FILE = "crash_last.txt"
    private const val RESTART_FILE = "restart_last.txt"
    private const val MAX_TRACE_LINES = 100
    private const val MAX_TRACE_CHARS = 8192

    data class LastCrash(
        val atMs: Long,
        val thread: String,
        val version: String,
        val trace: String
    )

    data class LastRestart(
        val atMs: Long,
        val wasOsResume: Boolean,
        val crashFilePresent: Boolean,
        val crashAtMs: Long
    )

    /** Install the uncaught-exception handler, chaining to the current default. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (e: Exception) {
                // A failing write must never break the crash chain —
                // log it and fall through to the finally.
                Log.w("CrashRecorder", "crash write failed", e)
            } finally {
                // ALWAYS chain — see the safety rule above.
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Record an OS-resume restart (called from NdiService's OS-restart
     * branch, BEFORE the render-state restore, so the marker exists even
     * if the restore throws). A normal user start does NOT write a
     * marker — the marker means "this boot followed a death".
     */
    fun noteRestart(context: Context) {
        try {
            val crash = readLastCrash(context)
            val obj = JSONObject().apply {
                put("atMs", System.currentTimeMillis())
                put("wasOsResume", true)
                put("crashFilePresent", crash != null)
                put("crashAtMs", crash?.atMs ?: -1L)
            }
            File(context.filesDir, RESTART_FILE).writeText(obj.toString())
        } catch (e: Exception) {
            Log.w("CrashRecorder", "noteRestart failed", e)
        }
    }

    /** The last recorded crash, or null when no crash file exists / it is unreadable. */
    fun readLastCrash(context: Context): LastCrash? {
        return try {
            val file = File(context.filesDir, CRASH_FILE)
            if (!file.isFile) return null
            val obj = JSONObject(file.readText())
            LastCrash(
                atMs = obj.optLong("atMs", -1L),
                thread = obj.optString("thread", ""),
                version = obj.optString("version", ""),
                trace = obj.optString("trace", "")
            )
        } catch (e: Exception) {
            Log.w("CrashRecorder", "readLastCrash failed", e)
            null
        }
    }

    /** The last recorded OS-resume restart, or null when no marker exists / it is unreadable. */
    fun readLastRestart(context: Context): LastRestart? {
        return try {
            val file = File(context.filesDir, RESTART_FILE)
            if (!file.isFile) return null
            val obj = JSONObject(file.readText())
            LastRestart(
                atMs = obj.optLong("atMs", -1L),
                wasOsResume = obj.optBoolean("wasOsResume", false),
                crashFilePresent = obj.optBoolean("crashFilePresent", false),
                crashAtMs = obj.optLong("crashAtMs", -1L)
            )
        } catch (e: Exception) {
            Log.w("CrashRecorder", "readLastRestart failed", e)
            null
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        // Bounded write — the process is dying; keep it small and fast.
        val trace = throwable.stackTraceToString()
            .lineSequence().take(MAX_TRACE_LINES).joinToString("\n").take(MAX_TRACE_CHARS)
        val obj = JSONObject().apply {
            put("atMs", System.currentTimeMillis())
            put("thread", thread.name)
            put("version", appVersion(context))
            put("trace", trace)
        }
        File(context.filesDir, CRASH_FILE).writeText(obj.toString())
    }

    private fun appVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0)?.versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
}
