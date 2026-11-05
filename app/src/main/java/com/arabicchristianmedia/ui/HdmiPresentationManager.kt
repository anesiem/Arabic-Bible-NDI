package com.arabicchristianmedia.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the HDMI Presentation lifecycle.
 *
 * - Listens for secondary display (HDMI) connect/disconnect via DisplayManager
 * - Automatically shows the HdmiPresentation when HDMI connects (if enabled)
 * - Dismisses it when HDMI disconnects
 * - Exposes connection state for the Setup tab UI
 */
class HdmiPresentationManager(private val context: Context) {

    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var presentation: HdmiPresentation? = null

    private val _isHdmiConnected = MutableStateFlow(false)
    val isHdmiConnected: StateFlow<Boolean> = _isHdmiConnected.asStateFlow()

    private val _isPresentationShowing = MutableStateFlow(false)
    val isPresentationShowing: StateFlow<Boolean> = _isPresentationShowing.asStateFlow()

    /**
     * Item 72 (1.9.9): state callback — MainActivity forwards it to the
     * ViewModel, which writes the server fields (/api/debug) and the
     * Setup tab status rows. Invoked after every state change.
     */
    var onStateChanged: ((connected: Boolean, showing: Boolean) -> Unit)? = null

    private fun notifyState() {
        onStateChanged?.invoke(_isHdmiConnected.value, _isPresentationShowing.value)
    }

    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (value) {
                checkAndShow()
            } else {
                dismiss()
            }
        }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            checkAndShow()
        }

        override fun onDisplayRemoved(displayId: Int) {
            checkAndShow()
        }

        override fun onDisplayChanged(displayId: Int) {
            // No action needed
        }
    }

    init {
        displayManager.registerDisplayListener(
            displayListener,
            null // Use current thread's handler
        )
        checkAndShow()
    }

    /**
     * Find the first secondary display suitable for Presentation.
     * Filters out the default display.
     */
    private fun findSecondaryDisplay(): Display? {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        return displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    private fun checkAndShow() {
        val secondary = findSecondaryDisplay()
        _isHdmiConnected.value = secondary != null

        if (secondary != null && isEnabled) {
            show(secondary)
        } else {
            dismiss()
        }
        notifyState()
    }

    private fun show(display: Display) {
        if (presentation?.isShowing == true) {
            return // Already showing
        }
        dismiss() // Clean up any stale instance
        try {
            presentation = HdmiPresentation(context, display).apply {
                show()
            }
            _isPresentationShowing.value = true
            Log.i("HdmiPresentation", "Showing on display ${display.displayId}")
        } catch (e: Exception) {
            Log.e("HdmiPresentation", "Failed to show presentation", e)
            _isPresentationShowing.value = false
        }
    }

    private fun dismiss() {
        try {
            presentation?.dismiss()
        } catch (e: Exception) {
            Log.w("HdmiPresentation", "Error dismissing presentation", e)
        }
        presentation = null
        _isPresentationShowing.value = false
        notifyState()
    }

    /**
     * Refresh the WebView content (call after template changes).
     */
    fun refresh() {
        presentation?.refresh()
    }

    /**
     * Retry loading the /full page (call when HTTP server becomes ready,
     * in case the Presentation showed before the server started).
     */
    fun retryLoad() {
        try {
            // Use reflection to call retryLoad if the presentation supports it
            (presentation as? HdmiPresentation)?.retryLoad()
        } catch (e: Exception) {
            Log.w("HdmiPresentation", "retryLoad failed", e)
        }
    }

    fun release() {
        try {
            displayManager.unregisterDisplayListener(displayListener)
        } catch (e: Exception) {
            Log.w("HdmiPresentation", "Error unregistering listener", e)
        }
        dismiss()
    }
}
