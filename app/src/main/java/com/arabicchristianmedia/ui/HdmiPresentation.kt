package com.arabicchristianmedia.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * HDMI Presentation — shows the Full Show (/full page) on a secondary display
 * (HDMI projector) while the tablet keeps showing the app controls.
 *
 * The WebView loads the local HTTP server's /full page, which already has
 * everything: videos, gradient animations, particles, z-index layering,
 * true fullscreen macro, and all template values. No separate HDMI renderer
 * needed — single source of truth is the /full page.
 */
class HdmiPresentation(
    context: Context,
    display: Display,
    private val serverPort: Int = 8080
) : Presentation(context, display) {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            // Hardware acceleration for smooth video/animations
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
            webViewClient = WebViewClient()
            // Transparent background — the /full page controls its own bg
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        container.addView(webView)
        setContentView(container)

        // Load the Full Show page from the local HTTP server
        webView?.loadUrl("http://127.0.0.1:$serverPort/full")
    }

    override fun onStop() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onStop()
    }

    /**
     * Reload the page (e.g., after template change).
     */
    fun refresh() {
        webView?.reload()
    }
}
