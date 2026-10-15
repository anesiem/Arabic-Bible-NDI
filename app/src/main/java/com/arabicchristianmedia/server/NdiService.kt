package com.arabicchristianmedia.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.arabicchristianmedia.MainActivity
import com.arabicchristianmedia.R

/**
 * Foreground service that owns the [NdiBroadcastServer] so NDI/HTTP broadcasting
 * keeps running when the app is backgrounded (screen off, Home pressed).
 *
 * Lifecycle contract:
 * - The service goes foreground only while broadcasting. A foreground service is
 *   the last thing the OS kills, and START_STICKY restarts it after a kill.
 * - "Broadcast active" is persisted in [PREFS]. An OS process kill auto-resumes
 *   the HTTP server on restart; the NDI senders are ViewModel-owned and restart
 *   when the app next opens (ViewModel init re-syncs them because the server
 *   reports isRunning). This repeats until the user explicitly stops/closes.
 * - The user closing the app (task swiped away, [onTaskRemoved]) or the Stop
 *   action/notification button clears the flag, stops the server, and stops
 *   the service.
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
            context.startService(Intent(context, NdiService::class.java).setAction(ACTION_REFRESH))
        }

        fun isBroadcastActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_BROADCAST_ACTIVE, false)

        private fun setBroadcastActive(context: Context, active: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_BROADCAST_ACTIVE, active).apply()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Reuse the ViewModel-created instance when present (normal start);
        // otherwise this is an OS restart after a process kill — create our own
        // so auto-resume has a server to start.
        ensureServer(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setBroadcastActive(this, false)
                try { server?.stop() } catch (e: Exception) { /* ignore */ }
                server = null
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
                // ACTION_START, or an OS restart (null action) after a process kill.
                if (intent?.action == ACTION_START) setBroadcastActive(this, true)
                if (!isBroadcastActive(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                // Make sure the HTTP server is up (idempotent): this is the
                // auto-resume path after an OS kill. The NDI senders are
                // ViewModel-owned; they restart when the app next opens because
                // the ViewModel sees the server already running and re-syncs them.
                try {
                    server?.let { if (!it.isRunning) it.start() }
                } catch (e: Exception) { /* ignore: retried on next app open */ }
                startForegroundNow()
                return START_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away = "closed the application": stop everything.
        setBroadcastActive(this, false)
        try { server?.stop() } catch (e: Exception) { /* ignore */ }
        server = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Defensive: the server releases its socket, SSE clients, video decoders,
        // and the Wi-Fi/multicast locks inside stop().
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
