package com.arabicchristianmedia.ui

import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

/**
 * Fullscreen overlay — shows the Full Show (/full page) in a fullscreen WebView.
 *
 * This is a thin wrapper: the WebView loads the local HTTP server's /full page,
 * which already has everything (videos, animations, particles, z-index layering,
 * true fullscreen macro, all template values). No native rendering duplication.
 *
 * For dual-screen (tablet + HDMI projector), use HdmiPresentationManager instead —
 * this activity is the fallback for single-display fullscreen.
 */
class FullscreenOverlayActivity : ComponentActivity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen, keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
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
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
            webViewClient = WebViewClient()
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        container.addView(webView)
        setContentView(container)

        // Load the Full Show page from the local HTTP server
        webView?.loadUrl("http://127.0.0.1:8080/full")
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    @Deprecated("Use HdmiPresentationManager for dual-screen; this is single-display fallback")
    fun refresh() {
        webView?.reload()
    }
}
