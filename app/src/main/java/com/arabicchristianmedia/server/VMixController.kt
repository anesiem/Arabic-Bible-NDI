package com.arabicchristianmedia.server

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * v1.8: LAN-only vMix controller for SetText push.
 *
 * Uses vMix's documented HTTP API (Function=SetText) to push verse text
 * into the user's vMix GT title fields. All I/O is blocking and must run
 * off the main thread (the ViewModel launches it on a background thread).
 *
 * Reference: vMix User Guide — Function=SetText, Input may be title number,
 * exact case-sensitive name, or GUID. GT field names use the .Text suffix.
 *
 * No cloud dependency; LAN only.
 */
class VMixController(
    private val host: String,
    private val port: Int = 8088,
    private val input: String
) {

    /**
     * Set a text field on the vMix input.
     * @param field The GT field name (e.g. "Headline.Text", "Description.Text").
     * @param value The text value (UTF-8 URL-encoded).
     * @return true if vMix acknowledged (HTTP 200).
     */
    fun setText(field: String, value: String): Boolean {
        if (host.isBlank() || input.isBlank() || field.isBlank()) return false
        return try {
            val encodedInput = URLEncoder.encode(input, "UTF-8")
            val encodedField = URLEncoder.encode(field, "UTF-8")
            val encodedValue = URLEncoder.encode(value, "UTF-8")
            val urlStr = "http://$host:$port/api/?Function=SetText&Input=$encodedInput&SelectedName=$encodedField&Value=$encodedValue"

            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.doInput = true

                val responseCode = conn.responseCode
                // Drain the response to release the connection.
                try {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                } catch (e: Exception) { }

                responseCode == HttpURLConnection.HTTP_OK
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Test the connection by sending a test string to a field.
     * @return (success, message) pair.
     */
    fun testConnection(field: String, testValue: String = "Test 123"): Pair<Boolean, String> {
        if (host.isBlank()) return false to "vMix IP not set"
        if (input.isBlank()) return false to "vMix input not set"
        if (field.isBlank()) return false to "Test field not set"
        return try {
            val ok = setText(field, testValue)
            if (ok) true to "vMix acknowledged the test"
            else false to "vMix did not respond (check IP/port/input)"
        } catch (e: Exception) {
            false to "Error: ${e.message}"
        }
    }
}
