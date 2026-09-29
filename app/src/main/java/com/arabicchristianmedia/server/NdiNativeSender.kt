package com.arabicchristianmedia.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

/** User-configurable spec for one NDI source: resolution + frame rate. */
data class NdiSourceSpec(
    val feedKey: String,
    val width: Int,
    val height: Int,
    val fps: Int
)

/** One NDI error event, shown to the user in the Advanced diagnostics panel. */
data class NdiErrorEvent(
    val time: String,
    val source: String,
    val message: String
)

/**
 * NdiNativeSender implements direct integration with the NDI 6 SDK for Android.
 *
 * Dual-tier design (per feed: Lower Third / Full Show):
 * - FULL tier: BGRA with alpha, for overlaying verses (transparency keying in OBS/vMix).
 * - HX tier ("bandwidth saver"): reduced resolution for slow networks.
 *
 * Every source is user-configurable: resolution and frame rate are chosen from
 * dropdown lists in the app (see [RESOLUTION_OPTIONS] / [FPS_OPTIONS]) — nothing
 * is hardcoded. Frames are only pushed when content changes (dirty-frame
 * detection) plus a low-rate heartbeat, so static verses cost ~zero bandwidth.
 */
class NdiNativeSender {

    /** Per-source session. Mutable fields are only touched from the session's own coroutine. */
    private class SenderSession(
        val ptr: Long,
        val spec: NdiSourceSpec,
        val isFullScreen: Boolean
    ) {
        var job: Job? = null
        /** Reused downscale target for reduced resolutions: zero allocation in the hot loop. */
        var scaledBitmap: Bitmap? = null
        @Volatile var lastSentVersion: Long = -1L
        @Volatile var lastSentAtMs: Long = 0L
    }

    private val activeSenders = mutableMapOf<String, SenderSession>()
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default)

    private var frameProvider: ((Boolean) -> Bitmap)? = null
    private var frameVersionProvider: (() -> Long)? = null

    /** Recent NDI error events for the Advanced diagnostics panel (capped, oldest dropped). */
    private val errorEvents = Collections.synchronizedList(mutableListOf<NdiErrorEvent>())
    private val lastErrorAtPerSource = mutableMapOf<String, Long>()

    @Volatile
    var isRunning = false
        private set

    /**
     * How often an *unchanged* frame is re-sent to keep NDI receivers alive.
     * Exposed (not hardcoded) per project AI preferences.
     */
    var heartbeatIntervalMs: Long = 2000L

    /** Idle poll cadence while waiting for content changes. */
    var idlePollMs: Long = 120L

    companion object {
        const val FRAME_WIDTH = 1920
        const val FRAME_HEIGHT = 1080

        /** HX (bandwidth-saver) tier default resolution: quarter pixels of full HD. */
        const val HX_WIDTH = 960
        const val HX_HEIGHT = 540

        const val FEED_LOWER = "Bible-NDI-Lower"
        const val FEED_LOWER_HX = "Bible-NDI-Lower-HX"
        const val FEED_FULL = "Bible-NDI-Full"
        const val FEED_FULL_HX = "Bible-NDI-Full-HX"

        /** All four feed keys, in display order. */
        val ALL_FEEDS = listOf(FEED_LOWER, FEED_LOWER_HX, FEED_FULL, FEED_FULL_HX)

        /** User-selectable resolutions (width to height), shown in dropdown lists. */
        val RESOLUTION_OPTIONS = listOf(
            1920 to 1080,
            1280 to 720,
            960 to 540,
            854 to 480,
            640 to 360
        )

        /** User-selectable frame rates (fps), shown in dropdown lists. */
        val FPS_OPTIONS = listOf(30, 25, 24, 15, 10, 5)

        fun defaultSpec(feedKey: String): NdiSourceSpec = when (feedKey) {
            FEED_LOWER_HX, FEED_FULL_HX -> NdiSourceSpec(feedKey, HX_WIDTH, HX_HEIGHT, 15)
            else -> NdiSourceSpec(feedKey, FRAME_WIDTH, FRAME_HEIGHT, 30)
        }

        /** Canonical on-air source name: "<TabletModel> - <FeedName>", e.g. "SM-X238U - Bible-NDI-Lower". */
        fun displayName(feedKey: String): String = "${Build.MODEL} - $feedKey"
    }

    // --- Native JNI Methods ---
    private external fun nativeInitialize(): Boolean
    private external fun nativeCreateSender(name: String, fps: Int): Long
    private external fun nativeDestroySender(ptr: Long)
    /** Zero-copy path: native code locks the Bitmap pixels directly (no IntArray round-trip). */
    private external fun nativeSendVideoBitmap(ptr: Long, bitmap: Bitmap, width: Int, height: Int): Boolean

    fun initialize(): Boolean {
        if (isInitialized) return true
        try {
            Log.i("NdiNativeSender", "Initializing NDI 6 Native Stack...")
            try { System.loadLibrary("c++_shared") } catch (e: Throwable) {}
            try { System.loadLibrary("ndi") } catch (e: Throwable) {
                reportError("init", "FAILED to load libndi.so: ${e.message}")
                return false
            }
            try { System.loadLibrary("ndi_wrapper") } catch (e: Throwable) {
                reportError("init", "FAILED to load libndi_wrapper.so: ${e.message}")
                return false
            }

            if (nativeInitialize()) {
                isInitialized = true
                return true
            }
            reportError("init", "NDIlib_initialize() returned false")
            return false
        } catch (t: Throwable) {
            reportError("init", "NDI initialization critical failure: ${t.message}")
            return false
        }
    }

    fun setFrameProvider(provider: (Boolean) -> Bitmap) {
        frameProvider = provider
    }

    /** Supplies the renderer's monotonically increasing frame version for dirty checking. */
    fun setFrameVersionProvider(provider: () -> Long) {
        frameVersionProvider = provider
    }

    fun isSourceActive(feedKey: String): Boolean =
        synchronized(activeSenders) { activeSenders.containsKey(feedKey) }

    fun getErrorEvents(): List<NdiErrorEvent> =
        synchronized(errorEvents) { errorEvents.toList() }

    fun clearErrorEvents() {
        synchronized(errorEvents) { errorEvents.clear() }
    }

    private fun reportError(source: String, message: String, throttleMs: Long = 0L) {
        if (throttleMs > 0L) {
            val now = SystemClock.elapsedRealtime()
            val last = synchronized(lastErrorAtPerSource) { lastErrorAtPerSource[source] } ?: 0L
            if (now - last < throttleMs) return
            synchronized(lastErrorAtPerSource) { lastErrorAtPerSource[source] = now }
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        synchronized(errorEvents) {
            errorEvents.add(NdiErrorEvent(time, source, message))
            while (errorEvents.size > 50) errorEvents.removeAt(0)
        }
        Log.w("NdiNativeSender", "[$source] $message")
    }

    fun startSource(feedKey: String, isFullScreen: Boolean, spec: NdiSourceSpec): Boolean {
        if (!isInitialized && !initialize()) {
            reportError(feedKey, "Cannot start: NDI stack failed to initialize")
            return false
        }

        synchronized(activeSenders) {
            if (activeSenders.containsKey(feedKey)) return true

            val ptr = nativeCreateSender(displayName(feedKey), spec.fps)
            if (ptr == 0L) {
                reportError(feedKey, "NDIlib_send_create failed (${spec.width}x${spec.height}@${spec.fps})")
                return false
            }

            val session = SenderSession(ptr, spec, isFullScreen)
            session.job = scope.launch {
                while (isActive) {
                    try {
                        val version = frameVersionProvider?.invoke() ?: 0L
                        val now = SystemClock.elapsedRealtime()
                        val dirty = version != session.lastSentVersion
                        val heartbeatDue = now - session.lastSentAtMs >= heartbeatIntervalMs
                        if (dirty || heartbeatDue) {
                            val bmp = frameProvider?.invoke(isFullScreen)
                            if (bmp != null && sendBitmapToPtr(session, bmp)) {
                                session.lastSentVersion = version
                                session.lastSentAtMs = now
                            }
                        }
                    } catch (e: Exception) {
                        reportError(feedKey, "Send loop error: ${e.message}", throttleMs = 10_000L)
                    }
                    delay(idlePollMs)
                }
            }
            activeSenders[feedKey] = session
            isRunning = true
            Log.i(
                "NdiNativeSender",
                "NDI Source started: ${displayName(feedKey)} " +
                    "(FS=$isFullScreen, ${spec.width}x${spec.height}@${spec.fps})"
            )
        }
        return true
    }

    fun stopSource(feedKey: String) {
        synchronized(activeSenders) {
            val session = activeSenders.remove(feedKey) ?: return
            try {
                session.job?.cancel()
                try { session.scaledBitmap?.recycle() } catch (e: Exception) {}
                session.scaledBitmap = null
                nativeDestroySender(session.ptr)
            } catch (e: Exception) {
                reportError(feedKey, "Error stopping source: ${e.message}")
            }
            if (activeSenders.isEmpty()) isRunning = false
            Log.i("NdiNativeSender", "NDI Source stopped: ${displayName(feedKey)}")
        }
    }

    private val triggerJobs = mutableMapOf<String, Job>()

    /** Push one frame immediately (e.g. right after a verse/template change). */
    fun triggerFrame(feedKey: String, isFullScreen: Boolean) {
        synchronized(triggerJobs) {
            triggerJobs[feedKey]?.cancel()
            triggerJobs[feedKey] = scope.launch {
                delay(120) // debounce rapid font size / slider adjustments by 120ms
                val session = synchronized(activeSenders) { activeSenders[feedKey] } ?: return@launch
                try {
                    val bmp = frameProvider?.invoke(isFullScreen) ?: return@launch
                    if (sendBitmapToPtr(session, bmp)) {
                        session.lastSentVersion = frameVersionProvider?.invoke() ?: session.lastSentVersion
                        session.lastSentAtMs = SystemClock.elapsedRealtime()
                    }
                } catch (e: Exception) {
                    reportError(feedKey, "triggerFrame error: ${e.message}", throttleMs = 10_000L)
                }
            }
        }
    }

    private fun sendBitmapToPtr(session: SenderSession, bitmap: Bitmap): Boolean {
        return try {
            if (bitmap.isRecycled) {
                reportError(session.spec.feedKey, "Skipped recycled bitmap", throttleMs = 10_000L)
                return false
            }
            val spec = session.spec
            val targetBitmap = if (bitmap.width == spec.width && bitmap.height == spec.height) {
                bitmap
            } else {
                // Reduced resolution: downscale into a per-sender REUSED bitmap (no per-frame allocation).
                var scaled = session.scaledBitmap
                if (scaled == null || scaled.isRecycled ||
                    scaled.width != spec.width || scaled.height != spec.height
                ) {
                    try { scaled?.recycle() } catch (e: Exception) {}
                    scaled = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
                    session.scaledBitmap = scaled
                }
                val canvas = Canvas(scaled)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.drawBitmap(
                    bitmap, null,
                    Rect(0, 0, spec.width, spec.height), null
                )
                scaled
            }

            val ok = nativeSendVideoBitmap(session.ptr, targetBitmap, spec.width, spec.height)
            if (!ok) reportError(spec.feedKey, "nativeSendVideoBitmap returned false", throttleMs = 10_000L)
            ok
        } catch (e: Exception) {
            reportError(session.spec.feedKey, "Error sending frame: ${e.message}", throttleMs = 10_000L)
            false
        }
    }

    fun stopAll() {
        synchronized(activeSenders) {
            val sessions = activeSenders.values.toList()
            activeSenders.clear()
            isRunning = false
            sessions.forEach { session ->
                try {
                    session.job?.cancel()
                    try { session.scaledBitmap?.recycle() } catch (e: Exception) {}
                    session.scaledBitmap = null
                    nativeDestroySender(session.ptr)
                } catch (e: Exception) {
                    reportError(session.spec.feedKey, "Error stopping source: ${e.message}")
                }
            }
        }
    }
}
