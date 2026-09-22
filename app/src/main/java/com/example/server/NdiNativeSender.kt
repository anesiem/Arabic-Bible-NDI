package com.example.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

/**
 * NdiNativeSender implements direct integration with the NDI 6 SDK for Android.
 */
class NdiNativeSender {

    private val activeSenders = mutableMapOf<String, Long>()
    private val sendJobs = mutableMapOf<String, Job>()
    // Buffer pool to avoid shared state and constant allocations
    private val senderBuffers = mutableMapOf<Long, IntArray>()
    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.Default)

    private var frameProvider: ((Boolean) -> Bitmap)? = null

    @Volatile
    var isRunning = false
        private set

    companion object {
        const val FRAME_WIDTH = 1920
        const val FRAME_HEIGHT = 1080
    }

    // --- Native JNI Methods ---
    private external fun nativeInitialize(): Boolean
    private external fun nativeCreateSender(name: String): Long
    private external fun nativeDestroySender(ptr: Long)
    private external fun nativeSendVideo(ptr: Long, pixels: IntArray, width: Int, height: Int): Boolean

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

    fun startSource(name: String, isFullScreen: Boolean): Boolean {
        if (!isInitialized && !initialize()) return false
        
        synchronized(activeSenders) {
            if (activeSenders.containsKey(name)) return true
            
            // Register as "$machineName - $name" to ensure it's easily identifiable in NDI tools.
            val machineName = Build.MODEL
            val fullName = "$machineName - $name"
            val ptr = nativeCreateSender(fullName)
            if (ptr == 0L) return false
            
            activeSenders[name] = ptr
            isRunning = true
            
            val job = scope.launch {
                while (isActive) {
                    try {
                        val bmp = frameProvider?.invoke(isFullScreen)
                        if (bmp != null) {
                            sendBitmapToPtr(ptr, bmp)
                        }
                    } catch (e: Exception) {}
                    delay(66)
                }
            }
            sendJobs[name] = job
            Log.i("NdiNativeSender", "NDI Source started: $fullName (FS=$isFullScreen)")
        }
        return true
    }

    fun stopSource(name: String) {
        synchronized(activeSenders) {
            sendJobs[name]?.cancel()
            sendJobs.remove(name)
            activeSenders[name]?.let { nativeDestroySender(it) }
            activeSenders.remove(name)
            if (activeSenders.isEmpty()) isRunning = false
        }
    }

    fun triggerFrame(name: String, isFullScreen: Boolean) {
        scope.launch {
            val ptr = synchronized(activeSenders) { activeSenders[name] } ?: return@launch
            frameProvider?.invoke(isFullScreen)?.let { sendBitmapToPtr(ptr, it) }
        }
    }

    private fun sendBitmapToPtr(ptr: Long, bitmap: Bitmap) {
        try {
            val targetBitmap = if (bitmap.width == FRAME_WIDTH && bitmap.height == FRAME_HEIGHT) {
                bitmap
            } else {
                val scaled = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(scaled)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                scaled
            }

            // Get or create a private buffer for this specific sender to prevent "flashing"
            val buffer = synchronized(senderBuffers) {
                senderBuffers.getOrPut(ptr) { IntArray(FRAME_WIDTH * FRAME_HEIGHT) }
            }

            targetBitmap.getPixels(buffer, 0, FRAME_WIDTH, 0, 0, FRAME_WIDTH, FRAME_HEIGHT)
            nativeSendVideo(ptr, buffer, FRAME_WIDTH, FRAME_HEIGHT)
        } catch (e: Exception) {
            Log.w("NdiNativeSender", "Error sending frame: ${e.message}")
        }
    }

    fun stopAll() {
        synchronized(activeSenders) {
            sendJobs.values.forEach { it.cancel() }
            sendJobs.clear()
            activeSenders.values.forEach { nativeDestroySender(it) }
            activeSenders.clear()
            isRunning = false
        }
    }
}
