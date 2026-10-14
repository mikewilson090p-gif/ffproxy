package com.venice.ffproxy

import java.net.Inet4Address
import java.net.NetworkInterface

object Util {
    fun localIpv4(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress ?: "0.0.0.0"
    } catch (t: Throwable) {
        "0.0.0.0"
    }
}