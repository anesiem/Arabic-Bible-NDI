package com.example.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

enum class InterfaceType {
    WIFI,
    ETHERNET,
    HOTSPOT,
    CELLULAR,
    OTHER
}

data class NetworkInterfaceInfo(
    val id: String,
    val interfaceName: String,
    val ip: String,
    val labelAr: String,
    val labelEn: String,
    val type: InterfaceType,
    val isRecommended: Boolean,
    val reachableFromLan: Boolean
)

object NetworkHelper {

    fun getAvailableInterfaces(): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                val rawName = intf.name.lowercase()
                val addresses = Collections.list(intf.inetAddresses)

                for (addr in addresses) {
                    if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                    val ip = addr.hostAddress ?: continue
                    if (ip.isEmpty() || ip.startsWith("127.")) continue

                    val isWifi = rawName.startsWith("wlan") || rawName.startsWith("wl")
                    val isEth = rawName.startsWith("eth") || rawName.startsWith("en")
                    val isHotspot = rawName.startsWith("ap") ||
                            rawName.startsWith("softap") ||
                            rawName.contains("p2p") ||
                            ip == "192.168.43.1" ||
                            ip == "192.168.49.1"
                    val isCellular = rawName.startsWith("rmnet") ||
                            rawName.startsWith("ccmni") ||
                            rawName.startsWith("pdp") ||
                            rawName.startsWith("cellular") ||
                            rawName.startsWith("dummy")

                    val type = when {
                        isWifi -> InterfaceType.WIFI
                        isEth -> InterfaceType.ETHERNET
                        isHotspot -> InterfaceType.HOTSPOT
                        isCellular -> InterfaceType.CELLULAR
                        else -> InterfaceType.OTHER
                    }

                    val isPrivateLan = ip.startsWith("192.168.") ||
                            ip.startsWith("10.") ||
                            (ip.startsWith("172.") && try {
                                val secondOctet = ip.split(".")[1].toInt()
                                secondOctet in 16..31
                            } catch (e: Exception) { false })

                    val isRecommended = (isWifi || isEth) && isPrivateLan && !isCellular
                    val reachableFromLan = !isCellular

                    val labelAr = when (type) {
                        InterfaceType.WIFI -> "واي فاي (Wi-Fi LAN) - $ip"
                        InterfaceType.ETHERNET -> "إيثرنت سلكي (Ethernet) - $ip"
                        InterfaceType.HOTSPOT -> "نقطة اتصال الهاتف (Hotspot) - $ip"
                        InterfaceType.CELLULAR -> "بيانات الهاتف الخلوية (غير متاح للبث المحلي) - $ip"
                        InterfaceType.OTHER -> "$rawName - $ip"
                    }

                    val labelEn = when (type) {
                        InterfaceType.WIFI -> "Wi-Fi LAN ($rawName): $ip [Recommended]"
                        InterfaceType.ETHERNET -> "Ethernet ($rawName): $ip [Recommended]"
                        InterfaceType.HOTSPOT -> "Mobile Hotspot: $ip"
                        InterfaceType.CELLULAR -> "Cellular (Not LAN reachable): $ip"
                        InterfaceType.OTHER -> "$rawName: $ip"
                    }

                    result.add(
                        NetworkInterfaceInfo(
                            id = "${intf.name}_$ip",
                            interfaceName = intf.name,
                            ip = ip,
                            labelAr = labelAr,
                            labelEn = labelEn,
                            type = type,
                            isRecommended = isRecommended,
                            reachableFromLan = reachableFromLan
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        // Sort priority: Recommended first, Wi-Fi/Ethernet, Hotspot, Other, Cellular last
        return result.sortedWith(
            compareByDescending<NetworkInterfaceInfo> { it.isRecommended }
                .thenByDescending { it.type == InterfaceType.WIFI }
                .thenByDescending { it.type == InterfaceType.ETHERNET }
                .thenByDescending { it.type == InterfaceType.HOTSPOT }
                .thenBy { it.type == InterfaceType.CELLULAR }
        )
    }

    fun getPrimaryIpAddress(): String {
        val interfaces = getAvailableInterfaces()

        // 1. Recommended Wi-Fi or Ethernet
        val recommended = interfaces.firstOrNull { it.isRecommended }
        if (recommended != null) return recommended.ip

        // 2. Any Wi-Fi or Ethernet
        val wifiOrEth = interfaces.firstOrNull { it.type == InterfaceType.WIFI || it.type == InterfaceType.ETHERNET }
        if (wifiOrEth != null) return wifiOrEth.ip

        // 3. Hotspot
        val hotspot = interfaces.firstOrNull { it.type == InterfaceType.HOTSPOT }
        if (hotspot != null) return hotspot.ip

        // 4. Any LAN reachable interface (excluding cellular)
        val nonCellular = interfaces.firstOrNull { it.reachableFromLan }
        if (nonCellular != null) return nonCellular.ip

        // 5. Any interface at all
        val any = interfaces.firstOrNull()
        if (any != null) return any.ip

        return "127.0.0.1"
    }
}
