package com.arabicchristianmedia.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.arabicchristianmedia.MainActivity
import com.arabicchristianmedia.R
import com.arabicchristianmedia.data.BibleRepository
import com.arabicchristianmedia.data.TemplateRepository
import com.arabicchristianmedia.model.AnimatedBackgroundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the [NdiBroadcastServer] so NDI/HTTP broadcasting
 * keeps running when the app is backgrounded (screen off, Home pressed).
 *
 * Lifecycle contract:
 * - The service goes foreground only while broadcasting. A foreground service is
 *   the last thing the OS kills, and START_STICKY restarts it after a kill.
 * - "Broadcast active" is persisted in [PREFS]. An OS process kill auto-resumes
 *   everything: the verse/styles/live state is rehydrated, the HTTP server
 *   restarts, and both NDI senders come back with the persisted feed specs.
 *   This repeats until the user explicitly stops or closes the app.
 * - The user closing the app (task swiped away, [onTaskRemoved]) or the Stop
 *   action/notification button clears the flag, stops the senders and server,
 *   and stops the service.
 *
 * v1.9 — background NDI (approved).
 */
class NdiService : Service() {

    companion object {
        const val ACTION_START = "com.arabicchristianmedia.server.NdiService.ACTION_START"
        const val ACTION_STOP = "com.arabicchristianmedia.server.NdiService.ACTION_STOP"
        private const val ACTION_REFRESH = "com.arabicchristianmedia.server.NdiService.ACTION_REFRESH"

        private const val PREFS = "bible_ndi_service_prefs"
        private const val KEY_BROADCAST_ACTIVE = "broadcast_active"
        private const val CHANNEL_ID = "ndi_broadcast"
        private const val NOTIFICATION_ID = 1001
        private const val PORT = 8080

        /**
         * The single shared server instance. Created on first [ensureServer] call
         * (usually by the ViewModel) and reused afterwards — never replaced, so
         * the ViewModel and the service can never diverge onto two servers.
         */
        @Volatile
        var server: NdiBroadcastServer? = null
            private set

        /**
         * Returns the shared server, creating it on first use. Safe from any
         * thread; the service reuses whatever instance is already held.
         */
        @Synchronized
        fun ensureServer(context: Context): NdiBroadcastServer {
            return server ?: NdiBroadcastServer(context.applicationContext, PORT).also { server = it }
        }

        /** Starts (or re-affirms) the foreground broadcast service. Idempotent. */
        fun start(context: Context) {
            setBroadcastActive(context, true)
            ensureServer(context)
            val intent = Intent(context, NdiService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        /**
         * Requests a full stop: clears the auto-resume flag, stops the server,
         * drops the notification, and stops the service. Idempotent.
         */
        fun stop(context: Context) {
            context.startService(Intent(context, NdiService::class.java).setAction(ACTION_STOP))
        }

        /** Rebuilds the ongoing notification (e.g. after feed toggles). No-op when idle. */
        fun refreshNotification(context: Context) {
            if (server == null) return
            // No-op while the broadcast is not active: sending REFRESH to
            // an idle service would make it stopSelf(), and onDestroy
            // would kill the still-running HTTP server — the opposite of
            // a feed-only stop or toggle.
            if (!isBroadcastActive(context)) return
            context.startService(Intent(context, NdiService::class.java).setAction(ACTION_REFRESH))
        }

        /**
         * Clears the auto-resume flag without touching the service,
         * senders or server — used by a feed-only Stop so a later OS
         * kill does not resurrect the broadcast.
         */
        fun clearBroadcastActive(context: Context) {
            setBroadcastActive(context, false)
        }

        fun isBroadcastActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_BROADCAST_ACTIVE, false)

        /**
         * The single shared NDI sender. Owns both genuine senders (Lower Third
         * and Full Show). Lives here — not in the ViewModel — so the feeds keep
         * rendering while the app is backgrounded and can auto-resume after an
         * OS process kill. Never nulled on stop: the frame providers below are
         * wired once and resolve the server lazily.
         */
        @Volatile
        var sender: NdiNativeSender? = null
            private set

        /**
         * Returns the shared sender, creating it on first use. Safe from any thread.
         * Frame providers are wired at creation so they exist before the instance
         * can ever reach a caller — no start-then-wire race.
         */
        @Synchronized
        fun ensureSender(context: Context): NdiNativeSender {
            return sender ?: NdiNativeSender().also {
                sender = it
                wireFrameProviders(context.applicationContext, it)
            }
        }

        /**
         * Wires the NDI sender's frame providers to the broadcast server. The server
         * is resolved lazily through the volatile holder on every call so the
         * providers never go stale if the server is recreated (stop → start).
         * No ViewModel state is captured — everything comes from the server, which
         * is what lets the feeds render headlessly after an OS process kill.
         */
        private fun wireFrameProviders(appCtx: Context, snd: NdiNativeSender) {
            snd.setFrameProvider { isFullScreen ->
                val srv = server ?: ensureServer(appCtx)
                if (isFullScreen) {
                    srv.renderCurrentFrame(1920, 1080, isForStream = false, overrideTemplate = srv.currentShowTemplate)
                } else {
                    srv.renderCurrentFrame(1920, 1080, isForStream = false)
                }
            }
            // Dirty-frame detection: NDI senders only push when the version changes.
            snd.setFrameVersionProvider { server?.frameVersion ?: 0L }
            // Motion mode: force-rerender animated backgrounds on a capped tick.
            snd.setMotionFrameProvider { isFullScreen ->
                val srv = server ?: ensureServer(appCtx)
                if (isFullScreen) {
                    srv.renderMotionFrame(overrideTemplate = srv.currentShowTemplate)
                } else {
                    srv.renderMotionFrame()
                }
            }
            snd.setMotionContentProvider { isFullScreen ->
                val srv = server ?: return@setMotionContentProvider false
                val tpl = if (isFullScreen) srv.currentShowTemplate else srv.currentTemplate
                tpl.animatedBackground != AnimatedBackgroundType.NONE
            }
            // Combined batch, motion split: a gradient animation is
            // motion content on its own — ticks run for it even with
            // no video background and even when the Setup Motion
            // (video-only) toggle is off.
            snd.setGradientMotionContentProvider { isFullScreen ->
                val srv = server ?: return@setGradientMotionContentProvider false
                val tpl = if (isFullScreen) srv.currentShowTemplate else srv.currentTemplate
                tpl.gradientEnabled && tpl.gradientAnimation != com.arabicchristianmedia.model.GradientAnimationType.NONE
            }
            // v2.0: verse-change transitions always pump (like the HTTP
            // overlay), independent of the feed's Motion toggle.
            snd.setTransitionProvider { isFullScreen ->
                server?.isTransitionActiveFor(isFullScreen) == true
            }
        }

        /**
         * Starts/stops the two NDI senders from the persisted feed specs
         * (resolution/fps/enabled/motion in `bible_ndi_source_specs`).
         * Returns (lowerActive, fullActive) so callers can reflect the result.
         */
        fun syncNativeSources(context: Context): Pair<Boolean, Boolean> {
            val appCtx = context.applicationContext
            val prefs = appCtx.getSharedPreferences("bible_ndi_source_specs", Context.MODE_PRIVATE)
            val snd = ensureSender(appCtx)
            val lower = applySource(prefs, snd, NdiNativeSender.FEED_LOWER, isFullScreen = false)
            val full = applySource(prefs, snd, NdiNativeSender.FEED_FULL, isFullScreen = true)
            return lower to full
        }

        private fun applySource(
            prefs: SharedPreferences,
            snd: NdiNativeSender,
            feedKey: String,
            isFullScreen: Boolean
        ): Boolean {
            val d = NdiNativeSender.defaultSpec(feedKey)
            val w = prefs.getInt("${feedKey}_w", d.width)
            val h = prefs.getInt("${feedKey}_h", d.height)
            val fps = prefs.getInt("${feedKey}_fps", d.fps)
            val motion = prefs.getBoolean("${feedKey}_motion", d.motionEnabled)
            // Same validation as the app's initial load: stale values fall back to defaults.
            val res = NdiNativeSender.RESOLUTION_OPTIONS.firstOrNull { it.first == w && it.second == h }
                ?: (d.width to d.height)
            val validFps = if (NdiNativeSender.FPS_OPTIONS.contains(fps)) fps else d.fps
            val spec = NdiSourceSpec(feedKey, res.first, res.second, validFps, motion)
            return if (prefs.getBoolean("${feedKey}_enabled", true)) {
                val started = snd.startSource(feedKey, isFullScreen, spec)
                // v1.9.2: motion off parks the feed's video decoder
                // immediately — the static provider serves the cache, so
                // no draw may follow to park it via the draw path.
                if (started && !motion) {
                    try { server?.parkVideoDecoder(isFullScreen) } catch (e: Exception) { /* ignore */ }
                }
                started
            } else {
                try { snd.stopSource(feedKey) } catch (e: Exception) { /* ignore */ }
                false
            }
        }

        /** Stops senders + server if they exist, without creating them. */
        fun stopAllIfPresent() {
            try { sender?.stopAll() } catch (e: Exception) { /* ignore */ }
            try { server?.stop() } catch (e: Exception) { /* ignore */ }
        }

        /**
         * Emitted when the broadcast is stopped outside the ViewModel (the
         * notification Stop action). The UI collects this to clear its stale
         * "active" state. Buffer of 1, no replay: if nobody is collecting (app
         * dead), the event is dropped — a fresh start reads the real state anyway.
         */
        private val _externalStopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val externalStopEvents: SharedFlow<Unit> = _externalStopEvents.asSharedFlow()

        private fun setBroadcastActive(context: Context, active: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BROADCAST_ACTIVE, active).apply()
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Reuse the ViewModel-created instances when present (normal start);
        // otherwise this is an OS restart after a process kill — create our own
        // so auto-resume has a server and senders. ensureSender wires the frame
        // providers at creation.
        ensureServer(this)
        ensureSender(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setBroadcastActive(this, false)
                try { sender?.stopAll() } catch (e: Exception) { /* ignore */ }
                try { server?.stop() } catch (e: Exception) { /* ignore */ }
                server = null
                _externalStopEvents.tryEmit(Unit)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (isBroadcastActive(this)) {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, buildNotification())
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START, or an OS restart (null/unknown action) after a process kill.
                val isOsRestart = intent?.action != ACTION_START
                if (intent?.action == ACTION_START) setBroadcastActive(this, true)
                if (!isBroadcastActive(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Foreground first: the restart timeout is satisfied before any I/O.
                startForegroundNow()
                if (isOsRestart) {
                    // v1.9: full NDI auto-resume — rehydrate the last verse/styles,
                    // then bring the HTTP server and both NDI senders back, all off
                    // the main thread. The notification is already up; refresh it
                    // once the feeds are live again.
                    serviceScope.launch {
                        try {
                            BibleRepository.initialize(this@NdiService)
                            val srv = ensureServer(this@NdiService)
                            val verseRestored = srv.restorePersistedRenderState(TemplateRepository(this@NdiService))
                            if (!srv.isRunning) srv.start()
                            val (lowerActive, fullActive) = syncNativeSources(this@NdiService)
                            refreshNotification(this@NdiService)
                            Log.i(
                                "NdiService",
                                "Auto-resume complete: verseRestored=$verseRestored, " +
                                    "isLive=${srv.isLive}, lower=$lowerActive, full=$fullActive"
                            )
                        } catch (e: Exception) {
                            Log.e("NdiService", "Auto-resume failed", e)
                        }
                    }
                } else {
                    // User-driven start: the ViewModel drives state from here.
                    // Make sure the HTTP server is up (idempotent).
                    try {
                        server?.let { if (!it.isRunning) it.start() }
                    } catch (e: Exception) { /* ignore: retried on next app open */ }
                }
                return START_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away = "closed the application": stop everything.
        setBroadcastActive(this, false)
        try { sender?.stopAll() } catch (e: Exception) { /* ignore */ }
        try { server?.stop() } catch (e: Exception) { /* ignore */ }
        server = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // Defensive: the server releases its socket, SSE clients, video decoders,
        // and the Wi-Fi/multicast locks inside stop().
        try { sender?.stopAll() } catch (e: Exception) { /* ignore */ }
        try { server?.stop() } catch (e: Exception) { /* ignore */ }
        super.onDestroy()
    }

    // ---------- foreground notification ----------

    private fun startForegroundNow() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NDI Broadcast",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps the NDI broadcast server running in the background"
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /** Feed names as the user configured them (persisted NDI source prefs). */
    private fun activeFeedsText(): String {
        val prefs = getSharedPreferences("bible_ndi_source_specs", Context.MODE_PRIVATE)
        val feeds = buildList {
            if (prefs.getBoolean("${NdiNativeSender.FEED_LOWER}_enabled", true)) add("Lower Third")
            if (prefs.getBoolean("${NdiNativeSender.FEED_FULL}_enabled", true)) add("Full Show")
        }
        return if (feeds.isEmpty()) "No feeds enabled" else feeds.joinToString(" • ")
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopBroadcast = PendingIntent.getService(
            this,
            1,
            Intent(this, NdiService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("NDI Live")
            .setContentText(activeFeedsText())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopBroadcast)
            .setOngoing(true)
            .build()
    }
}
