package com.arabicchristianmedia.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // Log the error — the /full page may fail if the HTTP server
                    // isn't running yet. The manager will retry on reconnect.
                    Log.e("HdmiPresentation", "WebView load error: ${error?.description} (code ${error?.errorCode})")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.i("HdmiPresentation", "Loaded $url")
                }
            }
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
     * Reload the page (e.g., after template change or server restart).
     */
    fun refresh() {
        webView?.reload()
    }

    /**
     * Retry loading the /full page (e.g., if the HTTP server wasn't ready
     * when the Presentation first showed).
     */
    fun retryLoad(serverPort: Int = 8080) {
        webView?.loadUrl("http://127.0.0.1:$serverPort/full")
    }
}
