package com.jarvis.assistant.research

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A fetched page is untrusted source material, not proof that a claim is true. */
data class EvidenceDocument(
    val requestedUrl: String,
    val canonicalUrl: String?,
    val host: String,
    val title: String,
    val description: String,
    val text: String,
    val publishedAt: Instant?,
    val retrievedAt: Instant,
    val sha256: String,
)

/** A search result is only a candidate URL. It has not yet been fetched or verified. */
data class EvidenceSearchHit(
    val title: String,
    val url: String,
    val snippet: String = "",
    val publisher: String = "",
)

/** Implement with a user-configured search provider; never silently use an unknown endpoint. */
fun interface EvidenceSearchProvider {
    fun search(query: String, limit: Int): List<EvidenceSearchHit>
}

data class ResearchRequest(
    val query: String,
    val approvedHosts: Set<String>,
    val maxSources: Int = 5,
) {
    init {
        require(query.isNotBlank()) { "Research query must not be blank." }
        require(approvedHosts.isNotEmpty()) { "At least one approved source host is required." }
        require(maxSources in 1..10) { "maxSources must be between 1 and 10." }
    }
}

data class ResearchBundle(
    val query: String,
    val documents: List<EvidenceDocument>,
    val rejected: List<String>,
    val generatedAt: Instant,
)

/** Candidate search + safe page retrieval. Semantic claim assessment remains a separate step. */
class EvidenceResearchEngine(
    private val searchProvider: EvidenceSearchProvider,
    private val pageFetcher: EvidenceResearchService = EvidenceResearchService(),
) {
    fun research(request: ResearchRequest, now: Instant = Instant.now()): ResearchBundle {
        val hits = searchProvider.search(request.query, request.maxSources)
        val documents = mutableListOf<EvidenceDocument>()
        val rejected = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (hit in hits) {
            if (documents.size >= request.maxSources) break
            val canonicalCandidate = hit.url.trim()
            if (!seen.add(canonicalCandidate)) continue
            try {
                documents += pageFetcher.fetch(canonicalCandidate, request.approvedHosts, now)
            } catch (error: Exception) {
                rejected += "${hit.url}: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        return ResearchBundle(request.query, documents, rejected, now)
    }
}

enum class EvidenceStance { SUPPORTS, CONTRADICTS, UNCLEAR }

data class ClaimEvidence(
    val sourceId: String,
    val publisher: String,
    val independenceGroup: String,
    val retrievedAt: Instant,
    val stance: EvidenceStance,
    val isPrimary: Boolean = false,
    val excerpt: String = "",
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(publisher.isNotBlank()) { "publisher must not be blank" }
        require(independenceGroup.isNotBlank()) { "independenceGroup must not be blank" }
    }
}

enum class EvidenceStatus {
    PRIMARY_CONFIRMED,
    MULTI_SOURCE_CONFIRMED,
    PARTIALLY_CONFIRMED,
    CONFLICTING,
    STALE,
    INSUFFICIENT,
}

data class EvidenceVerification(
    val claimId: String,
    val status: EvidenceStatus,
    val checkedAt: Instant,
    val sourceIds: List<String>,
    val staleSourceIds: List<String>,
    val reasons: List<String>,
) {
    /** Only suitable as one input to a policy gate; never a guarantee of truth. */
    val permitsHighImpactAction: Boolean
        get() = status == EvidenceStatus.PRIMARY_CONFIRMED ||
            status == EvidenceStatus.MULTI_SOURCE_CONFIRMED
}

/**
 * Bounded HTTPS page retrieval for an explicit host allowlist.
 *
 * The DNS answers are validated and pinned into the OkHttp call to reduce DNS
 * rebinding/TOCTOU exposure. Redirects are disabled and must be separately approved.
 */
class EvidenceResearchService(
    private val client: OkHttpClient = defaultClient(),
    private val maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
    private val maxTextChars: Int = DEFAULT_MAX_TEXT_CHARS,
    private val resolveHost: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
) {
    init {
        require(maxBodyBytes in 1..MAX_ALLOWED_BODY_BYTES)
        require(maxTextChars in 1..MAX_ALLOWED_TEXT_CHARS)
    }

    fun fetch(
        url: String,
        allowedHosts: Set<String>,
        retrievedAt: Instant = Instant.now(),
    ): EvidenceDocument {
        val uri = validateUrl(url, allowedHosts)
        val host = uri.host.lowercase(Locale.ROOT)
        val addresses = try { resolveHost(host) } catch (_: Exception) {
            throw IllegalArgumentException("Source hostname could not be resolved.")
        }
        require(addresses.isNotEmpty() && addresses.none(::isNonPublicAddress)) {
            "Source host resolves to a local, private, or otherwise non-public address."
        }
        val pinnedClient = client.newBuilder().dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                if (!hostname.equals(host, ignoreCase = true)) {
                    throw UnknownHostException("Unapproved secondary hostname.")
                }
                return addresses
            }
        }).followRedirects(false).followSslRedirects(false).build()
        val request = Request.Builder()
            .url(uri.toString())
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        pinnedClient.newCall(request).execute().use { response ->
            check(response.code == 200) { "Source returned HTTP ${response.code}; redirects and non-200 responses are not followed." }
            val body = response.body ?: error("Source returned an empty response body.")
            val contentType = body.contentType()?.let { "${it.type}/${it.subtype}" }?.lowercase(Locale.ROOT)
            check(contentType == null || contentType == "text/html" || contentType == "application/xhtml+xml") {
                "Unsupported source content type: $contentType"
            }
            val declaredLength = body.contentLength()
            check(declaredLength < 0 || declaredLength <= maxBodyBytes) { "Source exceeds the configured download limit." }
            val bytes = readBounded(body.byteStream(), maxBodyBytes)
            val charset = body.contentType()?.charset(Charset.forName("UTF-8")) ?: Charsets.UTF_8
            val parsed = parseHtml(bytes.toString(charset), maxTextChars)
            check(parsed.text.isNotBlank() || parsed.title.isNotBlank()) { "No readable page content was found." }
            return EvidenceDocument(
                requestedUrl = uri.toString(),
                canonicalUrl = parsed.canonicalUrl?.takeIf { canonical ->
                    runCatching { validateUrl(canonical, allowedHosts) }.isSuccess
                },
                host = host,
                title = parsed.title,
                description = parsed.description,
                text = parsed.text,
                publishedAt = parsed.publishedAt,
                retrievedAt = retrievedAt,
                sha256 = sha256(bytes),
            )
        }
    }

    private fun validateUrl(raw: String, allowedHosts: Set<String>): URI {
        require(raw.length <= MAX_URL_LENGTH) { "URL is too long." }
        val uri = try { URI(raw.trim()) } catch (_: Exception) { throw IllegalArgumentException("Invalid source URL.") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS source URLs are allowed." }
        require(uri.userInfo == null) { "URLs containing credentials are not allowed." }
        require(uri.port == -1 || uri.port == 443) { "Non-standard HTTPS ports are not allowed." }
        val rawHost = uri.host ?: throw IllegalArgumentException("Source URL has no valid hostname.")
        require(!isIpLiteral(rawHost)) { "IP-literal source hosts are not allowed." }
        val host = IDN.toASCII(rawHost).lowercase(Locale.ROOT).trimEnd('.')
        require(host.isNotBlank()) { "Source URL has no valid hostname." }
        require(host != "localhost" && !host.endsWith(".localhost")) { "Local source hosts are not allowed." }
        val allowed = allowedHosts.map { IDN.toASCII(it.trim()).lowercase(Locale.ROOT).trimEnd('.') }.toSet()
        require(host in allowed) { "Source host '$host' is not in the approved host allowlist." }
        return uri
    }

    private fun isIpLiteral(host: String): Boolean =
        host.contains(':') || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))

    private fun isNonPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return true
        if (address is Inet6Address) {
            val first = address.address.first().toInt() and 0xff
            if ((first and 0xfe) == 0xfc) return true
        }
        return false
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit) { "Source exceeds the configured download limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_MAX_BODY_BYTES = 512 * 1024
        const val DEFAULT_MAX_TEXT_CHARS = 20_000
        private const val MAX_ALLOWED_BODY_BYTES = 2 * 1024 * 1024
        private const val MAX_ALLOWED_TEXT_CHARS = 100_000
        private const val MAX_URL_LENGTH = 2048
        private const val USER_AGENT = "JarvisEvidenceResearch/1.0"

        private val META_TAG = Regex("""<meta\b([^>]*?)>""", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
        private val TITLE = Regex("""<title\b[^>]*>(.*?)</title\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val REMOVE_BLOCKS = Regex("""<(script|style|noscript|svg|template)\b[^>]*>.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val TAGS = Regex("""<[^>]+>""")
        private val SPACE = Regex("""\s+""")

        internal data class ParsedHtml(
            val title: String,
            val description: String,
            val canonicalUrl: String?,
            val publishedAt: Instant?,
            val text: String,
        )

        internal fun parseHtml(html: String, maxTextChars: Int = DEFAULT_MAX_TEXT_CHARS): ParsedHtml {
            val meta = META_TAG.findAll(html).mapNotNull { match ->
                val attrs = ATTRIBUTE.findAll(match.groupValues[1]).associate { a ->
                    a.groupValues[1].lowercase(Locale.ROOT) to
                        (a.groupValues[2].ifEmpty { a.groupValues[3].ifEmpty { a.groupValues[4] } })
                }
                val key = attrs["property"] ?: attrs["name"] ?: attrs["itemprop"]
                val value = attrs["content"]
                if (key.isNullOrBlank() || value.isNullOrBlank()) null else key.lowercase(Locale.ROOT) to decodeEntities(value)
            }.toMap()

            val title = TITLE.find(html)?.groupValues?.get(1)?.let(::plainText).orEmpty()
                .ifBlank { meta["og:title"].orEmpty() }
            val description = meta["description"] ?: meta["og:description"].orEmpty()
            val canonical = Regex(
                """<link\b(?=[^>]*\brel\s*=\s*["']?canonical["']?)[^>]*\bhref\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>]+))""",
                RegexOption.IGNORE_CASE,
            ).find(html)?.let { it.groupValues[1].ifEmpty { it.groupValues[2].ifEmpty { it.groupValues[3] } } }
                ?.let(::decodeEntities)
            val dateValue = listOf(
                meta["article:published_time"], meta["datepublished"], meta["date"],
                meta["pubdate"], meta["og:published_time"],
            ).firstOrNull { !it.isNullOrBlank() }
            val published = dateValue?.let(::parseDate)
            val text = plainText(REMOVE_BLOCKS.replace(html, " ")).take(maxTextChars)
            return ParsedHtml(title, description, canonical, published, text)
        }

        private fun plainText(html: String): String =
            decodeEntities(TAGS.replace(html, " ")).replace(SPACE, " ").trim()

        private fun decodeEntities(value: String): String {
            var result = value
            val named = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ")
            named.forEach { (entity, replacement) -> result = result.replace(entity, replacement, ignoreCase = true) }
            result = Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(result) { match ->
                val token = match.groupValues[1]
                val code = runCatching {
                    if (token.startsWith("x", true)) token.substring(1).toInt(16) else token.toInt()
                }.getOrNull()
                code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: " "
            }
            return result
        }

        private fun parseDate(value: String): Instant? {
            val trimmed = value.trim()
            return runCatching { Instant.parse(trimmed) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
                ?: runCatching { java.time.LocalDate.parse(trimmed).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() }.getOrNull()
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}

/** Deterministic freshness/conflict evaluator; semantic stance must be assessed separately. */
object EvidenceVerifier {
    fun verify(
        claimId: String,
        evidence: List<ClaimEvidence>,
        now: Instant = Instant.now(),
        maxAge: Duration = Duration.ofHours(24),
    ): EvidenceVerification {
        require(claimId.isNotBlank()) { "claimId must not be blank" }
        require(!maxAge.isNegative && !maxAge.isZero) { "maxAge must be positive" }
        require(evidence.map { it.sourceId }.distinct().size == evidence.size) { "sourceId values must be unique" }

        val fresh = evidence.filter {
            val age = Duration.between(it.retrievedAt, now)
            !age.isNegative && age <= maxAge
        }
        val stale = evidence.filterNot { it in fresh }
        val sourceIds = fresh.map { it.sourceId }
        val staleIds = stale.map { it.sourceId }

        if (fresh.isEmpty()) {
            val status = if (stale.isEmpty()) EvidenceStatus.INSUFFICIENT else EvidenceStatus.STALE
            return EvidenceVerification(
                claimId, status, now, sourceIds, staleIds,
                listOf(if (stale.isEmpty()) "No evidence was supplied." else "No fresh evidence is available."),
            )
        }

        val supporting = fresh.filter { it.stance == EvidenceStance.SUPPORTS }
        val contradicting = fresh.filter { it.stance == EvidenceStance.CONTRADICTS }
        if (supporting.isNotEmpty() && contradicting.isNotEmpty()) {
            return EvidenceVerification(
                claimId, EvidenceStatus.CONFLICTING, now, sourceIds, staleIds,
                listOf("Fresh evidence contains both supporting and contradicting assessments."),
            )
        }
        if (supporting.isEmpty()) {
            val unclearOnly = fresh.all { it.stance == EvidenceStance.UNCLEAR }
            return EvidenceVerification(
                claimId,
                if (unclearOnly) EvidenceStatus.INSUFFICIENT else EvidenceStatus.CONFLICTING,
                now, sourceIds, staleIds,
                listOf(if (unclearOnly) "Sources were fetched but no support assessment was made." else "Fresh evidence does not support the claim."),
            )
        }

        val status = when {
            supporting.any { it.isPrimary } -> EvidenceStatus.PRIMARY_CONFIRMED
            supporting.map { it.independenceGroup }.distinct().size >= 2 -> EvidenceStatus.MULTI_SOURCE_CONFIRMED
            else -> EvidenceStatus.PARTIALLY_CONFIRMED
        }
        val reason = when (status) {
            EvidenceStatus.PRIMARY_CONFIRMED -> "At least one fresh primary source was explicitly assessed as supporting."
            EvidenceStatus.MULTI_SOURCE_CONFIRMED -> "At least two independent fresh source groups were explicitly assessed as supporting."
            else -> "Support exists, but primary or independent corroboration is missing."
        }
        return EvidenceVerification(claimId, status, now, sourceIds, staleIds, listOf(reason))
    }
}
