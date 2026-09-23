package com.example.server

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import com.example.data.ArabicTextFormatter
import com.example.data.BibleRepository
import com.example.model.AnimatedBackgroundType
import com.example.model.BibleVerse
import com.example.model.BroadcastTextAlignment
import com.example.model.LowerThirdTemplate
import com.example.model.StreamBackgroundMode
import com.example.model.TemplateStyle
import com.example.model.TransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin

class NdiBroadcastServer(private val context: Context, private var port: Int = 8080) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var isLive = true

    // Pre-populate with default verse so NDI stream and overlay never open with a black screen
    @Volatile
    var currentVerse: BibleVerse? = BibleVerse(
        id = "jhn_3_16",
        bookId = "jhn",
        bookArabicName = "إنجيل يوحنا",
        bookEnglishName = "John",
        chapter = 3,
        verse = 16,
        arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ، لِكَيْ لاَ يَهْلِكَ كُلُّ مَنْ يُؤْمِنُ بِهِ، بَلْ تَكُونُ لَهُ الْحَيَاةُ الأَبَدِيَّةُ.",
        englishText = "For God so loved the world that He gave His only begotten Son, that whoever believes in Him should not perish but have everlasting life."
    )

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
            val url = "http://$ip:$port/ndi"
            onStarted(url)

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
        } catch (e: Exception) {
            isRunning = false
            onError(e.message ?: "Failed to start NDI server on port $port")
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverJob?.cancel()
        serverJob = null
        serverSocket = null

        sseClients.forEach { (writer, _) ->
            try { writer.close() } catch (e: Exception) {}
        }
        sseClients.clear()
    }

    fun updateVerse(verse: BibleVerse, live: Boolean = true) {
        currentVerse = verse
        isLive = live
        broadcastStateToClients()
    }

    fun updateTemplate(template: LowerThirdTemplate) {
        currentTemplate = template
        broadcastStateToClients()
    }

    fun setLiveState(live: Boolean) {
        isLive = live
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
                writer.print(if (isShow) showData else lowerData)
                writer.flush()
                if (writer.checkError()) {
                    deadClients.add(pair)
                }
            } catch (e: Exception) {
                deadClients.add(pair)
            }
        }
        sseClients.removeAll(deadClients.toSet())
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
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
            var headerLine: String?
            while (reader.readLine().also { headerLine = it } != null) {
                if (headerLine.isNullOrEmpty()) break
                val lower = headerLine?.lowercase() ?: ""
                if (lower.startsWith("accept:") && lower.contains("text/html")) {
                    isBrowserNavigation = true
                }
            }

            val out = socket.getOutputStream()

            when {
                cleanPath == "/ndi" || cleanPath == "/ndi/lowerthird" || cleanPath == "" || cleanPath == "/" -> {
                    serveNdiHtmlOverlay(out, isFullScreen = false)
                }
                cleanPath == "/ndi/show" || cleanPath == "/show" -> {
                    serveNdiHtmlOverlay(out, isFullScreen = true)
                }
                cleanPath == "/ndi/events" -> {
                    serveSseEvents(socket, out, fullPath.contains("show=1"))
                    return // Keep socket open for persistent SSE push
                }
                cleanPath == "/ndi/stream.png" || cleanPath == "/ndi/stream.mjpg" || cleanPath == "/ndi/stream" || cleanPath == "/stream" || cleanPath == "/video" || cleanPath == "/live" -> {
                    if (isBrowserNavigation && !fullPath.contains("raw=1")) {
                        serveMjpegPlayerHtml(out)
                    } else {
                        serveLiveStream(socket, out, fullPath)
                        return // Handled in persistent video loop
                    }
                }
                cleanPath == "/ndi/api/verse" -> {
                    serveJsonState(out, fullPath.contains("show=1"))
                }
                cleanPath == "/ndi/overlay.png" -> {
                    serveTransparentPngOverlay(out)
                }
                cleanPath == "/ndi/status" || cleanPath == "/status" -> {
                    serveStatus(out)
                }
                cleanPath == "/ndi/video_file" -> {
                    serveLocalVideo(out, fullPath)
                }
                else -> {
                    serveNdiHtmlOverlay(out)
                }
            }
        } catch (e: Exception) {
            // Connection closed or timed out
        } finally {
            try { socket.close() } catch (e: Exception) {}
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
            put("isLive", isLive && verse != null)
            put("arabicText", formattedVerseText)
            put("arabicCitation", formattedCitation)
            put("englishText", verse?.englishText ?: "")
            put("englishCitation", verse?.getFormattedEnglishCitation() ?: "")
            put("bilingual", tpl.bilingualMode)
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
            put("showCrossEmblem", tpl.showCrossEmblem)
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
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Amiri:ital,wght@0,400;0,700;1,400&family=Cairo:wght@400;600;700;800&family=Noto+Naskh+Arabic:wght@400;700&display=swap" rel="stylesheet">
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
    }

    /* Stage Container (Default: Lower Third alignment at screen bottom) */
    #stage {
      position: absolute;
      top: 0;
      left: 0;
      width: 100vw;
      height: 100vh;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      justify-content: flex-end;
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
      width: 100% !important;
      height: 100% !important;
      max-width: 100vw !important;
      display: flex !important;
      flex-direction: column !important;
      justify-content: center !important;
      align-items: center !important;
      padding: 8vh 8vw !important;
      border-radius: 0 !important;
      margin: 0 !important;
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
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: currentColor;
      box-shadow: 0 0 10px currentColor;
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
      display: inline-block;
      direction: ltr;
      font-weight: 700;
      margin-left: 6px;
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
    function applyData(data) {
      if (!data.isLive || !data.arabicText) {
        container.classList.remove('visible');
        return;
      }

      const isFull = data.isFullScreen || $forceFullScreen;

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

      // 2. Text Content
      verseText.textContent = data.arabicText;
      citationText.textContent = data.arabicCitation;

      // 3. Bilingual Mode
      if (data.bilingual && data.englishText) {
        englishSection.style.display = 'block';
        englishText.textContent = data.englishText;
        englishCitation.textContent = '(' + data.englishCitation + ')';
        englishText.style.color = data.secondaryTextColorHex || '#CBD5E1';
        englishCitation.style.color = data.secondaryReferenceColorHex || '#94A3B8';
        englishText.style.fontSize = (data.secondaryVerseFontSize || 18) + 'px';
        englishCitation.style.fontSize = (data.secondaryReferenceFontSize || 14) + 'px';
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

      // 4. Layout & Alignment (Ensures Full Show remains vertically centered in middle)
      if (isFull) {
        container.style.marginBottom = '0';
        container.style.marginLeft = '0';
        container.style.marginRight = '0';

        cardBox.style.display = 'flex';
        cardBox.style.flexDirection = 'column';
        cardBox.style.justifyContent = 'center';
        cardBox.style.alignItems = 'center';
        cardBox.style.margin = '0 auto';
        cardBox.style.textAlign = 'center';

        citationRow.style.display = 'flex';
        citationRow.style.width = '100%';
        citationRow.style.justifyContent = 'center';

        verseText.style.textAlign = 'center';
        englishSection.style.textAlign = 'center';
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
          englishSection.style.textAlign = 'right';
        }
      }

      // 5. Typography & Font Styling
      const font = data.fontFamily === 'Cairo' ? "'Cairo', sans-serif" :
                   data.fontFamily === 'Amiri' ? "'Amiri', serif" :
                   data.fontFamily === 'Noto Naskh Arabic' ? "'Noto Naskh Arabic', serif" :
                   data.fontFamily || "'Amiri', serif";
      cardBox.style.fontFamily = font;
      verseText.style.fontWeight = data.verseIsBold ? 'bold' : 'normal';
      verseText.style.fontStyle = data.verseIsItalic ? 'italic' : 'normal';
      citationRow.style.fontWeight = data.referenceIsBold ? 'bold' : 'normal';
      citationRow.style.fontStyle = data.referenceIsItalic ? 'italic' : 'normal';
      verseText.style.fontSize = (data.verseFontSize || 26) + 'px';
      citationRow.style.fontSize = (data.referenceFontSize || 18) + 'px';

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
      crossEmblem.style.backgroundColor = accent;
      crossEmblem.style.color = accent;
      crossEmblem.style.display = data.showCrossEmblem ? 'inline-block' : 'none';

      const radius = isFull ? '0' : (data.cornerRadiusDp || 16) + 'px';
      cardBox.style.borderRadius = radius;
      if (!isFull) cardBox.style.padding = '18px 26px';

      if (data.style === 'TRANSPARENT_OUTLINE') {
        cardBox.style.backgroundColor = 'transparent';
        cardBox.style.backdropFilter = 'none';
        cardBox.style.border = 'none';
        cardBox.style.boxShadow = 'none';
        verseText.style.textShadow = '0 0 5px #000, 0 0 12px #000, 2px 2px 8px #000';
        citationRow.style.textShadow = '0 0 4px #000, 0 0 8px #000';
      } else if (data.style === 'CLASSIC_BANNER') {
        cardBox.style.backgroundColor = 'rgba(' + r + ',' + g + ',' + b + ',' + opacity + ')';
        cardBox.style.borderRight = data.showAccentBorder ? '6px solid ' + accent : 'none';
        cardBox.style.borderLeft = 'none';
        cardBox.style.borderRadius = isFull ? '0' : '4px';
        cardBox.style.boxShadow = data.showDropShadow ? '0 12px 36px rgba(0,0,0,0.6)' : 'none';
      } else if (data.style === 'ROYAL_LITURGICAL') {
        cardBox.style.background = 'linear-gradient(135deg, rgba(' + r + ',' + g + ',' + b + ',' + opacity + ') 0%, rgba(20,20,35,' + opacity + ') 100%)';
        cardBox.style.border = data.showAccentBorder ? '2px solid ' + accent : 'none';
        cardBox.style.boxShadow = data.showDropShadow ? '0 10px 40px rgba(0,0,0,0.7)' : 'none';
      } else {
        cardBox.style.backgroundColor = 'rgba(' + r + ',' + g + ',' + b + ',' + opacity + ')';
        cardBox.style.backdropFilter = 'blur(16px)';
        cardBox.style.border = data.showAccentBorder ? '1.5px solid ' + accent + '77' : 'none';
        cardBox.style.boxShadow = data.showDropShadow ? '0 16px 40px rgba(0, 0, 0, 0.55)' : 'none';
      }

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
      } else if (animType === 'particles') {
        animBgLayer.classList.add('anim-particles');
        animBgLayer.style.opacity = animOpacity;
      } else if (animType === 'custom_video' && data.customVideoUrl) {
        const videoUrl = (data.customVideoUrl.startsWith('http') || data.customVideoUrl.startsWith('blob:')) 
           ? data.customVideoUrl 
           : '/ndi/video_file?path=' + encodeURIComponent(data.customVideoUrl);
        if (customVideoBg.src !== videoUrl) customVideoBg.src = videoUrl;
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
      const isShow = urlParams.get('show') === '1' || window.location.pathname.includes('/show');
      const sseUrl = '/ndi/events' + (isShow ? '?show=1' : '');
      
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
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val tpl = overrideTemplate ?: currentTemplate
        val fallbackVerse = BibleVerse(
            id = "jhn_3_16",
            bookId = "jhn",
            bookArabicName = "إنجيل يوحنا",
            bookEnglishName = "John",
            chapter = 3,
            verse = 16,
            arabicText = "لأَنَّهُ هكَذَا أَحَبَّ اللهُ الْعَالَمَ حَتَّى بَذَلَ ابْنَهُ الْوَحِيدَ، لِكَيْ لاَ يَهْلِكَ كُلُّ مَنْ يُؤْمِنُ بِهِ، بَلْ تَكُونُ لَهُ الْحَيَاةُ الأَبَدِيَّةُ.",
            englishText = "For God so loved the world that He gave His only begotten Son, that whoever believes in Him should not perish but have everlasting life."
        )
        val activeVerse = currentVerse ?: fallbackVerse

        // 100% Pure transparent alpha canvas by default
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

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
            return bitmap
        }

        val scale = width / 1920f
        val marginH = (width * (tpl.horizontalMarginPercent / 100f)).coerceAtLeast(40f * scale)
        val marginB = (height * (tpl.positionBottomPercent / 100f)).coerceAtLeast(30f * scale)
        val boxWidth = width - (2 * marginH)

        val verseText = ArabicTextFormatter.prepareForBroadcast(
            activeVerse.arabicText,
            tpl.useEasternArabicNumerals,
            tpl.useArabicPunctuation
        )
        val citation = activeVerse.getFormattedArabicCitation(tpl.useEasternArabicNumerals)

        // Setup TextPaint for multi-line wrapped text
        val verseTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = try { Color.parseColor(tpl.textColorHex) } catch (e: Exception) { Color.WHITE }
            textSize = (tpl.verseFontSize * 2f * scale).coerceAtLeast(24f)
            
            var textStyle = Typeface.NORMAL
            if (tpl.verseIsBold && tpl.verseIsItalic) textStyle = Typeface.BOLD_ITALIC
            else if (tpl.verseIsBold) textStyle = Typeface.BOLD
            else if (tpl.verseIsItalic) textStyle = Typeface.ITALIC
            
            typeface = getBestTypeface(tpl.fontFamily, textStyle)
            if (tpl.showDropShadow || tpl.isPureTransparentBackground) {
                setShadowLayer(8f * scale, 0f, 4f * scale, Color.BLACK)
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

        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(verseText, 0, verseText.length, verseTextPaint, contentWidth)
                .setAlignment(alignment)
                .setLineSpacing(6f * scale, 1.25f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(verseText, verseTextPaint, contentWidth, alignment, 1.25f, 6f * scale, true)
        }

        val citationHeight = 44f * scale
        
        // Calculate bilingual secondary text if needed
        var secondaryLayout: StaticLayout? = null
        if (tpl.bilingualMode && !activeVerse.englishText.isNullOrBlank()) {
            val citationText = " (" + activeVerse.getFormattedEnglishCitation() + ")"
            val fullSecondaryText = activeVerse.englishText + citationText
            
            val secondaryTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = try { Color.parseColor(tpl.secondaryTextColorHex) } catch (e: Exception) { Color.LTGRAY }
                textSize = (tpl.secondaryVerseFontSize * 2f * scale).coerceAtLeast(18f)
                
                var secStyle = Typeface.NORMAL
                if (tpl.secondaryVerseIsBold && tpl.secondaryVerseIsItalic) secStyle = Typeface.BOLD_ITALIC
                else if (tpl.secondaryVerseIsBold) secStyle = Typeface.BOLD
                else if (tpl.secondaryVerseIsItalic) secStyle = Typeface.ITALIC

                typeface = getBestTypeface(tpl.secondaryFontFamily, secStyle)
                if (tpl.showDropShadow || tpl.isPureTransparentBackground) {
                    setShadowLayer(4f * scale, 0f, 2f * scale, Color.BLACK)
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
            
            // For Bilingual mode: English should usually be Left aligned (NORMAL for LTR)
            // unless the user chose Center.
            val secAlignment = if (tpl.alignment == BroadcastTextAlignment.CENTER) {
                Layout.Alignment.ALIGN_CENTER
            } else {
                Layout.Alignment.ALIGN_NORMAL // Always Left for LTR English
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
        val secHeight = if (secondaryLayout != null) secondaryLayout.height + bilSpacing else 0f
        
        // Full screen check for projector/full show mode
        val boxHeight = if (tpl.isFullScreen) {
            height.toFloat() 
        } else {
            (staticLayout.height + citationHeight + secHeight + (paddingV * 2) + 16f * scale).coerceAtLeast(140f * scale)
        }

        val boxTop = if (tpl.isFullScreen) 0f else (height - marginB - boxHeight)
        val boxBottom = if (tpl.isFullScreen) height.toFloat() else (height - marginB)
        val boxLeft = if (tpl.isFullScreen) 0f else marginH
        val boxRight = if (tpl.isFullScreen) width.toFloat() else (width - marginH)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card background and Animated Motion Video Layer
        val rect = RectF(boxLeft, boxTop, boxRight, boxBottom)
        val radius = if (tpl.isFullScreen) 0f else (tpl.cornerRadiusDp * scale * 2f)

        if (!tpl.isPureTransparentBackground && tpl.style != TemplateStyle.TRANSPARENT_OUTLINE) {
            val bgAlpha = (tpl.bgOpacity * 255).toInt().coerceIn(0, 255)
            val baseBgCol = try { Color.parseColor(tpl.bgColorHex) } catch (e: Exception) { Color.DKGRAY }

            if (tpl.animatedBackground != AnimatedBackgroundType.NONE) {
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
        } else if (tpl.animatedBackground != AnimatedBackgroundType.NONE) {
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
            if (tpl.showDropShadow || tpl.isPureTransparentBackground) {
                setShadowLayer(6f * scale, 0f, 3f * scale, Color.BLACK)
            }
            isFakeBoldText = tpl.referenceIsBold
            if (tpl.referenceIsItalic) textSkewX = -0.25f
        }

        val contentTotalHeight = staticLayout.height + citationHeight + secHeight
        val startY = if (tpl.isFullScreen) {
            (height - contentTotalHeight) / 2f
        } else {
            boxTop + paddingV
        }

        val citationY = startY + (refPaint.textSize * 0.9f)
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

        // Draw Multi-line Verse Text wrapped smoothly
        val textY = citationY + (16f * scale)
        canvas.save()
        canvas.translate(boxLeft + paddingH, textY)
        staticLayout.draw(canvas)
        canvas.restore()

        // Draw Bilingual Section if active
        if (secondaryLayout != null) {
            val bilSpacing = if (tpl.showBilingualSpacing) tpl.bilingualSpacing * scale else 0f
            val secY = textY + staticLayout.height + bilSpacing
            canvas.save()
            canvas.translate(boxLeft + paddingH, secY)
            secondaryLayout.draw(canvas)
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

        return bitmap
    }

    private fun serveLocalVideo(out: OutputStream, fullPath: String) {
        try {
            val uriStr = Uri.parse(fullPath).getQueryParameter("path") ?: return send404(out)
            val uri = Uri.parse(uriStr)
            
            val inputStream = if (uriStr.startsWith("content://")) {
                context.contentResolver.openInputStream(uri)
            } else {
                FileInputStream(File(uriStr))
            } ?: return send404(out)

            val bytes = inputStream.use { it.readBytes() }
            val writer = PrintWriter(out)
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: video/mp4\r\n")
            writer.print("Content-Length: ${bytes.size}\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Connection: close\r\n\r\n")
            writer.flush()
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            send404(out)
        }
    }

    private fun getBestTypeface(family: String, textStyle: Int): Typeface {
        return try {
            when (family) {
                "Cairo" -> Typeface.create("sans-serif-condensed", textStyle)
                "Amiri" -> Typeface.create("serif", textStyle)
                "Noto Naskh Arabic" -> Typeface.create("sans-serif", textStyle)
                "System" -> Typeface.create(Typeface.DEFAULT, textStyle)
                else -> Typeface.create(family, textStyle)
            }
        } catch (e: Exception) {
            Typeface.create(Typeface.DEFAULT, textStyle)
        }
    }

    private fun serveTransparentPngOverlay(out: OutputStream) {
        val bitmap = renderCurrentFrame(1920, 1080, isForStream = false)
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
      <img id="streamImg" src="/ndi/stream.mjpg?raw=1" alt="Bible Live Broadcast Stream" onerror="setTimeout(() => { this.src = '/ndi/stream.mjpg?raw=1&t=' + Date.now(); }, 1500);" />
    </div>

    <div class="instructions">
      <b>إرشادات التشغيل في برامج البث (OBS Studio / vMix):</b>
      <br>1. <b>للحصول على خلفية شفافة كاملة (Alpha Transparency):</b> يُنصح بإضافة <b>Browser Source</b> ووضع الرابط التالي:
      <span class="code-box">http://$ip:$port/ndi</span>
      <br>2. <b>للبث كفيديو (Media Source / Video Stream):</b> أضف مصدر فيديو وضع الرابط:
      <span class="code-box">http://$ip:$port/ndi/stream.mjpg?raw=1</span>
    </div>

    <div class="btn-row">
      <a class="btn btn-primary" href="/ndi" target="_blank">فتح طبقة البث الشفافة (Overlay)</a>
      <a class="btn btn-secondary" href="/ndi/stream.mjpg?raw=1" target="_blank">عرض دفق الفيديو المباشر (Raw MJPEG)</a>
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
            val bitmap = renderCurrentFrame(1920, 1080, isForStream = useJpeg)
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
        val msg = "OK - Bible NDI Server Online\nIP: $ip\nPort: $port\nStream: http://$ip:$port/ndi/stream\nOverlay: http://$ip:$port/ndi\n"
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

    fun getServerUrl(): String {
        return "http://${getLocalIpAddress()}:$port/ndi"
    }

    fun getStreamUrl(): String {
        return "http://${getLocalIpAddress()}:$port/ndi/stream"
    }
}
