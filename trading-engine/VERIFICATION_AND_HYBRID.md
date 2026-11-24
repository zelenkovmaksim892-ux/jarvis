# Verification and hybrid execution boundary

## Evidence verification

`jarvis_trading.verification` stores provenance for each evidence item: source ID,
publisher, URL, independence group, retrieval time, optional publication time,
an explicit support/contradiction assessment, and an optional excerpt.

The verifier applies deterministic rules:

- stale or future-dated retrievals are excluded;
- a fresh supporting and a fresh contradicting assessment produce
  `conflicting`;
- one fresh primary source produces `primary_confirmed`;
- two or more explicitly independent fresh source groups produce
  `multi_source_confirmed`;
- otherwise support is only `partially_confirmed`;
- no usable evidence produces `stale` or `insufficient`.

Only primary-confirmed and multi-source-confirmed results permit high-impact
actions. Independence groups must be assigned carefully: two sites repeating
the same press release are one source group, not independent corroboration.

**Important limitation:** this is not a semantic fact-finding engine. The
`supports_claim` label must be supplied by a separately validated extractor
or a human reviewer. This module verifies provenance, freshness and consistency
of those labels; it cannot prove that a quotation logically entails a claim,
predict future events, or guarantee that a source is truthful.

## Hybrid gate

`jarvis_trading.hybrid` evaluates allowlisted symbols, per-order and daily
notional limits, market-data freshness, verification status, risk tier and
the kill switch. It fails closed on unknown risk, stale data, weak verification,
limit violations and active kill switch.

Elevated-risk approvals are:

- issued only when `user_confirmed=True`;
- bound to a SHA-256 digest of the exact order payload;
- short-lived and single-use;
- invalid for a changed symbol, side, quantity, price, risk tier or client order ID.

The gate returns a decision only. It does not submit orders. In this release,
even a policy-passed or approved hybrid decision is explicitly marked
`not_executable`: no live exchange adapter, account access, API-key storage,
withdrawal or transfer capability exists. Paper decisions can be passed to
the existing in-memory `PaperAccount` by a caller; this gate itself does not
execute them.

The kill switch blocks new orders. It does not close existing positions or
claim that positions have been flattened. Existing positions must be surfaced
separately and handled only under a separately approved policy.

## Trading safety

Bybit remains a public market-data source only. Yahoo Finance history and
Frankfurter reference FX rates are not executable broker quotes. Stocks and FX
need separately selected, region-eligible providers before any future live
integration. No return or forecast is guaranteed; fees, spread, slippage,
liquidity, outages and market gaps can materially change outcomes.
