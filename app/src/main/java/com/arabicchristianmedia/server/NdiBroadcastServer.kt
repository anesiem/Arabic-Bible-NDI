package com.arabicchristianmedia.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.model.AnimatedBackgroundType
import com.arabicchristianmedia.model.ArabicFonts
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.BroadcastTextAlignment
import com.arabicchristianmedia.model.LanguageMode
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.StreamBackgroundMode
import com.arabicchristianmedia.model.TemplateStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sin
import kotlin.math.cos

class NdiBroadcastServer(private val context: Context, private var port: Int = 8080) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var ssePingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Held while broadcasting. The high-perf WifiLock keeps the Wi-Fi radio
     * from throttling mid-service; the MulticastLock lets NDI discovery
     * (multicast) through on devices with aggressive Wi-Fi power saving.
     * Best-effort only — real background guarantees await the foreground
     * service migration (deferred, needs a UX decision on the notification).
     */
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * v1.8 remote API hooks, wired by the ViewModel.
     * onTriggerVerse(bookId, chapter, verse, target) — target is
     * "lower", "show"/"full", or "both".
     */
    var onTriggerVerse: ((bookId: String, chapter: Int, verse: Int, target: String) -> Boolean)? = null
    var onClearVerse: (() -> Unit)? = null
    var onListVideos: (() -> List<VideoInfo>)? = null

    /** Opaque video ID + display info for /api/videos. */
    data class VideoInfo(
        val id: String,
        val displayName: String,
        val sizeBytes: Long
    )

    private fun acquireBroadcastLocks() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return
            if (wifiLock == null) {
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ArabicBibleNDI::broadcast")
            }
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
            if (multicastLock == null) {
                multicastLock = wifi.createMulticastLock("ArabicBibleNDI::discovery").apply {
                    setReferenceCounted(true)
                }
            }
            if (multicastLock?.isHeld == false) multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w("NdiBroadcastServer", "Could not acquire broadcast locks: ${e.message}")
        }
    }

    private fun releaseBroadcastLocks() {
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (e: Exception) {}
        try { if (multicastLock?.isHeld == true) multicastLock?.release() } catch (e: Exception) {}
        wifiLock = null
        multicastLock = null
    }

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var isLive = true

    // v1.8: no verse selected on launch — the user picks one. Renderers
    // handle null by producing a blank frame (card chrome only).
    @Volatile
    var currentVerse: BibleVerse? = null

    @Volatile
    var currentTemplate: LowerThirdTemplate = LowerThirdTemplate(id = "default", name = "Default")

    @Volatile
    var currentShowTemplate: LowerThirdTemplate = LowerThirdTemplate(id = "default_show", name = "Default Show", isFullScreen = true)

    var customIpOverride: String? = null

    // Connected SSE clients for instantaneous broadcast push (PrintWriter to isShow)
    private val sseClients = CopyOnWriteArrayList<Pair<PrintWriter, Boolean>>()

    fun start(onStarted: (String) -> Unit = {}, onError: (String) -> Unit = {}) {
        if (isRunning) return

        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(port))
            }
            isRunning = true
            val ip = getLocalIpAddress()
            val url = "http://$ip:$port/lower"
            onStarted(url)
            acquireBroadcastLocks()

            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }
            // SSE keep-alive: a comment ping every 15s keeps NATs/proxies from
            // silently dropping idle overlay connections between verse changes.
            ssePingJob = scope.launch {
                while (isActive && isRunning) {
                    delay(15_000)
                    val dead = mutableListOf<Pair<PrintWriter, Boolean>>()
                    for ((writer, _) in sseClients) {
                        try {
                            synchronized(writer) {
                                writer.print(": ping\n\n")
                                writer.flush()
                            }
                            if (writer.checkError()) dead.add(writer to false)
                        } catch (e: Exception) {
                            dead.add(writer to false)
                        }
                    }
                    if (dead.isNotEmpty()) {
                        // Remove by writer identity; the isShow flag is irrelevant here.
                        val deadWriters = dead.map { it.first }.toSet()
                        sseClients.removeAll { deadWriters.contains(it.first) }
                        deadWriters.forEach { try { it.close() } catch (e: Exception) {} }
                    }
                }
            }
        } catch (e: Exception) {
            isRunning = false
            onError(e.message ?: "Failed to start NDI server on port $port")
        }
    }

    fun stop() {
        isRunning = false
        releaseBroadcastLocks()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
        serverJob = null
        serverSocket = null
        ssePingJob?.cancel()
        ssePingJob = null

        sseClients.forEach { (writer, _) ->
            try { writer.close() } catch (e: Exception) {}
        }
        sseClients.clear()
        // v1.7: release video decoders (recreated lazily on next start).
        synchronized(videoRenderers) {
            videoRenderers.values.forEach { try { it.release() } catch (e: Exception) {} }
            videoRenderers.clear()
        }
    }

    @Volatile
    private var isCacheDirtyLower = true
    @Volatile
    private var isCacheDirtyShow = true

    /**
     * Monotonically increasing frame version, bumped on every state change that
     * affects rendered output. NDI senders use it for dirty-frame detection so
     * static verses don't burn CPU/network re-sending identical frames.
     * AtomicLong: incrementing a @Volatile Long is not atomic, and this is
     * bumped from several threads (UI, SSE throttle, verse updates).
     */
    private val _frameVersion = AtomicLong(0L)
    val frameVersion: Long get() = _frameVersion.get()

    private var cachedLowerBitmap: Bitmap? = null
    private var cachedShowBitmap: Bitmap? = null

    fun invalidateBitmapCache() {
        isCacheDirtyLower = true
        isCacheDirtyShow = true
        _frameVersion.incrementAndGet()
    }

    fun updateVerse(verse: BibleVerse, live: Boolean = true) {
        currentVerse = verse
        isLive = live
        invalidateBitmapCache()
        broadcastStateToClients()
    }

    private var lastTemplateUpdateMs = 0L
    private var trailingBroadcastJob: Job? = null

    fun updateTemplate(template: LowerThirdTemplate) {
        currentTemplate = template
        invalidateBitmapCache()
        // v1.8: Kickstart video renderer if the new template uses a custom video.
        // Without this, the video doesn't start until motion is toggled OFF/ON.
        if (template.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO &&
            template.customVideoId.isNotEmpty()) {
            try {
                val file = videoStore.fileFor(template.customVideoId)
                if (file != null) {
                    val renderer = getVideoRenderer(false)
                    renderer.setSource(file)
                    renderer.setMuted(template.customVideoMuted)
                    renderer.requestFrame()
                }
            } catch (e: Exception) {
                // Non-fatal; the draw loop will retry.
            }
        }
        broadcastStateThrottled()
    }

    /**
     * Full Show template update. Goes through the same state-update pipeline as
     * the Lower Third (cache invalidation, frame-version bump, throttled +
     * trailing SSE broadcast) so HTTP /show clients update live — previously
     * only the NDI feed was refreshed.
     */
    fun updateShowTemplate(template: LowerThirdTemplate) {
        currentShowTemplate = template
        invalidateBitmapCache()
        // v1.8: Kickstart video renderer for full show (same as updateTemplate).
        if (template.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO &&
            template.customVideoId.isNotEmpty()) {
            try {
                val file = videoStore.fileFor(template.customVideoId)
                if (file != null) {
                    val renderer = getVideoRenderer(true)
                    renderer.setSource(file)
                    renderer.setMuted(template.customVideoMuted)
                    renderer.requestFrame()
                }
            } catch (e: Exception) {
                // Non-fatal; the draw loop will retry.
            }
        }
        broadcastStateThrottled()
    }

    /**
     * Leading-edge throttled broadcast with a trailing edge: rapid slider drags
     * broadcast immediately, and the final state is always delivered ~100ms
     * after the last change, so overlays never show a stale value.
     */
    private fun broadcastStateThrottled() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTemplateUpdateMs >= 100) {
            lastTemplateUpdateMs = now
            trailingBroadcastJob?.cancel()
            trailingBroadcastJob = null
            broadcastStateToClients()
        } else if (trailingBroadcastJob == null) {
            trailingBroadcastJob = scope.launch {
                delay(100)
                lastTemplateUpdateMs = SystemClock.elapsedRealtime()
                trailingBroadcastJob = null
                broadcastStateToClients()
            }
        }
    }

    fun setLiveState(live: Boolean) {
        isLive = live
        invalidateBitmapCache()
        broadcastStateToClients()
    }

    private fun broadcastStateToClients() {
        val lowerPayload = buildJsonState(false)
        val showPayload = buildJsonState(true)

        val lowerData = "data: $lowerPayload\n\n"
        val showData = "data: $showPayload\n\n"

        val deadClients = mutableListOf<Pair<PrintWriter, Boolean>>()
        for (pair in sseClients) {
            val (writer, isShow) = pair
            try {
                // Serialized per client: broadcasts come from several threads
                // (UI, throttle job, verse updates) and must not interleave.
                synchronized(writer) {
                    writer.print(if (isShow) showData else lowerData)
                    writer.flush()
                }
                if (writer.checkError()) {
                    deadClients.add(pair)
                }
            } catch (e: Exception) {
                deadClients.add(pair)
            }
        }
        if (deadClients.isNotEmpty()) {
            sseClients.removeAll(deadClients.toSet())
            deadClients.forEach { (writer, _) -> try { writer.close() } catch (e: Exception) {} }
        }
    }

    private fun handleClient(socket: Socket) {
        // Set when this connection must survive handleClient() (SSE event
        // stream, MJPEG loop): the finally block must NOT close it, otherwise
        // the registered client dies immediately and overlays only update on
        // browser reconnect (~3s lag instead of instant pushes).
        var keepOpen = false
        try {
            socket.soTimeout = 15000
            socket.tcpNoDelay = true
            try { socket.sendBufferSize = 64 * 1024 } catch (e: Exception) {}
            try { socket.receiveBufferSize = 64 * 1024 } catch (e: Exception) {}
            val rawInput = socket.getInputStream()
            val pushback = java.io.PushbackInputStream(rawInput, 4)
            val firstByte = pushback.read()
            if (firstByte == -1) return

            // Detect if client attempted TLS/SSL handshake on plain HTTP port (e.g. browser auto-typed https://)
            if (firstByte == 0x16) {
                val errorHtml = "HTTP/1.1 400 Bad Request\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n<h3>يرجى استخدام HTTP وليس HTTPS</h3><p>الخادم يعمل عبر بروتوكول HTTP العادي على الشبكة المحلية: <b>http://${getLocalIpAddress()}:$port/ndi</b></p>"
                socket.getOutputStream().write(errorHtml.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                return
            }
            pushback.unread(firstByte)

            // Direct binary NDI handshake or raw video stream request
            if (firstByte == 'N'.code || firstByte == 0 || firstByte < 0x20) {
                serveLiveStream(socket, socket.getOutputStream(), "")
                return
            }

            val reader = BufferedReader(InputStreamReader(pushback))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) {
                serveLiveStream(socket, socket.getOutputStream(), "")
                return
            }

            val method = parts[0]
            val fullPath = parts[1]
            val cleanPath = fullPath.substringBefore("?").trimEnd('/')

            // Parse request headers
            var isBrowserNavigation = false
            var rangeHeader: String? = null
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrEmpty()) break
                val lower = headerLine?.lowercase() ?: ""
                if (lower.startsWith("accept:") && lower.contains("text/html")) {
                    isBrowserNavigation = true
                }
                if (lower.startsWith("range:")) {
                    rangeHeader = headerLine!!.substringAfter(":").trim()
                }
            }

            val out = socket.getOutputStream()

            // v1.8: simple user-facing URIs (/lower, /full).
            when {
                cleanPath == "/lower" || cleanPath == "" || cleanPath == "/" -> {
                    serveNdiHtmlOverlay(out, isFullScreen = false)
                }
                cleanPath == "/full" -> {
                    serveNdiHtmlOverlay(out, isFullScreen = true)
                }
                cleanPath == "/events" -> {
                    serveSseEvents(socket, out, fullPath.contains("show=1"))
                    keepOpen = true
                    return // Persistent SSE stream: socket stays open for pushes
                }
                cleanPath == "/stream" -> {
                    if (isBrowserNavigation && !fullPath.contains("raw=1")) {
                        serveMjpegPlayerHtml(out)
                    } else {
                        serveLiveStream(socket, out, fullPath)
                        return // Handled in persistent video loop
                    }
                }
                cleanPath == "/api/verse" -> {
                    serveJsonState(out, fullPath.contains("show=1"))
                }
                cleanPath == "/api/trigger" -> {
                    serveApiTrigger(out, fullPath)
                }
                cleanPath == "/api/clear" -> {
                    serveApiClear(out)
                }
                cleanPath == "/api/videos" -> {
                    serveApiVideos(out)
                }
                cleanPath == "/snapshot.png" -> {
                    serveTransparentPngOverlay(out)
                }
                cleanPath == "/api/status" -> {
                    serveStatus(out)
                }
                cleanPath.startsWith("/fonts/") -> {
                    serveFontFile(out, cleanPath.removePrefix("/fonts/"))
                }
                cleanPath == "/video" -> {
                    serveVideoById(out, fullPath, rangeHeader)
                }
                cleanPath == "/remote" -> {
                    serveRemotePage(out)
                }
                cleanPath == "/bibleshow.xml" -> {
                    serveBibleShowXml(out)
                }
                else -> {
                    serveNdiHtmlOverlay(out)
                }
            }
        } catch (e: Exception) {
            // Connection closed or timed out
        } finally {
            // SSE streams opted out via keepOpen: their socket is closed only
            // when a broadcast write fails (see broadcastStateToClients) or on
            // server stop(). Everything else is one-shot: close it here.
            if (!keepOpen) {
                try { socket.close() } catch (e: Exception) {}
            }
        }
    }

    private fun serveSseEvents(socket: Socket, out: OutputStream, isShow: Boolean = false) {
        socket.soTimeout = 0 // Infinite timeout for SSE stream
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/event-stream\r\n")
        writer.print("Cache-Control: no-cache\r\n")
        writer.print("Connection: keep-alive\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n\r\n")
        writer.flush()

        // Send initial state immediately
        val initialData = "data: ${buildJsonState(isShow)}\n\n"
        writer.print(initialData)
        writer.flush()

        sseClients.add(Pair(writer, isShow))
    }

    private fun serveJsonState(out: OutputStream, isShow: Boolean = false) {
        val json = buildJsonState(isShow)
        val bytes = json.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun buildJsonState(isShow: Boolean = false): String {
        val tpl = if (isShow) currentShowTemplate else currentTemplate
        val verse = currentVerse

        val formattedVerseText = if (verse != null) {
            ArabicTextFormatter.prepareForBroadcast(
                verse.arabicText,
                tpl.useEasternArabicNumerals,
                tpl.useArabicPunctuation
            )
        } else ""

        val formattedCitation = if (verse != null) {
            verse.getFormattedArabicCitation(tpl.useEasternArabicNumerals)
        } else ""

        val root = JSONObject().apply {
            // Monotonic state version: browser sources re-sync on SSE reconnect
            // by comparing against the last version they applied.
            put("stateVersion", frameVersion)
            put("isLive", isLive && verse != null)
            put("arabicText", formattedVerseText)
            put("arabicCitation", formattedCitation)
            put("englishText", verse?.englishText ?: "")
            put("englishCitation", verse?.getFormattedEnglishCitation() ?: "")
            put("bilingual", tpl.languageMode == LanguageMode.BOTH)
            put("languageMode", tpl.languageMode.name)
            put("style", tpl.style.name)
            put("bgColorHex", tpl.bgColorHex)
            put("bgOpacity", tpl.bgOpacity)
            put("accentColorHex", tpl.accentColorHex)
            put("textColorHex", tpl.textColorHex)
            put("referenceColorHex", tpl.referenceColorHex)
            put("verseFontSize", tpl.verseFontSize)
            put("referenceFontSize", tpl.referenceFontSize)
            put("fontFamily", tpl.fontFamily)
            put("secondaryTextColorHex", tpl.secondaryTextColorHex)
            put("secondaryReferenceColorHex", tpl.secondaryReferenceColorHex)
            put("secondaryVerseFontSize", tpl.secondaryVerseFontSize)
            put("secondaryReferenceFontSize", tpl.secondaryReferenceFontSize)
            put("secondaryFontFamily", tpl.secondaryFontFamily)
            put("alignment", tpl.alignment.name)
            put("positionBottomPercent", tpl.positionBottomPercent)
            put("horizontalMarginPercent", tpl.horizontalMarginPercent)
            put("cornerRadiusDp", tpl.cornerRadiusDp)
            put("transition", tpl.transition.name)
            put("transitionDurationMs", tpl.transitionDurationMs)
            put("showAccentBorder", tpl.showAccentBorder)
            put("showDropShadow", tpl.showDropShadow)
            put("textShadowEnabled", tpl.textShadowEnabled)
            put("textShadowColorHex", tpl.textShadowColorHex)
            put("textShadowBlurDp", tpl.textShadowBlurDp)
            put("textShadowOffsetDp", tpl.textShadowOffsetDp)
            put("textShadowAngleDeg", tpl.textShadowAngleDeg)
            put("cardGlowEnabled", tpl.cardGlowEnabled)
            put("cardGlowColorHex", tpl.cardGlowColorHex)
            put("emblem", tpl.emblem)
            put("isPureTransparentBackground", tpl.isPureTransparentBackground)
            put("isFullScreen", tpl.isFullScreen)
            put("bilingualSpacing", tpl.bilingualSpacing)
            put("showBilingualSpacing", tpl.showBilingualSpacing)
            put("verseIsBold", tpl.verseIsBold)
            put("verseIsItalic", tpl.verseIsItalic)
            put("referenceIsBold", tpl.referenceIsBold)
            put("referenceIsItalic", tpl.referenceIsItalic)
            put("secondaryVerseIsBold", tpl.secondaryVerseIsBold)
            put("secondaryVerseIsItalic", tpl.secondaryVerseIsItalic)
            put("secondaryReferenceIsBold", tpl.secondaryReferenceIsBold)
            put("secondaryReferenceIsItalic", tpl.secondaryReferenceIsItalic)
            put("animatedBackground", tpl.animatedBackground.id)
            put("animatedBackgroundOpacity", tpl.animatedBackgroundOpacity)
            put("customVideoUrl", tpl.customVideoUrl)
            put("customVideoId", tpl.customVideoId)
            put("customVideoMuted", tpl.customVideoMuted)
            put("motionSpeed", tpl.motionSpeed)
        }
        return root.toString()
    }

    private fun serveNdiHtmlOverlay(out: OutputStream, isFullScreen: Boolean = false) {
        val html = generateBroadcastHtml(isFullScreen)
        val bytes = html.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Cache-Control: no-cache, no-store, must-revalidate\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun generateBroadcastHtml(forceFullScreen: Boolean = false): String {
        val initialJson = buildJsonState(forceFullScreen)
        return """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NDI Bible Live Overlay</title>
  <!-- Bundled Arabic fonts, served from this tablet: the overlay works fully offline. -->
  <style>${buildFontFaceCss()}</style>
  <style>
    /* ==========================================================================
       1. Global Reset & Base Setup (100% Transparent Background for OBS/vMix)
       ========================================================================== */
    *, *::before, *::after {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }
    html, body {
      width: 100vw;
      height: 100vh;
      background: transparent !important;
      background-color: transparent !important;
      overflow: hidden;
      font-family: 'Amiri', 'Noto Naskh Arabic', serif;
      -webkit-font-smoothing: antialiased;
      /* v1.8: Center the fixed 1920x1080 stage. The stage is always 1920x1080
         internally (matching NDI), scaled via transform to fit the viewport. */
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Stage Container (Default: Lower Third alignment at screen bottom) */
    /* v1.8: Fixed 1920x1080 internal resolution (matches NDI exactly).
       Scaled via JS transform to fit viewport while maintaining 16:9. */
    #stage {
      position: relative;
      width: 1920px;
      height: 1080px;
      flex-shrink: 0;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      justify-content: flex-end;
      transform-origin: center center;
    }
    }

    /* Stage Container (Full Show Mode: Centered vertically and horizontally) */
    #stage.mode-full-show {
      justify-content: center !important;
      align-items: center !important;
    }

    /* ==========================================================================
       2. Overlay Container & Animations
       ========================================================================== */
    #lowerthird-container {
      transition: opacity 0.4s cubic-bezier(0.16, 1, 0.3, 1),
                  transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
      opacity: 0;
      transform: translateY(40px);
      will-change: opacity, transform;
      direction: rtl;
      text-align: right;
    }

    #lowerthird-container.visible {
      opacity: 1;
      transform: translateY(0);
    }

    /* Full Show Mode Container */
    #lowerthird-container.mode-full-show {
      width: 100vw !important;
      height: 100vh !important;
      margin: 0 !important;
      display: flex !important;
      flex-direction: column !important;
      align-items: center !important;
      justify-content: center !important;
      transform: none !important;
    }

    /* Animation Presets */
    #lowerthird-container.anim-fade { transform: translateY(0) !important; }
    #lowerthird-container.anim-fade:not(.visible) { opacity: 0; }
    #lowerthird-container.anim-slide-up { transform: translateY(60px); }
    #lowerthird-container.anim-slide-up.visible { transform: translateY(0); }
    #lowerthird-container.anim-slide-right { transform: translateX(80px); }
    #lowerthird-container.anim-slide-right.visible { transform: translateX(0); }
    #lowerthird-container.anim-cut { transition: none !important; }

    /* ==========================================================================
       3. Card Content & Flex Layouts
       ========================================================================== */
    .card-content {
      position: relative;
      display: inline-block;
      min-width: 450px;
      max-width: 92vw;
      overflow: hidden;
      box-sizing: border-box;
    }

    /* Card Box in Full Show Mode (Flex-centered vertically in middle of screen) */
    .card-content.mode-full-show {
      width: 100vw !important;
      height: 100vh !important;
      max-width: 100vw !important;
      display: flex !important;
      flex-direction: column !important;
      justify-content: center !important;
      align-items: center !important;
      padding: 6vh 6vw !important;
      border-radius: 0 !important;
      margin: 0 !important;
      text-align: center !important;
    }

    .card-inner-content {
      position: relative;
      z-index: 1;
      width: 100%;
    }

    .card-content.mode-full-show .card-inner-content {
      display: flex !important;
      flex-direction: column !important;
      justify-content: center !important;
      align-items: center !important;
      text-align: center !important;
      width: 100% !important;
      margin: 0 auto !important;
    }

    /* ==========================================================================
       4. Motion Graphic / Animated Background Layer
       ========================================================================== */
    .animated-bg-layer {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 0;
      border-radius: inherit;
      opacity: 0;
      transition: opacity 0.5s ease;
      overflow: hidden;
    }

    .anim-golden-rays {
      background: radial-gradient(circle at 50% 50%, rgba(251, 191, 36, 0.4) 0%, transparent 70%),
                  linear-gradient(135deg, rgba(217, 119, 6, 0.3) 0%, rgba(251, 191, 36, 0.5) 50%, rgba(217, 119, 6, 0.3) 100%);
      background-size: 400% 400%;
      animation: smoothFlow 15s linear infinite;
    }
    @keyframes smoothFlow {
      0% { background-position: 0% 50%; }
      100% { background-position: 100% 50%; }
    }

    .anim-blue-waves {
      background: linear-gradient(120deg, rgba(30, 58, 138, 0.4) 0%, rgba(14, 165, 233, 0.6) 50%, rgba(30, 58, 138, 0.4) 100%);
      background-size: 300% 300%;
      animation: smoothFlow 20s linear infinite;
    }

    .anim-candle-glow {
      background: radial-gradient(circle at 50% 50%, rgba(245, 158, 11, 0.6) 0%, rgba(120, 53, 15, 0.3) 70%);
      animation: candleBreath 5s ease-in-out infinite alternate;
    }
    @keyframes candleBreath {
      0% { opacity: 0.7; transform: scale(1); }
      100% { opacity: 1; transform: scale(1.1); }
    }

    .anim-purple-silk {
      background: linear-gradient(45deg, rgba(88, 28, 135, 0.4) 0%, rgba(192, 132, 252, 0.6) 50%, rgba(88, 28, 135, 0.4) 100%);
      background-size: 400% 400%;
      animation: smoothFlow 18s linear infinite;
    }

    .anim-emerald-waves {
      background: linear-gradient(45deg, rgba(6, 78, 59, 0.4) 0%, rgba(52, 211, 153, 0.6) 50%, rgba(6, 78, 59, 0.4) 100%);
      background-size: 400% 400%;
      animation: smoothFlow 20s linear infinite;
    }

    .anim-rose-glow {
      background: radial-gradient(circle at 50% 50%, rgba(251, 113, 133, 0.5) 0%, rgba(136, 19, 55, 0.3) 100%);
      animation: candleBreath 8s ease-in-out infinite alternate;
    }

    .anim-gold-particles {
      background: linear-gradient(135deg, rgba(120, 53, 15, 0.4) 0%, rgba(252, 211, 77, 0.5) 50%, rgba(120, 53, 15, 0.4) 100%);
      background-size: 300% 300%;
      animation: smoothFlow 15s linear infinite;
    }

    .anim-particles {
      background: radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.1) 0%, transparent 100%);
      animation: particleBreath 10s ease-in-out infinite alternate;
    }
    @keyframes particleBreath {
      0% { filter: brightness(0.8); }
      100% { filter: brightness(1.3); }
    }

    #custom-video-bg {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      pointer-events: none;
      border-radius: inherit;
    }

    /* ==========================================================================
       5. Typography Elements
       ========================================================================== */
    .citation-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font-weight: 700;
      letter-spacing: 0.5px;
      margin-bottom: 8px;
    }

    .cross-icon {
      display: inline-block;
      font-size: 1.15em;
      line-height: 1;
    }

    .verse-text {
      line-height: 1.55;
      font-weight: 600;
      word-spacing: 2px;
    }

    .english-section {
      width: 100%;
      margin-top: 10px;
    }
    .english-text {
      direction: ltr;
      display: inline-block;
      text-align: left;
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 0.75em;
      opacity: 0.9;
      line-height: 1.35;
    }
    .english-citation {
      display: block;
      direction: ltr;
      font-weight: 700;
      margin-top: 4px;
      font-size: 0.7em;
    }
  </style>
</head>
<body>
  <div id="stage">
    <div id="lowerthird-container" class="visible">
      <div id="card-box" class="card-content">
        <div id="animated-bg-layer" class="animated-bg-layer">
          <video id="custom-video-bg" style="display:none;" autoplay loop muted playsinline></video>
        </div>

        <div class="card-inner-content">
          <div id="citation-row" class="citation-badge">
            <span id="cross-emblem" class="cross-icon"></span>
            <span id="citation-text"></span>
          </div>
          <div id="verse-text" class="verse-text"></div>
          <div id="english-section" style="display:none;">
            <div id="english-text" class="english-text"></div>
            <div id="english-citation" class="english-citation"></div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <script>
    // Elements
    const stage = document.getElementById('stage');
    // v1.8: Scale the fixed 1920x1080 stage to fit viewport (maintains 16:9).
    function fitStage() {
      const scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
      stage.style.transform = 'scale(' + scale + ')';
    }
    window.addEventListener('resize', fitStage);
    fitStage();
    const container = document.getElementById('lowerthird-container');
    const cardBox = document.getElementById('card-box');
    const animBgLayer = document.getElementById('animated-bg-layer');
    const customVideoBg = document.getElementById('custom-video-bg');
    const citationRow = document.getElementById('citation-row');
    const citationText = document.getElementById('citation-text');
    const verseText = document.getElementById('verse-text');
    const crossEmblem = document.getElementById('cross-emblem');
    const englishSection = document.getElementById('english-section');
    const englishText = document.getElementById('english-text');
    const englishCitation = document.getElementById('english-citation');

    /**
     * Main Renderer: Applies state data to the DOM elements
     */
    // Last applied SSE state version (-1 = none yet). See applyData guard.
    let lastStateVersion = -1;

    function applyData(data) {
      // State-version guard: drop stale/out-of-order deliveries and let a fresh
      // reconnect re-sync (the server bumps stateVersion on every change).
      if (data.stateVersion !== undefined && data.stateVersion !== null) {
        if (data.stateVersion <= lastStateVersion) return;
        lastStateVersion = data.stateVersion;
      }
      if (!data.isLive || !data.arabicText) {
        container.classList.remove('visible');
        return;
      }

      const isFull = data.isFullScreen || $forceFullScreen;

      // v1.8: viewport-relative font sizing — the style's font sizes are
      // designed for a 1920-wide canvas (like NDI); scale to the actual
      // viewport so HTTP matches preview/NDI proportions at any size.
      // v1.8: Match NDI font size exactly. NDI renders at verseFontSize * 2 (see
      // drawFrameInto: textSize = verseFontSize * 2f * scale). No viewport scaling —
      // HTTP must match NDI pixel-for-pixel at 1920x1080.
      function scaledPx(base) { return (base * 2) + 'px'; }

      // 1. Set mode classes
      if (isFull) {
        stage.classList.add('mode-full-show');
        container.classList.add('mode-full-show');
        cardBox.classList.add('mode-full-show');
      } else {
        stage.classList.remove('mode-full-show');
        container.classList.remove('mode-full-show');
        cardBox.classList.remove('mode-full-show');
      }

      // 2. Text Content (v1.8: three-way language mode)
      const langMode = data.languageMode || (data.bilingual ? 'BOTH' : 'ARABIC_ONLY');
      const showArabic = langMode !== 'ENGLISH_ONLY';
      // v1.8: English section is shown in BOTH and ENGLISH_ONLY (with its own colors).
      // No color swapping — each language keeps its own styling.
      const showEnglishSection = langMode !== 'ARABIC_ONLY' && data.englishText;
      if (showArabic) {
        verseText.style.display = '';
        citationText.style.display = '';
        verseText.textContent = data.arabicText;
        citationText.textContent = data.arabicCitation;
      } else {
        // ENGLISH_ONLY: Hide Arabic rows, English section takes over.
        verseText.style.display = 'none';
        citationText.style.display = 'none';
      }

      // 3. Bilingual Mode (BOTH only: English in secondary section below Arabic)
      if (showEnglishSection) {
        englishSection.style.display = 'block';
        englishText.textContent = data.englishText;
        englishCitation.textContent = '(' + data.englishCitation + ')';
        englishText.style.color = data.secondaryTextColorHex || '#CBD5E1';
        englishCitation.style.color = data.secondaryReferenceColorHex || '#94A3B8';
        englishText.style.fontSize = scaledPx(data.secondaryVerseFontSize || 18);
        englishCitation.style.fontSize = scaledPx(data.secondaryReferenceFontSize || 14);
        englishText.style.fontFamily = data.secondaryFontFamily || 'system-ui';
        englishText.style.fontWeight = data.secondaryVerseIsBold ? 'bold' : 'normal';
        englishText.style.fontStyle = data.secondaryVerseIsItalic ? 'italic' : 'normal';
        englishCitation.style.fontWeight = data.secondaryReferenceIsBold ? 'bold' : 'normal';
        englishCitation.style.fontStyle = data.secondaryReferenceIsItalic ? 'italic' : 'normal';
        const spacing = data.showBilingualSpacing ? (data.bilingualSpacing || 20) : 0;
        englishSection.style.marginTop = spacing + 'px';
      } else {
        englishSection.style.display = 'none';
      }

      // 4. Layout & Alignment (Ensures Full Show remains vertically & horizontally centered in middle)
      if (isFull) {
        container.style.marginBottom = '0';
        container.style.marginLeft = '0';
        container.style.marginRight = '0';
        container.style.width = '100vw';
        container.style.height = '100vh';

        cardBox.style.display = 'flex';
        cardBox.style.flexDirection = 'column';
        cardBox.style.justifyContent = 'center';
        cardBox.style.margin = '0 auto';
        cardBox.style.padding = '6vh 6vw';
        cardBox.style.width = '100vw';
        cardBox.style.maxWidth = '100vw';
        // v1.7: Full Show obeys the user's alignment — no forced centering.
        // Bidi-aware: LEFT/RIGHT mean visual left/right for both scripts.
        const align = data.alignment || 'RIGHT';
        const alignCss = align === 'CENTER' ? 'center' : (align === 'LEFT' ? 'left' : 'right');
        container.style.direction = align === 'LEFT' ? 'ltr' : 'rtl';
        cardBox.style.alignItems = align === 'CENTER' ? 'center' : (align === 'LEFT' ? 'flex-start' : 'flex-end');
        cardBox.style.textAlign = alignCss;

        citationRow.style.display = 'flex';
        citationRow.style.width = '100%';
        citationRow.style.justifyContent = align === 'CENTER' ? 'center' : 'flex-start';

        verseText.style.width = '100%';
        verseText.style.textAlign = alignCss;
        verseText.style.margin = '0 auto';

        englishSection.style.width = '100%';
        // v1.8: English stays left unless centered.
        const enAlignCss = align === 'CENTER' ? 'center' : 'left';
        englishSection.style.textAlign = enAlignCss;
        // v1.8: honor the bilingualSpacing setting (was hardcoded 10px,
        // overwriting the marginTop set above).
        const fsSpacing = data.showBilingualSpacing ? (data.bilingualSpacing || 20) : 0;
        englishSection.style.margin = fsSpacing + 'px auto 0 auto';
        englishText.style.width = '100%';
        englishText.style.textAlign = enAlignCss;
      } else {
        const botMargin = (data.positionBottomPercent || 6) + 'vh';
        const hMargin = (data.horizontalMarginPercent || 6) + 'vw';
        container.style.marginBottom = botMargin;
        container.style.marginLeft = hMargin;
        container.style.marginRight = hMargin;

        if (data.alignment === 'CENTER') {
          container.style.direction = 'rtl';
          cardBox.style.textAlign = 'center';
          cardBox.style.display = 'block';
          cardBox.style.margin = '0 auto';
          citationRow.style.display = 'flex';
          citationRow.style.width = '100%';
          citationRow.style.justifyContent = 'center';
          verseText.style.textAlign = 'center';
          englishSection.style.textAlign = 'center';
        } else if (data.alignment === 'LEFT') {
          container.style.direction = 'ltr';
          cardBox.style.textAlign = 'left';
          cardBox.style.display = 'inline-block';
          cardBox.style.margin = '0';
          citationRow.style.display = 'flex';
          citationRow.style.width = '100%';
          citationRow.style.justifyContent = 'flex-start';
          verseText.style.textAlign = 'left';
          englishSection.style.textAlign = 'left';
        } else {
          container.style.direction = 'rtl';
          cardBox.style.textAlign = 'right';
          cardBox.style.display = 'inline-block';
          cardBox.style.margin = '0';
          citationRow.style.display = 'flex';
          citationRow.style.width = '100%';
          citationRow.style.justifyContent = 'flex-start';
          verseText.style.textAlign = 'right';
          // v1.8: English stays left unless centered.
          englishSection.style.textAlign = 'left';
        }
      }

      // 5. Typography & Font Styling (bundled webfonts served from the tablet; fully offline)
      const rawFont = String(data.fontFamily || 'Amiri').replace(/['"\\]/g, '');
      const font = rawFont === 'System' ? 'system-ui, sans-serif' : "'" + rawFont + "', 'Amiri', serif";
      cardBox.style.fontFamily = font;
      verseText.style.fontWeight = data.verseIsBold ? 'bold' : 'normal';
      verseText.style.fontStyle = data.verseIsItalic ? 'italic' : 'normal';
      citationRow.style.fontWeight = data.referenceIsBold ? 'bold' : 'normal';
      citationRow.style.fontStyle = data.referenceIsItalic ? 'italic' : 'normal';
      verseText.style.fontSize = scaledPx(data.verseFontSize || 26);
      citationRow.style.fontSize = scaledPx(data.referenceFontSize || 18);

      // 6. Color Schemes & Container Styling
      const hex = data.bgColorHex || '#0A1128';
      const opacity = data.bgOpacity !== undefined ? data.bgOpacity : 0.85;
      const r = parseInt(hex.slice(1,3), 16) || 10;
      const g = parseInt(hex.slice(3,5), 16) || 17;
      const b = parseInt(hex.slice(5,7), 16) || 40;

      const accent = data.accentColorHex || '#E5A93C';
      const textColor = data.textColorHex || '#FFFFFF';
      const refColor = data.referenceColorHex || '#F4D06F';

      verseText.style.color = textColor;
      citationRow.style.color = refColor;
      // v1.8: emblem is transparent, citation-colored, inline with the citation
      // (was: accent-colored chip background).
      crossEmblem.style.backgroundColor = 'transparent';
      crossEmblem.style.color = refColor;
      // v1.7: free user emblem (emoji/symbol); empty = hidden.
      if (data.emblem) {
        crossEmblem.textContent = data.emblem;
        crossEmblem.style.display = 'inline-block';
      } else {
        crossEmblem.style.display = 'none';
      }

      const radius = isFull ? '0' : (data.cornerRadiusDp || 16) + 'px';
      cardBox.style.borderRadius = radius;
      if (!isFull) cardBox.style.padding = '18px 26px';

      // Independent card glow + text shadow (v1.6): each flag works on its own,
      // in both Lower Third and Full Show.
      function hexToRgb(hex) {
        var h = (hex || '#000000').replace('#', '');
        if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
        var n = parseInt(h, 16);
        if (isNaN(n)) return null;
        return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
      }
      function cardGlowShadow(shape, alpha) {
        // Card glow only where a real card exists (not full-bleed fullscreen).
        if (isFull || !data.cardGlowEnabled) return 'none';
        var c = hexToRgb(data.cardGlowColorHex);
        if (!c) return 'none';
        return shape + ' rgba(' + c.r + ',' + c.g + ',' + c.b + ',' + alpha + ')';
      }
      function textShadowCss() {
        // v1.8: directional shadow from the style's thickness (blur),
        // distance (offset) and compass angle — mirrors NDI canvas.
        if (!data.textShadowEnabled) return 'none';
        var col = data.textShadowColorHex || '#000000';
        var blur = (data.textShadowBlurDp !== undefined) ? data.textShadowBlurDp : 8;
        var dist = (data.textShadowOffsetDp !== undefined) ? data.textShadowOffsetDp : 4;
        var angDeg = (data.textShadowAngleDeg !== undefined) ? data.textShadowAngleDeg : 90;
        var ang = angDeg * Math.PI / 180;
        var dx = (Math.cos(ang) * dist).toFixed(1);
        var dy = (Math.sin(ang) * dist).toFixed(1);
        return dx + 'px ' + dy + 'px ' + blur + 'px ' + col;
      }

      // 100% transparent toggle behaves like the transparent style (mirrors the NDI canvas).
      if (data.style === 'TRANSPARENT_OUTLINE' || data.isPureTransparentBackground) {
        cardBox.style.backgroundColor = 'transparent';
        cardBox.style.backdropFilter = 'none';
        cardBox.style.border = 'none';
        cardBox.style.boxShadow = 'none';
      } else if (data.style === 'CLASSIC_BANNER') {
        cardBox.style.backgroundColor = 'rgba(' + r + ',' + g + ',' + b + ',' + opacity + ')';
        cardBox.style.borderRight = data.showAccentBorder ? '6px solid ' + accent : 'none';
        cardBox.style.borderLeft = 'none';
        cardBox.style.borderRadius = isFull ? '0' : '4px';
        cardBox.style.boxShadow = cardGlowShadow('0 12px 36px', 0.6);
      } else if (data.style === 'ROYAL_LITURGICAL') {
        cardBox.style.background = 'linear-gradient(135deg, rgba(' + r + ',' + g + ',' + b + ',' + opacity + ') 0%, rgba(20,20,35,' + opacity + ') 100%)';
        cardBox.style.border = data.showAccentBorder ? '2px solid ' + accent : 'none';
        cardBox.style.boxShadow = cardGlowShadow('0 10px 40px', 0.7);
      } else {
        cardBox.style.backgroundColor = 'rgba(' + r + ',' + g + ',' + b + ',' + opacity + ')';
        cardBox.style.backdropFilter = 'blur(16px)';
        cardBox.style.border = data.showAccentBorder ? '1.5px solid ' + accent + '77' : 'none';
        cardBox.style.boxShadow = cardGlowShadow('0 16px 40px', 0.55);
      }

      // Text shadow on verse + citation (independent flag, both feeds).
      verseText.style.textShadow = textShadowCss();
      citationRow.style.textShadow = textShadowCss();

      // 7. Motion Background Layer
      const animType = data.animatedBackground || 'none';
      const animOpacity = data.animatedBackgroundOpacity !== undefined ? data.animatedBackgroundOpacity : 0.65;

      animBgLayer.className = 'animated-bg-layer';
      customVideoBg.style.display = 'none';

      if (animType === 'golden_rays') {
        animBgLayer.classList.add('anim-golden-rays');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'blue_waves') {
        animBgLayer.classList.add('anim-blue-waves');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'candle_glow') {
        animBgLayer.classList.add('anim-candle-glow');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'purple_silk') {
        animBgLayer.classList.add('anim-purple-silk');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'emerald_waves') {
        animBgLayer.classList.add('anim-emerald-waves');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'rose_glow') {
        animBgLayer.classList.add('anim-rose-glow');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'gold_particles') {
        animBgLayer.classList.add('anim-gold-particles');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'particles') {
        animBgLayer.classList.add('anim-particles');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'custom_video' && data.customVideoId) {
        // v1.7: videos are served by opaque ID from private storage (never by path).
        const videoUrl = '/video?id=' + encodeURIComponent(data.customVideoId);
        if (customVideoBg.src !== videoUrl) customVideoBg.src = videoUrl;
        // v1.8: HTTP is ALWAYS muted (browser autoplay policy + Ashraf: audio goes to NDI only).
        // The customVideoMuted toggle only affects NDI.
        customVideoBg.muted = true;
        // v1.8: Apply motion speed to video playback rate.
        const speed = parseFloat(data.motionSpeed) || 1.0;
        if (customVideoBg.playbackRate !== speed) customVideoBg.playbackRate = speed;
        customVideoBg.style.display = 'block';
        customVideoBg.style.opacity = animOpacity;
        animBgLayer.style.opacity = '1';
      } else {
        animBgLayer.style.opacity = '0';
      }

      // 8. Entry Transitions
      container.className = isFull ? 'mode-full-show' : '';
      if (data.transition === 'FADE') container.classList.add('anim-fade');
      else if (data.transition === 'SLIDE_RIGHT') container.classList.add('anim-slide-right');
      else if (data.transition === 'CUT') container.classList.add('anim-cut');
      else container.classList.add('anim-slide-up');

      container.style.transitionDuration = (data.transitionDurationMs || 350) + 'ms';

      requestAnimationFrame(() => {
        container.classList.add('visible');
      });
    }

    // Connect Server-Sent Events (SSE)
    function connectSse() {
      const urlParams = new URLSearchParams(window.location.search);
      const isShow = urlParams.get('show') === '1' || window.location.pathname.includes('/full');
      const sseUrl = '/events' + (isShow ? '?show=1' : '');
      
      const evtSource = new EventSource(sseUrl);
      evtSource.onmessage = function(e) {
        try {
          applyData(JSON.parse(e.data));
        } catch (err) {
          console.error('Failed to parse SSE payload', err);
        }
      };
      evtSource.onerror = function() {
        evtSource.close();
        setTimeout(connectSse, 2000);
      };
    }

    // Initial payload render
    try {
      applyData($initialJson);
    } catch (e) {
      console.error(e);
    }

    connectSse();
  </script>
</body>
</html>
        """.trimIndent()
    }

    fun renderCurrentFrame(width: Int = 1920, height: Int = 1080, isForStream: Boolean = false, overrideTemplate: LowerThirdTemplate? = null): Bitmap {
        return renderFrame(width, height, isForStream, overrideTemplate, allowSharedCache = true)
    }

    /**
     * Renders a frame into a FRESH bitmap that the caller owns and must recycle.
     * Never returns the shared NDI cache bitmaps, so it is safe to recycle the
     * result even while NDI sender threads are reading the cached frames.
     * (This replaces the old pattern of recycling whatever renderCurrentFrame
     * returned, which could recycle the live shared bitmap mid-send.)
     */
    fun renderFrameOwned(width: Int = 1920, height: Int = 1080, isForStream: Boolean = false, overrideTemplate: LowerThirdTemplate? = null): Bitmap {
        return renderFrame(width, height, isForStream, overrideTemplate, allowSharedCache = false)
    }

    // Dedicated reusable bitmaps for motion rendering (one per feed). Never the
    // shared static cache: motion ticks redraw in place at up to ~30fps, and a shared
    // bitmap would let static readers (preview, MJPEG) see half-drawn frames.
    private var motionLowerBitmap: Bitmap? = null
    private var motionShowBitmap: Bitmap? = null

    /**
     * v1.7 custom video backgrounds: one decoder per feed (Lower/Full are
     * independent). Created lazily, released on server stop().
     */
    private val videoStore by lazy { VideoStore(context) }
    private val videoRenderers = mutableMapOf<Boolean, VideoBackgroundRenderer>()
    private fun getVideoRenderer(isFull: Boolean): VideoBackgroundRenderer =
        synchronized(videoRenderers) {
            videoRenderers.getOrPut(isFull) { VideoBackgroundRenderer() }
        }

    /**
     * Resolves the template's custom video to a private-storage file.
     * Null = no video (feature off or file missing); callers fall back to the
     * normal background. Legacy raw URLs are migrated by TemplateRepository.
     * Resolution is cached per feed (file I/O must not run on every frame).
     */
    private val resolvedVideoCache = mutableMapOf<Boolean, Pair<String, java.io.File?>>()
    private fun resolveCustomVideoFile(tpl: LowerThirdTemplate, isFull: Boolean): java.io.File? {
        if (tpl.animatedBackground != AnimatedBackgroundType.CUSTOM_VIDEO || tpl.customVideoId.isEmpty()) {
            synchronized(resolvedVideoCache) { resolvedVideoCache.remove(isFull) }
            return null
        }
        synchronized(resolvedVideoCache) {
            val cached = resolvedVideoCache[isFull]
            if (cached != null && cached.first == tpl.customVideoId) return cached.second
        }
        val file = videoStore.fileFor(tpl.customVideoId)
        synchronized(resolvedVideoCache) { resolvedVideoCache[isFull] = tpl.customVideoId to file }
        return file
    }

    /**
     * Force-renders a fresh frame for motion mode (current animation phase) into
     * the feed's dedicated bitmap, reused across ticks — zero per-tick allocation.
     * The caller must not recycle the returned bitmap; it is reused on the next tick.
     * The native send copies pixels synchronously, so reuse is race-free.
     */
    fun renderMotionFrame(width: Int = 1920, height: Int = 1080, overrideTemplate: LowerThirdTemplate? = null): Bitmap {
        val tpl = overrideTemplate ?: currentTemplate
        val isFull = tpl.isFullScreen
        var bmp = if (isFull) motionShowBitmap else motionLowerBitmap
        if (bmp == null || bmp.isRecycled || bmp.width != width || bmp.height != height) {
            try { bmp?.recycle() } catch (_: Exception) { /* ignore */ }
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (isFull) motionShowBitmap = bmp else motionLowerBitmap = bmp
        }
        drawFrameInto(bmp, width, height, isForStream = false, tpl, isFull, isMotionTick = true)
        return bmp
    }

    private fun renderFrame(width: Int = 1920, height: Int = 1080, isForStream: Boolean = false, overrideTemplate: LowerThirdTemplate? = null, allowSharedCache: Boolean): Bitmap {
        val tpl = overrideTemplate ?: currentTemplate
        val isFull = tpl.isFullScreen

        // Fast Path: Return cached Bitmap if state hasn't changed (0ms allocation-free)
        if (allowSharedCache && !isForStream && overrideTemplate == null && width == 1920 && height == 1080) {
            if (isFull && !isCacheDirtyShow && cachedShowBitmap != null && !cachedShowBitmap!!.isRecycled) {
                return cachedShowBitmap!!
            }
            if (!isFull && !isCacheDirtyLower && cachedLowerBitmap != null && !cachedLowerBitmap!!.isRecycled) {
                return cachedLowerBitmap!!
            }
        }

        val bitmap = if (allowSharedCache && !isForStream && overrideTemplate == null && width == 1920 && height == 1080) {
            val target = if (isFull) cachedShowBitmap else cachedLowerBitmap
            if (target != null && !target.isRecycled) {
                target
            } else {
                val newBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                if (isFull) cachedShowBitmap = newBmp else cachedLowerBitmap = newBmp
                newBmp
            }
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        drawFrameInto(bitmap, width, height, isForStream, tpl, isFull)

        if (allowSharedCache && !isForStream && overrideTemplate == null && width == 1920 && height == 1080) {
            if (isFull) isCacheDirtyShow = false else isCacheDirtyLower = false
        }

        return bitmap
    }

    /**
     * Draws the complete frame into [bitmap].
     *
     * Shared by the static dirty-frame path and motion rendering. Motion uses
     * dedicated reused bitmaps (see [renderMotionFrame]) so the static cache is
     * never redrawn in place — static readers can never observe a half-drawn frame.
     */
    private fun drawFrameInto(bitmap: Bitmap, width: Int, height: Int, isForStream: Boolean, tpl: LowerThirdTemplate, isFull: Boolean, isMotionTick: Boolean = false) {
        val canvas = Canvas(bitmap)

        // 100% Pure transparent alpha canvas by default
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // v1.8: no verse selected on launch — blank frame (cleared above; no text).
        val activeVerse = currentVerse ?: return

        // Video Stream Background: Since JPEG encoders have no alpha channel,
        // provide keyable broadcast background (Chroma Green #00FF00, Luma Black, or Studio)
        if (isForStream) {
            val bgCol = when (tpl.streamBackgroundMode) {
                StreamBackgroundMode.CHROMA_GREEN -> Color.parseColor("#00FF00")
                StreamBackgroundMode.PURE_BLACK -> Color.parseColor("#000000")
                StreamBackgroundMode.DARK_STUDIO -> Color.parseColor("#0A1128")
                StreamBackgroundMode.TRANSPARENT_ALPHA -> Color.parseColor("#00FF00") // Chroma green so OBS can key it instantly
            }
            canvas.drawColor(bgCol)
        }

        // When off-air and rendering for transparent overlay, return 100% transparent alpha frame.
        // Special Case: Full Show (Projector) should typically NOT be blanked even if not 'Live' for broadcast,
        // as the projector usually remains active with a background or current verse.
        if (!isLive && !isForStream && !tpl.isFullScreen) {
            return
        }

        val scale = width / 1920f
        // v1.8: user-controlled text shadow — thickness (blur radius), offset
        // distance, compass direction (0°=right, 90°=down, 180°=left, 270°=up).
        val shadowAngleRad = Math.toRadians(tpl.textShadowAngleDeg.toDouble())
        val shadowDx = (cos(shadowAngleRad) * tpl.textShadowOffsetDp * scale).toFloat()
        val shadowDy = (sin(shadowAngleRad) * tpl.textShadowOffsetDp * scale).toFloat()
        val shadowBlur = tpl.textShadowBlurDp * scale
        val marginH = (width * (tpl.horizontalMarginPercent / 100f)).coerceAtLeast(40f * scale)
        val marginB = (height * (tpl.positionBottomPercent / 100f)).coerceAtLeast(30f * scale)
        val boxWidth = width - (2 * marginH)

        // v1.8: three-way language control.
        val showArabic = tpl.languageMode != LanguageMode.ENGLISH_ONLY
        val showEnglish = tpl.languageMode != LanguageMode.ARABIC_ONLY &&
                !activeVerse.englishText.isNullOrBlank()

        val verseText = ArabicTextFormatter.prepareForBroadcast(
            activeVerse.arabicText,
            tpl.useEasternArabicNumerals,
            tpl.useArabicPunctuation
        )
        val citation = if (showArabic) activeVerse.getFormattedArabicCitation(tpl.useEasternArabicNumerals)
                       else activeVerse.getFormattedEnglishCitation()

        // Setup TextPaint for multi-line wrapped text
        val verseTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = try { Color.parseColor(tpl.textColorHex) } catch (e: Exception) { Color.WHITE }
            textSize = (tpl.verseFontSize * 2f * scale).coerceAtLeast(24f)
            
            var textStyle = Typeface.NORMAL
            if (tpl.verseIsBold && tpl.verseIsItalic) textStyle = Typeface.BOLD_ITALIC
            else if (tpl.verseIsBold) textStyle = Typeface.BOLD
            else if (tpl.verseIsItalic) textStyle = Typeface.ITALIC
            
            typeface = getBestTypeface(tpl.fontFamily, textStyle)
            if (tpl.textShadowEnabled) {
                val shadowCol = try { Color.parseColor(tpl.textShadowColorHex) } catch (e: Exception) { Color.BLACK }
                setShadowLayer(shadowBlur, shadowDx, shadowDy, shadowCol)
            }
            isFakeBoldText = tpl.verseIsBold
        }

        val paddingH = 32f * scale
        val paddingV = 24f * scale
        val contentWidth = (boxWidth - (2 * paddingH)).toInt().coerceAtLeast(200)

        // Fix alignment: ALIGN_NORMAL is Start (Right for RTL Arabic)
        val alignment = when (tpl.alignment) {
            BroadcastTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            BroadcastTextAlignment.LEFT -> Layout.Alignment.ALIGN_OPPOSITE // For RTL text, Opposite is Left
            BroadcastTextAlignment.RIGHT -> Layout.Alignment.ALIGN_NORMAL   // For RTL text, Normal is Right
        }

        // Apply Italic if requested via a Custom Typeface Wrapper or just skewed
        if (tpl.verseIsItalic) {
            verseTextPaint.textSkewX = -0.25f
        }

        val staticLayout: StaticLayout? = if (showArabic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(verseText, 0, verseText.length, verseTextPaint, contentWidth)
                .setAlignment(alignment)
                .setLineSpacing(6f * scale, 1.25f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(verseText, verseTextPaint, contentWidth, alignment, 1.25f, 6f * scale, true)
        }
        } else null

        val citationHeight = 44f * scale
        
        // Calculate bilingual secondary text if needed
        var secondaryLayout: StaticLayout? = null
        if (showEnglish) {
            // v1.8: In ENGLISH_ONLY, the citation is already in the main citation row.
            // Don't duplicate it here. In BOTH mode, include it with the English verse.
            val isEnglishOnly = tpl.languageMode == LanguageMode.ENGLISH_ONLY
            val citationText = if (isEnglishOnly) "" else " (" + activeVerse.getFormattedEnglishCitation() + ")"
            val fullSecondaryText = activeVerse.englishText + citationText
            
            val secondaryTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = try { Color.parseColor(tpl.secondaryTextColorHex) } catch (e: Exception) { Color.LTGRAY }
                textSize = (tpl.secondaryVerseFontSize * 2f * scale).coerceAtLeast(18f)
                
                var secStyle = Typeface.NORMAL
                if (tpl.secondaryVerseIsBold && tpl.secondaryVerseIsItalic) secStyle = Typeface.BOLD_ITALIC
                else if (tpl.secondaryVerseIsBold) secStyle = Typeface.BOLD
                else if (tpl.secondaryVerseIsItalic) secStyle = Typeface.ITALIC

                typeface = getBestTypeface(tpl.secondaryFontFamily, secStyle)
                if (tpl.textShadowEnabled) {
                    val shadowCol = try { Color.parseColor(tpl.textShadowColorHex) } catch (e: Exception) { Color.BLACK }
                    setShadowLayer(shadowBlur, shadowDx, shadowDy, shadowCol)
                }
                isFakeBoldText = tpl.secondaryVerseIsBold
                if (tpl.secondaryVerseIsItalic) textSkewX = -0.25f
            }
            
            // Use Spannable to apply different color and size to English citation if needed
            val spannable = SpannableString(fullSecondaryText)
            val secRefColor = try { Color.parseColor(tpl.secondaryReferenceColorHex) } catch (e: Exception) { Color.GRAY }
            val secRefSize = (tpl.secondaryReferenceFontSize * 2f * scale).toInt().coerceAtLeast(14)
            
            spannable.setSpan(
                ForegroundColorSpan(secRefColor),
                activeVerse.englishText.length,
                fullSecondaryText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                AbsoluteSizeSpan(secRefSize),
                activeVerse.englishText.length,
                fullSecondaryText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // v1.8: English stays visually left-aligned for LEFT and RIGHT;
            // only CENTER centers it (matches HTTP overlay + Compose preview).
            val secAlignment = when (tpl.alignment) {
                BroadcastTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_NORMAL // LTR English: NORMAL = left
            }
            
            secondaryLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(spannable, 0, spannable.length, secondaryTextPaint, contentWidth)
                    .setAlignment(secAlignment)
                    .setLineSpacing(4f * scale, 1.15f)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(spannable, secondaryTextPaint, contentWidth, secAlignment, 1.15f, 4f * scale, true)
            }
        }

        val bilSpacing = if (tpl.showBilingualSpacing) tpl.bilingualSpacing * scale else 0f
        // v1.8: primary content drives sizing/positioning — Arabic when shown,
        // else English (ENGLISH_ONLY). The offset secondary block only exists
        // when BOTH languages are shown.
        val primaryLayout = staticLayout ?: secondaryLayout ?: return
        val bilingualSecondary = if (staticLayout != null) secondaryLayout else null
        val secHeight = if (bilingualSecondary != null) bilingualSecondary.height + bilSpacing else 0f
        
        // Full screen check for projector/full show mode
        val boxHeight = if (tpl.isFullScreen) {
            height.toFloat() 
        } else {
            (primaryLayout.height + citationHeight + secHeight + (paddingV * 2) + 16f * scale).coerceAtLeast(140f * scale)
        }

        val boxTop = if (tpl.isFullScreen) 0f else (height - marginB - boxHeight)
        val boxBottom = if (tpl.isFullScreen) height.toFloat() else (height - marginB)
        val boxLeft = if (tpl.isFullScreen) 0f else marginH
        val boxRight = if (tpl.isFullScreen) width.toFloat() else (width - marginH)
        val rect = RectF(boxLeft, boxTop, boxRight, boxBottom)
        val radius = if (tpl.isFullScreen) 0f else (tpl.cornerRadiusDp * scale * 2f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // v1.7 custom video background (per feed). The video is the background
        // layer: drawn behind card/effects/text/emblem with the user's opacity.
        // Motion ticks pull fresh decoded frames; static renders freeze on the
        // last frame (the first static render kick-starts decoding).
        val customVideoFile = resolveCustomVideoFile(tpl, isFull)
        val videoRenderer = if (customVideoFile != null) getVideoRenderer(isFull) else null
        if (videoRenderer != null && customVideoFile != null) {
            videoRenderer.setSource(customVideoFile)
            videoRenderer.setAlpha(tpl.animatedBackgroundOpacity)
            videoRenderer.setMuted(tpl.customVideoMuted)
            videoRenderer.setSpeed(tpl.motionSpeed)
            if (isMotionTick || !videoRenderer.hasFrame()) videoRenderer.requestFrame()
            val videoRect = Rect(boxLeft.toInt(), boxTop.toInt(), boxRight.toInt(), boxBottom.toInt())
            if (radius > 0f) {
                canvas.save()
                canvas.clipPath(Path().apply {
                    addRoundRect(RectF(boxLeft, boxTop, boxRight, boxBottom), radius, radius, Path.Direction.CW)
                })
                videoRenderer.drawOnto(canvas, videoRect)
                canvas.restore()
            } else {
                videoRenderer.drawOnto(canvas, videoRect)
            }
        } else {
            // Not a video template: park this feed's decoder (no-op if none).
            synchronized(videoRenderers) { videoRenderers[isFull]?.pause() }
        }

        if (!tpl.isPureTransparentBackground && tpl.style != TemplateStyle.TRANSPARENT_OUTLINE) {
            // Independent card glow (v1.8): deterministic layered outer halo in
            // the user's glow color, drawn strictly OUTSIDE the card bounds.
            // Replaces BlurMaskFilter.OUTER, whose blurred color bled through
            // semi-transparent card fills. Only where a real card exists.
            if (tpl.cardGlowEnabled && !tpl.isFullScreen) {
                val glowCol = try { Color.parseColor(tpl.cardGlowColorHex) } catch (e: Exception) { Color.BLACK }
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                // (expand beyond card, alpha, corner-radius bump)
                val layers = listOf(
                    Triple(14f * scale, 0.30f, 4f * scale),
                    Triple(30f * scale, 0.15f, 8f * scale)
                )
                for ((expand, alpha, radiusBump) in layers) {
                    glowPaint.color = Color.argb(
                        (255 * alpha).toInt().coerceIn(0, 255),
                        Color.red(glowCol), Color.green(glowCol), Color.blue(glowCol)
                    )
                    canvas.drawRoundRect(
                        RectF(rect.left - expand, rect.top - expand,
                              rect.right + expand, rect.bottom + expand),
                        radius + radiusBump, radius + radiusBump, glowPaint
                    )
                }
            }
            val bgAlpha = (tpl.bgOpacity * 255).toInt().coerceIn(0, 255)
            val baseBgCol = try { Color.parseColor(tpl.bgColorHex) } catch (e: Exception) { Color.DKGRAY }

            if (videoRenderer != null) {
                // v1.7: the custom video IS the card background (already drawn
                // above) — skip the normal fill so it stays visible.
            } else if (tpl.animatedBackground != AnimatedBackgroundType.NONE) {
                val timeSec = (System.currentTimeMillis() % 12000L) / 1000f
                val phase = (sin(timeSec.toDouble() * 1.5).toFloat() + 1f) / 2f
                val shader: Shader? = when (tpl.animatedBackground) {
                    AnimatedBackgroundType.GOLDEN_DIVINE_RAYS -> {
                        val c1 = Color.argb(bgAlpha, 217, 119, 6)
                        val c2 = Color.argb(bgAlpha, 251, 191, 36)
                        LinearGradient(
                            boxLeft + (boxWidth * phase), boxTop,
                            boxRight - (boxWidth * (1f - phase)), boxBottom,
                            intArrayOf(c1, c2, c1), null, Shader.TileMode.CLAMP
                        )
                    }
                    AnimatedBackgroundType.ETHEREAL_BLUE_WAVES -> {
                        val c1 = Color.argb(bgAlpha, 30, 58, 138)
                        val c2 = Color.argb(bgAlpha, 14, 165, 233)
                        LinearGradient(
                            boxLeft, boxTop + (boxHeight * phase),
                            boxRight, boxBottom - (boxHeight * (1f - phase)),
                            intArrayOf(c1, c2, c1), null, Shader.TileMode.CLAMP
                        )
                    }
                    AnimatedBackgroundType.CANDLE_LITURGICAL_GLOW -> {
                        val c1 = Color.argb(bgAlpha, 120, 53, 15)
                        val c2 = Color.argb(bgAlpha, 245, 158, 11)
                        RadialGradient(
                            boxRight - (paddingH * 2f), boxTop + (boxHeight / 2f),
                            (boxHeight * 1.4f) * (0.8f + 0.3f * phase),
                            intArrayOf(c2, c1), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                        )
                    }
                    AnimatedBackgroundType.ROYAL_PURPLE_SILK -> {
                        val c1 = Color.argb(bgAlpha, 88, 28, 135)
                        val c2 = Color.argb(bgAlpha, 192, 132, 252)
                        LinearGradient(
                            boxLeft + (boxWidth * phase), boxTop,
                            boxRight, boxBottom,
                            intArrayOf(c1, c2, c1), null, Shader.TileMode.CLAMP
                        )
                    }
                    AnimatedBackgroundType.EMERALD_GARDEN_WAVES -> {
                        val c1 = Color.argb(bgAlpha, 6, 78, 59)
                        val c2 = Color.argb(bgAlpha, 52, 211, 153)
                        LinearGradient(
                            boxLeft, boxTop + (boxHeight * phase),
                            boxRight, boxBottom,
                            intArrayOf(c1, c2, c1), null, Shader.TileMode.CLAMP
                        )
                    }
                    AnimatedBackgroundType.ROSE_DAWN_GLOW -> {
                        val c1 = Color.argb(bgAlpha, 136, 19, 55)
                        val c2 = Color.argb(bgAlpha, 251, 113, 133)
                        RadialGradient(
                            boxLeft + (boxWidth / 2f), boxTop + (boxHeight / 2f),
                            (boxHeight * 1.2f) * (0.8f + 0.3f * phase),
                            intArrayOf(c2, c1), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
                        )
                    }
                    AnimatedBackgroundType.GOLDEN_PARTICLES -> {
                        val c1 = Color.argb(bgAlpha, 120, 53, 15)
                        val c2 = Color.argb(bgAlpha, 252, 211, 77)
                        LinearGradient(
                            boxLeft + (boxWidth * (1f - phase)), boxTop,
                            boxRight, boxBottom,
                            intArrayOf(c1, c2, c1), null, Shader.TileMode.CLAMP
                        )
                    }
                    AnimatedBackgroundType.PARTICLE_STARS -> {
                        val c1 = Color.argb(bgAlpha, 15, 23, 42)
                        val c2 = Color.argb(bgAlpha, 56, 189, 248)
                        LinearGradient(
                            boxLeft, boxTop,
                            boxRight, boxBottom,
                            intArrayOf(c1, c2, c1), null, Shader.TileMode.CLAMP
                        )
                    }
                    else -> null
                }

                if (shader != null) {
                    paint.shader = shader
                    canvas.drawRoundRect(rect, radius, radius, paint)
                    paint.shader = null
                } else {
                    paint.color = Color.argb(bgAlpha, Color.red(baseBgCol), Color.green(baseBgCol), Color.blue(baseBgCol))
                    canvas.drawRoundRect(rect, radius, radius, paint)
                }
            } else {
                paint.color = Color.argb(bgAlpha, Color.red(baseBgCol), Color.green(baseBgCol), Color.blue(baseBgCol))
                canvas.drawRoundRect(rect, radius, radius, paint)
            }

            if (tpl.showAccentBorder) {
                val accCol = try { Color.parseColor(tpl.accentColorHex) } catch (e: Exception) { Color.YELLOW }
                paint.color = accCol
                paint.strokeWidth = 6f * scale
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
        } else if (tpl.animatedBackground != AnimatedBackgroundType.NONE && tpl.animatedBackground != AnimatedBackgroundType.CUSTOM_VIDEO) {
            // If Pure Transparent background with animated motion background is selected:
            // Draw a delicate, ethereal luminous motion glow directly behind the verse while maintaining 100% alpha transparency elsewhere!
            val animAlpha = (tpl.animatedBackgroundOpacity * 75).toInt().coerceIn(15, 120)
            val timeSec = (System.currentTimeMillis() % 12000L) / 1000f
            val phase = (sin(timeSec.toDouble() * 1.5).toFloat() + 1f) / 2f
            val glowColor = when (tpl.animatedBackground) {
                AnimatedBackgroundType.GOLDEN_DIVINE_RAYS -> Color.argb(animAlpha, 251, 191, 36)
                AnimatedBackgroundType.ETHEREAL_BLUE_WAVES -> Color.argb(animAlpha, 56, 189, 248)
                AnimatedBackgroundType.CANDLE_LITURGICAL_GLOW -> Color.argb(animAlpha, 245, 158, 11)
                AnimatedBackgroundType.ROYAL_PURPLE_SILK -> Color.argb(animAlpha, 192, 132, 252)
                AnimatedBackgroundType.PARTICLE_STARS -> Color.argb(animAlpha, 125, 211, 252)
                AnimatedBackgroundType.EMERALD_GARDEN_WAVES -> Color.argb(animAlpha, 52, 211, 153)
                AnimatedBackgroundType.ROSE_DAWN_GLOW -> Color.argb(animAlpha, 251, 113, 133)
                AnimatedBackgroundType.GOLDEN_PARTICLES -> Color.argb(animAlpha, 252, 211, 77)
                else -> Color.argb(animAlpha, 255, 255, 255)
            }
            val glowShader = RadialGradient(
                boxLeft + (boxWidth * (0.35f + 0.3f * phase)), boxTop + (boxHeight / 2f),
                boxWidth * 0.55f,
                intArrayOf(glowColor, Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            paint.shader = glowShader
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.shader = null
        }

        // Draw Citation Badge
        val refPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = try { Color.parseColor(tpl.referenceColorHex) } catch (e: Exception) { Color.YELLOW }
            textSize = (tpl.referenceFontSize * 2f * scale).coerceAtLeast(18f)
            
            var refStyle = Typeface.NORMAL
            if (tpl.referenceIsBold && tpl.referenceIsItalic) refStyle = Typeface.BOLD_ITALIC
            else if (tpl.referenceIsBold) refStyle = Typeface.BOLD
            else if (tpl.referenceIsItalic) refStyle = Typeface.ITALIC

            typeface = getBestTypeface(tpl.fontFamily, refStyle)
            if (tpl.textShadowEnabled) {
                val shadowCol = try { Color.parseColor(tpl.textShadowColorHex) } catch (e: Exception) { Color.BLACK }
                setShadowLayer(shadowBlur, shadowDx, shadowDy, shadowCol)
            }
            isFakeBoldText = tpl.referenceIsBold
            if (tpl.referenceIsItalic) textSkewX = -0.25f
        }

        val contentTotalHeight = primaryLayout.height + citationHeight + secHeight
        val startY = if (tpl.isFullScreen) {
            (height - contentTotalHeight) / 2f
        } else {
            boxTop + paddingV
        }

        val citationY = startY + (refPaint.textSize * 0.9f)

        // User emblem (emoji/symbol) drawn adjacent to the citation on the
        // reading-start side, in the accent color like the HTTP overlay.
        // Empty emblem = nothing drawn.
        val emblemText = tpl.emblem
        if (emblemText.isNotEmpty()) {
            val emblemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // v1.8: emblem uses citation styling (was accent color).
                color = refPaint.color
                textSize = refPaint.textSize
                // System default typeface: reliable emoji/symbol fallback.
                typeface = Typeface.DEFAULT
                if (tpl.textShadowEnabled) {
                    val shadowCol = try { Color.parseColor(tpl.textShadowColorHex) } catch (e: Exception) { Color.BLACK }
                    setShadowLayer(shadowBlur, shadowDx, shadowDy, shadowCol)
                }
            }
            val emblemW = emblemPaint.measureText(emblemText)
            val citeW = refPaint.measureText(citation)
            val emblemGap = 16f * scale
            val totalW = emblemW + emblemGap + citeW

            fun drawEmblemAt(x: Float, align: Paint.Align) {
                emblemPaint.textAlign = align
                canvas.drawText(emblemText, x, citationY, emblemPaint)
            }

            // v1.7: no forced centering — Full Show obeys the user's alignment
            // exactly like Lower Third and every other surface.
            when (tpl.alignment) {
                    BroadcastTextAlignment.CENTER -> {
                        val startX = (width - totalW) / 2f
                        refPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText(citation, startX, citationY, refPaint)
                        drawEmblemAt(startX + citeW + emblemGap, Paint.Align.LEFT)
                    }
                    BroadcastTextAlignment.LEFT -> {
                        refPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText(citation, boxLeft + paddingH + emblemW + emblemGap, citationY, refPaint)
                        drawEmblemAt(boxLeft + paddingH, Paint.Align.LEFT)
                    }
                    BroadcastTextAlignment.RIGHT -> {
                        refPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(citation, boxRight - paddingH - emblemW - emblemGap, citationY, refPaint)
                        drawEmblemAt(boxRight - paddingH, Paint.Align.RIGHT)
                    }
            }
        } else {
            // v1.7: no forced centering here either.
            when (tpl.alignment) {
                BroadcastTextAlignment.CENTER -> {
                    refPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(citation, width / 2f, citationY, refPaint)
                }
                BroadcastTextAlignment.LEFT -> {
                    refPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(citation, boxLeft + paddingH, citationY, refPaint)
                }
                BroadcastTextAlignment.RIGHT -> {
                    refPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(citation, boxRight - paddingH, citationY, refPaint)
                }
            }
        }

        // v1.7: the verse block obeys the user's alignment on Full Show too.
        val translateX = if (tpl.alignment == BroadcastTextAlignment.CENTER) {
            (width - contentWidth) / 2f
        } else if (tpl.alignment == BroadcastTextAlignment.RIGHT) {
            // v1.8: Right-align the verse layout with the citation (account for emblem).
            // The citation's right edge is at boxRight - paddingH - emblemW - emblemGap.
            // Use a temporary paint for measurement (emblemPaint is defined later).
            val tmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 24f * scale
            }
            val emblemW = tmpPaint.measureText(tpl.emblem)
            val emblemGap = 16f * scale
            boxRight - paddingH - emblemW - emblemGap - contentWidth
        } else {
            boxLeft + paddingH
        }

        // Draw Multi-line Verse Text wrapped smoothly
        val textY = citationY + (16f * scale)
        canvas.save()
        canvas.translate(translateX, textY)
        primaryLayout.draw(canvas)
        canvas.restore()

        // Draw Bilingual Section if active (BOTH mode only)
        if (bilingualSecondary != null) {
            val bilSpacing = if (tpl.showBilingualSpacing) tpl.bilingualSpacing * scale else 0f
            val secY = textY + primaryLayout.height + bilSpacing
            canvas.save()
            canvas.translate(translateX, secY)
            bilingualSecondary.draw(canvas)
            canvas.restore()
        }

        if (!isLive && isForStream) {
            val standbyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(220, 15, 23, 42)
            }
            val standbyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(255, 245, 158, 11)
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
            }
            val standbyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 20f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                setShadowLayer(4f * scale, 0f, 2f * scale, Color.BLACK)
            }
            val badgeW = 420f * scale
            val badgeH = 50f * scale
            val badgeRect = RectF(width / 2f - badgeW / 2f, 35f * scale, width / 2f + badgeW / 2f, 35f * scale + badgeH)
            canvas.drawRoundRect(badgeRect, 14f * scale, 14f * scale, standbyBgPaint)
            canvas.drawRoundRect(badgeRect, 14f * scale, 14f * scale, standbyBorderPaint)
            canvas.drawText("وضع الاستعداد • STANDBY (اضغط على الهواء للبث)", width / 2f, 35f * scale + (badgeH * 0.65f), standbyTextPaint)
        }

    }

    /**
     * v1.7: serves a user background video by opaque ID from private storage.
     * - ID allowlist only (VideoStore validates the pattern + canonical path):
     *   a LAN client can never request arbitrary files.
     * - Streams in chunks (no whole-file readBytes into RAM).
     * - Supports HTTP Range / 206 so browsers can seek.
     */
    private fun serveVideoById(out: OutputStream, fullPath: String, rangeHeader: String?) {
        try {
            val id = Uri.parse(fullPath).getQueryParameter("id") ?: return send404(out)
            val file = videoStore.fileFor(id) ?: return send404(out)
            val total = file.length()
            if (total <= 0) return send404(out)

            var start = 0L
            var end = total - 1
            var partial = false
            if (rangeHeader != null) {
                // Single range: "bytes=start-end".
                val m = Regex("bytes=(\\d*)-(\\d*)").find(rangeHeader.trim())
                if (m != null) {
                    val s = m.groupValues[1]
                    val e = m.groupValues[2]
                    try {
                        start = if (s.isNotEmpty()) s.toLong() else maxOf(0L, total - e.toLong())
                        end = if (e.isNotEmpty() && s.isNotEmpty()) e.toLong().coerceAtMost(total - 1) else total - 1
                        if (start in 0 until total && end >= start) partial = true
                    } catch (nfe: NumberFormatException) { /* fall through to full */ }
                }
            }
            val length = end - start + 1
            val writer = PrintWriter(out)
            if (partial) {
                writer.print("HTTP/1.1 206 Partial Content\r\n")
            } else {
                writer.print("HTTP/1.1 200 OK\r\n")
            }
            writer.print("Content-Type: video/mp4\r\n")
            writer.print("Accept-Ranges: bytes\r\n")
            writer.print("Content-Length: $length\r\n")
            if (partial) {
                writer.print("Content-Range: bytes $start-$end/$total\r\n")
            }
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Connection: close\r\n\r\n")
            writer.flush()

            file.inputStream().use { input ->
                var skipped = 0L
                while (skipped < start) {
                    val n = input.skip(start - skipped)
                    if (n <= 0) break
                    skipped += n
                }
                val buf = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    remaining -= n
                }
                out.flush()
            }
        } catch (e: Exception) {
            // Headers may already be sent; nothing more we can do.
        }
    }

    private fun getBestTypeface(family: String, textStyle: Int): Typeface {
        val cacheKey = "$family|$textStyle"
        typefaceCache[cacheKey]?.let { return it }
        val result = try {
            val bundled = ArabicFonts.find(family)
            if (bundled != null) {
                // Bundled Google Font (assets/fonts, fully offline). Prefer the true
                // bold file when bold was requested; synthesize italic (Arabic has no
                // true italics) or a missing bold via Typeface.create().
                val wantBold = textStyle == Typeface.BOLD || textStyle == Typeface.BOLD_ITALIC
                val assetPath =
                    if (wantBold && bundled.asset700 != null) bundled.asset700 else bundled.asset400
                val base = Typeface.createFromAsset(context.assets, assetPath)
                val remainingStyle =
                    if (assetPath == bundled.asset700) textStyle and Typeface.BOLD.inv() else textStyle
                if (remainingStyle == Typeface.NORMAL) base else Typeface.create(base, remainingStyle)
            } else {
                // "System" or unknown family: Android system default.
                Typeface.create(Typeface.DEFAULT, textStyle)
            }
        } catch (e: Exception) {
            try {
                Typeface.create(Typeface.DEFAULT, textStyle)
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        typefaceCache[cacheKey] = result
        return result
    }

    /** Cache of asset-loaded typefaces, keyed by "family|style". Thread-safe: render paths run under per-feed locks. */
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    /** @font-face rules for every bundled Arabic font, served from this tablet (fully offline). */
    private fun buildFontFaceCss(): String {
        val sb = StringBuilder(8192)
        for (f in ArabicFonts.all) {
            val f400 = f.asset400.substringAfterLast('/')
            sb.append("@font-face{font-family:'").append(f.family)
                .append("';font-style:normal;font-weight:400;font-display:swap;src:url('/fonts/")
                .append(f400).append("') format('truetype');}")
            val a700 = f.asset700
            if (a700 != null) {
                val f700 = a700.substringAfterLast('/')
                sb.append("@font-face{font-family:'").append(f.family)
                    .append("';font-style:normal;font-weight:700;font-display:swap;src:url('/fonts/")
                    .append(f700).append("') format('truetype');}")
            }
        }
        return sb.toString()
    }

    /** Exact allowlist of servable font files, derived from the registry (no path traversal). */
    private val fontAssetFiles: Set<String> by lazy {
        ArabicFonts.all.flatMap {
            listOfNotNull(it.asset400.substringAfterLast('/'), it.asset700?.substringAfterLast('/'))
        }.toSet()
    }

    /** Serves a bundled font TTF from assets so HTTP overlays work fully offline. */
    private fun serveFontFile(out: OutputStream, requested: String) {
        val writer = PrintWriter(out)
        val name = requested.substringAfterLast('/').trim()
        if (!name.endsWith(".ttf") || name !in fontAssetFiles) {
            writer.print("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n")
            writer.flush()
            return
        }
        try {
            context.assets.open("fonts/$name").use { ins ->
                val bytes = ins.readBytes()
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: font/ttf\r\n")
                writer.print("Content-Length: ${bytes.size}\r\n")
                writer.print("Cache-Control: public, max-age=31536000, immutable\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.flush()
                out.write(bytes)
                out.flush()
            }
        } catch (e: Exception) {
            try {
                writer.print("HTTP/1.1 500 Internal Server Error\r\nConnection: close\r\n\r\n")
                writer.flush()
            } catch (_: Exception) { /* ignore */ }
        }
    }

    private fun serveTransparentPngOverlay(out: OutputStream) {
        // Owned bitmap: safe to recycle (never the shared NDI cache bitmap).
        val bitmap = renderFrameOwned(1920, 1080, isForStream = false)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        val pngBytes = stream.toByteArray()

        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: image/png\r\n")
        writer.print("Content-Length: ${pngBytes.size}\r\n")
        writer.print("Cache-Control: no-cache, no-store\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(pngBytes)
        out.flush()
    }

    private fun serveMjpegPlayerHtml(out: OutputStream) {
        val ip = getLocalIpAddress()
        val html = """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>بث آيات الكتاب المقدس | Bible Live Stream</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #0d1117;
      color: #e6edf3;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 16px;
    }
    .player-card {
      background: #161b22;
      border: 1px solid #30363d;
      border-radius: 16px;
      padding: 24px;
      max-width: 960px;
      width: 100%;
      box-shadow: 0 16px 40px rgba(0,0,0,0.6);
      text-align: center;
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: #238636;
      color: #ffffff;
      padding: 6px 16px;
      border-radius: 999px;
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 0.5px;
      margin-bottom: 16px;
    }
    .badge-dot {
      width: 8px;
      height: 8px;
      background: #39d353;
      border-radius: 50%;
      animation: pulse 1.5s infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.4; transform: scale(0.85); }
    }
    h1 {
      font-size: 20px;
      margin-bottom: 8px;
      color: #f0f6fc;
    }
    p.subtitle {
      color: #8b949e;
      font-size: 14px;
      margin-bottom: 20px;
    }
    .video-container {
      position: relative;
      background: #000000;
      border-radius: 12px;
      overflow: hidden;
      border: 2px solid #388bfd;
      box-shadow: 0 8px 24px rgba(0,0,0,0.5);
      margin-bottom: 20px;
      aspect-ratio: 16 / 9;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .video-container img {
      width: 100%;
      height: 100%;
      object-fit: contain;
      display: block;
    }
    .instructions {
      background: #0d1117;
      border: 1px solid #21262d;
      border-radius: 10px;
      padding: 14px;
      font-size: 13px;
      color: #c9d1d9;
      text-align: right;
      line-height: 1.6;
    }
    .code-box {
      direction: ltr;
      display: block;
      background: #1f242c;
      padding: 8px 12px;
      border-radius: 6px;
      color: #79c0ff;
      font-family: monospace;
      font-size: 13px;
      margin-top: 6px;
      word-break: break-all;
    }
    .btn-row {
      display: flex;
      gap: 10px;
      justify-content: center;
      margin-top: 16px;
      flex-wrap: wrap;
    }
    .btn {
      padding: 8px 18px;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.2s;
    }
    .btn-primary {
      background: #1f6feb;
      color: white;
    }
    .btn-secondary {
      background: #21262d;
      color: #c9d1d9;
      border: 1px solid #30363d;
    }
  </style>
</head>
<body>
  <div class="player-card">
    <div class="badge">
      <span class="badge-dot"></span>
      بث حي مباشر (LIVE MJPEG STREAM)
    </div>
    <h1>بث آيات الكتاب المقدس على الشبكة المحلية</h1>
    <p class="subtitle">يتم تحديث البث تلقائياً وفورياً عند اختيار أي آية من التطبيق</p>

    <div class="video-container">
      <img id="streamImg" src="/stream?raw=1" alt="Bible Live Broadcast Stream" onerror="setTimeout(() => { this.src = '/stream?raw=1&t=' + Date.now(); }, 1500);" />
    </div>

    <div class="instructions">
      <b>إرشادات التشغيل في برامج البث (OBS Studio / vMix):</b>
      <br>1. <b>للحصول على خلفية شفافة كاملة (Alpha Transparency):</b> يُنصح بإضافة <b>Browser Source</b> ووضع الرابط التالي:
      <span class="code-box">http://$ip:$port/ndi</span>
      <br>2. <b>للبث كفيديو (Media Source / Video Stream):</b> أضف مصدر فيديو وضع الرابط:
      <span class="code-box">http://$ip:$port/ndi/stream.mjpg?raw=1</span>
    </div>

    <div class="btn-row">
      <a class="btn btn-primary" href="/lower" target="_blank">فتح طبقة البث الشفافة (Overlay)</a>
      <a class="btn btn-secondary" href="/stream?raw=1" target="_blank">عرض دفق الفيديو المباشر (Raw MJPEG)</a>
    </div>
  </div>
</body>
</html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun serveLiveStream(socket: Socket, out: OutputStream, fullPath: String) {
        try {
            socket.soTimeout = 0 // Infinite timeout for live streaming
            socket.tcpNoDelay = true

            val useJpeg = !fullPath.contains("format=png")
            val boundary = "mjpeg_frame"
            val mime = if (useJpeg) "image/jpeg" else "image/png"

            val streamHeader = ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
            out.write(streamHeader)
            out.flush()

            // Continuous active frame loop (~25 FPS smooth broadcast)
            while (isRunning && !socket.isClosed && !socket.isOutputShutdown) {
                val ok = pushFrameToStream(out, boundary, useJpeg)
                if (!ok) break
                Thread.sleep(40) // ~25 FPS
            }
        } catch (e: Exception) {
            // Stream client disconnected
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun pushFrameToStream(out: OutputStream, boundary: String, useJpeg: Boolean): Boolean {
        return try {
            // JPEG path already allocates a fresh bitmap (isForStream=true bypasses the
            // shared cache). PNG path uses an owned render so recycle() can never hit
            // the live NDI cache bitmaps. Either way, recycle() is safe here.
            val bitmap = if (useJpeg) {
                renderCurrentFrame(1920, 1080, isForStream = true)
            } else {
                renderFrameOwned(1920, 1080, isForStream = false)
            }
            val stream = ByteArrayOutputStream()
            val format = if (useJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val quality = if (useJpeg) 85 else 100
            bitmap.compress(format, quality, stream)
            bitmap.recycle()
            val frameBytes = stream.toByteArray()
            val mime = if (useJpeg) "image/jpeg" else "image/png"

            val header = "--$boundary\r\nContent-Type: $mime\r\nContent-Length: ${frameBytes.size}\r\n\r\n"
            out.write(header.toByteArray(Charsets.US_ASCII))
            out.write(frameBytes)
            out.write("\r\n".toByteArray(Charsets.US_ASCII))
            out.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun serveStatus(out: OutputStream) {
        val ip = getLocalIpAddress()
        val msg = "OK - Bible NDI Server Online\nIP: $ip\nPort: $port\nStream: http://$ip:$port/stream\nLower: http://$ip:$port/lower\nFull: http://$ip:$port/full\n"
        val bytes = msg.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/plain; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    // ---- v1.8 remote API ----

    /** Parse URL query string into a decoded map. */
    private fun parseQueryParams(fullPath: String): Map<String, String> {
        val qIndex = fullPath.indexOf('?')
        if (qIndex < 0) return emptyMap()
        val query = fullPath.substring(qIndex + 1)
        val map = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq < 0) continue
            val key = pair.substring(0, eq)
            val value = try {
                java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
            } catch (e: Exception) {
                pair.substring(eq + 1)
            }
            map[key] = value
        }
        return map
    }

    /** Send a JSON response with consistent {"ok": ...} shape. */
    private fun sendJson(out: OutputStream, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "OK"
        }
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun jsonEscape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * GET /api/trigger?ref=John+3:16[&target=lower|show|both]
     * GET /api/trigger?book=43&chapter=3&verse=16[&target=...]
     * Triggers a verse on the broadcast outputs.
     */
    private fun serveApiTrigger(out: OutputStream, fullPath: String) {
        val params = parseQueryParams(fullPath)
        val target = (params["target"] ?: "both").lowercase().let {
            when (it) {
                "lower", "lowerthird", "lower-third" -> "lower"
                "show", "full", "fullscreen", "fullshow" -> "show"
                else -> "both"
            }
        }

        val ref = params["ref"]
        var bookId: String? = null
        var chapter: Int? = null
        var verseNum: Int? = null

        if (ref != null) {
            // Free-form reference: English/Arabic name or abbreviation.
            val books = try {
                com.arabicchristianmedia.data.BibleRepository.allBooks
            } catch (e: Exception) { emptyList() }
            val parsed = com.arabicchristianmedia.model.ReferenceParser.parse(ref, books)
            if (parsed == null) {
                sendJson(out, 400, """{"ok":false,"error":"Could not understand reference: ${jsonEscape(ref)}"}""")
                return
            }
            bookId = parsed.book.id
            chapter = parsed.chapter
            verseNum = parsed.verse
        } else {
            // Numeric fallback: ?book=43&chapter=3&verse=16 (1-indexed book).
            val bookNum = params["book"]?.toIntOrNull()
            chapter = params["chapter"]?.toIntOrNull()
            verseNum = params["verse"]?.toIntOrNull()
            if (bookNum == null || chapter == null || verseNum == null ||
                bookNum < 1 || chapter < 1 || verseNum < 1) {
                sendJson(out, 400, """{"ok":false,"error":"Provide ?ref=John+3:16 or ?book=43&chapter=3&verse=16"}""")
                return
            }
            val books = try {
                com.arabicchristianmedia.data.BibleRepository.allBooks
            } catch (e: Exception) { emptyList() }
            val book = books.getOrNull(bookNum - 1)
            if (book == null) {
                sendJson(out, 400, """{"ok":false,"error":"Book number out of range: $bookNum"}""")
                return
            }
            bookId = book.id
        }

        val ok = try {
            onTriggerVerse?.invoke(bookId!!, chapter!!, verseNum!!, target) ?: false
        } catch (e: Exception) { false }

        if (ok) {
            sendJson(out, 200, """{"ok":true,"bookId":"${jsonEscape(bookId!!)}","chapter":$chapter,"verse":$verseNum,"target":"$target"}""")
        } else {
            sendJson(out, 400, """{"ok":false,"error":"Verse not found: bookId=$bookId $chapter:$verseNum"}""")
        }
    }

    /** GET /api/clear — clear the active verse (take outputs off-air). */
    private fun serveApiClear(out: OutputStream) {
        try { onClearVerse?.invoke() } catch (e: Exception) { }
        sendJson(out, 200, """{"ok":true,"cleared":true}""")
    }

    /** GET /api/videos — list background videos by opaque ID. */
    private fun serveApiVideos(out: OutputStream) {
        val videos = try { onListVideos?.invoke() ?: emptyList() } catch (e: Exception) { emptyList() }
        val sb = StringBuilder()
        sb.append("""{"ok":true,"videos":[""")
        videos.forEachIndexed { i, v ->
            if (i > 0) sb.append(",")
            sb.append("""{"id":"${jsonEscape(v.id)}","name":"${jsonEscape(v.displayName)}","sizeBytes":${v.sizeBytes},"url":"/video?id=${jsonEscape(v.id)}"}""")
        }
        sb.append("]}")
        sendJson(out, 200, sb.toString())
    }

    /**
     * GET /bibleshow.xml — vMix BibleShow-compatible text feed (v1.8).
     * Text/data only; vMix controls all formatting. TimeCode is .NET ticks.
     */
    private fun serveBibleShowXml(out: OutputStream) {
        val verse = currentVerse
        val tpl = currentTemplate

        fun xmlEscape(s: String): String {
            return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        // .NET ticks: 100ns intervals since 0001-01-01 UTC.
        val dotNetTicks = System.currentTimeMillis() * 10000L + 621355968000000000L

        // v1.8: Respect the Lower Third language mode.
        val langMode = tpl.languageMode
        val showArabic = langMode != LanguageMode.ENGLISH_ONLY
        val showEnglish = langMode != LanguageMode.ARABIC_ONLY
        // Numbers in Eastern Arabic when Arabic is shown (BOTH uses Arabic numbers per Ashraf).
        val useEasternNumbers = showArabic && tpl.useEasternArabicNumerals

        fun formatNum(n: Int): String {
            return if (useEasternNumbers) ArabicTextFormatter.toEasternArabicDigits(n.toString())
            else n.toString()
        }

        val arabicVerse = if (verse != null && showArabic) {
            ArabicTextFormatter.prepareForBroadcast(
                verse.arabicText, tpl.useEasternArabicNumerals, tpl.useArabicPunctuation
            )
        } else ""
        val arabicCitation = if (verse != null && showArabic) {
            verse.getFormattedArabicCitation(tpl.useEasternArabicNumerals)
        } else ""
        val englishVerse = if (verse != null && showEnglish) verse.englishText ?: "" else ""
        val englishCitation = if (verse != null && showEnglish) verse.getFormattedEnglishCitation() else ""

        // Scripture: respects language mode.
        val scripture = buildString {
            if (arabicVerse.isNotEmpty()) {
                append(arabicVerse)
                append("\n")
                append(arabicCitation)
            }
            if (englishVerse.isNotEmpty()) {
                if (arabicVerse.isNotEmpty()) append("\n\n")
                append(englishVerse)
                append("\n")
                append(englishCitation)
            }
        }

        // Separate fields for vMix text inputs.
        val bookName = when {
            showArabic && !showEnglish -> verse?.bookArabicName ?: ""
            showEnglish && !showArabic -> verse?.bookEnglishName ?: ""
            else -> verse?.bookArabicName ?: ""
        }
        val chapterNum = verse?.chapter?.let { formatNum(it) } ?: ""
        val verseNum = verse?.verse?.let { formatNum(it) } ?: ""
        val chapterVerse = if (verse != null) {
            if (useEasternNumbers) "${formatNum(verse.chapter)} : ${formatNum(verse.verse)}"
            else "${verse.chapter}:${verse.verse}"
        } else ""
        // Verse text without citation.
        val verseTextOnly = buildString {
            if (arabicVerse.isNotEmpty()) append(arabicVerse)
            if (englishVerse.isNotEmpty()) {
                if (arabicVerse.isNotEmpty()) append("\n\n")
                append(englishVerse)
            }
        }
        val bibleLanguage = when (langMode) {
            LanguageMode.ARABIC_ONLY -> "Arabic"
            LanguageMode.ENGLISH_ONLY -> "English"
            LanguageMode.BOTH -> "Arabic,English"
        }

        val xml = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("\n<BibleShowData>\n")
            append("  <TimeCode>$dotNetTicks</TimeCode>\n")
            append("  <Reference />\n")
            append("  <Scripture>${xmlEscape(scripture)}</Scripture>\n")
            append("  <ImagePath />\n")
            append("  <BibleVersion></BibleVersion>\n")
            append("  <BibleCopyright></BibleCopyright>\n")
            append("  <BibleLanguage>$bibleLanguage</BibleLanguage>\n")
            append("  <BookName>${xmlEscape(bookName)}</BookName>\n")
            append("  <BookTitle>${xmlEscape(bookName)}</BookTitle>\n")
            append("  <BookAbbreviation></BookAbbreviation>\n")
            append("  <ChapterNumber>${xmlEscape(chapterNum)}</ChapterNumber>\n")
            append("  <VerseNumber>${xmlEscape(verseNum)}</VerseNumber>\n")
            append("  <ChapterVerse>${xmlEscape(chapterVerse)}</ChapterVerse>\n")
            append("  <VerseText>${xmlEscape(verseTextOnly)}</VerseText>\n")
            append("  <BackgroundPath />\n")
            append("</BibleShowData>\n")
        }

        val bytes = xml.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/xml; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    /**
     * GET /remote — phone-friendly remote control page (v1.8).
     * One-handed operation, follows the device's light/dark theme,
     * fully offline (served by the tablet, no external resources).
     */
    private fun serveRemotePage(out: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="color-scheme" content="light dark">
<title>Bible Remote</title>
<style>
  :root { color-scheme: light dark; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0; padding: 0;
    font-family: system-ui, -apple-system, sans-serif;
    background: Canvas; color: CanvasText;
    min-height: 100vh; min-height: 100dvh;
    display: flex; flex-direction: column;
  }
  @media (prefers-color-scheme: light) {
    body { background: #f1f5f9; color: #0f172a; }
    .card { background: #ffffff; border-color: #e2e8f0; }
    input { background: #f8fafc; border-color: #cbd5e1; color: #0f172a; }
    .seg button { color: #475569; }
    .seg button.active { background: #0f172a; color: #fff; }
    .hint { color: #64748b; }
  }
  @media (prefers-color-scheme: dark) {
    body { background: #0f172a; color: #f1f5f9; }
    .card { background: #1e293b; border-color: #334155; }
    input { background: #0f172a; border-color: #475569; color: #f1f5f9; }
    .seg button { color: #94a3b8; }
    .seg button.active { background: #f1f5f9; color: #0f172a; }
    .hint { color: #94a3b8; }
  }
  header {
    padding: 16px 20px 8px;
    font-size: 20px; font-weight: 700;
  }
  header .sub { font-size: 13px; font-weight: 400; opacity: 0.7; margin-top: 2px; }
  main { flex: 1; padding: 8px 16px 16px; display: flex; flex-direction: column; gap: 12px; max-width: 560px; width: 100%; margin: 0 auto; }
  .card {
    border: 1px solid; border-radius: 16px;
    padding: 16px;
  }
  label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 8px; opacity: 0.8; }
  input {
    width: 100%; font-size: 20px; padding: 14px 16px;
    border: 1px solid; border-radius: 12px;
    outline: none;
  }
  input:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59,130,246,0.25); }
  .seg { display: flex; gap: 8px; }
  .seg button {
    flex: 1; padding: 12px 8px; font-size: 15px; font-weight: 600;
    border: 1px solid; border-radius: 12px; background: transparent;
    cursor: pointer;
  }
  .seg button.active { border-color: transparent; }
  .btn {
    width: 100%; padding: 18px; font-size: 20px; font-weight: 700;
    border: none; border-radius: 16px; cursor: pointer;
    background: #16a34a; color: #fff;
    min-height: 64px;
  }
  .btn:active { transform: scale(0.98); opacity: 0.9; }
  .btn-clear {
    background: transparent; color: inherit;
    border: 2px solid; font-size: 17px; padding: 14px;
  }
  .status {
    text-align: center; font-size: 14px; min-height: 22px;
    font-weight: 600;
  }
  .status.ok { color: #16a34a; }
  .status.err { color: #dc2626; }
  .hint { font-size: 12px; margin-top: 8px; line-height: 1.5; }
  .now { font-size: 15px; text-align: center; padding: 4px 0; opacity: 0.85; }
</style>
</head>
<body>
<header>
  📖 Bible Remote
  <div class="sub">Arabic Bible NDI — live verse trigger</div>
</header>
<main>
  <div class="now" id="now">—</div>
  <div class="card">
    <label for="ref">Bible reference</label>
    <input id="ref" type="text" inputmode="text" autocomplete="off"
           placeholder="John 3:16  •  يوحنا 3:16  •  Jn 3:16"
           enterkeyhint="go">
    <div class="hint">English, Arabic, or abbreviation. Examples: <b>Rom 8:28</b>, <b>مزمور 23:1</b>, <b>1Jn 1:9</b></div>
  </div>
  <div class="card">
    <label>Target output</label>
    <div class="seg" id="targetSeg">
      <button data-t="lower">Lower Third</button>
      <button data-t="show">Full Show</button>
      <button data-t="both" class="active">Both</button>
    </div>
  </div>
  <button class="btn" id="goBtn" onclick="trigger()">▶ Show Verse</button>
  <button class="btn btn-clear" onclick="clearVerse()">Clear (off-air)</button>
  <div class="status" id="status"></div>
</main>
<script>
  let target = 'both';
  const seg = document.getElementById('targetSeg');
  seg.addEventListener('click', e => {
    const b = e.target.closest('button');
    if (!b) return;
    target = b.dataset.t;
    seg.querySelectorAll('button').forEach(x => x.classList.toggle('active', x === b));
  });
  const refInput = document.getElementById('ref');
  refInput.addEventListener('keydown', e => { if (e.key === 'Enter') trigger(); });
  // Select-all on focus so re-typing replaces (v2.0 pattern, applied here too).
  refInput.addEventListener('focus', () => refInput.select());

  function setStatus(msg, cls) {
    const el = document.getElementById('status');
    el.textContent = msg;
    el.className = 'status ' + (cls || '');
  }

  async function trigger() {
    const ref = refInput.value.trim();
    if (!ref) { setStatus('Type a reference first', 'err'); refInput.focus(); return; }
    setStatus('Sending…', '');
    try {
      const r = await fetch('/api/trigger?ref=' + encodeURIComponent(ref) + '&target=' + target);
      const j = await r.json();
      if (j.ok) {
        setStatus('✓ ' + ref + ' → ' + target, 'ok');
        updateNow(ref);
      } else {
        setStatus('✗ ' + (j.error || 'failed'), 'err');
      }
    } catch (e) {
      setStatus('✗ Network error', 'err');
    }
  }

  async function clearVerse() {
    setStatus('Clearing…', '');
    try {
      const r = await fetch('/api/clear');
      const j = await r.json();
      if (j.ok) { setStatus('✓ Cleared', 'ok'); updateNow('—'); }
      else setStatus('✗ failed', 'err');
    } catch (e) {
      setStatus('✗ Network error', 'err');
    }
  }

  function updateNow(t) { document.getElementById('now').textContent = 'Now: ' + t; }

  // Poll current verse for the "Now" line.
  async function pollNow() {
    try {
      const r = await fetch('/api/verse');
      const j = await r.json();
      if (j.isLive && (j.arabicCitation || j.englishCitation)) {
        updateNow(j.arabicCitation || j.englishCitation);
      } else {
        updateNow('—');
      }
    } catch (e) {}
  }
  pollNow();
  setInterval(pollNow, 5000);
</script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Cache-Control: no-cache\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun send404(out: OutputStream) {
        val msg = "404 Not Found"
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 404 Not Found\r\n")
        writer.print("Content-Type: text/plain\r\n")
        writer.print("Content-Length: ${msg.length}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(msg)
        writer.flush()
    }

    fun getLocalIpAddress(): String {
        customIpOverride?.let {
            if (it.isNotBlank()) return it
        }
        return NetworkHelper.getPrimaryIpAddress()
    }

    /** True when the device has a real LAN address (never loopback/localhost). */
    fun hasLanAddress(): Boolean = !getLocalIpAddress().startsWith("127.")

    fun getServerUrl(): String {
        if (!hasLanAddress()) return ""
        return "http://${getLocalIpAddress()}:$port/ndi"
    }

    fun getStreamUrl(): String {
        if (!hasLanAddress()) return ""
        return "http://${getLocalIpAddress()}:$port/ndi/stream"
    }
}
