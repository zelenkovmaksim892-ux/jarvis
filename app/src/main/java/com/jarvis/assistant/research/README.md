# Jarvis Evidence Research Core

Shared app-side foundation for research-capable chat and specialist agents.

## Included

- `EvidenceSearchProvider`: explicit adapter contract for a user-configured search provider. No provider, API key, endpoint or paid service is silently selected.
- `EvidenceResearchEngine`: obtains candidate URLs from that provider, enforces exact per-request host allowlists, fetches approved candidates and records rejected candidates.
- `EvidenceResearchService`: bounded HTTPS retrieval, public DNS validation pinned to the HTTP call, no redirects, response-size/content-type limits, timeouts, metadata/text extraction and SHA-256.
- `EvidenceVerifier`: deterministic freshness, explicit support/contradiction and source-independence evaluation.
- `toUntrustedPromptContext()`: safely serializes source metadata and bounded page text as JSON, with explicit instructions that external content is untrusted data.

## Security boundaries

- HTTPS only, default port 443, no URL credentials, no IP-literal hosts, exact allowlisted hostnames.
- Private, loopback, link-local, multicast and IPv6 unique-local DNS answers are rejected. Validated DNS answers are pinned for the request.
- Redirects are not followed; every redirect destination needs separate review and allowlisting.
- Download defaults to 512 KiB (hard cap 2 MiB); extracted text defaults to 20,000 characters (hard cap 100,000); connect/read/call timeouts are bounded.
- Page content is untrusted data, never instructions.

## Limitations

- No search provider is bundled or configured. The app owner must choose one, review privacy/retention terms, supply credentials if needed, and implement `EvidenceSearchProvider`.
- Search snippets and fetched pages are not proof. A separate reviewer or human must assign `EvidenceStance`; this core does not infer whether text logically entails a claim.
- Primary-source labels and independence groups require review. Two mirrors of one press release must share one group.
- This commit does not wire a search provider into the chat turn loop or settings UI. It establishes the reusable API; live chat research needs provider selection and user-controlled privacy/host settings.
- It does not execute phone actions, trades, payments or account operations.

## Example

```kotlin
val engine = EvidenceResearchEngine(configuredSearchProvider)
val bundle = engine.research(
    ResearchRequest(
        query = "vendor firmware compatibility",
        approvedHosts = setOf("vendor.example", "support.vendor.example"),
        maxSources = 5,
    )
)
val promptContext = bundle.toUntrustedPromptContext()
```
