package com.jarvis.assistant.research

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Search adapter for a SearXNG instance explicitly configured by the user.
 *
 * Jarvis ships no public-instance list and never silently sends queries to a third party.
 * The instance must use HTTPS; search hits remain untrusted candidates and are still
 * subject to EvidenceResearchService's per-request source-host allowlist.
 */
class SearxngSearchProvider(
    instanceUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : EvidenceSearchProvider {
    private val endpoint: HttpUrl = validateInstanceUrl(instanceUrl)

    override fun search(query: String, limit: Int): List<EvidenceSearchHit> {
        require(query.isNotBlank()) { "Search query must not be blank." }
        require(limit in 1..10) { "Search limit must be between 1 and 10." }

        val url = endpoint.newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("categories", "general")
            .addQueryParameter("language", "all")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "JarvisEvidenceResearch/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            check(response.code == 200) { "SearXNG returned HTTP ${response.code}." }
            val body = response.body ?: error("SearXNG returned an empty response.")
            check(body.contentLength() < 0 || body.contentLength() <= MAX_RESPONSE_BYTES) {
                "SearXNG response exceeds the configured limit."
            }
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MAX_RESPONSE_BYTES) { "SearXNG response exceeds the configured limit." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val results = json.optJSONArray("results") ?: return emptyList()
            return (0 until minOf(results.length(), limit)).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title").take(MAX_FIELD_CHARS)
                val target = item.optString("url")
                if (title.isBlank() || target.isBlank()) return@mapNotNull null
                EvidenceSearchHit(
                    title = title,
                    url = target,
                    snippet = item.optString("content").take(MAX_FIELD_CHARS),
                    publisher = item.optString("engine").take(MAX_FIELD_CHARS),
                )
            }
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val MAX_FIELD_CHARS = 2_000

        private fun validateInstanceUrl(value: String): HttpUrl {
            require(value.length <= 2_048) { "SearXNG instance URL is too long." }
            val uri = runCatching { URI(value.trim()) }.getOrElse {
                throw IllegalArgumentException("Invalid SearXNG instance URL.")
            }
            require(uri.scheme.equals("https", ignoreCase = true)) {
                "SearXNG must be configured with HTTPS. Plain HTTP is not accepted."
            }
            require(uri.userInfo == null && (uri.port == -1 || uri.port == 443)) {
                "SearXNG URL credentials and non-standard ports are not allowed."
            }
            require(uri.query == null && uri.fragment == null) {
                "SearXNG instance URL must not contain a query or fragment."
            }
            return uri.toString().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid SearXNG instance URL.")
        }

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}
