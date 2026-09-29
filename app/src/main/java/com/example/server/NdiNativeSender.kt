package com.example.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*

/**
 * NdiNativeSender implements direct integration with the NDI 6 SDK for Android.
 *
 * Dual-tier design (per feed: Lower Third / Full Show):
 * - FULL tier: 1920x1080 BGRA with alpha, sent on every content change.
 * - HX tier ("bandwidth saver"): 960x540 BGRA with alpha for slow networks.
 *
 * NOTE on true NDI|HX: the standard NDI SDK's send API exposes no codec/HX option
 * (see Processing.NDI.Send.h: NDIlib_send_create_t has name/groups/clock only).
 * HX *encoding* requires Vizrt's paid NDI Advanced SDK. Until then, the HX tier is a
 * reduced-resolution / reduced-rate full-NDI stream: same protocol (OBS/vMix discover
 * it with zero setup), a fraction of the bandwidth. If an Advanced SDK becomes
 * available, only [sendBitmapToPtr]/the native layer needs to change.
 *
 * Bandwidth saving: frames are only pushed to the NDI SDK when the rendered content
 * actually changed (frame-version dirty check) plus a low-rate heartbeat so receivers
 * stay alive. Static verses no longer burn CPU/network at 15 FPS.
 */
class NdiNativeSender {

    /** Per-source session. Mutable fields are only touched from the session's own coroutine. */
    private class SenderSession(
        val ptr: Long,
        val feedKey: String,
        val isFullScreen: Boolean,
        val targetWidth: Int,
        val targetHeight: Int
    ) {
        var job: Job? = null
        /** Reused downscale target for the HX tier: zero allocation in the hot loop. */
        var scaledBitmap: Bitmap? = null
        @Volatile var lastSentVersion: Long = -1L
        @Volatile var lastSentAtMs: Long = 0L
    }

    private val activeSenders = mutableMapOf<String, SenderSession>()
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default)

    private var frameProvider: ((Boolean) -> Bitmap)? = null
    private var frameVersionProvider: (() -> Long)? = null

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

        /** HX (bandwidth-saver) tier resolution: quarter pixels of full HD. */
        const val HX_WIDTH = 960
        const val HX_HEIGHT = 540

        const val FEED_LOWER = "Bible-NDI-Lower"
        const val FEED_LOWER_HX = "Bible-NDI-Lower-HX"
        const val FEED_FULL = "Bible-NDI-Full"
        const val FEED_FULL_HX = "Bible-NDI-Full-HX"

        /** Canonical on-air source name: "<TabletModel> - <FeedName>", e.g. "SM-X238U - Bible-NDI-Lower". */
        fun displayName(feedKey: String): String = "${Build.MODEL} - $feedKey"
    }

    // --- Native JNI Methods ---
    private external fun nativeInitialize(): Boolean
    private external fun nativeCreateSender(name: String): Long
    private external fun nativeDestroySender(ptr: Long)
    /** Zero-copy path: native code locks the Bitmap pixels directly (no IntArray round-trip). */
    private external fun nativeSendVideoBitmap(ptr: Long, bitmap: Bitmap, width: Int, height: Int): Boolean

    fun initialize(): Boolean {
        if (isInitialized) return true
        try {
            Log.i("NdiNativeSender", "Initializing NDI 6 Native Stack...")
            try { System.loadLibrary("c++_shared") } catch (e: Throwable) {}
            try { System.loadLibrary("ndi") } catch (e: Throwable) {
                Log.e("NdiNativeSender", "FAILED to load libndi.so", e)
                return false
            }
            try { System.loadLibrary("ndi_wrapper") } catch (e: Throwable) {
                Log.e("NdiNativeSender", "FAILED to load libndi_wrapper.so", e)
                return false
            }

            if (nativeInitialize()) {
                isInitialized = true
                return true
            }
            return false
        } catch (t: Throwable) {
            Log.e("NdiNativeSender", "NDI initialization critical failure", t)
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

    fun startSource(
        feedKey: String,
        isFullScreen: Boolean,
        targetWidth: Int = FRAME_WIDTH,
        targetHeight: Int = FRAME_HEIGHT
    ): Boolean {
        if (!isInitialized && !initialize()) {
            Log.w("NdiNativeSender", "Cannot start $feedKey: NDI stack failed to initialize")
            return false
        }

        synchronized(activeSenders) {
            if (activeSenders.containsKey(feedKey)) return true

            val ptr = nativeCreateSender(displayName(feedKey))
            if (ptr == 0L) {
                Log.e("NdiNativeSender", "NDIlib_send_create failed for $feedKey")
                return false
            }

            val session = SenderSession(ptr, feedKey, isFullScreen, targetWidth, targetHeight)
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
                        Log.w("NdiNativeSender", "Send loop error for $feedKey: ${e.message}")
                    }
                    delay(idlePollMs)
                }
            }
            activeSenders[feedKey] = session
            isRunning = true
            Log.i(
                "NdiNativeSender",
                "NDI Source started: ${displayName(feedKey)} " +
                    "(FS=$isFullScreen, ${targetWidth}x$targetHeight)"
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
                Log.w("NdiNativeSender", "Error stopping $feedKey: ${e.message}")
            }
            if (activeSenders.isEmpty()) isRunning = false
            Log.i("NdiNativeSender", "NDI Source stopped: ${displayName(feedKey)}")
        }
    }

    /** Push one frame immediately (e.g. right after a verse/template change). */
    fun triggerFrame(feedKey: String, isFullScreen: Boolean) {
        scope.launch {
            val session = synchronized(activeSenders) { activeSenders[feedKey] } ?: return@launch
            try {
                val bmp = frameProvider?.invoke(isFullScreen) ?: return@launch
                if (sendBitmapToPtr(session, bmp)) {
                    session.lastSentVersion = frameVersionProvider?.invoke() ?: session.lastSentVersion
                    session.lastSentAtMs = SystemClock.elapsedRealtime()
                }
            } catch (e: Exception) {
                Log.w("NdiNativeSender", "triggerFrame error for $feedKey: ${e.message}")
            }
        }
    }

    private fun sendBitmapToPtr(session: SenderSession, bitmap: Bitmap): Boolean {
        return try {
            if (bitmap.isRecycled) {
                Log.w("NdiNativeSender", "Skipping recycled bitmap for ${session.feedKey}")
                return false
            }
            val targetBitmap = if (bitmap.width == session.targetWidth && bitmap.height == session.targetHeight) {
                bitmap
            } else {
                // HX tier: downscale into a per-sender REUSED bitmap (no per-frame allocation).
                var scaled = session.scaledBitmap
                if (scaled == null || scaled.isRecycled ||
                    scaled.width != session.targetWidth || scaled.height != session.targetHeight
                ) {
                    try { scaled?.recycle() } catch (e: Exception) {}
                    scaled = Bitmap.createBitmap(session.targetWidth, session.targetHeight, Bitmap.Config.ARGB_8888)
                    session.scaledBitmap = scaled
                }
                val canvas = Canvas(scaled)
                canvas.drawBitmap(
                    bitmap, null,
                    Rect(0, 0, session.targetWidth, session.targetHeight), null
                )
                scaled
            }

            nativeSendVideoBitmap(session.ptr, targetBitmap, session.targetWidth, session.targetHeight)
        } catch (e: Exception) {
            Log.w("NdiNativeSender", "Error sending frame for ${session.feedKey}: ${e.message}")
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
                    Log.w("NdiNativeSender", "Error stopping ${session.feedKey}: ${e.message}")
                }
            }
        }
    }
}
