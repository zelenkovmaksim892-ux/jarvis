"""Evidence-based verification primitives.

This module checks provenance, freshness and explicit support/conflict labels.
It does not decide whether a natural-language excerpt semantically proves a claim;
that judgement must come from a separately audited extractor or human reviewer.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from typing import Iterable


class VerificationStatus(str, Enum):
    PRIMARY_CONFIRMED = "primary_confirmed"
    MULTI_SOURCE_CONFIRMED = "multi_source_confirmed"
    PARTIALLY_CONFIRMED = "partially_confirmed"
    CONFLICTING = "conflicting"
    STALE = "stale"
    INSUFFICIENT = "insufficient"


@dataclass(frozen=True, slots=True)
class Evidence:
    source_id: str
    publisher: str
    url: str
    independence_key: str
    retrieved_at: datetime
    supports_claim: bool
    is_primary: bool = False
    published_at: datetime | None = None
    excerpt: str = ""

    def __post_init__(self) -> None:
        for name in ("source_id", "publisher", "url", "independence_key"):
            if not getattr(self, name).strip():
                raise ValueError(f"{name} must not be empty")
        _require_aware(self.retrieved_at, "retrieved_at")
        if self.published_at is not None:
            _require_aware(self.published_at, "published_at")


@dataclass(frozen=True, slots=True)
class VerificationPolicy:
    max_age_seconds: int = 86_400

    def __post_init__(self) -> None:
        if self.max_age_seconds <= 0:
            raise ValueError("max_age_seconds must be positive")


@dataclass(frozen=True, slots=True)
class VerificationResult:
    claim_id: str
    status: VerificationStatus
    checked_at: datetime
    source_ids: tuple[str, ...]
    stale_source_ids: tuple[str, ...]
    reasons: tuple[str, ...]

    @property
    def permits_high_impact_action(self) -> bool:
        return self.status in {
            VerificationStatus.PRIMARY_CONFIRMED,
            VerificationStatus.MULTI_SOURCE_CONFIRMED,
        }


def _require_aware(value: datetime, field: str) -> None:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError(f"{field} must be timezone-aware")


def verify_claim(
    claim_id: str,
    evidence: Iterable[Evidence],
    policy: VerificationPolicy = VerificationPolicy(),
    now: datetime | None = None,
) -> VerificationResult:
    """Classify evidence using freshness, explicit stance and source independence.

    Two URLs from the same publisher/group count as one source. A fresh
    contradiction always prevents a confirmed status. Stale evidence is retained
    in provenance but never used to confirm a claim.
    """
    if not claim_id.strip():
        raise ValueError("claim_id must not be empty")
    checked_at = now or datetime.now(timezone.utc)
    _require_aware(checked_at, "now")
    checked_at = checked_at.astimezone(timezone.utc)

    items = tuple(evidence)
    ids = [item.source_id for item in items]
    if len(ids) != len(set(ids)):
        raise ValueError("source_id values must be unique")

    fresh: list[Evidence] = []
    stale: list[Evidence] = []
    for item in items:
        age = (checked_at - item.retrieved_at.astimezone(timezone.utc)).total_seconds()
        if age < 0 or age > policy.max_age_seconds:
            stale.append(item)
        else:
            fresh.append(item)

    source_ids = tuple(item.source_id for item in fresh)
    stale_ids = tuple(item.source_id for item in stale)
    if not fresh:
        status = VerificationStatus.STALE if stale else VerificationStatus.INSUFFICIENT
        reason = "No fresh evidence is available." if stale else "No evidence was supplied."
        return VerificationResult(claim_id, status, checked_at, source_ids, stale_ids, (reason,))

    supporters = [item for item in fresh if item.supports_claim]
    contradictors = [item for item in fresh if not item.supports_claim]
    if supporters and contradictors:
        return VerificationResult(
            claim_id, VerificationStatus.CONFLICTING, checked_at, source_ids, stale_ids,
            ("Fresh evidence contains both supporting and contradicting assessments.",),
        )
    if not supporters:
        return VerificationResult(
            claim_id, VerificationStatus.CONFLICTING, checked_at, source_ids, stale_ids,
            ("Fresh evidence does not support the claim.",),
        )

    primary = any(item.is_primary for item in supporters)
    independent_groups = {item.independence_key for item in supporters}
    if primary:
        status = VerificationStatus.PRIMARY_CONFIRMED
        reason = "At least one fresh primary source was explicitly marked as supporting."
    elif len(independent_groups) >= 2:
        status = VerificationStatus.MULTI_SOURCE_CONFIRMED
        reason = "At least two fresh, explicitly independent source groups support the claim."
    else:
        status = VerificationStatus.PARTIALLY_CONFIRMED
        reason = "Support exists, but primary or independent corroboration is missing."

    return VerificationResult(claim_id, status, checked_at, source_ids, stale_ids, (reason,))
