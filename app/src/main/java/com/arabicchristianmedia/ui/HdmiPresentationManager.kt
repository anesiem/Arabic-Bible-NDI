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
    }

    /**
     * Refresh the WebView content (call after template changes).
     */
    fun refresh() {
        presentation?.refresh()
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
