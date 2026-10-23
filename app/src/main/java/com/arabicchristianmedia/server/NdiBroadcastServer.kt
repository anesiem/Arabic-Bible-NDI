package com.arabicchristianmedia.server

import android.content.Context
import android.content.SharedPreferences
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
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import com.arabicchristianmedia.data.ArabicTextFormatter
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.model.AnimatedBackgroundType
import com.arabicchristianmedia.model.BundledFonts
import com.arabicchristianmedia.model.BibleVerse
import com.arabicchristianmedia.model.BroadcastTextAlignment
import com.arabicchristianmedia.model.LanguageMode
import com.arabicchristianmedia.model.LowerThirdTemplate
import com.arabicchristianmedia.model.StreamBackgroundMode
import com.arabicchristianmedia.model.TemplateStyle
import com.arabicchristianmedia.model.TransitionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
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

/**
 * v1.9 auto-fit math (pure, unit-tested): how much to shrink text so a content
 * block of [contentH] fits in [maxContentH]. Returns 1f when no shrink is
 * needed; never below [minRatio] (the user's minimum-size floor).
 */
internal fun computeFitRatio(contentH: Float, maxContentH: Float, minRatio: Float): Float {
    if (contentH <= maxContentH || maxContentH <= 0f) return 1f
    return (maxContentH / contentH).coerceIn(minRatio, 1f)
}

class NdiBroadcastServer(private val context: Context, private var port: Int = 8080) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var ssePingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    var onSetOnAir: ((Boolean) -> Unit)? = null
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

    // v1.9.1: /debug instrumentation — per-action timings (diagnostics only;
    // volatile counters, read by /api/debug).
    private class ActionStat {
        @Volatile var count: Int = 0
        @Volatile var lastMs: Double = 0.0
        @Volatile var maxMs: Double = 0.0
        @Volatile var totalMs: Double = 0.0
        fun record(ms: Double) {
            count++; lastMs = ms; totalMs += ms
            if (ms > maxMs) maxMs = ms
        }
        fun avgMs(): Double = if (count == 0) 0.0 else totalMs / count
    }
    private val statStateBuild = ActionStat()
    private val statBroadcast = ActionStat()
    private val statVerseApi = ActionStat()
    private val statTrigger = ActionStat()
    @Volatile private var serverStartTimeMs: Long = 0L

    // v1.9.1: last self-report received from each overlay page (raw JSON,
    // validated at ingestion), shown by /api/debug as overlayClients.
    @Volatile private var clientStatLower: String? = null
    @Volatile private var clientStatLowerAtMs: Long = 0L
    @Volatile private var clientStatFull: String? = null
    @Volatile private var clientStatFullAtMs: Long = 0L
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

    // v1.9: persisted verse/live state — the only render state not already persisted
    // elsewhere (style IDs live in TemplateRepository, feed specs in
    // bible_ndi_source_specs). Lets NdiService fully resume the NDI feeds
    // (same verse, same ON AIR/standby) after an OS process kill.
    private val verseStatePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("bible_ndi_render_state", Context.MODE_PRIVATE)
    }

    private fun persistVerseState() {
        try {
            verseStatePrefs.edit()
                .putString("verse_book_id", currentVerse?.bookId)
                .putInt("verse_chapter", currentVerse?.chapter ?: -1)
                .putInt("verse_number", currentVerse?.verse ?: -1)
                .putBoolean("is_live", isLive)
                .apply()
        } catch (e: Exception) {
            // Best-effort: a failed write just means auto-resume falls back to standby.
        }
    }

    /**
     * Restores the last persisted verse/styles/live state for OS-kill auto-resume.
     * Call off the main thread (reads the Bible DB); the caller must have run
     * `BibleRepository.initialize(context)` first. Style IDs come from the
     * already-persisted TemplateRepository highlights. Returns true if a verse
     * was restored (live or standby); false when there is nothing to show.
     * [verseLookup] is injectable for tests; production passes the real repository.
     */
    fun restorePersistedRenderState(
        templates: TemplateRepository,
        verseLookup: (bookId: String, chapter: Int) -> List<BibleVerse> =
            { bookId, chapter -> BibleRepository.getVerses(bookId, chapter) }
    ): Boolean {
        return try {
            val all = templates.getAllTemplates()
            val lowerTpl = all.firstOrNull { it.id == templates.getActiveTemplateId() && !it.isFullScreen }
                ?: all.firstOrNull { !it.isFullScreen }
            val showTpl = all.firstOrNull { it.id == templates.getActiveShowTemplateId() && it.isFullScreen }
                ?: all.firstOrNull { it.isFullScreen }
            if (lowerTpl != null) updateTemplate(lowerTpl)
            if (showTpl != null) updateShowTemplate(showTpl)

            val bookId = verseStatePrefs.getString("verse_book_id", null)
            val chapter = verseStatePrefs.getInt("verse_chapter", -1)
            val verseNum = verseStatePrefs.getInt("verse_number", -1)
            val live = verseStatePrefs.getBoolean("is_live", false)
            val verse = if (bookId != null && chapter >= 0 && verseNum >= 0) {
                try {
                    verseLookup(bookId, chapter).firstOrNull { it.verse == verseNum }
                } catch (e: Exception) { null }
            } else null
            if (verse != null) {
                updateVerse(verse, live = live)
                true
            } else {
                setLiveState(false)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    var customIpOverride: String? = null

    // Connected SSE clients for instantaneous broadcast push (PrintWriter to isShow)
    private val sseClients = CopyOnWriteArrayList<Pair<PrintWriter, Boolean>>()

    // v1.9.1: SSE connection journal + session counters (diagnostics for
    // the overlay-drop investigation). A 3-hour service can cycle many
    // connections, so the counters are cumulative for the whole server
    // session and the journal keeps the last 200 events. Read by
    // /api/debug and the Setup tab's server-health readout. Cost: a few
    // short strings per connect/disconnect only — nothing per message.
    private val sseJournal = java.util.Collections.synchronizedList(mutableListOf<String>())
    @Volatile var sseConnectTotal: Int = 0; private set
    @Volatile var sseDropTotal: Int = 0; private set
    @Volatile var sseLastDropAtMs: Long = 0L; private set
    @Volatile var sseLastDropReason: String = ""; private set
    private val sseDropReasons = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val sseConnInfo = java.util.concurrent.ConcurrentHashMap<PrintWriter, LongArray>()
    val sseConnectedNow: Int get() = sseClients.size

    private fun journalSse(line: String) {
        synchronized(sseJournal) {
            sseJournal.add(line)
            while (sseJournal.size > 200) sseJournal.removeAt(0)
        }
    }

    /** Records a dropped overlay connection (no-op if it wasn't tracked). */
    private fun noteSseDrop(writer: PrintWriter, isShow: Boolean, via: String, reason: String) {
        val info = sseConnInfo.remove(writer) ?: return
        val now = System.currentTimeMillis()
        val lived = now - info[0]
        sseDropTotal++
        sseLastDropAtMs = now
        sseLastDropReason = reason
        sseDropReasons.compute(reason) { _, v -> (v ?: 0) + 1 }
        journalSse("$now DROP ${if (isShow) "full" else "lower"} lived=${lived}ms msgs=${info[1]} via=$via reason=$reason")
    }

    fun sseJournalSnapshot(): List<String> = synchronized(sseJournal) { ArrayList(sseJournal) }
    fun sseDropReasonCounts(): Map<String, Int> = HashMap(sseDropReasons)

    fun start(onStarted: (String) -> Unit = {}, onError: (String) -> Unit = {}) {
        if (isRunning) return

        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(port))
            }
            isRunning = true
            serverStartTimeMs = System.currentTimeMillis()
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
                    val deadWhy = mutableMapOf<PrintWriter, String>()
                    for ((writer, isShow) in sseClients) {
                        try {
                            synchronized(writer) {
                                writer.print("event: ping\ndata: 1\n\n")
                                writer.flush()
                            }
                            if (writer.checkError()) { dead.add(writer to isShow); deadWhy[writer] = "write-error" }
                        } catch (e: Exception) {
                            dead.add(writer to isShow); deadWhy[writer] = "exception:${e.javaClass.simpleName}"
                        }
                    }
                    if (dead.isNotEmpty()) {
                        val deadWriters = dead.map { it.first }.toSet()
                        sseClients.removeAll { deadWriters.contains(it.first) }
                        dead.forEach { (writer, isShow) ->
                            noteSseDrop(writer, isShow, "ping", deadWhy[writer] ?: "write-error")
                            try { writer.close() } catch (e: Exception) {}
                        }
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
        sseConnInfo.clear()
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

    /**
     * v2.0: NDI verse-change transitions. [updateVerse] snapshots the verse
     * being replaced; while a transition is active, drawFrameInto crossfades
     * the old frame into the new one over the style's transitionDurationMs
     * (FADE and both SLIDE_* use a crossfade on NDI; CUT = instant).
     * The motion pump stays on during the window so static styles still
     * animate (see [isTransitionActiveFor]).
     */
    @Volatile private var transitionPrevVerse: BibleVerse? = null
    @Volatile private var transitionStartMs: Long = 0L

    /** True while a verse-change crossfade is still animating for this feed. */
    fun isTransitionActiveFor(isFull: Boolean): Boolean {
        if (transitionPrevVerse == null) return false
        if (!isLive) return false
        val tpl = if (isFull) currentShowTemplate else currentTemplate
        val dur = tpl.transitionDurationMs
        if (tpl.transition == TransitionType.CUT || dur <= 0) return false
        val elapsed = SystemClock.elapsedRealtime() - transitionStartMs
        if (elapsed >= dur) {
            // Finished: clear state and refresh the static cache, so the
            // sender's heartbeat/static sends serve the NEW verse instead
            // of the pre-change frame still sitting in the cache.
            transitionPrevVerse = null
            invalidateBitmapCache()
            return false
        }
        return true
    }

    fun transitionProgressFor(isFull: Boolean): Float {
        if (!isTransitionActiveFor(isFull)) return 1f
        val tpl = if (isFull) currentShowTemplate else currentTemplate
        val dur = tpl.transitionDurationMs.coerceAtLeast(1)
        return ((SystemClock.elapsedRealtime() - transitionStartMs).toFloat() / dur).coerceIn(0f, 1f)
    }

    fun invalidateBitmapCache() {
        isCacheDirtyLower = true
        isCacheDirtyShow = true
        _frameVersion.incrementAndGet()
    }

    fun updateVerse(verse: BibleVerse, live: Boolean = true) {
        val prev = currentVerse
        currentVerse = verse
        isLive = live
        // v2.0: snapshot the replaced verse so the next frames can crossfade
        // (only on a real verse change, and only when going live with content).
        if (prev != null && prev.id != verse.id && isLive && live) {
            transitionPrevVerse = prev
            transitionStartMs = SystemClock.elapsedRealtime()
        }
        invalidateBitmapCache()
        broadcastStateToClients()
        persistVerseState()
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
     * v1.8: Kick the video renderer for a feed (call when motion is toggled ON).
     * Ensures the video starts without needing an OFF/ON cycle.
     */
    fun kickVideoRenderer(isFull: Boolean) {
        try {
            val tpl = if (isFull) currentShowTemplate else currentTemplate
            if (tpl.animatedBackground == AnimatedBackgroundType.CUSTOM_VIDEO &&
                tpl.customVideoId.isNotEmpty()) {
                val file = videoStore.fileFor(tpl.customVideoId)
                if (file != null) {
                    val renderer = getVideoRenderer(isFull)
                    renderer.setSource(file)
                    renderer.setMuted(tpl.customVideoMuted)
                    renderer.setSpeed(tpl.motionSpeed)
                    renderer.requestFrame()
                }
            }
        } catch (e: Exception) {
            // Non-fatal.
        }
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
        persistVerseState()
    }

    private fun broadcastStateToClients() {
        scope.launch(Dispatchers.IO) {
            val broadcastT0 = System.nanoTime()
            val lowerPayload = buildJsonState(false)
            val showPayload = buildJsonState(true)

            val lowerData = "data: $lowerPayload\n\n"
            val showData = "data: $showPayload\n\n"

            val deadClients = mutableListOf<Pair<PrintWriter, Boolean>>()
            val deadWhy = mutableMapOf<PrintWriter, String>()
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
                        deadClients.add(pair); deadWhy[writer] = "write-error"
                    } else {
                        sseConnInfo[writer]?.let { it[1]++ }
                    }
                } catch (e: Exception) {
                    deadClients.add(pair); deadWhy[writer] = "exception:${e.javaClass.simpleName}"
                }
            }
            if (deadClients.isNotEmpty()) {
                sseClients.removeAll(deadClients.toSet())
                deadClients.forEach { (writer, isShow) ->
                    noteSseDrop(writer, isShow, "broadcast", deadWhy[writer] ?: "write-error")
                    try { writer.close() } catch (e: Exception) {}
                }
            }
            statBroadcast.record((System.nanoTime() - broadcastT0) / 1_000_000.0)
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
                    serveJsonState(out, fullPath.contains("show=1"), fullPath.contains("light=1"))
                }
                cleanPath == "/api/trigger" -> {
                    serveApiTrigger(out, fullPath)
                }
                cleanPath == "/api/clear" -> {
                    serveApiClear(out)
                }
                cleanPath == "/api/books" -> {
                    serveApiBooks(out)
                }
                cleanPath == "/api/chapter" -> {
                    serveApiChapter(out, fullPath)
                }
                cleanPath == "/api/onair" -> {
                    serveApiOnAir(out, fullPath)
                }
                cleanPath == "/api/videos" -> {
                    serveApiVideos(out)
                }
                cleanPath == "/lower/snapshot.png" -> {
                    // v1.9.1: canonical per-feed snapshot URLs (the old bare
                    // /snapshot.png was never used in production — removed
                    // rather than kept as an alias, per Ashraf).
                    serveTransparentPngOverlay(out, isFull = false)
                }
                cleanPath == "/full/snapshot.png" -> {
                    serveTransparentPngOverlay(out, isFull = true)
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
                cleanPath == "/api/debug" -> {
                    serveApiDebug(out)
                }
                cleanPath == "/api/clientstat" -> {
                    serveApiClientStat(out, fullPath)
                }
                cleanPath == "/debug" -> {
                    serveDebugPage(out)
                }
                cleanPath == "/lower/bibleshow.xml" -> {
                    // v1.9.1: canonical per-feed XML URLs (the old bare
                    // /bibleshow.xml alias is removed — vMix inputs point
                    // at the per-feed URLs, per Ashraf).
                    serveBibleShowXml(out, currentTemplate)
                }
                cleanPath == "/full/bibleshow.xml" -> {
                    serveBibleShowXml(out, currentShowTemplate)
                }
                cleanPath == "/lower/bibleshow.json" -> {
                    serveBibleShowJson(out, currentTemplate)
                }
                cleanPath == "/full/bibleshow.json" -> {
                    serveBibleShowJson(out, currentShowTemplate)
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

        // Send initial state immediately. The retry field asks the browser
        // to reconnect 1s after a drop instead of its ~3s default.
        val initialData = "retry: 1000\ndata: ${buildJsonState(isShow)}\n\n"
        writer.print(initialData)
        writer.flush()

        sseClients.add(Pair(writer, isShow))
        sseConnInfo[writer] = longArrayOf(System.currentTimeMillis(), 1)
        sseConnectTotal++
        journalSse("${System.currentTimeMillis()} CONNECT ${if (isShow) "full" else "lower"} ${socket.remoteSocketAddress}")
    }

    /**
     * v1.9.1 (light remote feed): the small state the /remote page's
     * live mirror actually needs — citation, position, ON AIR, version.
     * Same citation formatting as the full state (Lower template), a
     * fraction of the payload for what can be several polling remotes.
     */
    private fun buildLightJsonState(): String {
        val tpl = currentTemplate
        val verse = currentVerse
        val root = JSONObject().apply {
            put("stateVersion", frameVersion)
            put("isLive", isLive && verse != null)
            put("arabicCitation", if (verse != null) verse.getFormattedArabicCitation(tpl.useEasternArabicNumerals) else "")
            put("englishCitation", verse?.getFormattedEnglishCitation() ?: "")
            put("bookId", verse?.bookId ?: "")
            put("chapterNum", verse?.chapter ?: 0)
            put("verseNum", verse?.verse ?: 0)
        }
        return root.toString()
    }

    private fun serveJsonState(out: OutputStream, isShow: Boolean = false, light: Boolean = false) {
        val apiT0 = System.nanoTime()
        val json = if (light) buildLightJsonState() else buildJsonState(isShow)
        statVerseApi.record((System.nanoTime() - apiT0) / 1_000_000.0)
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
        val buildT0 = System.nanoTime()
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
            put("serverTimeMs", System.currentTimeMillis())
            put("isLive", isLive && verse != null)
            put("arabicText", formattedVerseText)
            put("arabicCitation", formattedCitation)
            put("englishText", verse?.englishText ?: "")
            put("englishCitation", verse?.getFormattedEnglishCitation() ?: "")
            put("bookId", verse?.bookId ?: "")
            put("chapterNum", verse?.chapter ?: 0)
            put("verseNum", verse?.verse ?: 0)
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
            put("videoLoopMode", tpl.videoLoopMode.id)
            put("cardPaddingTopDp", tpl.cardPaddingTopDp.toDouble())
            put("cardPaddingBottomDp", tpl.cardPaddingBottomDp.toDouble())
            put("cardPaddingLeftDp", tpl.cardPaddingLeftDp.toDouble())
            put("cardPaddingRightDp", tpl.cardPaddingRightDp.toDouble())
            put("autoFitText", tpl.autoFitText)
            put("autoFitMaxVerseSp", tpl.autoFitMaxVerseSp)
            put("boxHeightPercent", tpl.boxHeightPercent)
        }
        val built = root.toString()
        statStateBuild.record((System.nanoTime() - buildT0) / 1_000_000.0)
        return built
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
      /* v1.8: Center the 16:9 stage. */
      display: flex;
      align-items: center;
      justify-content: center;
    }

    /* Stage Container (Default: Lower Third alignment at screen bottom) */
    /* v1.8: True 16:9 aspect ratio. Width is min(100vw, 177.78vh) to fit
       any viewport without distortion. Content scales via JS stageScale. */
    #stage {
      position: relative;
      aspect-ratio: 16 / 9;
      width: min(100vw, 177.78vh);
      height: auto;
      max-height: 100vh;
      flex-shrink: 0;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      justify-content: flex-end;
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
      /* v1.9: fill the 16:9 stage, not the viewport (was 100vw/100vh). */
      width: 100% !important;
      height: 100% !important;
      margin: 0 !important;
      display: flex !important;
      flex-direction: column !important;
      align-items: center !important;
      justify-content: center !important;
      /* v1.9.1: no transform lock here — a former "transform: none
         !important" in this rule silently killed every slide/zoom/bounce
         entry preset in Full mode (Lower has no such lock, so the two
         overlays disagreed). The resting transform comes from .visible /
         the anim presets, exactly as in Lower mode. */
    }

    /* Animation Presets */
    #lowerthird-container.anim-fade { transform: translateY(0) !important; }
    #lowerthird-container.anim-fade:not(.visible) { opacity: 0; }
    #lowerthird-container.anim-slide-up { transform: translateY(60px); }
    #lowerthird-container.anim-slide-up.visible { transform: translateY(0); }
    #lowerthird-container.anim-slide-down { transform: translateY(-60px); }
    #lowerthird-container.anim-slide-down.visible { transform: translateY(0); }
    #lowerthird-container.anim-slide-right { transform: translateX(80px); }
    #lowerthird-container.anim-slide-right.visible { transform: translateX(0); }
    #lowerthird-container.anim-slide-left { transform: translateX(-80px); }
    #lowerthird-container.anim-slide-left.visible { transform: translateX(0); }
    #lowerthird-container.anim-zoom { transform: scale(0.92); }
    #lowerthird-container.anim-zoom.visible { transform: scale(1.0); }
    #lowerthird-container.anim-bounce { transform: scale(0.88); transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1) !important; }
    #lowerthird-container.anim-bounce.visible { transform: scale(1.0); }
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
      /* v1.9: fill the 16:9 stage, not the viewport (was 100vw/100vh). */
      width: 100% !important;
      height: 100% !important;
      max-width: 100% !important;
      display: flex !important;
      flex-direction: column !important;
      justify-content: center !important;
      align-items: center !important;
      /* v1.9: padding is set inline from the style's card padding (was 6vh 6vw). */
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
                  linear-gradient(135deg, rgba(217, 119, 6, 1) 0%, rgba(251, 191, 36, 1) 50%, rgba(217, 119, 6, 1) 100%);
      background-size: 400% 400%;
      animation: smoothFlow 15s linear infinite;
    }
    @keyframes smoothFlow {
      0% { background-position: 0% 50%; }
      100% { background-position: 100% 50%; }
    }

    .anim-blue-waves {
      background: linear-gradient(120deg, rgba(30, 58, 138, 1) 0%, rgba(14, 165, 233, 1) 50%, rgba(30, 58, 138, 1) 100%);
      background-size: 300% 300%;
      animation: smoothFlow 20s linear infinite;
    }

    .anim-candle-glow {
      background: radial-gradient(circle at 50% 50%, rgba(245, 158, 11, 1) 0%, rgba(120, 53, 15, 1) 70%);
      animation: candleBreath 5s ease-in-out infinite alternate;
    }
    @keyframes candleBreath {
      0% { opacity: 0.7; transform: scale(1); }
      100% { opacity: 1; transform: scale(1.1); }
    }

    .anim-purple-silk {
      background: linear-gradient(45deg, rgba(88, 28, 135, 1) 0%, rgba(192, 132, 252, 1) 50%, rgba(88, 28, 135, 1) 100%);
      background-size: 400% 400%;
      animation: smoothFlow 18s linear infinite;
    }

    .anim-emerald-waves {
      background: linear-gradient(45deg, rgba(6, 78, 59, 1) 0%, rgba(52, 211, 153, 1) 50%, rgba(6, 78, 59, 1) 100%);
      background-size: 400% 400%;
      animation: smoothFlow 20s linear infinite;
    }

    .anim-rose-glow {
      background: radial-gradient(circle at 50% 50%, rgba(251, 113, 133, 1) 0%, rgba(136, 19, 55, 1) 100%);
      animation: candleBreath 8s ease-in-out infinite alternate;
    }

    .anim-gold-particles {
      background: linear-gradient(135deg, rgba(120, 53, 15, 1) 0%, rgba(252, 211, 77, 1) 50%, rgba(120, 53, 15, 1) 100%);
      background-size: 300% 300%;
      animation: smoothFlow 15s linear infinite;
    }

    .anim-particles {
      background: linear-gradient(135deg, rgba(15, 23, 42, 1) 0%, rgba(56, 189, 248, 1) 50%, rgba(15, 23, 42, 1) 100%);
      background-size: 300% 300%;
      animation: smoothFlow 15s linear infinite;
    }

    /* v1.9.1: animation utilities for INLINE animated gradients (the
       gradient itself is written per-apply from the payload — see
       section 7 — these only supply the motion). */
    .bg-flow { animation: smoothFlow 15s linear infinite; }
    .bg-breath { animation: candleBreath 5s ease-in-out infinite alternate; }
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
      font-size: 1em;
      line-height: 1;
    }

    .verse-text {
      line-height: 1.3;
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
      line-height: 1.21;
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
        <!-- v1.9.1 parity: the particle canvas is a DIRECT child of the card,
             above its background and below the content (NDI's paint order).
             It used to live inside the animated layer, which Lower mode
             zeroes out — the stars never showed on HTTP Lower at all. -->
        <canvas id="particle-canvas" style="display:none; position:absolute; inset:0; width:100%; height:100%; pointer-events:none;"></canvas>

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
    // v1.8: stageScale for font sizing — stage is 16:9, scale fonts to match NDI proportions.
    // NDI renders at verseFontSize * 2 at 1920px wide; scale proportionally.
    function getStageScale() {
      const w = stage.clientWidth || 1920;
      return w / 1920;
    }
    const container = document.getElementById('lowerthird-container');
    const cardBox = document.getElementById('card-box');
    const animBgLayer = document.getElementById('animated-bg-layer');
    const customVideoBg = document.getElementById('custom-video-bg');
    const particleCanvas = document.getElementById('particle-canvas');
    const citationRow = document.getElementById('citation-row');
    const citationText = document.getElementById('citation-text');
    const verseText = document.getElementById('verse-text');
    const crossEmblem = document.getElementById('cross-emblem');
    const englishSection = document.getElementById('english-section');
    const englishText = document.getElementById('english-text');
    const englishCitation = document.getElementById('english-citation');

    // v2.0: Real particle field on HTTP (mirrors NDI drawParticleField).
    // Previously HTTP only pulsed a CSS glow; now draws actual particles.
    let particleAnimId = null;
    let particleColor = '#7DD3FC';
    function startParticles(colorHex, opacity) {
      stopParticles();
      particleColor = colorHex;
      const canvas = particleCanvas;
      const ctx = canvas.getContext('2d');
      const rect = animBgLayer.getBoundingClientRect();
      canvas.width = Math.max(1, Math.floor(rect.width));
      canvas.height = Math.max(1, Math.floor(rect.height));
      canvas.style.display = 'block';
      const count = Math.min(120, Math.max(30, Math.floor((canvas.width * canvas.height) / 15000)));
      const particles = [];
      for (let i = 0; i < count; i++) {
        particles.push({
          x: Math.random() * canvas.width,
          y: Math.random() * canvas.height,
          vx: (Math.random() - 0.5) * 0.4,
          vy: -0.3 - Math.random() * 0.5,
          r: 1 + Math.random() * 2.5,
          tw: Math.random() * Math.PI * 2,
          twSpeed: 0.02 + Math.random() * 0.05
        });
      }
      const baseAlpha = Math.floor(opacity * 255);
      function frame() {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        for (const p of particles) {
          p.x += p.vx; p.y += p.vy; p.tw += p.twSpeed;
          if (p.y < -5) { p.y = canvas.height + 5; p.x = Math.random() * canvas.width; }
          if (p.x < -5) p.x = canvas.width + 5;
          if (p.x > canvas.width + 5) p.x = -5;
          const twinkle = (Math.sin(p.tw) + 1) / 2;
          const alpha = Math.floor(baseAlpha * (0.25 + 0.75 * twinkle));
          ctx.beginPath();
          ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
          ctx.fillStyle = particleColor + alpha.toString(16).padStart(2, '0');
          ctx.fill();
        }
        particleAnimId = requestAnimationFrame(frame);
      }
      frame();
    }
    function stopParticles() {
      if (particleAnimId) { cancelAnimationFrame(particleAnimId); particleAnimId = null; }
      if (particleCanvas) particleCanvas.style.display = 'none';
    }

    /**
     * Main Renderer: Applies state data to the DOM elements
     */
    // Last applied SSE state version (-1 = none yet). See applyData guard.
    let lastStateVersion = -1;
    let lastVerseText = null;
    // v1.9.1: sequence guard for the card swap timer — a newer apply
    // supersedes a pending swap, whose timer must then do nothing.
    let swapSeq = 0;
    // v1.9.1: overlay stability state (fast path, font gate, particles).
    let lastStyleKey = null;
    let revealToken = 0;
    let lastFitFn = null;
    let activeParticleKey = null;
    let cardGradOn = false; // v1.9.1: an inline animated gradient currently sits on the card (Lower placement)
    // v1.9.1: overlay self-diagnostics \u2014 the page times its own receive
    // -> apply -> fit path and reports it to /api/clientstat every 10s; the
    // /debug page shows it beside the server timings (deep HTTP analysis).
    const diagPage = (location.pathname.indexOf('full') >= 0 || location.search.indexOf('show=1') >= 0) ? 'full' : 'lower';
    const diag = { msgs: 0, applies: 0, opens: 0, reconnects: 0, lastVersion: 0, lastApplyMs: 0, maxApplyMs: 0, lastFitMs: 0, events: [] };
    let applyT0 = 0;
    function diagEvent(kind, info) {
      diag.events.push({ t: Math.round(performance.now()), kind: kind, info: info || '' });
      if (diag.events.length > 40) diag.events.shift();
    }
    function sendDiag() {
      try {
        const payload = encodeURIComponent(JSON.stringify({
          page: diagPage, ua: navigator.userAgent, version: diag.lastVersion,
          msgs: diag.msgs, applies: diag.applies, reconnects: diag.reconnects,
          lastApplyMs: Math.round(diag.lastApplyMs * 10) / 10,
          maxApplyMs: Math.round(diag.maxApplyMs * 10) / 10,
          lastFitMs: Math.round(diag.lastFitMs * 10) / 10,
          events: diag.events
        }));
        fetch('/api/clientstat?page=' + diagPage + '&data=' + payload, { keepalive: true }).catch(function() {});
      } catch (e) { /* diagnostics must never break the overlay */ }
    }
    let lastSseSeen = Date.now();
    let sseWatchdog = null;

    // v1.9.1 (#11): the font faces a state depends on — shared by the apply
    // path (finishApply, inside applyData) and the page-load warm-up below.
    // Pure: payload in, CSS font shorthands out.
    function facesForData(d) {
      const faces = [];
      const prim = String(d.fontFamily || 'Amiri').replace(/['"\\]/g, '');
      if (prim && prim !== 'System') {
        faces.push('400 32px "' + prim + '"');
        if (d.verseIsBold || d.referenceIsBold) faces.push('700 32px "' + prim + '"');
      }
      const lang = d.languageMode || (d.bilingual ? 'BOTH' : 'ARABIC_ONLY');
      const secFam = d.secondaryFontFamily;
      if (lang !== 'ARABIC_ONLY' && d.englishText && secFam && secFam !== 'System' && secFam !== 'system-ui') {
        faces.push('400 32px "' + secFam + '"');
        if (d.secondaryVerseIsBold || d.secondaryReferenceIsBold) faces.push('700 32px "' + secFam + '"');
      }
      return faces;
    }

    function applyData(data) {
      // State-version guard: drop stale/out-of-order deliveries and let a fresh
      // reconnect re-sync (the server bumps stateVersion on every change).
      if (data.stateVersion !== undefined && data.stateVersion !== null) {
        if (data.stateVersion <= lastStateVersion) return;
        lastStateVersion = data.stateVersion;
      }
      diag.lastVersion = data.stateVersion || diag.lastVersion;
      applyT0 = performance.now();
      if (!data.isLive || !data.arabicText) {
        container.classList.remove('visible');
        // v1.9.1: cancel any pending font-gated reveal and force the next
        // live state through the full apply path.
        revealToken++;
        swapSeq++;
        lastStyleKey = null;
        return;
      }

      const wasVisible = container.classList.contains('visible');
      // v1.9.1: style key = the whole payload minus the volatile text fields
      // (verse/citation text and the state version). Identical key means the
      // style did not change — only the words did.
      const styleKey = JSON.stringify(data, function(k, v) {
        return (k === 'arabicText' || k === 'englishText' || k === 'arabicCitation' ||
                k === 'englishCitation' || k === 'stateVersion' || k === 'bookId' ||
                k === 'chapterNum' || k === 'verseNum' || k === 'serverTimeMs') ? undefined : v;
      });

      const isFull = data.isFullScreen || $forceFullScreen;

      // v1.9: per-side card padding (design dp at 1920, same basis as the NDI
      // renderer) — replaces the old hardcoded paddings.
      function padPx(dp) { return (dp * getStageScale()) + 'px'; }
      const padT = padPx(data.cardPaddingTopDp ?? 32);
      const padB = padPx(data.cardPaddingBottomDp ?? 32);
      const padL = padPx(data.cardPaddingLeftDp ?? 36);
      const padR = padPx(data.cardPaddingRightDp ?? 36);

      // v1.8: viewport-relative font sizing — the style's font sizes are
      // designed for a 1920-wide canvas (like NDI); scale to the actual
      // viewport so HTTP matches preview/NDI proportions at any size.
      // v1.8: Match NDI font size proportionally. NDI renders at verseFontSize * 2
      // at 1920px wide; scale by actual stage width to maintain proportions at any size.
      function scaledPx(base) { return (base * 2 * getStageScale()) + 'px'; }

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
      const transType = data.transition || 'SLIDE_UP';
      const transDur = data.transitionDurationMs !== undefined ? data.transitionDurationMs : 350;
      const verseChanged = lastVerseText !== null && lastVerseText !== data.arabicText;
      lastVerseText = data.arabicText;

      function applyVerseTextContent() {
        if (showArabic) {
          verseText.style.display = '';
          citationText.style.display = '';
          verseText.textContent = data.arabicText;
          citationText.textContent = data.arabicCitation;
        } else {
          verseText.style.display = 'none';
          citationText.style.display = 'none';
        }
        if (showEnglishSection) {
          englishSection.style.display = 'block';
          englishText.textContent = data.englishText;
          englishCitation.textContent = '(' + data.englishCitation + ')';
        } else {
          englishSection.style.display = 'none';
        }
      }

      if (wasVisible && verseChanged && transType !== 'CUT' && transDur > 0) {
        const halfDur = Math.floor(transDur / 2);
        const mySwap = ++swapSeq;
        cardBox.style.transition = 'opacity ' + halfDur + 'ms ease-in-out, transform ' + halfDur + 'ms ease-in-out';
        cardBox.style.opacity = '0';
        if (transType === 'SLIDE_UP') cardBox.style.transform = 'translateY(15px)';
        else if (transType === 'SLIDE_DOWN') cardBox.style.transform = 'translateY(-15px)';
        else if (transType === 'SLIDE_RIGHT') cardBox.style.transform = 'translateX(20px)';
        else if (transType === 'SLIDE_LEFT') cardBox.style.transform = 'translateX(-20px)';
        else if (transType === 'SCALE_ZOOM') cardBox.style.transform = 'scale(0.95)';
        else if (transType === 'BOUNCE') cardBox.style.transform = 'scale(0.92)';

        setTimeout(function() {
          if (mySwap !== swapSeq) return; // superseded by a newer apply
          applyVerseTextContent();
          cardBox.style.opacity = '1';
          cardBox.style.transform = 'none';
          // The fit in finishApply measured the OLD text (the swap is
          // timer-deferred); re-fit now that the new text is in place.
          doFit();
          setTimeout(function() { if (mySwap === swapSeq) cardBox.style.transition = ''; }, halfDur);
        }, halfDur);
      } else {
        // Cancel any pending swap and clear its mid-animation card state,
        // so the card can never be left faded/shifted by a stale timer.
        swapSeq++;
        cardBox.style.transition = '';
        cardBox.style.opacity = '';
        cardBox.style.transform = '';
        applyVerseTextContent();
      }

      // 3. Bilingual Styling
      if (showEnglishSection) {
        englishText.style.color = data.secondaryTextColorHex || '#CBD5E1';
        englishCitation.style.color = data.secondaryReferenceColorHex || '#94A3B8';
        englishText.style.fontSize = scaledPx(data.secondaryVerseFontSize || 18);
        englishText.style.lineHeight = (1.15 + 2 / (data.secondaryVerseFontSize || 18)).toFixed(3);
        englishCitation.style.fontSize = scaledPx(data.secondaryReferenceFontSize || 14);
        englishCitation.style.lineHeight = (1.15 + 2 / (data.secondaryReferenceFontSize || 14)).toFixed(3);
        // Round 2 (#5): 'System' is not a CSS family - assigning it raw
        // made the browser fall back to its default SERIF while NDI drew
        // the system sans. Map it to the sans stack; named fonts get a
        // quoted stack. The citation never had a family (inherited the
        // Arabic stack) - it takes the same secondary stack now.
        const secFam = data.secondaryFontFamily;
        const secFontStack = (!secFam || secFam === 'System' || secFam === 'system-ui')
          ? 'system-ui, sans-serif' : "'" + secFam + "', serif";
        englishText.style.fontFamily = secFontStack;
        englishCitation.style.fontFamily = secFontStack;
        englishText.style.fontWeight = data.secondaryVerseIsBold ? 'bold' : 'normal';
        englishText.style.fontStyle = data.secondaryVerseIsItalic ? 'italic' : 'normal';
        englishCitation.style.fontWeight = data.secondaryReferenceIsBold ? 'bold' : 'normal';
        englishCitation.style.fontStyle = data.secondaryReferenceIsItalic ? 'italic' : 'normal';
        const spacing = data.showBilingualSpacing ? (data.bilingualSpacing || 20) : 0;
        englishSection.style.marginTop = (spacing * getStageScale()) + 'px';
      }

      // 4. Layout & Alignment (Ensures Full Show remains vertically & horizontally centered in middle)
      if (isFull) {
        container.style.marginBottom = '0';
        container.style.marginLeft = '0';
        container.style.marginRight = '0';
        // v1.9: fill the 16:9 stage, not the viewport — 100vw/100vh stretched
        // the video background on non-16:9 windows while NDI stayed 16:9.
        container.style.width = '100%';
        container.style.height = '100%';

        cardBox.style.display = 'flex';
        cardBox.style.flexDirection = 'column';
        cardBox.style.justifyContent = 'center';
        cardBox.style.margin = '0 auto';
        cardBox.style.padding = padT + ' ' + padR + ' ' + padB + ' ' + padL;
        cardBox.style.width = '100%';
        cardBox.style.maxWidth = '100%';
        cardBox.style.height = '100%';
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
        // v2.0: scaled by stageScale like NDI (see lower-third branch).
        const fsSpacing = data.showBilingualSpacing ? (data.bilingualSpacing || 20) : 0;
        englishSection.style.margin = (fsSpacing * getStageScale()) + 'px auto 0 auto';
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
          cardBox.style.width = '100%';
          cardBox.style.margin = '0 auto';
          citationRow.style.display = 'flex';
          citationRow.style.width = '100%';
          citationRow.style.justifyContent = 'center';
          verseText.style.textAlign = 'center';
          englishSection.style.textAlign = 'center';
        } else if (data.alignment === 'LEFT') {
          container.style.direction = 'ltr';
          cardBox.style.textAlign = 'left';
          // v1.9: full-width card like the NDI feed (was inline-block,
          // which shrink-wrapped the card to the text length).
          cardBox.style.display = 'block';
          cardBox.style.width = '100%';
          cardBox.style.margin = '0';
          citationRow.style.display = 'flex';
          citationRow.style.width = '100%';
          citationRow.style.justifyContent = 'flex-start';
          verseText.style.textAlign = 'left';
          englishSection.style.textAlign = 'left';
        } else {
          container.style.direction = 'rtl';
          cardBox.style.textAlign = 'right';
          // v1.9: full-width card like the NDI feed (was inline-block,
          // which shrink-wrapped the card to the text length).
          cardBox.style.display = 'block';
          cardBox.style.width = '100%';
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
      verseText.style.lineHeight = (1.25 + 3 / (data.verseFontSize || 26)).toFixed(3);
      citationRow.style.fontSize = scaledPx(data.referenceFontSize || 18);
      // v1.9: reset the span too — the auto-fit loop shrinks citationText directly,
      // so without this each applyData compounds the shrink.
      citationText.style.fontSize = scaledPx(data.referenceFontSize || 18);

      // v1.9.1: auto-fit as a callable unit (was a tail block of applyData).
      // It runs AFTER the used fonts are ready (see finishApply), so sizes
      // are computed with the real font metrics — measuring with fallback
      // metrics and swapping the font in afterwards was one of the visible
      // size jumps. Base sizes were just reset above (section 5).
      const doFit = function() {
        if (data.autoFitText === false) return;
        // v1.9.1 (#11): base sizes come from the PAYLOAD, not from the
        // current DOM styles. The old code read them back from the styles
        // section 5 had just reset — coupling the fit to reset ordering
        // and to the font gate. Payload-derived bases (the same formula
        // sections 3/5 use) make the fit exact and safe to run
        // synchronously the moment the text changes.
        const ss0 = getStageScale();
        // v1.9.1 (Ashraf's auto-fit model, mirrors the NDI renderer): the
        // size setting is a CAP — fitted text starts at min(selected, cap)
        // and shrinks below the cap only to fit the box (hard floor 10sp).
        const capVersePx = (data.autoFitMaxVerseSp || 48) * 2 * ss0;
        const hardMinPx = 10 * 2 * ss0;
        const baseVerse = (data.verseFontSize || 26) * 2 * ss0;
        const baseCite = (data.referenceFontSize || 18) * 2 * ss0;
        const baseEnV = (data.secondaryVerseFontSize || 18) * 2 * ss0;
        const baseEnC = (data.secondaryReferenceFontSize || 14) * 2 * ss0;
        const capRatio = Math.min(1, capVersePx / baseVerse);
        const hardMinRatio = Math.min(1, hardMinPx / baseVerse);
        // SAME budget as the NDI renderer, measured the SAME way — content
        // only (card padding excluded). The box height is the template's
        // boxHeightPercent of the stage (Lower's was a hardcoded 45%).
        const ss = getStageScale();
        const padTopPx = parseFloat(padT) || 0;
        const padBotPx = parseFloat(padB) || 0;
        function contentHeight() { return cardBox.scrollHeight - padTopPx - padBotPx; }
        const maxContentH = isFull
          ? (stage.clientHeight * ((data.boxHeightPercent || 100) / 100) - padTopPx - padBotPx)
          : (stage.clientHeight * ((data.boxHeightPercent || 45) / 100) - padTopPx - padBotPx - 16 * ss);
        function applyRatio(r) {
          verseText.style.fontSize = (baseVerse * r) + 'px';
          citationText.style.fontSize = (baseCite * r) + 'px';
          englishText.style.fontSize = (baseEnV * r) + 'px';
          englishCitation.style.fontSize = (baseEnC * r) + 'px';
        }
        const cH = contentHeight();
        let ratio = capRatio;
        if (cH > maxContentH && maxContentH > 0) {
          // One exact step (NDI's computeFitRatio), then one corrective
          // pass because line wrapping makes height non-linear. The cap
          // binds first; the box wins when both apply.
          ratio = Math.min(capRatio, Math.max(hardMinRatio, maxContentH / cH));
          applyRatio(ratio);
          const cH2 = contentHeight();
          if (cH2 > maxContentH && ratio > hardMinRatio) {
            ratio = Math.max(hardMinRatio, ratio * (maxContentH / cH2));
            applyRatio(ratio);
          }
        } else if (ratio < 1) {
          // Fits at full size — the cap alone applies (short verse).
          applyRatio(ratio);
        }
      };

      // v1.9.1: finish an apply — fit with real metrics, then reveal (only
      // if hidden). The style's actual faces are loaded first; font-display
      // 'swap' otherwise paints fallback text and swaps each face in as it
      // downloads (the "font changes two or three times" cascade). If a
      // face still lands late, fonts.ready re-fits once with final metrics.
      // v1.9.1 (#11): when every needed face is ALREADY loaded — the
      // normal verse-change case — finish SYNCHRONOUSLY in this same task:
      // no promise race, no timer. The async gate (150ms cap, was 300ms)
      // now runs only for a style's first use, so a plain verse change is
      // as immediate as the push that carried it.
      const finishApply = function() {
        const token = ++revealToken;
        lastFitFn = doFit;
        const faces = facesForData(data);
        let finished = false;
        const finish = function() {
          if (finished || token !== revealToken) return;
          finished = true;
          const fitT = performance.now();
          doFit();
          diag.lastFitMs = performance.now() - fitT;
          if (!wasVisible) {
            requestAnimationFrame(function() {
              if (token === revealToken) container.classList.add('visible');
            });
          }
          if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(function() {
              if (token === revealToken && lastFitFn === doFit) doFit();
            });
          }
          diag.applies++;
          diag.lastApplyMs = performance.now() - applyT0;
          if (diag.lastApplyMs > diag.maxApplyMs) diag.maxApplyMs = diag.lastApplyMs;
          diagEvent('apply', 'v' + (data.stateVersion || '?') + ' ' + diag.lastApplyMs.toFixed(1) + 'ms fit ' + diag.lastFitMs.toFixed(1) + 'ms');
        };
        if (!document.fonts || !document.fonts.load || faces.length === 0) { finish(); return; }
        let allReady = false;
        try {
          allReady = faces.every(function(f) { return document.fonts.check(f, 'A\u0627'); });
        } catch (e) { allReady = false; }
        if (allReady) { finish(); return; }
        const loads = faces.map(function(f) {
          try { return Promise.resolve(document.fonts.load(f, 'A\u0627')).catch(function() { return []; }); }
          catch (e) { return Promise.resolve([]); }
        });
        diagEvent('font-gate', faces.length + ' faces');
        Promise.race([
          Promise.all(loads),
          new Promise(function(res) { setTimeout(res, 150); })
        ]).then(finish).catch(finish);
      };

      // v1.9.1 fast path: identical style — text (section 2) and typography
      // (section 5) already updated above; skip the card, background,
      // particle and transition sections so a plain verse change causes
      // zero visual churn and no fade cycle.
      if (styleKey === lastStyleKey) {
        finishApply();
        return;
      }

      // 6. Color Schemes & Container Styling
      const hex = data.bgColorHex || '#0A1128';
      const opacity = data.bgOpacity !== undefined ? data.bgOpacity : 0.85;
      // v1.9: use isNaN, not || — parseInt('00',16) is 0 (falsy), and pure black
      // #000000 must stay black, not fall through to the defaults.
      const rv = parseInt(hex.slice(1,3), 16); const r = isNaN(rv) ? 10 : rv;
      const gv = parseInt(hex.slice(3,5), 16); const g = isNaN(gv) ? 17 : gv;
      const bv = parseInt(hex.slice(5,7), 16); const b = isNaN(bv) ? 40 : bv;

      const accent = data.accentColorHex || '#E5A93C';
      const textColor = data.textColorHex || '#FFFFFF';
      // v1.8: In ENGLISH_ONLY, use the English citation color for clear design.
      const refColor = langMode === 'ENGLISH_ONLY'
        ? (data.secondaryReferenceColorHex || '#94A3B8')
        : (data.referenceColorHex || '#F4D06F');

      verseText.style.color = textColor;
      citationRow.style.color = refColor;
      // v1.8: emblem is transparent, citation-colored, inline with the citation
      // (was: accent-colored chip background).
      crossEmblem.style.backgroundColor = 'transparent';
      crossEmblem.style.color = refColor;
      // v1.9.1 (#13): emoji-first stack so a symbol emblem (e.g. the sun)
      // renders as a COLOR emoji like on NDI, not the text font's
      // monochrome glyph.
      crossEmblem.style.fontFamily = "'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif";
      // v1.7: free user emblem (emoji/symbol); empty = hidden.
      if (data.emblem) {
        crossEmblem.textContent = data.emblem;
        crossEmblem.style.display = 'inline-block';
      } else {
        crossEmblem.style.display = 'none';
      }

      // v2.0: scaled like the fonts (NDI: cornerRadiusDp x 2 x frame scale).
      // Raw px made HTTP corners half as round as NDI at full size, drifting
      // further as the window shrank.
      const radius = isFull ? '0' : scaledPx(data.cornerRadiusDp || 16);
      cardBox.style.borderRadius = radius;
      if (!isFull) cardBox.style.padding = padT + ' ' + padR + ' ' + padB + ' ' + padL;

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
        // v2.0: scale offset + blur by stageScale (NDI: value x frame scale);
        // raw px made the shadow proportionally heavier than NDI at any
        // window size other than exactly 1920 wide.
        var ss = getStageScale();
        var dx = (Math.cos(ang) * dist * ss).toFixed(1);
        var dy = (Math.sin(ang) * dist * ss).toFixed(1);
        return dx + 'px ' + dy + 'px ' + (blur * ss).toFixed(1) + 'px ' + col;
      }

      // 100% transparent toggle behaves like the transparent style (mirrors the NDI canvas).
      if (data.isPureTransparentBackground) { // v1.9: toggle is the single authority (was: style check overrode it)
        cardBox.style.backgroundColor = 'transparent';
        cardBox.style.backdropFilter = 'none';
        cardBox.style.border = 'none';
        cardBox.style.boxShadow = 'none';
      } else if (data.style === 'CLASSIC_BANNER') {
        cardBox.style.backgroundColor = 'rgba(' + r + ',' + g + ',' + b + ',' + opacity + ')';
        // v2.0: border width scaled (NDI stroke: 6 x frame scale); the
        // corner radius keeps the scaled value set above (NDI honors the
        // style's cornerRadiusDp for the banner too — was hardcoded 4px).
        cardBox.style.borderRight = data.showAccentBorder ? (6 * getStageScale()) + 'px solid ' + accent : 'none';
        cardBox.style.borderLeft = 'none';
        cardBox.style.boxShadow = cardGlowShadow('0 12px 36px', 0.6);
      } else if (data.style === 'ROYAL_LITURGICAL') {
        cardBox.style.background = 'linear-gradient(135deg, rgba(' + r + ',' + g + ',' + b + ',' + opacity + ') 0%, rgba(20,20,35,' + opacity + ') 100%)';
        cardBox.style.border = data.showAccentBorder ? (6 * getStageScale()) + 'px solid ' + accent : 'none';
        cardBox.style.boxShadow = cardGlowShadow('0 10px 40px', 0.7);
      } else {
        cardBox.style.backgroundColor = 'rgba(' + r + ',' + g + ',' + b + ',' + opacity + ')';
        cardBox.style.backdropFilter = 'blur(16px)';
        cardBox.style.border = data.showAccentBorder ? (6 * getStageScale()) + 'px solid ' + accent : 'none';
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
      // v1.9.1: don't tear the particle field down on every apply — it was
      // restarted (positions reset) by any state change, even a verse tap.
      // Round 2 (#7) stopped the canvas under Alpha 100%; the audit (A2)
      // refined that: NDI DOES draw particles (and the ethereal glow)
      // under pure for animated styles - only the wash/fill is gone. So
      // the canvas follows the animated type alone, pure or not.
      if (animType !== 'gold_particles' && animType !== 'particles') {
        stopParticles();
        activeParticleKey = null;
      }

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
        if (activeParticleKey !== 'gold_particles') {
          startParticles('#FCD34D', animOpacity);
          activeParticleKey = 'gold_particles';
        }
      } else if (animType === 'particles') {
        animBgLayer.classList.add('anim-particles');
        animBgLayer.style.opacity = animOpacity;
        if (activeParticleKey !== 'particles') {
          startParticles('#7DD3FC', animOpacity);
          activeParticleKey = 'particles';
        }
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
        // v2.0: Crossfade on HTTP — fade the video element near the loop point
        // (matches the NDI draw-time fade). timeupdate fires ~4x/sec; enough
        // for a 500ms fade window.
        const wantFade = (data.videoLoopMode || 'fade') === 'fade';
        if (wantFade) {
          customVideoBg.ontimeupdate = function() {
            const dur = customVideoBg.duration;
            const pos = customVideoBg.currentTime;
            if (!dur || dur <= 0) { customVideoBg.style.opacity = animOpacity; return; }
            const fadeS = 0.5;
            let f = 1;
            if (pos >= dur - fadeS) f = Math.max(0, (dur - pos) / fadeS);
            else if (pos < fadeS) f = Math.max(0, pos / fadeS);
            customVideoBg.style.opacity = (animOpacity * f).toFixed(3);
          };
        } else {
          customVideoBg.ontimeupdate = null;
        }
      } else {
        animBgLayer.style.opacity = '0';
      }

      // v1.9: motion speed scales procedural animations too (was custom-video
      // only). Base durations match the CSS keyframes above.
      const animBaseDurations = {
        'golden_rays': 15, 'blue_waves': 20, 'candle_glow': 5, 'purple_silk': 18,
        'emerald_waves': 20, 'rose_glow': 8, 'gold_particles': 15, 'particles': 10
      };
      const animSpeed = data.motionSpeed === undefined ? 1.0 : parseFloat(data.motionSpeed);
      if (animType !== 'none' && animType !== 'custom_video' && animBaseDurations[animType]) {
        if (!(animSpeed > 0)) {
          animBgLayer.style.animationPlayState = 'paused';
        } else {
          animBgLayer.style.animationPlayState = 'running';
          animBgLayer.style.animationDuration = (animBaseDurations[animType] / animSpeed) + 's';
        }
      } else {
        animBgLayer.style.animationPlayState = 'running';
        animBgLayer.style.animationDuration = '';
      }

      // v1.9.1 (#13 + F4): animated backgrounds at NATIVE strength and
      // placement, written INLINE from the payload on every apply. The NDI
      // renderer paints the animated gradient AS the background — the
      // type's two colors at the style's bgOpacity (Full: the whole frame;
      // Lower: inside the card only) — and the 100%-transparent toggle
      // suppresses it entirely. The first version of this fix used CSS
      // classes for the gradient: a class left on the card survived toggle
      // flips (HTTP appeared to ignore the Edit tab until the background
      // was cycled None -> chosen) and the card gradient ignored
      // bgOpacity. Inline styles rewritten from the payload each apply
      // cannot go stale: what the editor says is what paints.
      const GRAD_COLORS = {
        golden_rays: [[217,119,6],[251,191,36]],
        blue_waves: [[30,58,138],[14,165,233]],
        candle_glow: [[120,53,15],[245,158,11]],
        purple_silk: [[88,28,135],[192,132,252]],
        emerald_waves: [[6,78,59],[52,211,153]],
        rose_glow: [[136,19,55],[251,113,133]],
        gold_particles: [[120,53,15],[252,211,77]],
        particles: [[15,23,42],[56,189,248]]
      };
      const grad = GRAD_COLORS[animType];
      const gradIsRadial = animType === 'candle_glow' || animType === 'rose_glow';
      function rgbaStr(c, a) { return 'rgba(' + c[0] + ',' + c[1] + ',' + c[2] + ',' + a + ')'; }
      function gradCss(alpha, band) {
        if (gradIsRadial) {
          return 'radial-gradient(circle at 50% 50%, ' + rgbaStr(grad[1], alpha) + ' 0%, ' + rgbaStr(grad[0], alpha) + ' 100%)';
        }
        // Round 2 (#3): the Full frame's native shader is a moving WINDOW
        // (bright band between darker edges), not a broad wash - compress
        // the bright stop into a band for the frame path.
        if (band) {
          return 'linear-gradient(135deg, ' + rgbaStr(grad[0], alpha) + ' 30%, ' + rgbaStr(grad[1], alpha) + ' 50%, ' + rgbaStr(grad[0], alpha) + ' 70%)';
        }
        return 'linear-gradient(135deg, ' + rgbaStr(grad[0], alpha) + ' 0%, ' + rgbaStr(grad[1], alpha) + ' 50%, ' + rgbaStr(grad[0], alpha) + ' 100%)';
      }
      function clearGrad(el) {
        el.style.backgroundImage = 'none';
        el.style.backgroundSize = '';
        el.classList.remove('bg-flow');
        el.classList.remove('bg-breath');
        el.style.animationPlayState = '';
        el.style.animationDuration = '';
      }
      function applyGrad(el, breath, band) {
        const bgOp = data.bgOpacity !== undefined ? data.bgOpacity : 0.85;
        el.style.backgroundImage = gradCss(bgOp, band);
        // v1.9.1 parity: 200%, not 400% — at 400% the gradient's bright
        // mid-band stretched ~4x wider than the native shader's (its stops
        // span the box once), washing the whole card bright on HTTP.
        // Round 2 (#3): the band variant tiles at 300% so bg-flow sweeps
        // the bright band across the frame like the native window.
        el.style.backgroundSize = band ? '300% 300%' : '200% 200%';
        el.classList.add(breath ? 'bg-breath' : 'bg-flow');
        const dur = animBaseDurations[animType];
        if (dur) {
          if (!(animSpeed > 0)) el.style.animationPlayState = 'paused';
          else { el.style.animationPlayState = 'running'; el.style.animationDuration = (dur / animSpeed) + 's'; }
        }
      }
      if (cardGradOn) { clearGrad(cardBox); cardGradOn = false; }
      if (grad && !data.isPureTransparentBackground) {
        if (isFull) {
          // The layer IS the frame background; the card goes clear so the
          // gradient is not buried under the flat fill.
          animBgLayer.className = 'animated-bg-layer';
          applyGrad(animBgLayer, gradIsRadial, !gradIsRadial);
          animBgLayer.style.opacity = '1';
          cardBox.style.backgroundColor = 'transparent';
        } else {
          // Lower: the gradient belongs INSIDE the card (native paints
          // the card box only) — inline on the card, layer emptied.
          animBgLayer.className = 'animated-bg-layer';
          animBgLayer.style.opacity = '0';
          clearGrad(animBgLayer);
          applyGrad(cardBox, false);
          // v1.9.1 parity: native paints the gradient INSTEAD of the card
          // color (the shader replaces the fill) — drop the section-6
          // underlay so the gradient composites like NDI's frame.
          cardBox.style.backgroundColor = 'transparent';
          cardGradOn = true;
        }
      } else if (grad && data.isPureTransparentBackground) {
        // Audit fix (A2): under Alpha 100% NDI paints NO wash and NO card
        // fill, but animated styles still get the delicate ethereal glow
        // (radial, alpha = motion opacity x 75, capped 15..120) behind
        // the text. Mirror it on the layer; particles (for the particle
        // types) are started by the class chain above.
        const GLOW_COLORS = { golden_rays: [251,191,36], blue_waves: [56,189,248], candle_glow: [245,158,11], purple_silk: [192,132,252], emerald_waves: [52,211,153], rose_glow: [251,113,133], gold_particles: [252,211,77], particles: [125,211,252] };
        const gc = GLOW_COLORS[animType] || [255,255,255];
        const ga = Math.max(15, Math.min(120, animOpacity * 75)) / 255;
        animBgLayer.className = 'animated-bg-layer';
        clearGrad(animBgLayer);
        animBgLayer.style.backgroundImage = 'radial-gradient(circle at 50% 50%, ' + rgbaStr(gc, ga.toFixed(3)) + ' 0%, ' + rgbaStr(gc, 0) + ' 70%)';
        animBgLayer.style.opacity = '1';
      } else if (animType !== 'custom_video') {
        // No animated gradient for this payload (or the transparent
        // toggle is on): the layer must carry nothing.
        animBgLayer.className = 'animated-bg-layer';
        animBgLayer.style.opacity = '0';
        clearGrad(animBgLayer);
      } else {
        // custom_video: the chain above owns the layer; just drop any
        // inline gradient left from a previous animated style.
        animBgLayer.style.backgroundImage = 'none';
      }

      // 8. Entry Transitions
      container.className = isFull ? 'mode-full-show' : '';
      if (transType === 'FADE') container.classList.add('anim-fade');
      else if (transType === 'SLIDE_DOWN') container.classList.add('anim-slide-down');
      else if (transType === 'SLIDE_RIGHT') container.classList.add('anim-slide-right');
      else if (transType === 'SLIDE_LEFT') container.classList.add('anim-slide-left');
      else if (transType === 'SCALE_ZOOM') container.classList.add('anim-zoom');
      else if (transType === 'BOUNCE') container.classList.add('anim-bounce');
      else if (transType === 'CUT') container.classList.add('anim-cut');
      else container.classList.add('anim-slide-up');

      container.style.transitionDuration = transDur + 'ms';
      if (wasVisible) container.classList.add('visible');

      // v1.9.1: fit + reveal happen through the font gate (finishApply),
      // defined in section 5. The old tail block (immediate fit with
      // fallback metrics + rAF reveal) is gone.
      lastStyleKey = styleKey;
      finishApply();
    }

    // Connect Server-Sent Events (SSE)
    // v1.9.1: shared forced reconnect (half-open socket recovery). The
    // watchdog calls this on its 5 s tick — but Chrome throttles timers in
    // background tabs, so the visibility/focus hooks below call it the
    // moment the user returns to the tab: the watchdog's blind spot.
    function forceReconnect(reason) {
      diagEvent(reason, 'silence>35s, reconnecting');
      try { evtSource.close(); } catch (e) { /* ignore */ }
      if (sseWatchdog) { clearInterval(sseWatchdog); sseWatchdog = null; }
      connectSse();
    }

    function connectSse() {
      const urlParams = new URLSearchParams(window.location.search);
      const isShow = urlParams.get('show') === '1' || window.location.pathname.includes('/full');
      const sseUrl = '/events' + (isShow ? '?show=1' : '');
      
      const evtSource = new EventSource(sseUrl);
      // v1.9: the server's stateVersion (frameVersion) resets to 0 on every
      // process restart (in-memory counter). A fresh SSE connection must accept
      // the current state unconditionally, or every update after an OS-kill
      // auto-resume is dropped as "stale" and the overlay freezes on the
      // pre-crash verse until a manual page refresh.
      evtSource.onopen = function() { lastStateVersion = -1; lastSseSeen = Date.now(); diag.opens++; diag.reconnects = Math.max(0, diag.opens - 1); diagEvent('sse-open', ''); };
      evtSource.onmessage = function(e) {
        lastSseSeen = Date.now();
        diag.msgs++;
        try {
          applyData(JSON.parse(e.data));
        } catch (err) {
          console.error('Failed to parse SSE payload', err);
        }
      };
      // v1.9.1: heartbeat watchdog. A weak-Wi-Fi drop can leave the socket
      // half-open: no error fires, no data arrives, and the page freezes on
      // its last state (the stale-card trap). The server pings every 15s;
      // if nothing — data or ping — is heard for 35s, force a reconnect,
      // which re-syncs from the server's connect-time state push.
      evtSource.addEventListener('ping', function() { lastSseSeen = Date.now(); });
      if (sseWatchdog) clearInterval(sseWatchdog);
      sseWatchdog = setInterval(function() {
        if (Date.now() - lastSseSeen > 35000) forceReconnect('watchdog');
      }, 5000);
      evtSource.onerror = function() {
        evtSource.close();
        if (sseWatchdog) { clearInterval(sseWatchdog); sseWatchdog = null; }
        setTimeout(connectSse, 2000);
      };
    }

    // Initial payload render
    const initialData = $initialJson;
    try {
      applyData(initialData);
    } catch (e) {
      console.error(e);
    }

    // v1.9.1 (#11): warm the initial style's fonts in the background. In
    // standby, applyData returns before any font loading runs — without
    // this, the FIRST verse pushed live would pay the full font-load wait.
    try {
      if (document.fonts && document.fonts.load && initialData) {
        facesForData(initialData).forEach(function(f) {
          try { document.fonts.load(f, 'A\u0627'); } catch (e) { /* best-effort */ }
        });
      }
    } catch (e) { /* best-effort */ }

    setInterval(sendDiag, 10000);
    document.addEventListener('visibilitychange', function() {
      if (document.visibilityState === 'hidden') { sendDiag(); return; }
      if (Date.now() - lastSseSeen > 35000) forceReconnect('visibility-resume');
    });
    window.addEventListener('focus', function() {
      if (Date.now() - lastSseSeen > 35000) forceReconnect('visibility-resume');
    });
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

    /**
     * v1.9: builds the verse/citation/English text layouts at [fitRatio] of the
     * selected text sizes. Auto-fit measures at full size first, then rebuilds
     * once at a reduced ratio so long verses reflow (not just shrink) into the
     * available content height instead of overflowing the card or screen.
     * Returns null when there is no verse text to lay out.
     */
    private data class FittedTextLayouts(
        val primary: StaticLayout,
        val bilingualSecondary: StaticLayout?,
        val secondaryCitation: StaticLayout?,
        val secHeight: Float,
        val citationHeight: Float,
        val contentH: Float,
    )

    /**
     * v1.9: true particle field on NDI (matches the HTTP overlay's particle
     * animations — previously a plain moving gradient). Stateless and
     * time-driven: each particle's position and twinkle derive from its index
     * and the animation clock, so no cross-frame state is needed and
     * motion-speed scaling applies automatically. [baseAlpha] ties the
     * particles to the style's animation opacity.
     */
    private fun drawParticleField(
        canvas: Canvas,
        boxLeft: Float, boxTop: Float, boxRight: Float, boxBottom: Float,
        radius: Float,
        clockSec: Float,
        scale: Float,
        particleColor: Int,
        baseAlpha: Int,
    ) {
        val w = boxRight - boxLeft
        val h = boxBottom - boxTop
        if (w <= 1f || h <= 1f) return
        val count = ((w * h) / (1920f * 1080f) * 120f).toInt().coerceIn(30, 150)
        fun posMod(v: Float, m: Float): Float = ((v % m) + m) % m
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.save()
        if (radius > 0f) {
            canvas.clipPath(Path().apply {
                addRoundRect(RectF(boxLeft, boxTop, boxRight, boxBottom), radius, radius, Path.Direction.CW)
            })
        }
        for (i in 0 until count) {
            // Deterministic pseudo-random per particle — stable across frames.
            val r1 = ((i * 2654435761L + 1013904223L) % 1000L).toFloat() / 1000f
            val r2 = ((i * 40503L + 2246822519L) % 1000L).toFloat() / 1000f
            val r3 = ((i * 65599L + 3266489917L) % 1000L).toFloat() / 1000f
            // Slow drift (upward + sideways), wrapping around the box.
            val x = boxLeft + posMod(r1 * w + clockSec * (6f + r2 * 18f) * scale, w)
            val y = boxTop + posMod(r2 * h - clockSec * (10f + r3 * 22f) * scale, h)
            // Twinkle.
            val tw = (sin((clockSec * (0.8f + r3 * 2.2f) + r1 * 6.2831f).toDouble()).toFloat() + 1f) / 2f
            val alpha = (baseAlpha * (0.25f + 0.75f * tw)).toInt().coerceIn(0, 255)
            val size = (1.2f + r2 * 2.8f) * scale
            paint.color = Color.argb(alpha, Color.red(particleColor), Color.green(particleColor), Color.blue(particleColor))
            canvas.drawCircle(x, y, size, paint)
        }
        canvas.restore()
    }

    /**
     * v1.9.1 F3: ONE shared legibility floor for auto-fit. When the fitted
     * verse size would fall below 24px, EVERY element (verse, citation,
     * English verse, English citation) is boosted by the same factor, so the
     * user-designed size ratios between them are preserved exactly. (The old
     * per-element floors — 24/18/18/14 — distorted the citation:verse ratio
     * on deep shrinks.) Ratio-preserving by construction.
     */
    private fun fitFloorBoost(tpl: LowerThirdTemplate, scale: Float, fitRatio: Float): Float {
        val rawVerse = tpl.verseFontSize * 2f * scale * fitRatio
        return if (rawVerse > 0.01f && rawVerse < 24f) 24f / rawVerse else 1f
    }

    // Audit fix (perf): fitted-layout cache. StaticLayouts are immutable
    // after build; entries are keyed by everything the fit depends on and
    // discriminated by feed (isFull / isForStream) so the Lower, Full and
    // stream renderers never share an entry. Bounded to 4 (live verse,
    // transition partner, one spare per feed family).
    private data class FittedLayoutsKey(
        val tpl: LowerThirdTemplate,
        val verse: BibleVerse,
        val verseText: String,
        val width: Int,
        val height: Int,
        val contentWidth: Int,
        val scale: Float,
        val shadowBlur: Float,
        val shadowDx: Float,
        val shadowDy: Float,
        val showArabic: Boolean,
        val showEnglish: Boolean,
        val isFull: Boolean,
        val isForStream: Boolean
    )

    private val fittedLayoutsCache = object : LinkedHashMap<FittedLayoutsKey, Pair<FittedTextLayouts, Float>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FittedLayoutsKey, Pair<FittedTextLayouts, Float>>?): Boolean = size > 4
    }

    private fun fittedLayoutsCached(
        tpl: LowerThirdTemplate,
        verseText: String,
        activeVerse: BibleVerse,
        contentWidth: Int,
        scale: Float,
        shadowBlur: Float,
        shadowDx: Float,
        shadowDy: Float,
        showArabic: Boolean,
        showEnglish: Boolean,
        frameWidth: Int,
        frameHeight: Int,
        isFull: Boolean,
        isForStream: Boolean
    ): Pair<FittedTextLayouts, Float>? {
        val key = FittedLayoutsKey(
            tpl, activeVerse, verseText, frameWidth, frameHeight,
            contentWidth, scale, shadowBlur, shadowDx, shadowDy,
            showArabic, showEnglish, isFull, isForStream
        )
        synchronized(fittedLayoutsCache) { fittedLayoutsCache[key]?.let { return it } }
        // v1.9 auto-fit: build layouts at full size, then rebuild once at a
        // reduced scale when the content exceeds its height budget, so long
        // verses reflow into the card (lower third) or screen (full show)
        // instead of overflowing. v1.9.1: the rebuild ratio also applies the
        // autoFitMaxVerseSp CAP, so short verses render at min(selected, cap).
        // v1.9.1 (Ashraf's auto-fit model): the size setting is a CAP, not a
        // floor. The fitted text starts at min(selected size, cap) - a short
        // verse no longer blows up huge on a full screen - and long content
        // shrinks below the cap until it fits the box, down to a hard 10sp
        // floor. The box height is the template's boxHeightPercent of the
        // frame (Lower's budget was a hardcoded 45% before).
        val capRatio = if (tpl.verseFontSize > 0)
            (tpl.autoFitMaxVerseSp.toFloat() / tpl.verseFontSize).coerceIn(0.05f, 1f)
        else 1f
        val hardFloorRatio = if (tpl.verseFontSize > 0)
            (10f / tpl.verseFontSize).coerceIn(0.05f, 1f)
        else 1f
        var layouts = buildFittedTextLayouts(
            tpl, verseText, activeVerse, contentWidth, scale,
            shadowBlur, shadowDx, shadowDy, showArabic, showEnglish, 1f
        ) ?: return null
        var r = 1f
        if (tpl.autoFitText) {
            val padT = tpl.cardPaddingTopDp * scale
            val padB = tpl.cardPaddingBottomDp * scale
            val maxContentH = if (tpl.isFullScreen) {
                frameHeight * tpl.boxHeightPercent / 100f - padT - padB
            } else {
                // Lower third must not swallow the screen.
                frameHeight * tpl.boxHeightPercent / 100f - padT - padB - 16f * scale
            }
            r = capRatio
            if (layouts.contentH > maxContentH && maxContentH > 0) {
                r = minOf(computeFitRatio(layouts.contentH, maxContentH, hardFloorRatio), capRatio)
            }
            if (r < 1f) {
                layouts = buildFittedTextLayouts(
                    tpl, verseText, activeVerse, contentWidth, scale,
                    shadowBlur, shadowDx, shadowDy, showArabic, showEnglish, r
                ) ?: return null
            }
        }
        val result = layouts to r
        synchronized(fittedLayoutsCache) { fittedLayoutsCache[key] = result }
        return result
    }

    private fun buildFittedTextLayouts(
        tpl: LowerThirdTemplate,
        verseText: String,
        activeVerse: BibleVerse,
        contentWidth: Int,
        scale: Float,
        shadowBlur: Float,
        shadowDx: Float,
        shadowDy: Float,
        showArabic: Boolean,
        showEnglish: Boolean,
        fitRatio: Float,
    ): FittedTextLayouts? {
        // v1.9.1 F3: shared ratio-preserving floor boost (see fitFloorBoost).
        val floorBoost = fitFloorBoost(tpl, scale, fitRatio)
        // Setup TextPaint for multi-line wrapped text
        val verseTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = try { Color.parseColor(tpl.textColorHex) } catch (e: Exception) { Color.WHITE }
            textSize = tpl.verseFontSize * 2f * scale * fitRatio * floorBoost
            
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
                .setLineSpacing(6f * scale * fitRatio, 1.25f)
                .setIncludePad(true)
                // v2.0: greedy line breaking like the browser (HTTP overlay).
                // The default HIGH_QUALITY balances lines (breaks early), so
                // the same verse took more, shorter lines on NDI than on HTTP.
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(verseText, verseTextPaint, contentWidth, alignment, 1.25f, 6f * scale * fitRatio, true)
        }
        } else null

        // v2.0: budget the citation at its TRUE drawn footprint — baseline at
        // startY + textSize*0.9, primary text starts 16*scale below that. The
        // old fixed 44*scale*fitRatio underestimated it, shifting everything
        // below (Arabic, English) down and eating the bottom padding.
        val citationHeight = (tpl.referenceFontSize * 2f * scale * fitRatio * floorBoost) * 0.9f + 16f * scale

        // Calculate bilingual secondary text if needed
        var secondaryLayout: StaticLayout? = null
        var secondaryCitationLayout: StaticLayout? = null
        if (showEnglish) {
            // v1.8: In ENGLISH_ONLY, the citation is already in the main citation row.
            // Don't duplicate it here. In BOTH mode, the English citation gets its
            // own line below the English verse (matches the HTTP overlay).
            val isEnglishOnly = tpl.languageMode == LanguageMode.ENGLISH_ONLY
            val fullSecondaryText = activeVerse.englishText
            
            val secondaryTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = try { Color.parseColor(tpl.secondaryTextColorHex) } catch (e: Exception) { Color.LTGRAY }
                textSize = tpl.secondaryVerseFontSize * 2f * scale * fitRatio * floorBoost
                
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
            
            // v1.9: English citation on its own line below the verse (was appended
            // inline). Matches the HTTP overlay's separate english-citation div.
            val secAlignment = when (tpl.alignment) {
                BroadcastTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_NORMAL // LTR English: NORMAL = left
            }

            secondaryLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(fullSecondaryText, 0, fullSecondaryText.length, secondaryTextPaint, contentWidth)
                    .setAlignment(secAlignment)
                    .setLineSpacing(4f * scale * fitRatio, 1.15f)
                    .setIncludePad(true)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(fullSecondaryText, secondaryTextPaint, contentWidth, secAlignment, 1.15f, 4f * scale * fitRatio, true)
            }

            if (!isEnglishOnly) {
                val secCitationText = "(" + activeVerse.getFormattedEnglishCitation() + ")"
                val secCitationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = try { Color.parseColor(tpl.secondaryReferenceColorHex) } catch (e: Exception) { Color.GRAY }
                    textSize = tpl.secondaryReferenceFontSize * 2f * scale * fitRatio * floorBoost
                    var citStyle = Typeface.NORMAL
                    if (tpl.secondaryReferenceIsBold && tpl.secondaryReferenceIsItalic) citStyle = Typeface.BOLD_ITALIC
                    else if (tpl.secondaryReferenceIsBold) citStyle = Typeface.BOLD
                    else if (tpl.secondaryReferenceIsItalic) citStyle = Typeface.ITALIC
                    typeface = getBestTypeface(tpl.secondaryFontFamily, citStyle)
                    if (tpl.textShadowEnabled) {
                        val shadowCol = try { Color.parseColor(tpl.textShadowColorHex) } catch (e: Exception) { Color.BLACK }
                        setShadowLayer(shadowBlur, shadowDx, shadowDy, shadowCol)
                    }
                    isFakeBoldText = tpl.secondaryReferenceIsBold
                    if (tpl.secondaryReferenceIsItalic) textSkewX = -0.25f
                }
                secondaryCitationLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(secCitationText, 0, secCitationText.length, secCitationPaint, contentWidth)
                        .setAlignment(secAlignment)
                        .setLineSpacing(4f * scale * fitRatio, 1.15f)
                        .setIncludePad(true)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(secCitationText, secCitationPaint, contentWidth, secAlignment, 1.15f, 4f * scale * fitRatio, true)
                }
            }
        }

        val bilSpacing = if (tpl.showBilingualSpacing) tpl.bilingualSpacing * scale else 0f
        // v1.8: primary content drives sizing/positioning — Arabic when shown,
        // else English (ENGLISH_ONLY). The offset secondary block only exists
        // when BOTH languages are shown.
        val primary = staticLayout ?: secondaryLayout ?: return null
        val bilingualSecondary = if (staticLayout != null) secondaryLayout else null
        val secondaryCitation = if (staticLayout != null) secondaryCitationLayout else null
        val secCitHeight = (secondaryCitation?.height ?: 0) + (4f * scale * fitRatio)
        val secHeight = if (bilingualSecondary != null) bilingualSecondary.height + secCitHeight + bilSpacing else 0f
        val contentH = primary.height + citationHeight + secHeight
        return FittedTextLayouts(primary, bilingualSecondary, secondaryCitation, secHeight, citationHeight, contentH)
    }

    private fun renderFrame(width: Int = 1920, height: Int = 1080, isForStream: Boolean = false, overrideTemplate: LowerThirdTemplate? = null, allowSharedCache: Boolean): Bitmap {
        val tpl = overrideTemplate ?: currentTemplate
        val isFull = tpl.isFullScreen

        // Fast Path: Return cached Bitmap if state hasn't changed (0ms allocation-free)
        // v2.0: never serve the cache mid verse-transition — the crossfade
        // must be re-rendered every frame until it completes.
        if (allowSharedCache && !isForStream && overrideTemplate == null && width == 1920 && height == 1080 && !isTransitionActiveFor(isFull)) {
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
    private fun drawFrameInto(bitmap: Bitmap, width: Int, height: Int, isForStream: Boolean, tpl: LowerThirdTemplate, isFull: Boolean, isMotionTick: Boolean = false, verseOverride: BibleVerse? = null, isTransitionSnapshot: Boolean = false) {
        // v1.9: frame-timing instrumentation (replaces the GPU investigation).
        val drawStartNs = System.nanoTime()
        val canvas = Canvas(bitmap)

        // 100% Pure transparent alpha canvas by default
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // v1.8: no verse selected on launch — blank frame (cleared above; no text).
        // v2.0: verseOverride renders a specific verse (transition snapshots).
        val activeVerse = verseOverride ?: currentVerse ?: return

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

        // When off-air, return a 100% transparent alpha frame for BOTH feeds
        // (v1.9.1 F1: the old Full Show exemption kept the Full feed showing
        // the verse after OFF AIR — removed; OFF AIR blanks every output).
        if (!isLive && !isForStream) {
            return
        }

        // v2.0: verse-change crossfade. The OLD verse's finished frame is
        // laid down fading out; everything below (the new frame) is drawn
        // into a layer at fade-in alpha, so the pair composite to exactly
        // the finished frames at progress 0 and 1. Snapshot renders (the
        // recursive call that produces the old frame) skip this block.
        var transitionScratch: Bitmap? = null
        var transitionLayer = -1
        if (!isTransitionSnapshot && isTransitionActiveFor(isFull)) {
            val prevVerse = transitionPrevVerse
            if (prevVerse != null) {
                val p = transitionProgressFor(isFull)
                try {
                    val scratch = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    drawFrameInto(scratch, width, height, isForStream, tpl, isFull, verseOverride = prevVerse, isTransitionSnapshot = true)
                    val outPaint = Paint().apply { alpha = ((1f - p) * 255f).toInt().coerceIn(0, 255) }
                    canvas.drawBitmap(scratch, 0f, 0f, outPaint)
                    transitionScratch = scratch
                } catch (_: OutOfMemoryError) { /* fall through: plain fade-in */
                } catch (_: Exception) { /* fall through: plain fade-in */ }
                val layerPaint = Paint().apply { alpha = (p * 255f).toInt().coerceIn(0, 255) }
                transitionLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)
                // Apply directional motion or scaling during transition
                when (tpl.transition) {
                    TransitionType.SLIDE_UP -> canvas.translate(0f, (1f - p) * 50f * (width / 1920f))
                    TransitionType.SLIDE_DOWN -> canvas.translate(0f, -(1f - p) * 50f * (width / 1920f))
                    TransitionType.SLIDE_RIGHT -> canvas.translate((1f - p) * 60f * (width / 1920f), 0f)
                    TransitionType.SLIDE_LEFT -> canvas.translate(-(1f - p) * 60f * (width / 1920f), 0f)
                    TransitionType.SCALE_ZOOM -> {
                        val s = 0.92f + 0.08f * p
                        canvas.scale(s, s, width / 2f, height / 2f)
                    }
                    TransitionType.BOUNCE -> {
                        val bp = if (p < 0.7f) (p / 0.7f) * 1.05f else 1.05f - (p - 0.7f) / 0.3f * 0.05f
                        canvas.scale(bp, bp, width / 2f, height / 2f)
                    }
                    else -> {}
                }
            }
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

        // v1.9: per-side card padding from the style (was hardcoded 36/32).
        val padL = tpl.cardPaddingLeftDp * scale
        val padR = tpl.cardPaddingRightDp * scale
        val padT = tpl.cardPaddingTopDp * scale
        val padB = tpl.cardPaddingBottomDp * scale
        val contentWidth = (boxWidth - padL - padR).toInt().coerceAtLeast(200)

        // Audit fix (perf): the fitted layouts (incl. the auto-fit search)
        // come from a small cache - they only change when the verse, the
        // template, or the frame size changes, while drawFrameInto runs
        // every frame. Only animation phase/particles vary per frame, and
        // they never touch layouts.
        val fitted = fittedLayoutsCached(
            tpl, verseText, activeVerse, contentWidth, scale,
            shadowBlur, shadowDx, shadowDy, showArabic, showEnglish,
            width, height, isFull, isForStream
        ) ?: return
        val layouts = fitted.first
        val fitRatio = fitted.second
        val primaryLayout = layouts.primary
        val bilingualSecondary = layouts.bilingualSecondary
        val secHeight = layouts.secHeight
        val citationHeight = layouts.citationHeight
        
        // Full screen check for projector/full show mode
        val boxHeight = if (tpl.isFullScreen) {
            height.toFloat() 
        } else {
            (layouts.contentH + padT + padB + 16f * scale).coerceAtLeast(140f * scale)
        }

        val boxTop = if (tpl.isFullScreen) 0f else (height - marginB - boxHeight)
        val boxBottom = if (tpl.isFullScreen) height.toFloat() else (height - marginB)
        val boxLeft = if (tpl.isFullScreen) 0f else marginH
        val boxRight = if (tpl.isFullScreen) width.toFloat() else (width - marginH)
        val rect = RectF(boxLeft, boxTop, boxRight, boxBottom)
        val radius = if (tpl.isFullScreen) 0f else (tpl.cornerRadiusDp * scale * 2f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // v1.8: Card glow drawn FIRST (behind video/card) so it appears as an
        // outer halo without covering the video. The glow expands beyond the
        // card bounds; drawing it first lets the video/card cover the inner part.
        if (!tpl.isPureTransparentBackground) { // v1.9: toggle is the single authority (was: style check overrode it)
            if (tpl.cardGlowEnabled && !tpl.isFullScreen) {
                val glowCol = try { Color.parseColor(tpl.cardGlowColorHex) } catch (e: Exception) { Color.BLACK }
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        }

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
            // v2.0: Apply the template's loop mode (fade or cut).
            videoRenderer.loopMode = tpl.videoLoopMode.id
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

        if (!tpl.isPureTransparentBackground) { // v1.9: toggle is the single authority (was: style check overrode it)
            val bgAlpha = (tpl.bgOpacity * 255).toInt().coerceIn(0, 255)
            val baseBgCol = try { Color.parseColor(tpl.bgColorHex) } catch (e: Exception) { Color.DKGRAY }

            if (videoRenderer != null) {
                // v1.7: the custom video IS the card background (already drawn
                // above) — skip the normal fill so it stays visible.
            } else if (tpl.animatedBackground != AnimatedBackgroundType.NONE) {
                val timeSec = (System.currentTimeMillis() % 12000L) / 1000f
                // v1.9: motion speed scales procedural animations too (was fixed speed;
                // only custom video responded). 0 = paused (frozen mid-phase).
                val phase = (sin(timeSec.toDouble() * 1.5 * tpl.motionSpeed).toFloat() + 1f) / 2f
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
                            boxRight - (padR * 2f), boxTop + (boxHeight / 2f),
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
                            boxLeft + (boxWidth * phase), boxTop,
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
                // v1.9: true particle field over the card base (matches HTTP —
                // previously these two types were a plain gradient).
                if (tpl.animatedBackground == AnimatedBackgroundType.PARTICLE_STARS ||
                    tpl.animatedBackground == AnimatedBackgroundType.GOLDEN_PARTICLES) {
                    val pColor = if (tpl.animatedBackground == AnimatedBackgroundType.PARTICLE_STARS)
                        Color.rgb(125, 211, 252) else Color.rgb(252, 211, 77)
                    val pClock = (System.currentTimeMillis() % 60000L) / 1000f * tpl.motionSpeed
                    // Audit fix: particles use the motion-opacity formula on
                    // BOTH branches (the pure branch already did; this one
                    // passed the card bgAlpha, so the same style's stars were
                    // dimmer over a visible card - and HTTP only ever matched
                    // the pure formula).
                    val particleAlpha = (tpl.animatedBackgroundOpacity * 255).toInt().coerceIn(25, 255)
                    drawParticleField(canvas, boxLeft, boxTop, boxRight, boxBottom, radius, pClock, scale, pColor, particleAlpha)
                }
            } else {
                paint.color = Color.argb(bgAlpha, Color.red(baseBgCol), Color.green(baseBgCol), Color.blue(baseBgCol))
                canvas.drawRoundRect(rect, radius, radius, paint)
            }

        } else if (tpl.animatedBackground != AnimatedBackgroundType.NONE && tpl.animatedBackground != AnimatedBackgroundType.CUSTOM_VIDEO) {
            // If Pure Transparent background with animated motion background is selected:
            // Draw a delicate, ethereal luminous motion glow directly behind the verse while maintaining 100% alpha transparency elsewhere!
            val animAlpha = (tpl.animatedBackgroundOpacity * 75).toInt().coerceIn(15, 120)
            val timeSec = (System.currentTimeMillis() % 12000L) / 1000f
            // v1.9: motion speed scales procedural animations too (was fixed speed).
            val phase = (sin(timeSec.toDouble() * 1.5 * tpl.motionSpeed).toFloat() + 1f) / 2f
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
            // v1.9: true particle field over the glow (matches HTTP).
            if (tpl.animatedBackground == AnimatedBackgroundType.PARTICLE_STARS ||
                tpl.animatedBackground == AnimatedBackgroundType.GOLDEN_PARTICLES) {
                val pColor = if (tpl.animatedBackground == AnimatedBackgroundType.PARTICLE_STARS)
                    Color.rgb(125, 211, 252) else Color.rgb(252, 211, 77)
                val pClock = (System.currentTimeMillis() % 60000L) / 1000f * tpl.motionSpeed
                // v2.0: particles need full-range alpha like HTTP (opacity*255);
                // the delicate animAlpha (~45) belongs to the glow gradient only.
                val particleAlpha = (tpl.animatedBackgroundOpacity * 255).toInt().coerceIn(25, 255)
                drawParticleField(canvas, boxLeft, boxTop, boxRight, boxBottom, radius, pClock, scale, pColor, particleAlpha)
            }
        }

        // v2.0: Accent border for ALL background types (video, animated, plain).
        // Was drawn only inside the animated-background branch, so plain and
        // video styles never showed a border on NDI.
        if (!tpl.isPureTransparentBackground && tpl.showAccentBorder) {
            val accCol = try { Color.parseColor(tpl.accentColorHex) } catch (e: Exception) { Color.YELLOW }
            paint.color = accCol
            paint.strokeWidth = 6f * scale
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }

        // Draw Citation Badge
        val refPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // v1.8: In ENGLISH_ONLY, use the English citation color for clear design.
            val refColorHex = if (tpl.languageMode == LanguageMode.ENGLISH_ONLY) tpl.secondaryReferenceColorHex else tpl.referenceColorHex
            color = try { Color.parseColor(refColorHex) } catch (e: Exception) { Color.YELLOW }
            textSize = tpl.referenceFontSize * 2f * scale * fitRatio * fitFloorBoost(tpl, scale, fitRatio)
            
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
            boxTop + padT
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
                        canvas.drawText(citation, boxLeft + padL + emblemW + emblemGap, citationY, refPaint)
                        drawEmblemAt(boxLeft + padL, Paint.Align.LEFT)
                    }
                    BroadcastTextAlignment.RIGHT -> {
                        refPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(citation, boxRight - padR - emblemW - emblemGap, citationY, refPaint)
                        drawEmblemAt(boxRight - padR, Paint.Align.RIGHT)
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
                    canvas.drawText(citation, boxLeft + padL, citationY, refPaint)
                }
                BroadcastTextAlignment.RIGHT -> {
                    refPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(citation, boxRight - padR, citationY, refPaint)
                }
            }
        }

        // v1.7: the verse block obeys the user's alignment on Full Show too.
        val translateX = if (tpl.alignment == BroadcastTextAlignment.CENTER) {
            (width - contentWidth) / 2f
        } else if (tpl.alignment == BroadcastTextAlignment.RIGHT) {
            // v2.0: the verse/English block's right edge sits at the padding
            // edge (boxRight - padR) exactly like the HTTP overlay. The
            // emblem space (emblemW + emblemGap) belongs ONLY to the citation
            // row, which lays it out separately above — it was wrongly
            // shrinking the verse block and shifting it left ~40dp at 1080p.
            boxRight - padR - contentWidth
        } else {
            boxLeft + padL
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
            // v1.9: English citation on its own line below the verse (matches HTTP).
            val secCitation = layouts.secondaryCitation
            if (secCitation != null) {
                // v1.9: gap matches the layout budget (4*scale*fitRatio).
                val citY = secY + bilingualSecondary.height + (4f * scale * fitRatio)
                canvas.save()
                canvas.translate(translateX, citY)
                secCitation.draw(canvas)
                canvas.restore()
            }
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

        // v2.0: close the verse-transition layer (see prologue) before timing.
        if (transitionLayer >= 0) {
            canvas.restoreToCount(transitionLayer)
            transitionScratch?.recycle()
        }

        recordDrawTiming(isFull, System.nanoTime() - drawStartNs)
    }

    // v1.9: frame-timing instrumentation — finds the real bottleneck (canvas draw vs
    // downscale vs NDI encode) with a 1-line log per feed every 300 frames. Keeps log
    // spam and hot-path overhead negligible; informs whether a GPU path is ever worth it.
    // v1.9: ConcurrentHashMap — the two sender threads write their own feed keys
    // concurrently; a plain mutableMapOf risks lost updates/corruption.
    private val drawTimingNs = ConcurrentHashMap<Boolean, Long>()
    private val drawTimingFrames = ConcurrentHashMap<Boolean, Int>()
    private fun recordDrawTiming(isFull: Boolean, ns: Long) {
        val frames = (drawTimingFrames[isFull] ?: 0) + 1
        drawTimingFrames[isFull] = frames
        drawTimingNs[isFull] = (drawTimingNs[isFull] ?: 0L) + ns
        if (frames >= 300) {
            val avgMs = (drawTimingNs[isFull] ?: 0L) / 1_000_000.0 / frames
            Log.d("NdiPerf", "draw ${if (isFull) "full" else "lower"}: avg ${"%.2f".format(avgMs)} ms over $frames frames")
            drawTimingFrames[isFull] = 0
            drawTimingNs[isFull] = 0L
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
        // v1.9.1: only bundled-asset typefaces are cached. A custom font can
        // be imported/deleted under the same family name at runtime, and an
        // unknown family must not cache its system fallback — either would
        // freeze the wrong typeface for the rest of the process.
        val bundled = BundledFonts.find(family)
        val result = try {
            val custom = if (bundled == null) com.arabicchristianmedia.model.CustomFonts.find(family) else null
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
            } else if (custom != null) {
                // v1.9.1: user-imported font (private storage, single weight —
                // bold/italic are synthesized by Typeface.create()).
                val file = FontStore(context).fileFor(custom)
                if (file != null) {
                    val base = Typeface.createFromFile(file)
                    if (textStyle == Typeface.NORMAL) base else Typeface.create(base, textStyle)
                } else {
                    Typeface.create(Typeface.DEFAULT, textStyle)
                }
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
        if (bundled != null) typefaceCache[cacheKey] = result
        return result
    }

    /** Cache of asset-loaded typefaces, keyed by "family|style". Thread-safe: render paths run under per-feed locks. */
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    /** @font-face rules for every bundled Arabic font, served from this tablet (fully offline). */
    private fun buildFontFaceCss(): String {
        val sb = StringBuilder(8192)
        for (f in BundledFonts.all) {
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
        // v1.9.1: user-imported fonts (single weight; the 700 rule points at
        // the same file and the browser synthesizes the extra weight).
        for (cf in com.arabicchristianmedia.model.CustomFonts.all) {
            val format = if (cf.ext == "otf") "opentype" else "truetype"
            sb.append("@font-face{font-family:'").append(cf.name)
                .append("';font-style:normal;font-weight:400;font-display:swap;src:url('/fonts/")
                .append(cf.serveName).append("') format('").append(format).append("');}")
            sb.append("@font-face{font-family:'").append(cf.name)
                .append("';font-style:normal;font-weight:700;font-display:swap;src:url('/fonts/")
                .append(cf.serveName).append("') format('").append(format).append("');}")
        }
        return sb.toString()
    }

    /** Exact allowlist of servable font files, derived from the registry (no path traversal). */
    private val fontAssetFiles: Set<String> by lazy {
        BundledFonts.all.flatMap {
            listOfNotNull(it.asset400.substringAfterLast('/'), it.asset700?.substringAfterLast('/'))
        }.toSet()
    }

    /** Serves a bundled font TTF from assets so HTTP overlays work fully offline. */
    private fun serveFontFile(out: OutputStream, requested: String) {
        val writer = PrintWriter(out)
        val name = requested.substringAfterLast('/').trim()
        // v1.9.1: user-imported fonts are served from private storage under
        // "custom-<id>.<ext>", allowlisted against the live registry.
        if (name.startsWith("custom-")) {
            val custom = com.arabicchristianmedia.model.CustomFonts.all
                .firstOrNull { it.serveName == name }
            val file = custom?.let { FontStore(context).fileFor(it) }
            if (custom == null || file == null) {
                writer.print("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n")
                writer.flush()
                return
            }
            try {
                val bytes = file.readBytes()
                writer.print("HTTP/1.1 200 OK\r\n")
                writer.print("Content-Type: font/${if (custom.ext == "otf") "otf" else "ttf"}\r\n")
                writer.print("Content-Length: ${bytes.size}\r\n")
                writer.print("Cache-Control: public, max-age=31536000, immutable\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("Connection: close\r\n\r\n")
                writer.flush()
                out.write(bytes)
                out.flush()
            } catch (e: Exception) {
                try {
                    writer.print("HTTP/1.1 500 Internal Server Error\r\nConnection: close\r\n\r\n")
                    writer.flush()
                } catch (_: Exception) { /* ignore */ }
            }
            return
        }
        if (!(name.endsWith(".ttf") || name.endsWith(".otf")) || name !in fontAssetFiles) {
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

    private fun serveTransparentPngOverlay(out: OutputStream, isFull: Boolean = false) {
        // Owned bitmap: safe to recycle (never the shared NDI cache bitmap).
        // Full renders through the show template via overrideTemplate
        // (renderFrame derives the feed from the template's isFullScreen).
        val bitmap = if (isFull) renderFrameOwned(1920, 1080, isForStream = false, overrideTemplate = currentShowTemplate)
                     else renderFrameOwned(1920, 1080, isForStream = false)
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

        val trigT0 = System.nanoTime()
        val ok = try {
            onTriggerVerse?.invoke(bookId!!, chapter!!, verseNum!!, target) ?: false
        } catch (e: Exception) { false }
        statTrigger.record((System.nanoTime() - trigT0) / 1_000_000.0)

        if (ok) {
            sendJson(out, 200, """{"ok":true,"bookId":"${jsonEscape(bookId!!)}","chapter":$chapter,"verse":$verseNum,"target":"$target"}""")
        } else {
            sendJson(out, 400, """{"ok":false,"error":"Verse not found: bookId=$bookId $chapter:$verseNum"}""")
        }
    }

    /** GET /api/books — the book list for the /remote Bible browser. */
    private fun serveApiBooks(out: OutputStream) {
        val books = try { com.arabicchristianmedia.data.BibleRepository.allBooks } catch (e: Exception) { emptyList() }
        val sb = StringBuilder()
        sb.append("{\"ok\":true,\"books\":[")
        books.forEachIndexed { i, b ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${jsonEscape(b.id)}\",\"ar\":\"${jsonEscape(b.arabicName)}\",\"en\":\"${jsonEscape(b.englishName)}\",\"chapters\":${b.totalChapters}}")
        }
        sb.append("]}")
        sendJson(out, 200, sb.toString())
    }

    /** GET /api/chapter?book=<bookId>&chapter=<n> — verses for the /remote browser. */
    private fun serveApiChapter(out: OutputStream, fullPath: String) {
        val params = parseQueryParams(fullPath)
        val bookId = params["book"]
        val chapter = params["chapter"]?.toIntOrNull()
        if (bookId == null || chapter == null) {
            sendJson(out, 400, "{\"ok\":false,\"error\":\"Provide ?book=<id>&chapter=<n>\"}")
            return
        }
        val verses = try { com.arabicchristianmedia.data.BibleRepository.getVerses(bookId, chapter) } catch (e: Exception) { emptyList() }
        val sb = StringBuilder()
        sb.append("{\"ok\":true,\"bookId\":\"${jsonEscape(bookId)}\",\"chapter\":$chapter,\"verses\":[")
        verses.forEachIndexed { i, v ->
            if (i > 0) sb.append(",")
            sb.append("{\"v\":${v.verse},\"ar\":\"${jsonEscape(v.arabicText)}\",\"en\":\"${jsonEscape(v.englishText)}\"}")
        }
        sb.append("]}")
        sendJson(out, 200, sb.toString())
    }

    /**
     * GET /api/onair?on=1|0 — absolute ON AIR control for remote surfaces.
     * Mirrors the Library ON AIR button: off blanks the outputs but keeps
     * the verse; on brings the same verse back.
     */
    private fun serveApiOnAir(out: OutputStream, fullPath: String) {
        val params = parseQueryParams(fullPath)
        val on = params["on"] == "1" || params["on"] == "true"
        try { onSetOnAir?.invoke(on) } catch (e: Exception) { }
        sendJson(out, 200, "{\"ok\":true,\"onAir\":$on}")
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

    /** v1.9.1: the values behind the BibleShow feeds, computed once and
     * shared by the XML writer (vMix) and the JSON writer below. */
    private data class BibleShowData(
        val dotNetTicks: Long,
        val scripture: String,
        val verseTextOnly: String,
        val arabicVerse: String,
        val arabicCitation: String,
        val englishVerse: String,
        val englishCitation: String,
        val bookName: String,
        val chapterNumber: String,
        val verseNumber: String,
        val chapterVerse: String,
        val bibleLanguage: String,
        val chapter: Int,
        val verse: Int,
        /** v1.9.1: per-slot version identity ("" when that slot is not shown). */
        val arabicVersion: String,
        val englishVersion: String,
        /** v1.9.1: human-readable combined version line for the XML feed. */
        val bibleVersion: String
    )

    private fun computeBibleShowData(tpl: LowerThirdTemplate): BibleShowData {
        val verse = currentVerse

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
        // v1.9.1: each slot declares its own version, so a consumer never has
        // to guess which text it received (Ashraf). Arabic is always the
        // Smith–Van Dyck (SVD); English follows the user's live selection.
        val englishCode = BibleRepository.getEnglishVersion().uppercase()
        val englishFull = when (englishCode) {
            "ASV" -> "American Standard Version (ASV)"
            "KJV" -> "King James Version (KJV)"
            "WEB" -> "World English Bible (WEB)"
            else -> englishCode
        }
        val arabicVersion = if (showArabic) "SVD" else ""
        val englishVersion = if (showEnglish) englishCode else ""
        val bibleVersion = when {
            showArabic && showEnglish -> "Smith–Van Dyck (SVD) + $englishFull"
            showArabic -> "Smith–Van Dyck (SVD)"
            showEnglish -> englishFull
            else -> ""
        }
        return BibleShowData(
            dotNetTicks = dotNetTicks,
            scripture = scripture,
            verseTextOnly = verseTextOnly,
            arabicVerse = arabicVerse,
            arabicCitation = arabicCitation,
            englishVerse = englishVerse,
            englishCitation = englishCitation,
            bookName = bookName,
            chapterNumber = chapterNum,
            verseNumber = verseNum,
            chapterVerse = chapterVerse,
            bibleLanguage = bibleLanguage,
            chapter = verse?.chapter ?: 0,
            verse = verse?.verse ?: 0,
            arabicVersion = arabicVersion,
            englishVersion = englishVersion,
            bibleVersion = bibleVersion
        )
    }

    /**
     * GET /bibleshow.xml — vMix BibleShow-compatible text feed (v1.8).
     * Text/data only; vMix controls all formatting. TimeCode is .NET ticks.
     */
    private fun serveBibleShowXml(out: OutputStream, tpl: LowerThirdTemplate) {
        val d = computeBibleShowData(tpl)

        fun xmlEscape(s: String): String {
            return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        val xml = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("\n<BibleShowData>\n")
            append("  <TimeCode>${d.dotNetTicks}</TimeCode>\n")
            append("  <Reference />\n")
            append("  <Scripture>${xmlEscape(d.scripture)}</Scripture>\n")
            append("  <ImagePath />\n")
            append("  <BibleVersion>${xmlEscape(d.bibleVersion)}</BibleVersion>\n")
            append("  <BibleCopyright></BibleCopyright>\n")
            append("  <BibleLanguage>${d.bibleLanguage}</BibleLanguage>\n")
            append("  <BookName>${xmlEscape(d.bookName)}</BookName>\n")
            append("  <BookTitle>${xmlEscape(d.bookName)}</BookTitle>\n")
            append("  <BookAbbreviation></BookAbbreviation>\n")
            append("  <ChapterNumber>${xmlEscape(d.chapterNumber)}</ChapterNumber>\n")
            append("  <VerseNumber>${xmlEscape(d.verseNumber)}</VerseNumber>\n")
            append("  <ChapterVerse>${xmlEscape(d.chapterVerse)}</ChapterVerse>\n")
            append("  <VerseText>${xmlEscape(d.verseTextOnly)}</VerseText>\n")
            // v1.9.1: the two languages as their OWN elements (the combined
            // Scripture/VerseText above stay for BibleShow compatibility),
            // each with its version.
            append("  <ArabicVerse>${xmlEscape(d.arabicVerse)}</ArabicVerse>\n")
            append("  <ArabicCitation>${xmlEscape(d.arabicCitation)}</ArabicCitation>\n")
            append("  <EnglishVerse>${xmlEscape(d.englishVerse)}</EnglishVerse>\n")
            append("  <EnglishCitation>${xmlEscape(d.englishCitation)}</EnglishCitation>\n")
            append("  <ArabicVersion>${xmlEscape(d.arabicVersion)}</ArabicVersion>\n")
            append("  <EnglishVersion>${xmlEscape(d.englishVersion)}</EnglishVersion>\n")
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
     * GET /lower|/full/bibleshow.json — v1.9.1: JSON mirror of the XML
     * feed (same values, computed by [computeBibleShowData]) for modern
     * titlers and cloud graphics that prefer JSON, plus the two fields
     * polling clients need: onAir and stateVersion.
     */
    private fun serveBibleShowJson(out: OutputStream, tpl: LowerThirdTemplate) {
        val d = computeBibleShowData(tpl)
        val root = JSONObject().apply {
            put("timeCode", d.dotNetTicks)
            put("scripture", d.scripture)
            put("verseText", d.verseTextOnly)
            put("arabicVerse", d.arabicVerse)
            put("arabicCitation", d.arabicCitation)
            put("englishVerse", d.englishVerse)
            put("englishCitation", d.englishCitation)
            put("arabicVersion", d.arabicVersion)
            put("englishVersion", d.englishVersion)
            put("bookName", d.bookName)
            put("chapterNumber", d.chapterNumber)
            put("verseNumber", d.verseNumber)
            put("chapterVerse", d.chapterVerse)
            put("chapter", d.chapter)
            put("verse", d.verse)
            put("bibleLanguage", d.bibleLanguage)
            put("languageMode", tpl.languageMode.name)
            put("onAir", isLive && currentVerse != null)
            put("stateVersion", frameVersion)
        }
        sendJson(out, 200, root.toString())
    }

    /**
     * GET /api/clientstat?page=lower|full&data=<urlencoded JSON> — the
     * overlay pages report their own receive/apply timings here (see the
     * self-diagnostics block in the overlay script). Stored raw (after a
     * JSON validity check) and surfaced by /api/debug.
     */
    private fun serveApiClientStat(out: OutputStream, fullPath: String) {
        val params = parseQueryParams(fullPath)
        val page = params["page"]
        val data = params["data"]
        var stored = false
        if ((page == "lower" || page == "full") && data != null && data.length <= 16384) {
            try {
                // Validate before storing: /api/debug embeds this verbatim.
                val checked = org.json.JSONObject(data).toString()
                if (page == "lower") {
                    clientStatLower = checked; clientStatLowerAtMs = System.currentTimeMillis()
                } else {
                    clientStatFull = checked; clientStatFullAtMs = System.currentTimeMillis()
                }
                stored = true
            } catch (e: Exception) { /* malformed report: drop it */ }
        }
        sendJson(out, 200, "{\"ok\":$stored}")
    }

    /**
     * GET /api/debug — full diagnostics snapshot (config, device, live
     * state, per-action server timings) for the /debug page. Built for
     * advanced troubleshooting of the HTTP path.
     */
    private fun serveApiDebug(out: OutputStream) {
        val pi = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
        val versionName = pi?.versionName ?: "?"
        val versionCode = try { pi?.longVersionCode ?: -1L } catch (e: Exception) { -1L }
        val deviceName = try { android.provider.Settings.Global.getString(context.contentResolver, "device_name") ?: "" } catch (e: Exception) { "" }
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576
        val maxMb = rt.maxMemory() / 1048576
        val verse = currentVerse
        val bookNum = if (verse != null) {
            try { com.arabicchristianmedia.data.BibleRepository.allBooks.indexOfFirst { it.id == verse.bookId } + 1 } catch (e: Exception) { 0 }
        } else 0
        fun fmt(x: Double): String = String.format(java.util.Locale.US, "%.2f", x)
        fun statJson(st: ActionStat): String =
            "{\"count\":${st.count},\"lastMs\":${fmt(st.lastMs)},\"avgMs\":${fmt(st.avgMs())},\"maxMs\":${fmt(st.maxMs)}}"
        fun tplJson(t: LowerThirdTemplate): String {
            // v1.9.1: FULL template dump (was a 14-key summary) so a value
            // change anywhere in the Editor can be confirmed here.
            val o = JSONObject()
            o.put("name", t.name)
            o.put("style", t.style.name)
            o.put("bgColorHex", t.bgColorHex)
            o.put("bgOpacity", t.bgOpacity.toDouble())
            o.put("accentColorHex", t.accentColorHex)
            o.put("textColorHex", t.textColorHex)
            o.put("referenceColorHex", t.referenceColorHex)
            o.put("verseFontSize", t.verseFontSize)
            o.put("referenceFontSize", t.referenceFontSize)
            o.put("fontFamily", t.fontFamily)
            o.put("secondaryTextColorHex", t.secondaryTextColorHex)
            o.put("secondaryReferenceColorHex", t.secondaryReferenceColorHex)
            o.put("secondaryVerseFontSize", t.secondaryVerseFontSize)
            o.put("secondaryReferenceFontSize", t.secondaryReferenceFontSize)
            o.put("secondaryFontFamily", t.secondaryFontFamily)
            o.put("isFullScreen", t.isFullScreen)
            o.put("bilingualSpacing", t.bilingualSpacing)
            o.put("showBilingualSpacing", t.showBilingualSpacing)
            o.put("verseIsBold", t.verseIsBold)
            o.put("verseIsItalic", t.verseIsItalic)
            o.put("referenceIsBold", t.referenceIsBold)
            o.put("referenceIsItalic", t.referenceIsItalic)
            o.put("secondaryVerseIsBold", t.secondaryVerseIsBold)
            o.put("secondaryVerseIsItalic", t.secondaryVerseIsItalic)
            o.put("secondaryReferenceIsBold", t.secondaryReferenceIsBold)
            o.put("secondaryReferenceIsItalic", t.secondaryReferenceIsItalic)
            o.put("useEasternArabicNumerals", t.useEasternArabicNumerals)
            o.put("useArabicPunctuation", t.useArabicPunctuation)
            o.put("alignment", t.alignment.name)
            o.put("positionBottomPercent", t.positionBottomPercent)
            o.put("horizontalMarginPercent", t.horizontalMarginPercent)
            o.put("cornerRadiusDp", t.cornerRadiusDp)
            o.put("transition", t.transition.name)
            o.put("transitionDurationMs", t.transitionDurationMs)
            o.put("showAccentBorder", t.showAccentBorder)
            o.put("textShadowEnabled", t.textShadowEnabled)
            o.put("textShadowColorHex", t.textShadowColorHex)
            o.put("textShadowBlurDp", t.textShadowBlurDp.toDouble())
            o.put("textShadowOffsetDp", t.textShadowOffsetDp.toDouble())
            o.put("textShadowAngleDeg", t.textShadowAngleDeg)
            o.put("cardGlowEnabled", t.cardGlowEnabled)
            o.put("cardGlowColorHex", t.cardGlowColorHex)
            o.put("emblem", t.emblem)
            o.put("languageMode", t.languageMode.name)
            o.put("pureTransparent", t.isPureTransparentBackground)
            o.put("animatedBackground", t.animatedBackground.id)
            o.put("animatedBackgroundOpacity", t.animatedBackgroundOpacity.toDouble())
            o.put("customVideoUrl", t.customVideoUrl)
            o.put("customVideoId", t.customVideoId)
            o.put("customVideoMuted", t.customVideoMuted)
            o.put("motionSpeed", t.motionSpeed.toDouble())
            o.put("videoLoopMode", t.videoLoopMode.id)
            o.put("streamBackgroundMode", t.streamBackgroundMode.name)
            o.put("cardPaddingTopDp", t.cardPaddingTopDp.toDouble())
            o.put("cardPaddingBottomDp", t.cardPaddingBottomDp.toDouble())
            o.put("cardPaddingLeftDp", t.cardPaddingLeftDp.toDouble())
            o.put("cardPaddingRightDp", t.cardPaddingRightDp.toDouble())
            o.put("autoFitText", t.autoFitText)
            o.put("autoFitMaxVerseSp", t.autoFitMaxVerseSp)
            o.put("boxHeightPercent", t.boxHeightPercent)
            return o.toString()
        }
        val up = if (serverStartTimeMs == 0L) 0 else System.currentTimeMillis() - serverStartTimeMs
        val sb = StringBuilder()
        sb.append("{\"ok\":true,")
        sb.append("\"app\":{\"versionName\":\"${jsonEscape(versionName)}\",\"versionCode\":$versionCode},")
        sb.append("\"device\":{\"manufacturer\":\"${jsonEscape(android.os.Build.MANUFACTURER)}\",\"model\":\"${jsonEscape(android.os.Build.MODEL)}\",\"deviceName\":\"${jsonEscape(deviceName)}\",\"android\":\"${android.os.Build.VERSION.RELEASE}\",\"sdk\":${android.os.Build.VERSION.SDK_INT}},")
        sb.append("\"server\":{\"running\":$isRunning,\"port\":$port,\"ip\":\"${getLocalIpAddress()}\",\"uptimeMs\":$up,\"frameVersion\":$frameVersion,\"isLive\":$isLive,\"sseClients\":${sseClients.size},\"broadcastDispatcher\":\"Dispatchers.IO (Safe)\"},")
        sb.append("\"current\":{\"bookId\":\"${verse?.bookId ?: ""}\",\"bookNum\":$bookNum,\"chapter\":${verse?.chapter ?: 0},\"verse\":${verse?.verse ?: 0},\"citation\":\"${jsonEscape(verse?.getFormattedEnglishCitation() ?: "")}\"},")
        sb.append("\"lower\":${tplJson(currentTemplate)},")
        sb.append("\"full\":${tplJson(currentShowTemplate)},")
        sb.append("\"timings\":{\"stateBuild\":${statJson(statStateBuild)},\"broadcast\":${statJson(statBroadcast)},\"verseApi\":${statJson(statVerseApi)},\"trigger\":${statJson(statTrigger)}},")
        sb.append("\"overlayClients\":{")
        sb.append("\"lower\":${clientStatLower ?: "null"},\"lowerAgeMs\":${if (clientStatLowerAtMs == 0L) -1 else System.currentTimeMillis() - clientStatLowerAtMs},")
        sb.append("\"full\":${clientStatFull ?: "null"},\"fullAgeMs\":${if (clientStatFullAtMs == 0L) -1 else System.currentTimeMillis() - clientStatFullAtMs}")
        sb.append("},")
        sb.append("\"sseTotals\":{\"connects\":$sseConnectTotal,\"drops\":$sseDropTotal,\"reasons\":{")
        var firstReason = true
        for ((reason, count) in sseDropReasonCounts()) {
            if (!firstReason) sb.append(",")
            firstReason = false
            sb.append("\"${jsonEscape(reason)}\":$count")
        }
        sb.append("}},")
        sb.append("\"sseJournal\":[")
        val journal = sseJournalSnapshot()
        for (i in journal.indices) {
            if (i > 0) sb.append(",")
            sb.append("\"${jsonEscape(journal[i])}\"")
        }
        sb.append("],")
        sb.append("\"memory\":{\"usedMb\":$usedMb,\"maxMb\":$maxMb}")
        sb.append("}")
        sendJson(out, 200, sb.toString())
    }

/**
     * GET /debug — HTTP diagnostics page (v1.9.1): full configuration,
     * device info, server-side per-action timings, and browser-side
     * latency tests (ping RTT + tap-to-screen over SSE), with a
     * copy-able report for advanced troubleshooting.
     */
    private fun serveDebugPage(out: OutputStream) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>Bible NDI — HTTP Debug</title>
<style>
  body { font-family: system-ui, sans-serif; margin: 0; padding: 16px; background: Canvas; color: CanvasText; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  h2 { font-size: 15px; margin: 20px 0 6px; }
  .sub { opacity: 0.7; font-size: 13px; }
  table { border-collapse: collapse; width: 100%; max-width: 720px; }
  td, th { border: 1px solid rgba(128,128,128,0.35); padding: 5px 8px; font-size: 13px; text-align: left; vertical-align: top; }
  td.k { font-weight: 600; white-space: nowrap; width: 190px; }
  button { padding: 10px 14px; font-size: 14px; font-weight: 700; border-radius: 10px; border: 1px solid rgba(128,128,128,0.5); background: transparent; color: inherit; cursor: pointer; margin: 4px 4px 4px 0; }
  button:active { transform: scale(0.97); }
  .row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
  pre, textarea { font-family: ui-monospace, monospace; font-size: 12px; }
  textarea { width: 100%; max-width: 720px; height: 180px; }
  .out { white-space: pre-wrap; font-family: ui-monospace, monospace; font-size: 12.5px; background: rgba(128,128,128,0.12); border-radius: 10px; padding: 10px; max-width: 720px; min-height: 20px; }
</style>
</head>
<body>
<h1>🛠 Bible NDI — HTTP Debug</h1>
<div class="sub">Live diagnostics from the tablet. Timings are measured where they happen: server actions on the tablet, network + display in this browser.</div>
<div class="row">
  <button onclick="loadDebug()">↻ Refresh</button>
  <label><input type="checkbox" id="autoChk"> Auto-refresh (3s)</label>
  <button onclick="copyReport()">📋 Copy report</button>
</div>

<h2>Server &amp; device</h2>
<div id="serverSec">Loading…</div>

<h2>Active templates</h2>
<div id="tplSec">—</div>

<h2>Server action timings (ms)</h2>
<div id="timeSec">—</div>

<h2>Overlay pages — self-reported (browser side)</h2>
<div id="clientStatSec">—</div>

<h2>SSE connections — tablet's journal</h2>
<div class="out" id="sseSec">—</div>

<h2>This browser</h2>
<div id="clientSec">—</div>

<h2>Latency tests</h2>
<div class="row">
  <button onclick="pingTest()">Run ping test (10 × /api/verse)</button>
  <button onclick="tapTest()">Run tap-to-screen test (SSE)</button>
</div>
<div class="sub">Tap-to-screen re-airs the current verse and measures trigger → SSE arrival in this browser, on one clock. It changes what is on air.</div>
<div class="out" id="testOut">No tests run yet.</div>

<h2>Report</h2>
<textarea id="report" readonly></textarea>

<script>
  var lastDebug = null;
  var testLog = '';
  function esc(t) { return String(t == null ? '' : t).replace(/&/g, '&amp;').replace(/</g, '&lt;'); }
  function table(pairs) {
    var h = '<table>';
    for (var i = 0; i < pairs.length; i++) h += '<tr><td class="k">' + esc(pairs[i][0]) + '</td><td>' + esc(pairs[i][1]) + '</td></tr>';
    return h + '</table>';
  }
  function loadDebug() {
    fetch('/api/debug').then(function(r) { return r.json(); }).then(function(j) {
      lastDebug = j;
      var up = Math.round((j.server.uptimeMs || 0) / 1000);
      document.getElementById('serverSec').innerHTML = table([
        ['App', j.app.versionName + ' (' + j.app.versionCode + ')'],
        ['Device', j.device.manufacturer + ' ' + j.device.model],
        ['Device name', j.device.deviceName],
        ['Android', j.device.android + ' (SDK ' + j.device.sdk + ')'],
        ['Server', (j.server.running ? 'running' : 'STOPPED') + ' · ' + j.server.ip + ':' + j.server.port],
        ['Uptime', up + ' s'],
        ['State version', j.server.frameVersion],
        ['On air', j.server.isLive],
        ['SSE clients (overlays)', j.server.sseClients],
        ['Broadcast thread', j.server.broadcastDispatcher || 'Dispatchers.IO (Safe)'],
        ['Current verse', (j.current.citation || '—') + ' [book ' + j.current.bookNum + ' ' + j.current.chapter + ':' + j.current.verse + ']'],
        ['App memory', j.memory.usedMb + ' MB used / ' + j.memory.maxMb + ' MB max']
      ]);
      function tplPairs(t) {
        var transHint = t.transitionDurationMs > 300 ? ' (💡 Note: ' + t.transitionDurationMs + 'ms transition duration contributes to visible transition time; set to CUT in Editor for instant changes)' : '';
        return [
          ['Name / style', t.name + ' / ' + t.style],
          ['Animated background', t.animatedBackground + ' @ ' + t.animatedBackgroundOpacity],
          ['Background opacity', t.bgOpacity],
          ['100% transparent', t.pureTransparent],
          ['Transition', t.transition + ' · ' + t.transitionDurationMs + ' ms' + transHint],
          ['Verse / citation size', t.verseFontSize + ' / ' + t.referenceFontSize],
          ['Auto-fit', t.autoFitText + ' · max verse ' + t.autoFitMaxVerseSp + ' · box H ' + t.boxHeightPercent + '%'],
          ['Language / font', t.languageMode + ' / ' + t.fontFamily]
        ];
      }
      document.getElementById('tplSec').innerHTML =
        '<div class="sub">LOWER</div>' + table(tplPairs(j.lower)) +
        '<div class="sub" style="margin-top:8px">FULL</div>' + table(tplPairs(j.full));
      function statRow(label, st) { return [label, 'count ' + st.count + ' · last ' + st.lastMs + ' · avg ' + st.avgMs + ' · max ' + st.maxMs]; }
      document.getElementById('timeSec').innerHTML = table([
        statRow('State JSON build', j.timings.stateBuild),
        statRow('Broadcast to SSE clients', j.timings.broadcast),
        statRow('/api/verse answer', j.timings.verseApi),
        statRow('/api/trigger handling', j.timings.trigger)
      ]);
      var oc = j.overlayClients || {};
      function ocText(name, stat, ageMs) {
        if (!stat) return name + ': no report yet (open the overlay page, wait up to 10 s)';
        var ev = (stat.events || []).map(function(e) { return e.t + 'ms ' + e.kind + ' ' + e.info; }).join(' | ');
        return name + ' · report age ' + Math.round(ageMs / 1000) + ' s · state v' + stat.version +
          ' · msgs ' + stat.msgs + ' · applies ' + stat.applies + ' · reconnects ' + stat.reconnects +
          ' · last apply ' + stat.lastApplyMs + ' ms · max apply ' + stat.maxApplyMs + ' ms · last fit ' + stat.lastFitMs + ' ms' +
          '\n  events: ' + ev +
          '\n  ua: ' + stat.ua;
      }
      document.getElementById('clientStatSec').innerHTML =
        '<div class="out">' + esc(ocText('LOWER', oc.lower, oc.lowerAgeMs || 0)) + '</div>' +
        '<div class="out" style="margin-top:6px">' + esc(ocText('FULL', oc.full, oc.fullAgeMs || 0)) + '</div>';
      var st = j.sseTotals || { connects: 0, drops: 0, reasons: {} };
      var reasons = Object.keys(st.reasons || {}).map(function(k) { return k + '=' + st.reasons[k]; }).join(', ') || 'none';
      var lines = (j.sseJournal || []).slice(-14);
      document.getElementById('sseSec').textContent =
        'Session totals · connects ' + st.connects + ' · drops ' + st.drops + ' · drop reasons: ' + reasons +
        '\n' + (lines.length ? lines.join('\n') : '(no connections recorded yet)');
      renderClient();
      buildReport();
    }).catch(function(e) {
      document.getElementById('serverSec').textContent = 'Failed to load /api/debug: ' + e;
    });
  }
  function renderClient() {
    var c = (navigator.connection || {});
    document.getElementById('clientSec').innerHTML = table([
      ['User agent', navigator.userAgent],
      ['Screen', window.screen.width + ' × ' + window.screen.height + ' · DPR ' + window.devicePixelRatio],
      ['Window', window.innerWidth + ' × ' + window.innerHeight],
      ['Connection', (c.effectiveType || '?') + ' · downlink ' + (c.downlink || '?') + ' Mb/s · rtt ' + (c.rtt || '?') + ' ms'],
      ['Page loaded in', Math.round(performance.now()) + ' ms']
    ]);
  }
  function logTest(line) {
    testLog += line + '\n';
    document.getElementById('testOut').textContent = testLog;
    buildReport();
  }
  function pingTest() {
    logTest('— Ping test —');
    var times = [];
    var offsetShown = false;
    var chain = Promise.resolve();
    function one(i) {
      return function() {
        var t0 = performance.now();
        return fetch('/api/verse').then(function(r) { return r.json(); }).then(function(j) {
          var t1 = performance.now();
          var ms = t1 - t0;
          times.push(ms);
          if (!offsetShown && j.serverTimeMs) {
            offsetShown = true;
            var offset = Math.round(j.serverTimeMs - (Date.now() - ms / 2));
            logTest('Server clock minus browser clock ≈ ' + offset + ' ms (for comparing timestamps)');
          }
          logTest('ping ' + (i + 1) + ': ' + ms.toFixed(1) + ' ms');
        }).catch(function(e) { logTest('ping ' + (i + 1) + ': ERROR ' + e); });
      };
    }
    for (var i = 0; i < 10; i++) chain = chain.then(one(i));
    chain.then(function() {
      if (times.length) {
        var sum = 0, mx = 0;
        for (var k = 0; k < times.length; k++) { sum += times[k]; if (times[k] > mx) mx = times[k]; }
        logTest('Ping avg ' + (sum / times.length).toFixed(1) + ' ms · max ' + mx.toFixed(1) + ' ms');
      }
    });
  }
  function tapTest() {
    if (!lastDebug || !lastDebug.current.bookNum) { logTest('Tap test: no current verse to re-air.'); return; }
    var cur = lastDebug.current;
    logTest('— Tap-to-screen test —');
    fetch('/api/verse').then(function(r) { return r.json(); }).then(function(j) {
      var v0 = j.stateVersion;
      var es = new EventSource('/events');
      var done = false;
      var t0 = 0;
      var to = setTimeout(function() {
        if (!done) { done = true; es.close(); logTest('Tap test: NO event within 10 s — SSE delivery is stuck.'); }
      }, 10000);
      es.onmessage = function(e) {
        try {
          var d = JSON.parse(e.data);
          if (!done && t0 && d.stateVersion > v0) {
            done = true;
            clearTimeout(to);
            es.close();
            logTest('Tap → screen event: ' + (performance.now() - t0).toFixed(1) + ' ms (state ' + v0 + ' → ' + d.stateVersion + ')');
          }
        } catch (err) {}
      };
      es.onopen = function() {
        t0 = performance.now();
        fetch('/api/trigger?book=' + cur.bookNum + '&chapter=' + cur.chapter + '&verse=' + cur.verse)
          .then(function(r) { return r.json(); })
          .then(function(j) { if (!j.ok) logTest('Tap test: trigger failed: ' + (j.error || '')); })
          .catch(function(e) { logTest('Tap test: trigger error ' + e); });
      };
    }).catch(function(e) { logTest('Tap test: setup error ' + e); });
  }
  function buildReport() {
    if (!lastDebug) return;
    var txt = 'BIBLE NDI HTTP DEBUG REPORT\n' + JSON.stringify(lastDebug, null, 2) +
      '\n\nBROWSER: ' + navigator.userAgent +
      '\nSCREEN: ' + window.screen.width + 'x' + window.screen.height + ' DPR ' + window.devicePixelRatio +
      '\n\nTESTS:\n' + (testLog || '(none run)');
    document.getElementById('report').value = txt;
  }
  function copyReport() {
    var ta = document.getElementById('report');
    ta.select();
    try { document.execCommand('copy'); } catch (e) {}
    if (navigator.clipboard) { try { navigator.clipboard.writeText(ta.value); } catch (e) {} }
  }
  document.getElementById('autoChk').addEventListener('change', function() {
    if (this.checked) { window._dbgTimer = setInterval(loadDebug, 3000); }
    else if (window._dbgTimer) { clearInterval(window._dbgTimer); window._dbgTimer = null; }
  });
  loadDebug();
</script>
</body>
</html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(out)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: " + bytes.size + "\r\n")
        writer.print("Cache-Control: no-cache\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }


    /**
     * GET /remote — the web REMOTE DEVICE (v1.9.1, reworked from the v1.8
     * reference-typer). A Bible browser (book → chapter → tap a verse) plus
     * an ON AIR button mirroring the master, exactly like a native remote:
     * it never chooses outputs — the master decides what shows. Fully
     * offline, follows the device's light/dark theme.
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
    input, select { background: #f8fafc; border-color: #cbd5e1; color: #0f172a; }
    .hint { color: #64748b; }
    .chips button { border-color: #cbd5e1; color: #334155; }
    .chips button.active { background: #0f172a; color: #fff; border-color: transparent; }
    .vrow { border-color: #e2e8f0; }
  }
  @media (prefers-color-scheme: dark) {
    body { background: #0f172a; color: #f1f5f9; }
    .card { background: #1e293b; border-color: #334155; }
    input, select { background: #0f172a; border-color: #475569; color: #f1f5f9; }
    .hint { color: #94a3b8; }
    .chips button { border-color: #475569; color: #cbd5e1; }
    .chips button.active { background: #f1f5f9; color: #0f172a; border-color: transparent; }
    .vrow { border-color: #334155; }
    .livepill { color: #f1f5f9; }
    .livepill.on { background: rgba(34,197,94,0.22); border-color: rgba(34,197,94,0.7); color: #bbf7d0; }
  }
  header { padding: 16px 20px 8px; font-size: 20px; font-weight: 700; display: flex; align-items: flex-start; justify-content: space-between; gap: 10px;  flex-wrap: wrap; }
  /* The live reference rides in the header as a slim pill \u2014 information
     only, zero extra layout space. */
  .livepill { display: inline-flex; align-items: center; gap: 8px; max-width: 55%; flex: none;
    direction: rtl; position: relative; justify-content: center;
    background: rgba(127,127,127,0.16); border: 1px solid rgba(127,127,127,0.45); border-radius: 999px;
    padding: 7px 30px; font-size: 13px; font-weight: 600; margin-top: 2px; color: #111827; }
  /* v1.9.1: ON AIR tints the WHOLE pill, not just the dot, and the citation
     gets an explicit high-contrast color (was: inherited page color washed
     out on the pill's gray tint) + more breathing room around the text. */
  .livepill.on { background: rgba(34,197,94,0.18); border-color: rgba(34,197,94,0.65); color: #14532d; }
  /* Round 2 (#8): the status dot is pinned to the pill's right edge and
     the citation centers in the space left over - a long citation can
     never slide under the dot. */
  .livepill .dot { width: 8px; height: 8px; border-radius: 50%; background: #9ca3af; flex: none;
    position: absolute; right: 11px; top: 50%; transform: translateY(-50%); }
  .livepill.on .dot { background: #22c55e; box-shadow: 0 0 6px #22c55e; }
  .livepill .ref { min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; unicode-bidi: plaintext; flex: 1; text-align: center; }
  @media (max-width: 480px) { .livepill { max-width: 100%; } }
  header .sub { font-size: 13px; font-weight: 400; opacity: 0.7; margin-top: 2px; }
  main { flex: 1; padding: 8px 16px 16px; display: flex; flex-direction: column; gap: 12px; max-width: 640px; width: 100%; margin: 0 auto; }
  .card { border: 1px solid; border-radius: 16px; padding: 16px; }
  label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 8px; opacity: 0.8; }
  input, select {
    width: 100%; font-size: 18px; padding: 13px 14px;
    border: 1px solid; border-radius: 12px; outline: none;
  }
  input:focus, select:focus { border-color: #3b82f6; box-shadow: 0 0 0 3px rgba(59,130,246,0.25); }
  .btn {
    width: 100%; padding: 16px; font-size: 19px; font-weight: 700;
    border: none; border-radius: 16px; cursor: pointer;
    background: #16a34a; color: #fff; min-height: 60px;
  }
  .btn:active { transform: scale(0.98); opacity: 0.9; }
  .onair {
    width: 100%; padding: 16px; font-size: 18px; font-weight: 800;
    border: none; border-radius: 16px; cursor: pointer; min-height: 60px;
    letter-spacing: 0.4px;
  }
  .onair.on { background: #16a34a; color: #fff; }
  .onair.off { background: #dc2626; color: #fff; }
  .onair:active { transform: scale(0.98); opacity: 0.9; }
  .status { text-align: center; font-size: 14px; min-height: 22px; font-weight: 600; }
  .status.ok { color: #16a34a; }
  .status.err { color: #dc2626; }
  .hint { font-size: 12px; margin-top: 8px; line-height: 1.5; }
  .chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
  .chips button {
    width: 46px; padding: 9px 0; font-size: 15px; font-weight: 600;
    border: 1px solid; border-radius: 10px; background: transparent; cursor: pointer;
  }
  .selrow { display: flex; gap: 8px; }
  .selrow #bookSel { flex: 2; }
  .selrow #chSel { flex: 1; }
  .selrow #langSel { flex: 1; }
  .vlist { margin-top: 8px; max-height: 52vh; overflow-y: auto; }
  /* Desktop / wide tablet: two columns — controls left, Bible browser
     right with a tall verse list. Narrow screens keep the stacked
     one-hand layout above. */
  @media (min-width: 900px) {
    main {
      max-width: 1180px;
      display: grid;
      grid-template-columns: 340px 1fr;
      grid-template-areas: "onair browse" "ref browse" "go browse" "status browse";
      align-items: start;
    }
    #onAirBtn { grid-area: onair; }
    #browseCard { grid-area: browse; }
    #refCard { grid-area: ref; }
    #goBtn { grid-area: go; }
    .status { grid-area: status; }
    .vlist { max-height: 66vh; }
  }
  .vrow { display: flex; gap: 10px; padding: 10px 8px; border-bottom: 1px solid; cursor: pointer; align-items: flex-start; }
  .vrow:active { opacity: 0.7; }
  .vrow.live { background: rgba(22,163,74,0.18); border-radius: 10px; }
  .vn { font-weight: 800; min-width: 30px; text-align: center; opacity: 0.65; padding-top: 2px; }
  .va { font-size: 17px; line-height: 1.8; }
  .ve { font-size: 13px; opacity: 0.75; margin-top: 2px; line-height: 1.5; }
</style>
</head>
<body>
<header>
  <div>📖 Bible Remote
    <div class="sub">Arabic Bible NDI — remote device: tap a verse to show it</div>
  </div>
  <span class="livepill" id="livePill"><span class="dot"></span><span class="ref" id="liveRef" dir="auto">—</span></span>
</header>
<main>
  <button class="onair off" id="onAirBtn" onclick="toggleOnAir()">OFF AIR</button>
  <div class="card" id="browseCard">
    <label for="bookSel">Browse the Bible — اختر سفراً</label>
    <div class="selrow">
      <select id="bookSel"><option value="">Loading books…</option></select>
      <select id="chSel" aria-label="Chapter"></select>
      <select id="langSel" aria-label="Version"><option value="ar">العربية</option><option value="en">English</option></select>
    </div>
    <div class="vlist" id="vList"></div>
  </div>
  <div class="card" id="refCard">
    <label for="ref">Quick reference</label>
    <input id="ref" type="text" inputmode="text" autocomplete="off"
           placeholder="John 3:16  •  يوحنا 3:16  •  Jn 3:16"
           enterkeyhint="go">
    <div class="hint">Or type a reference — English, Arabic, or abbreviation. Examples: <b>Rom 8:28</b>, <b>مزمور 23:1</b></div>
  </div>
  <button class="btn" id="goBtn" onclick="triggerRef()">▶ Show Verse</button>
  <div class="status" id="status"></div>
</main>
<script>
  var books = [];
  var curBookIdx = -1;
  var curChapter = 0;
  var live = { onAir: false, bookId: '', chapter: 0, verse: 0 };

  var bookSel = document.getElementById('bookSel');
  var chSel = document.getElementById('chSel');
  var langSel = document.getElementById('langSel');
  // Round 2 (#9): the browser shows ONE version - Arabic by default,
  // English on request - for the book list AND the verse rows. The old
  // composite "Arabic - English" labels also bidi-split numbered books
  // ("1 Samuel"); single-language labels cannot.
  var browseLang = 'ar';
  try { browseLang = localStorage.getItem('ndiBrowseLang') || 'ar'; } catch (e) {}
  if (browseLang !== 'en') browseLang = 'ar';
  langSel.value = browseLang;
  var vList = document.getElementById('vList');
  var refInput = document.getElementById('ref');
  refInput.addEventListener('keydown', function(e) { if (e.key === 'Enter') triggerRef(); });
  refInput.addEventListener('focus', function() { refInput.select(); });
  langSel.addEventListener('change', function() {
    browseLang = langSel.value === 'en' ? 'en' : 'ar';
    try { localStorage.setItem('ndiBrowseLang', browseLang); } catch (e) {}
    fillBooks();
    if (curBookIdx >= 0 && curChapter > 0) loadChapter(curChapter);
  });
  bookSel.addEventListener('change', function() {
    var i = parseInt(bookSel.value, 10);
    if (isNaN(i) || !books[i]) return;
    curBookIdx = i;
    fillChapters(books[i]);
    loadChapter(1);
  });
  chSel.addEventListener('change', function() {
    var c = parseInt(chSel.value, 10);
    if (!isNaN(c)) loadChapter(c);
  });

  function setStatus(msg, cls) {
    var el = document.getElementById('status');
    el.textContent = msg;
    el.className = 'status ' + (cls || '');
  }

  function fillBooks() {
    var html = '';
    for (var i = 0; i < books.length; i++) {
      html += '<option value="' + i + '">' + escapeHtml(browseLang === 'en' ? books[i].en : books[i].ar) + '</option>';
    }
    bookSel.innerHTML = html;
    bookSel.dir = browseLang === 'en' ? 'ltr' : 'rtl';
    if (curBookIdx >= 0 && books[curBookIdx]) bookSel.value = String(curBookIdx);
  }

  function loadBooks() {
    fetch('/api/books').then(function(r) { return r.json(); }).then(function(j) {
      if (!j.ok) { setStatus('Could not load books', 'err'); return; }
      books = j.books;
      fillBooks();
      if (books.length) {
        curBookIdx = 0;
        bookSel.value = '0';
        fillChapters(books[0]);
        loadChapter(1);
      }
    }).catch(function() { setStatus('Network error loading books', 'err'); });
  }

  function fillChapters(book) {
    var html = '';
    for (var c = 1; c <= book.chapters; c++) {
      html += '<option value="' + c + '">' + c + '</option>';
    }
    chSel.innerHTML = html;
    chSel.value = '1';
  }

  function loadChapter(ch) {
    if (curBookIdx < 0) return;
    curChapter = ch;
    if (chSel.value !== String(ch)) chSel.value = String(ch);
    var book = books[curBookIdx];
    vList.innerHTML = '<div class="hint">Loading…</div>';
    fetch('/api/chapter?book=' + encodeURIComponent(book.id) + '&chapter=' + ch)
      .then(function(r) { return r.json(); })
      .then(function(j) {
        if (!j.ok) { vList.innerHTML = '<div class="hint">Could not load chapter</div>'; return; }
        var html = '';
        for (var i = 0; i < j.verses.length; i++) {
          var v = j.verses[i];
          var textDiv = browseLang === 'en'
            ? '<div class="ve" dir="ltr">' + escapeHtml(v.en) + '</div>'
            : '<div class="va" dir="rtl" lang="ar">' + escapeHtml(v.ar) + '</div>';
          html += '<div class="vrow" data-v="' + v.v + '"><div class="vn">' + v.v + '</div>' +
                  '<div>' + textDiv + '</div></div>';
        }
        vList.innerHTML = html;
        var rows = vList.querySelectorAll('.vrow');
        for (var k = 0; k < rows.length; k++) {
          rows[k].addEventListener('click', function() {
            triggerVerse(parseInt(this.getAttribute('data-v'), 10));
          });
        }
        markLiveRow();
      })
      .catch(function() { vList.innerHTML = '<div class="hint">Network error</div>'; });
  }

  function escapeHtml(t) {
    return String(t || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function markLiveRow() {
    var rows = vList.querySelectorAll('.vrow');
    var book = books[curBookIdx];
    var match = book && live.bookId === book.id && live.chapter === curChapter;
    for (var k = 0; k < rows.length; k++) {
      rows[k].classList.toggle('live', !!(match && parseInt(rows[k].getAttribute('data-v'), 10) === live.verse));
    }
  }

  function triggerVerse(verseNum) {
    if (curBookIdx < 0) return;
    setStatus('Sending…', '');
    fetch('/api/trigger?book=' + (curBookIdx + 1) + '&chapter=' + curChapter + '&verse=' + verseNum)
      .then(function(r) { return r.json(); })
      .then(function(j) {
        if (j.ok) { setStatus('✓ On screen', 'ok'); pollNow(); }
        else setStatus('✗ ' + (j.error || 'failed'), 'err');
      })
      .catch(function() { setStatus('✗ Network error', 'err'); });
  }

  function triggerRef() {
    var ref = refInput.value.trim();
    if (!ref) { setStatus('Type a reference first', 'err'); refInput.focus(); return; }
    setStatus('Sending…', '');
    fetch('/api/trigger?ref=' + encodeURIComponent(ref))
      .then(function(r) { return r.json(); })
      .then(function(j) {
        if (j.ok) { setStatus('✓ ' + ref, 'ok'); pollNow(); }
        else setStatus('✗ ' + (j.error || 'failed'), 'err');
      })
      .catch(function() { setStatus('✗ Network error', 'err'); });
  }

  function toggleOnAir() {
    var want = !live.onAir;
    fetch('/api/onair?on=' + (want ? '1' : '0'))
      .then(function(r) { return r.json(); })
      .then(function(j) { if (j.ok) pollNow(); })
      .catch(function() { setStatus('✗ Network error', 'err'); });
  }

  function renderOnAir() {
    var btn = document.getElementById('onAirBtn');
    if (live.onAir) {
      btn.className = 'onair on';
      btn.textContent = '🔴 ON AIR — tap to hide';
    } else {
      btn.className = 'onair off';
      btn.textContent = '⚫ OFF AIR — tap to show';
    }
  }

  function pollNow() {
    fetch('/api/verse?light=1').then(function(r) { return r.json(); }).then(function(j) {
      live.onAir = !!j.isLive;
      live.bookId = j.bookId || '';
      live.chapter = j.chapterNum || 0;
      live.verse = j.verseNum || 0;
      var cit = j.arabicCitation || j.englishCitation || '';
      var pill = document.getElementById('livePill');
      document.getElementById('liveRef').textContent =
        cit ? (live.onAir ? cit : 'Off air — ' + cit) : 'Off air';
      pill.className = 'livepill' + (live.onAir && cit ? ' on' : '');
      pill.title = cit || '';
      renderOnAir();
      markLiveRow();
    }).catch(function() {});
  }

  loadBooks();
  pollNow();
  setInterval(pollNow, 3000);
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

    // v1.9.1 (#16): optional display host — the tablet's network name.
    // When set, advertised URLs use the name instead of the raw IP (which
    // changes with DHCP). Display only: the server keeps listening on all
    // interfaces, and the IP remains reachable as the fallback.
    @Volatile var advertiseName: String? = null

    fun getDisplayHost(): String {
        val n = advertiseName?.trim()
        return if (!n.isNullOrEmpty()) n else getLocalIpAddress()
    }

    fun getServerUrl(): String {
        if (!hasLanAddress()) return ""
        return "http://${getDisplayHost()}:$port/ndi"
    }

    fun getStreamUrl(): String {
        if (!hasLanAddress()) return ""
        return "http://${getDisplayHost()}:$port/ndi/stream"
    }
}
