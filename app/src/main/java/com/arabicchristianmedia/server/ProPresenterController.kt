package com.arabicchristianmedia.server

import android.content.Context
import android.util.Base64
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Batch-2 item 25: LAN-only ProPresenter push (v21 API).
 *
 * Two targets, both verified against ProPresenter's official OpenAPI
 * ("ProPresenter API 1.0 OAS3", openapi.propresenter.com):
 *
 * - Stage message (stage screens): PUT /v1/stage/message with a JSON
 *   string body; DELETE /v1/stage/message hides it.
 * - Audience message (main output): POST /v1/messages creates a message
 *   (UUID auto-assigned), POST /v1/message/{uuid}/trigger shows it,
 *   GET /v1/message/{uuid}/clear hides it, DELETE /v1/message/{uuid}
 *   removes it. The app creates one message per verse and deletes the
 *   previous, so nothing accumulates in ProPresenter.
 *
 * Auth: the v21 API defines NO security scheme — no username, password
 * or token; the only gate is ProPresenter -> Settings -> Network enabled.
 * Username/password fields are kept optional in Setup (sent as Basic
 * Auth when filled) for unusual setups, but are normally unnecessary.
 *
 * Default API port 50001 (ProPresenter -> Settings -> Network shows the
 * actual port). GET /v1/version is the lightweight probe for the
 * Test button and the network discovery scan.
 *
 * All I/O is blocking and must run off the main thread (the ViewModel
 * launches it on a background thread, mirroring pushToVMix).
 *
 * No cloud dependency; LAN only.
 */
class ProPresenterController(
    private val host: String,
    private val port: Int = 50001,
    private val username: String = "",
    private val password: String = ""
) {

    private data class Resp(val ok: Boolean, val msg: String, val body: String)

    private fun authHeader(): String? {
        if (username.isBlank() && password.isBlank()) return null
        val creds = "$username:$password".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.encodeToString(creds, Base64.NO_WRAP)
    }

    /** Minimal JSON string encoder (the API wants a JSON string body). */
    private fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        contentType: String? = null
    ): Resp {
        if (host.isBlank()) return Resp(false, "ProPresenter host not set", "")
        return try {
            val url = URL("http://$host:$port$path")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                authHeader()?.let { conn.setRequestProperty("Authorization", it) }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty(
                        "Content-Type",
                        contentType ?: "application/json; charset=utf-8"
                    )
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val respBody = try {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                if (code in 200..299) Resp(true, "HTTP $code", respBody)
                else Resp(false, "HTTP $code", respBody)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Resp(false, e.message ?: "connection failed", "")
        }
    }

    // ---- Stage message (stage screens) ----

    /** Show the verse on ProPresenter's stage message. */
    fun setStageMessage(text: String): Boolean {
        return request("PUT", "/v1/stage/message", jsonString(text)).ok
    }

    /** Hide the stage message (verse off air). */
    fun clearStageMessage(): Boolean {
        return request("DELETE", "/v1/stage/message").ok
    }

    // ---- Audience message (main output) ----

    /**
     * Create an audience message holding the verse text.
     * @return the auto-assigned UUID, or null on failure.
     */
    fun createAudienceMessage(name: String, text: String): String? {
        // Per the official OpenAPI spec (API 1.0), POST /v1/messages requires:
        // id{uuid,name,index}, message, theme{uuid,name,index}, visible_on_network, is_active.
        // The id uuid/index are ignored (auto-assigned); theme must resolve on his machine.
        val body = "{\"id\":{\"uuid\":\"\",\"name\":" + jsonString(name) + ",\"index\":0}" +
            ",\"message\":" + jsonString(text) +
            ",\"theme\":{\"uuid\":\"\",\"name\":\"Default\",\"index\":0}" +
            ",\"visible_on_network\":false,\"is_active\":false}"
        val resp = request("POST", "/v1/messages", body)
        if (!resp.ok) return null
        return Regex("\"uuid\"\\s*:\\s*\"([^\"]+)\"")
            .find(resp.body)?.groupValues?.getOrNull(1)
    }

    /** Show (trigger) a previously created audience message. */
    fun triggerAudienceMessage(uuid: String): Boolean {
        return request("POST", "/v1/message/$uuid/trigger").ok
    }

    /** Hide (clear) a previously created audience message. */
    fun clearAudienceMessage(uuid: String): Boolean {
        return request("GET", "/v1/message/$uuid/clear").ok
    }

    /** Delete a previously created audience message. */
    fun deleteAudienceMessage(uuid: String): Boolean {
        return request("DELETE", "/v1/message/$uuid").ok
    }

    /**
     * Test the connection with a lightweight GET on /version.
     * @return (success, message) pair for the Setup status line.
     */
    fun testConnection(): Pair<Boolean, String> {
        if (host.isBlank()) return false to "ProPresenter host not set"
        val resp = request("GET", "/version")
        return if (resp.ok) true to "ProPresenter responded (${resp.msg})"
        else false to "ProPresenter answered ${resp.msg} (check host/port; enable Network in ProPresenter Settings)"
    }

    companion object {
        // Password obfuscation for SharedPreferences (per his "never
        // plaintext" principle). This is obfuscation against casual
        // inspection, NOT encryption — stated honestly.
        private const val OBF_KEY = "BibleNDI-PP-v1"

        fun obfuscatePassword(raw: String): String {
            if (raw.isEmpty()) return ""
            val kb = OBF_KEY.toByteArray(Charsets.UTF_8)
            val rb = raw.toByteArray(Charsets.UTF_8)
            val out = ByteArray(rb.size) { i ->
                (rb[i].toInt() xor kb[i % kb.size].toInt()).toByte()
            }
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        fun deobfuscatePassword(stored: String): String {
            if (stored.isEmpty()) return ""
            return try {
                val kb = OBF_KEY.toByteArray(Charsets.UTF_8)
                val eb = Base64.decode(stored, Base64.NO_WRAP)
                val out = ByteArray(eb.size) { i ->
                    (eb[i].toInt() xor kb[i % kb.size].toInt()).toByte()
                }
                String(out, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * Local Wi-Fi subnet prefix, e.g. "192.168.50." — for discovery.
         */
        fun localSubnetPrefix(context: Context): String? {
            return try {
                @Suppress("DEPRECATION")
                val wm = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                @Suppress("DEPRECATION")
                val ip = wm.connectionInfo.ipAddress
                if (ip == 0) return null
                val a = ip and 0xff
                val b = ip shr 8 and 0xff
                val c = ip shr 16 and 0xff
                "$a.$b.$c."
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Probe the subnet for a ProPresenter API (GET /v1/version).
         * Blocking; call off the main thread. Returns IPs that answered.
         */
        fun scanForProPresenter(
            subnetPrefix: String,
            port: Int,
            username: String,
            password: String,
            timeoutMs: Int = 400
        ): List<String> {
            val auth = if (username.isBlank() && password.isBlank()) null
            else "Basic " + Base64.encodeToString(
                "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
            )
            val found = Collections.synchronizedList(mutableListOf<String>())
            val batch = mutableListOf<Thread>()
            for (i in 1..254) {
                val ip = "$subnetPrefix$i"
                val t = Thread {
                    try {
                        val url = URL("http://$ip:$port/version")
                        val conn = url.openConnection() as HttpURLConnection
                        try {
                            conn.requestMethod = "GET"
                            conn.connectTimeout = timeoutMs
                            conn.readTimeout = timeoutMs
                            if (auth != null) conn.setRequestProperty("Authorization", auth)
                            if (conn.responseCode in 200..299) found.add(ip)
                        } finally {
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        // Not a ProPresenter host; ignore.
                    }
                }
                batch.add(t)
                t.start()
                if (batch.size >= 32) {
                    batch.forEach { it.join(3000) }
                    batch.clear()
                }
            }
            batch.forEach { it.join(3000) }
            return found.sorted()
        }
    }
}
