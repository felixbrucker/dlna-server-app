package com.felixbrucker.dlnaserver.core.util

import com.felixbrucker.dlnaserver.core.model.NetworkInterfaceInfo
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getActiveNetworkInterface(): NetworkInterfaceInfo? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (networkInterface in interfaces.toList()) {
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }
                val addresses = networkInterface.inetAddresses
                for (inetAddress in addresses.toList()) {
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val hostAddress = inetAddress.hostAddress ?: continue
                        return NetworkInterfaceInfo(
                            name = networkInterface.name,
                            displayName = networkInterface.displayName ?: networkInterface.name,
                            ipAddress = hostAddress
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun getAvailableNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val list = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (networkInterface in interfaces.toList()) {
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }
                val addresses = networkInterface.inetAddresses
                for (inetAddress in addresses.toList()) {
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val hostAddress = inetAddress.hostAddress ?: continue
                        list.add(
                            NetworkInterfaceInfo(
                                name = networkInterface.name,
                                displayName = networkInterface.displayName ?: networkInterface.name,
                                ipAddress = hostAddress
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun getNetworkInterfaceByName(name: String): NetworkInterfaceInfo? {
        if (name == "auto") {
            return getActiveNetworkInterface()
        }
        val available = getAvailableNetworkInterfaces()
        return available.firstOrNull { it.name == name } ?: getActiveNetworkInterface()
    }
}
