package com.jarvis.assistant.ai

import java.net.InetAddress
import java.net.URI

/** Where an AI endpoint lives: on this phone (loopback), on the local network, or in the cloud. */
object Endpoints {
    private fun parse(url: String): URI? {
        val t = url.trim()
        if (t.isEmpty()) return null
        return runCatching { URI(if ("://" in t) t else "http://$t") }.getOrNull()
    }

    fun host(url: String): String? = parse(url)?.host?.lowercase()

    /** Parse only literal IP addresses. Never resolve hostnames here: classification must not trust DNS. */
    private fun ipAddress(host: String): InetAddress? {
        val value = host.removePrefix("[").removeSuffix("]")
        if (value.contains(':')) {
            if (!value.matches(Regex("[0-9a-fA-F:.%]+"))) return null
            return runCatching { InetAddress.getByName(value) }.getOrNull()
        }
        if (!value.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) return null
        val octets = value.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return runCatching { InetAddress.getByAddress(octets.map(Int::toByte).toByteArray()) }.getOrNull()
    }

    /** True for a server running on this phone, e.g. llama-server in Termux. */
    fun isLoopback(url: String): Boolean {
        val h = host(url) ?: return false
        if (h == "localhost") return true
        val address = ipAddress(h) ?: return false
        return address.isLoopbackAddress
    }

    /**
     * All remote endpoints must use HTTPS, even when they do not require an API key.
     * Cleartext HTTP is allowed only for loopback services on this device.
     * URLs containing embedded user-info are rejected to avoid credential leakage.
     */
    fun isTransportSafe(url: String): Boolean {
        val uri = parse(url) ?: return false
        if (uri.userInfo != null || uri.host.isNullOrBlank()) return false
        val scheme = uri.scheme ?: return false
        return scheme.equals("https", ignoreCase = true) ||
            (scheme.equals("http", ignoreCase = true) && isLoopback(url))
    }

    /** Backwards-compatible name; transport safety is enforced regardless of whether a key is present. */
    @Suppress("UNUSED_PARAMETER")
    fun isCredentialTransportSafe(url: String, hasCredentials: Boolean): Boolean = isTransportSafe(url)

    /** True when the endpoint works without the internet: this phone or the local network. */
    fun isLocal(url: String): Boolean {
        if (isLoopback(url)) return true
        val h = host(url) ?: return false
        if (h == "localhost" || h.endsWith(".local")) return true
        val address = ipAddress(h) ?: return false
        val bytes = address.address.map { it.toInt() and 0xff }
        if (bytes.size == 4) {
            val a = bytes[0]
            val b = bytes[1]
            return a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31)
        }
        if (bytes.size == 16) {
            val first = bytes[0]
            return (first and 0xfe) == 0xfc || address.isLinkLocalAddress
        }
        return false
    }

    /** `http://127.0.0.1:8080/v1` -> `http://127.0.0.1:8080`. */
    fun origin(url: String): String? {
        val u = parse(url) ?: return null
        val h = u.host ?: return null
        val port = if (u.port > 0) ":" + u.port else ""
        return (u.scheme ?: "http") + "://" + h + port
    }
}