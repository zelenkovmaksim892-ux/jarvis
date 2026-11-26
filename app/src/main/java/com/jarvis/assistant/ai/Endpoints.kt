package com.jarvis.assistant.ai

import java.net.InetAddress
import java.net.URI

object Endpoints {
    private fun parse(url: String): URI? {
        val t = url.trim()
        if (t.isEmpty()) return null
        return runCatching { URI(if ("://" in t) t else "http://$t") }.getOrNull()
    }
    fun host(url: String): String? = parse(url)?.host?.lowercase()
    private fun ipAddress(host: String): InetAddress? {
        val value = host.removePrefix("[").removeSuffix("]")
        if (value.contains(':')) {
            if (!value.matches(Regex("[0-9a-fA-F:.]+"))) return null
            return runCatching { InetAddress.getByName(value) }.getOrNull()
        }
        if (!value.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return null
        val octets = value.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return runCatching { InetAddress.getByAddress(octets.map { it.toByte() }.toByteArray()) }.getOrNull()
    }
    fun isLoopback(url: String): Boolean {
        val h = host(url) ?: return false
        if (h == "localhost") return true
        return ipAddress(h)?.isLoopbackAddress == true
    }
    fun isTransportSafe(url: String): Boolean {
        val uri = parse(url) ?: return false
        if (uri.userInfo != null || uri.host.isNullOrBlank()) return false
        val scheme = uri.scheme ?: return false
        return scheme.equals("https", true) || (scheme.equals("http", true) && isLoopback(url))
    }
    @Suppress("UNUSED_PARAMETER")
    fun isCredentialTransportSafe(url: String, hasCredentials: Boolean): Boolean = isTransportSafe(url)
    fun isLocal(url: String): Boolean {
        if (isLoopback(url)) return true
        val h = host(url) ?: return false
        if (h.endsWith(".local")) return true
        val address = ipAddress(h) ?: return false
        val bytes = address.address.map { it.toInt() and 0xff }
        if (bytes.size == 4) {
            val x = bytes[0]; val y = bytes[1]
            return x == 10 || (x == 192 && y == 168) || (x == 172 && y in 16..31)
        }
        if (bytes.size == 16) return (bytes[0] and 0xfe) == 0xfc || address.isLinkLocalAddress
        return false
    }
    fun origin(url: String): String? {
        val u = parse(url) ?: return null
        val h = u.host ?: return null
        val port = if (u.port > 0) ":" + u.port else ""
        return (u.scheme ?: "http") + "://" + h + port
    }
}