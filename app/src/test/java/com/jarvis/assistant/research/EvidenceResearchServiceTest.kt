package com.jarvis.assistant.research

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

class EvidenceResearchServiceTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")

    @Test
    fun parsesTitleDescriptionCanonicalDateAndReadableText() {
        val html = """
            <html><head>
            <title>Device &amp; Firmware</title>
            <meta name="description" content="Official release notes">
            <meta property="article:published_time" content="2026-11-20T10:30:00Z">
            <link rel="canonical" href="https://docs.example.com/release">
            <script>ignoreSecret()</script><style>.hidden{}</style>
            </head><body><h1>Release 4</h1><p>Supports Android 14.</p></body></html>
        """.trimIndent()
        val parsed = EvidenceResearchService.parseHtml(html)
        assertEquals("Device & Firmware", parsed.title)
        assertEquals("Official release notes", parsed.description)
        assertEquals("https://docs.example.com/release", parsed.canonicalUrl)
        assertEquals(Instant.parse("2026-11-20T10:30:00Z"), parsed.publishedAt)
        assertTrue(parsed.text.contains("Supports Android 14."))
        assertFalse(parsed.text.contains("ignoreSecret"))
    }

    @Test
    fun textExtractionHonorsCharacterLimit() {
        val parsed = EvidenceResearchService.parseHtml("<p>${"x".repeat(100)}</p>", 12)
        assertEquals(12, parsed.text.length)
    }

    @Test
    fun refusesNonHttpsUrlBeforeNetworkAccess() {
        val service = EvidenceResearchService()
        val error = runCatching { service.fetch("http://example.com", setOf("example.com"), now) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun refusesHostOutsideAllowlistBeforeNetworkAccess() {
        val service = EvidenceResearchService()
        val error = runCatching { service.fetch("https://example.com", setOf("trusted.example"), now) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("allowlist"))
    }

    @Test
    fun rejectsPrivateDnsAnswersBeforeConnecting() {
        val service = EvidenceResearchService(resolveHost = { listOf(InetAddress.getByName("127.0.0.1")) })
        val error = runCatching {
            service.fetch("https://docs.example.com/article", setOf("docs.example.com"), now)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("private"))
    }

    @Test
    fun primarySourceCanBeMarkedConfirmedOnlyWhenFreshAndExplicitlySupporting() {
        val result = EvidenceVerifier.verify(
            "android-version",
            listOf(ClaimEvidence("a", "Vendor", "vendor", now.minusSeconds(30), EvidenceStance.SUPPORTS, isPrimary = true)),
            now,
        )
        assertEquals(EvidenceStatus.PRIMARY_CONFIRMED, result.status)
        assertTrue(result.permitsHighImpactAction)
    }

    @Test
    fun copiedPagesFromSamePublisherDoNotCountAsIndependent() {
        val result = EvidenceVerifier.verify(
            "claim",
            listOf(
                ClaimEvidence("a", "Mirror A", "same-release", now, EvidenceStance.SUPPORTS),
                ClaimEvidence("b", "Mirror B", "same-release", now, EvidenceStance.SUPPORTS),
            ), now,
        )
        assertEquals(EvidenceStatus.PARTIALLY_CONFIRMED, result.status)
        assertFalse(result.permitsHighImpactAction)
    }

    @Test
    fun independentFreshSourcesCanConfirm() {
        val result = EvidenceVerifier.verify(
            "claim",
            listOf(
                ClaimEvidence("a", "Publisher A", "group-a", now, EvidenceStance.SUPPORTS),
                ClaimEvidence("b", "Publisher B", "group-b", now, EvidenceStance.SUPPORTS),
            ), now,
        )
        assertEquals(EvidenceStatus.MULTI_SOURCE_CONFIRMED, result.status)
    }

    @Test
    fun freshContradictionBlocksConfirmation() {
        val result = EvidenceVerifier.verify(
            "claim",
            listOf(
                ClaimEvidence("a", "Publisher A", "group-a", now, EvidenceStance.SUPPORTS),
                ClaimEvidence("b", "Publisher B", "group-b", now, EvidenceStance.CONTRADICTS),
            ), now,
        )
        assertEquals(EvidenceStatus.CONFLICTING, result.status)
        assertFalse(result.permitsHighImpactAction)
    }

    @Test
    fun staleEvidenceCannotConfirm() {
        val result = EvidenceVerifier.verify(
            "claim",
            listOf(ClaimEvidence("a", "Publisher", "group-a", now.minus(Duration.ofDays(2)), EvidenceStance.SUPPORTS)),
            now,
        )
        assertEquals(EvidenceStatus.STALE, result.status)
        assertFalse(result.permitsHighImpactAction)
    }

    @Test
    fun unclearEvidenceIsInsufficient() {
        val result = EvidenceVerifier.verify(
            "claim",
            listOf(ClaimEvidence("a", "Publisher", "group-a", now, EvidenceStance.UNCLEAR)),
            now,
        )
        assertEquals(EvidenceStatus.INSUFFICIENT, result.status)
    }

    @Test
    fun duplicateSourceIdsAreRejected() {
        val duplicate = listOf(
            ClaimEvidence("same", "A", "a", now, EvidenceStance.SUPPORTS),
            ClaimEvidence("same", "B", "b", now, EvidenceStance.SUPPORTS),
        )
        assertThrows(IllegalArgumentException::class.java) { EvidenceVerifier.verify("claim", duplicate, now) }
    }

    @Test
    fun researchEngineRejectsUnapprovedSearchHits() {
        var requestedLimit = 0
        val provider = EvidenceSearchProvider { query, limit ->
            assertEquals("firmware support", query)
            requestedLimit = limit
            listOf(EvidenceSearchHit("Unexpected site", "https://evil.example/article"))
        }
        val result = EvidenceResearchEngine(provider).research(
            ResearchRequest("firmware support", setOf("docs.vendor.example"), 3), now,
        )
        assertEquals(3, requestedLimit)
        assertTrue(result.documents.isEmpty())
        assertEquals(1, result.rejected.size)
    }

    @Test
    fun sourceContextLabelsExternalTextAsUntrustedAndBoundsIt() {
        val doc = EvidenceDocument(
            "https://docs.example.com/a", null, "docs.example.com",
            "Title", "Description", "abcdefgh", null, now, "hash",
        )
        val context = ResearchBundle("query", listOf(doc), emptyList(), now)
            .toUntrustedPromptContext(maxCharsPerSource = 4)
        assertTrue(context.contains("untrusted data"))
        assertTrue(context.contains("abcd"))
        assertFalse(context.contains("abcdefgh"))
    }
}
