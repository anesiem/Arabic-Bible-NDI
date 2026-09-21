package com.example.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

class NdiDiscoveryBeacon(private val context: Context) {

    private var beaconJob: Job? = null
    private var responderJob: Job? = null
    private var tcp5960Job: Job? = null
    private var tcp5959Job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var multicastLock: WifiManager.MulticastLock? = null

    private val nsdManager: NsdManager? by lazy {
        try {
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        } catch (e: Exception) {
            null
        }
    }

    private var ndiRegistrationListener: NsdManager.RegistrationListener? = null
    private var ndiVideoRegistrationListener: NsdManager.RegistrationListener? = null
    var isRunning = false
        private set

    fun start(serviceUrl: String, ip: String = "", port: Int = 8080, udpPort: Int = 5960) {
        stop()
        isRunning = true

        // 1. Acquire Wi-Fi Multicast Lock to allow mDNS and UDP broadcasts
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("NdiDiscoveryLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w("NdiDiscoveryBeacon", "Could not acquire multicast lock: ${e.message}")
        }

        // 2. Register mDNS service with official NDI 6 specifications
        registerNdiMdnsService(ip, port)

        // 3. Resolve broadcast targets (Global 255.255.255.255 + Subnet Directed e.g. 192.168.1.255)
        val broadcastAddresses = getBroadcastAddresses(ip)

        // 4. Periodic UDP discovery beacon on port 5960 and 5961 for NDI v3, v4, v5, and v6 tools
        beaconJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true

                val deviceModel = Build.MODEL
                val baseSourceName = "$deviceModel (Bible-NDI)"
                
                // Packets compatible with NDI 6, 5, 4, and 3
                val msg1 = "NDI_BIBLE_SOURCE|NAME=$baseSourceName|PORT=$port|URL=$serviceUrl|STREAM=http://$ip:$port/ndi/stream|VER=6.0|VER=5.0|VER=4.0|FORMAT=BGRA|ALPHA=1"
                val msg2 = "NDI $baseSourceName $port\n"
                
                val sourceNameBytes = baseSourceName.toByteArray(Charsets.UTF_8)
                val rawNdiBytes = ByteArray(3 + 1 + sourceNameBytes.size + 1 + 2)
                rawNdiBytes[0] = 'N'.code.toByte()
                rawNdiBytes[1] = 'D'.code.toByte()
                rawNdiBytes[2] = 'I'.code.toByte()
                rawNdiBytes[3] = 0
                System.arraycopy(sourceNameBytes, 0, rawNdiBytes, 4, sourceNameBytes.size)
                rawNdiBytes[4 + sourceNameBytes.size] = 0
                rawNdiBytes[4 + sourceNameBytes.size + 1] = (port shr 8).toByte()
                rawNdiBytes[4 + sourceNameBytes.size + 2] = (port and 0xFF).toByte()

                val b1 = msg1.toByteArray(Charsets.UTF_8)
                val b2 = msg2.toByteArray(Charsets.UTF_8)

                while (isActive && isRunning) {
                    for (targetAddr in broadcastAddresses) {
                        try {
                            // Standard NDI discovery port 5960
                            socket.send(DatagramPacket(b1, b1.size, targetAddr, udpPort))
                            socket.send(DatagramPacket(b2, b2.size, targetAddr, udpPort))
                            socket.send(DatagramPacket(rawNdiBytes, rawNdiBytes.size, targetAddr, udpPort))

                            // Secondary NDI discovery port 5961 (video stream port query)
                            socket.send(DatagramPacket(b1, b1.size, targetAddr, 5961))
                        } catch (e: Exception) {
                            // ignore individual packet send error
                        }
                    }
                    delay(2500) // Broadcast every 2.5s
                }
            } catch (e: Exception) {
                Log.w("NdiDiscoveryBeacon", "Broadcast socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }

        // 5. Query responder on UDP 5960: when NDI tools query the network via UDP, reply immediately
        responderJob = scope.launch {
            var recvSocket: DatagramSocket? = null
            try {
                recvSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(udpPort))
                }
                val buffer = ByteArray(1024)
                val replyBytes = "NDI_BIBLE_SOURCE|NAME=Bible-NDI|PORT=$port|URL=$serviceUrl|STREAM=http://$ip:$port/ndi/stream|VER=6.0|VER=5.0|VER=4.0|FORMAT=BGRA|ALPHA=1\n".toByteArray(Charsets.UTF_8)

                while (isActive && isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        recvSocket.receive(packet)
                        // Reply to the requesting device
                        val replyPacket = DatagramPacket(replyBytes, replyBytes.size, packet.address, packet.port)
                        recvSocket.send(replyPacket)
                    } catch (e: Exception) {
                        // socket receive timeout or closed
                    }
                }
            } catch (e: Exception) {
                // port 5960 might be in use, ignore
            } finally {
                recvSocket?.close()
            }
        }

        // 6. TCP Server on port 5960: Standard NDI Messaging & Directory Server
        // When OBS Studio, vMix, or NDI Studio Monitor queries TCP port 5960, return source directory
        tcp5960Job = scope.launch {
            var server: ServerSocket? = null
            try {
                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(5960))
                }
                while (isActive && isRunning) {
                    try {
                        val client = server.accept()
                        launch {
                            handleNdiDirectoryQuery(client, ip, port, serviceUrl)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.w("NdiDiscoveryBeacon", "TCP 5960 already in use or unavailable: ${e.message}")
            } finally {
                try { server?.close() } catch (e: Exception) {}
            }
        }

        // 7. TCP Server on port 5959: Official NDI 6 Discovery Server protocol
        tcp5959Job = scope.launch {
            var server: ServerSocket? = null
            try {
                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(5959))
                }
                while (isActive && isRunning) {
                    try {
                        val client = server.accept()
                        launch {
                            handleNdi6DiscoveryServerQuery(client, ip, port, serviceUrl)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.w("NdiDiscoveryBeacon", "TCP 5959 already in use or unavailable: ${e.message}")
            } finally {
                try { server?.close() } catch (e: Exception) {}
            }
        }
    }

    private fun handleNdiDirectoryQuery(socket: Socket, ip: String, port: Int, serviceUrl: String) {
        try {
            socket.soTimeout = 3000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = try { reader.readLine() } catch (e: Exception) { null }

            val out = socket.getOutputStream()
            val writer = PrintWriter(out)

            val json = """{"version":"6.0","sources":[{"name":"Bible-NDI","address":"$ip","port":$port,"url":"http://$ip:$port/ndi","stream":"http://$ip:$port/ndi/stream","format":"BGRA","alpha":true,"width":1920,"height":1080,"fps":30}]}"""

            if (firstLine != null && (firstLine.startsWith("GET") || firstLine.startsWith("POST") || firstLine.startsWith("HEAD"))) {
                val httpHeader = "HTTP/1.1 200 OK\r\n" +
                    "Server: Bible-NDI-v6\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${json.toByteArray(Charsets.UTF_8).size}\r\n" +
                    "NDI-Source: Bible-NDI\r\n" +
                    "NDI-Stream: http://$ip:$port/ndi/stream\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
                writer.print(httpHeader)
                writer.print(json)
                writer.flush()
            } else {
                val ndiResponse = """
NDI/6.0 200 OK
Server: Bible-NDI-v6
Source-Name: Bible-NDI
Port: $port
Stream-Url: http://$ip:$port/ndi/stream
Overlay-Url: http://$ip:$port/ndi
Format: BGRA
Resolution: 1920x1080
Framerate: 30
Alpha-Channel: true
Connection: close

NDI_SOURCE_LIST:
1: Bible-NDI (Channel 1)|http://$ip:$port/ndi/stream|BGRA
""".trimIndent() + "\r\n\r\n"

                writer.print(ndiResponse)
                writer.flush()
            }
        } catch (e: Exception) {
            // client disconnected
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun handleNdi6DiscoveryServerQuery(socket: Socket, ip: String, port: Int, serviceUrl: String) {
        try {
            socket.soTimeout = 3000
            val out = socket.getOutputStream()
            val writer = PrintWriter(out)

            val json = """
{
  "version": "6.0",
  "status": "ok",
  "sources": [
    {
      "name": "Bible-NDI",
      "address": "$ip",
      "port": $port,
      "url": "http://$ip:$port/ndi",
      "stream": "http://$ip:$port/ndi/stream.mjpg?raw=1",
      "format": "BGRA",
      "alpha": true,
      "width": 1920,
      "height": 1080
    }
  ]
}
""".trimIndent()

            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: application/json; charset=utf-8\r\n")
            writer.print("Content-Length: ${json.toByteArray(Charsets.UTF_8).size}\r\n")
            writer.print("Connection: close\r\n\r\n")
            writer.print(json)
            writer.flush()
        } catch (e: Exception) {
            // client disconnected
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun registerNdiMdnsService(ip: String, port: Int) {
        try {
            val deviceModel = Build.MODEL
            // 1. Web Broadcast Overlay Service: _http._tcp
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "$deviceModel-Bible-NDI"
                serviceType = "_http._tcp"
                setPort(port)
                if (ip.isNotEmpty()) {
                    setAttribute("name", "$deviceModel (Bible-NDI)")
                    setAttribute("path", "/ndi")
                    setAttribute("stream", "/ndi/stream")
                    setAttribute("url", "http://$ip:$port/ndi")
                    setAttribute("format", "BGRA_ALPHA")
                    setAttribute("width", "1920")
                    setAttribute("height", "1080")
                }
            }

            ndiRegistrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    Log.i("NdiDiscoveryBeacon", "mDNS NDI 6 service registered: ${info?.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.w("NdiDiscoveryBeacon", "mDNS NDI 6 registration failed: errorCode $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo?) {
                    Log.i("NdiDiscoveryBeacon", "mDNS NDI 6 service unregistered")
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    Log.w("NdiDiscoveryBeacon", "mDNS NDI 6 unregistration failed: errorCode $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, ndiRegistrationListener)

            // 2. Official NDI Video Service: _ndi._tcp
            val videoServiceInfo = NsdServiceInfo().apply {
                serviceName = "Bible-NDI"
                serviceType = "_ndi._tcp"
                setPort(port)
                if (ip.isNotEmpty()) {
                    setAttribute("name", "Bible-NDI")
                    setAttribute("ndi_version", "6.0.0")
                    setAttribute("format", "BGRA")
                }
            }

            ndiVideoRegistrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {}
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo?) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {}
            }

            nsdManager?.registerService(videoServiceInfo, NsdManager.PROTOCOL_DNS_SD, ndiVideoRegistrationListener)

        } catch (e: Exception) {
            Log.w("NdiDiscoveryBeacon", "NsdManager registration failed: ${e.message}")
        }
    }

    private fun getBroadcastAddresses(activeIp: String): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {
            // ignore
        }

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.interfaceAddresses) {
                    val bcast = addr.broadcast
                    if (bcast != null && !list.contains(bcast)) {
                        list.add(bcast)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        // Subnet fallback based on activeIp (e.g. 192.168.5.242 -> 192.168.5.255)
        if (activeIp.count { it == '.' } == 3) {
            try {
                val subnetPrefix = activeIp.substringBeforeLast(".")
                val subnetBroadcast = InetAddress.getByName("$subnetPrefix.255")
                if (!list.contains(subnetBroadcast)) {
                    list.add(subnetBroadcast)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        return list
    }

    fun stop() {
        isRunning = false
        beaconJob?.cancel()
        beaconJob = null
        responderJob?.cancel()
        responderJob = null
        tcp5960Job?.cancel()
        tcp5960Job = null
        tcp5959Job?.cancel()
        tcp5959Job = null

        try {
            ndiRegistrationListener?.let { nsdManager?.unregisterService(it) }
            ndiVideoRegistrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            // ignore
        }
        ndiRegistrationListener = null
        ndiVideoRegistrationListener = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            // ignore
        }
        multicastLock = null
    }
}

