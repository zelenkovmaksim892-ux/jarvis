# Jarvis Evidence Research Core

Shared app-side foundation for research-capable chat and specialist agents.

## Free search option: self-hosted SearXNG

Jarvis supports a user-configured SearXNG instance through `SearxngSearchProvider`.
SearXNG is free/open-source software; the machine, electricity, network and optional
domain/TLS hosting are not necessarily free. Jarvis does not ship public SearXNG
instances, does not select a third-party endpoint, and does not send searches anywhere
until the user configures an instance URL.

Recommended deployment:
- Run SearXNG on a computer or server you control, using the official SearXNG project
  and its maintained deployment documentation.
- Expose it only through HTTPS with a valid certificate.
- Do not publish an unauthenticated instance to the open internet. Restrict access
  with a firewall or private network/VPN, and keep the instance updated.
- Configure only that instance URL in Jarvis. No API key is needed for a normal
  self-hosted SearXNG JSON endpoint.
- If the instance is unavailable, Jarvis fails closed; it does not silently switch
  to a public search server or paid provider.

No third-party public search servers are preconfigured. A public instance can observe
queries and may be unreliable or malicious, so it is not treated as a safe default.

## Included

- `EvidenceSearchProvider`: explicit adapter contract.
- `SearxngSearchProvider`: HTTPS-only adapter for a user-configured SearXNG instance,
  bounded response size, timeouts, no redirects, no credentials in the URL.
- `EvidenceResearchEngine`: obtains candidate URLs from the provider, enforces exact
  per-request host allowlists, fetches approved candidates and records rejected candidates.
- `EvidenceResearchService`: bounded HTTPS retrieval, public DNS validation pinned to
  the HTTP call, no redirects, response-size/content-type limits, timeouts, metadata/text
  extraction and SHA-256.
- `EvidenceVerifier`: deterministic freshness, explicit support/contradiction and
  source-independence evaluation.
- `toUntrustedPromptContext()`: serializes source metadata and bounded page text as
  JSON, with explicit instructions that external content is untrusted data.

## Security boundaries

- HTTPS only for the search endpoint and fetched source pages; default port 443.
- No URL credentials, public-instance directory, automatic fallback or implicit provider.
- Search response limited to 512 KiB; connect/read/call timeouts are bounded; redirects disabled.
- Search hits are untrusted candidates, not verified sources. The separate page fetcher
  requires an exact host allowlist and rejects private, loopback, link-local, multicast
  and IPv6 unique-local DNS answers.
- Page content is untrusted data, never instructions.
- Configure SearXNG access controls yourself. HTTPS alone does not make a public server
  trustworthy or guarantee privacy.

## Limitations

- SearXNG software is free; hosting it on a separate always-on VPS may cost money.
  Running it on your own PC is usually the no-hosting-fee option, but the PC must be on.
- The Android app adapter is not yet wired into the chat turn loop or settings UI.
  The user must configure and approve the instance before it can be used.
- Search snippets and fetched pages are not proof. A separate reviewer or human must
  assign `EvidenceStance`; this core does not infer whether text logically entails a claim.
- Primary-source labels and independence groups require review. Two mirrors of one
  press release must share one group.
- This module does not execute phone actions, trades, payments or account operations.
