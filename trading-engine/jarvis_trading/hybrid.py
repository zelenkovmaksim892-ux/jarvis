"""Fail-closed decision gate for paper and future hybrid trading.

This module does not place orders. It evaluates policy and binds elevated-risk
approvals to one exact order payload. A live exchange adapter is intentionally
absent from this package.
"""
from __future__ import annotations

import hashlib
import json
import secrets
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from enum import Enum

from .core import D
from .verification import VerificationResult


class ExecutionMode(str, Enum):
    PAPER_ONLY = "paper_only"
    HYBRID = "hybrid"


class RiskTier(str, Enum):
    NORMAL = "normal"
    ELEVATED = "elevated"
    UNKNOWN = "unknown"


class DecisionStatus(str, Enum):
    BLOCKED = "blocked"
    PAPER_READY = "paper_ready"
    REQUIRES_APPROVAL = "requires_approval"
    LIVE_POLICY_PASSED_NOT_EXECUTABLE = "live_policy_passed_not_executable"
    LIVE_APPROVED_NOT_EXECUTABLE = "live_approved_not_executable"


@dataclass(frozen=True, slots=True)
class OrderIntent:
    symbol: str
    side: str
    quantity: Decimal
    reference_price: Decimal
    risk_tier: RiskTier = RiskTier.NORMAL
    client_order_id: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "symbol", self.symbol.strip().upper())
        object.__setattr__(self, "side", self.side.strip().lower())
        object.__setattr__(self, "quantity", D(self.quantity))
        object.__setattr__(self, "reference_price", D(self.reference_price))
        if not self.symbol or self.side not in {"buy", "sell"}:
            raise ValueError("symbol and valid side are required")
        if self.quantity <= 0 or self.reference_price <= 0:
            raise ValueError("quantity and reference_price must be positive")

    @property
    def notional(self) -> Decimal:
        return self.quantity * self.reference_price

    @property
    def payload_digest(self) -> str:
        payload = {
            "symbol": self.symbol,
            "side": self.side,
            "quantity": str(self.quantity),
            "reference_price": str(self.reference_price),
            "risk_tier": self.risk_tier.value,
            "client_order_id": self.client_order_id,
        }
        encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return hashlib.sha256(encoded).hexdigest()


@dataclass(frozen=True, slots=True)
class HybridLimits:
    allowed_symbols: tuple[str, ...]
    max_order_notional: Decimal
    max_daily_notional: Decimal
    max_market_data_age_seconds: int = 30

    def __post_init__(self) -> None:
        normalized = tuple(symbol.strip().upper() for symbol in self.allowed_symbols)
        if not normalized or any(not symbol for symbol in normalized):
            raise ValueError("allowed_symbols must contain non-empty symbols")
        object.__setattr__(self, "allowed_symbols", normalized)
        object.__setattr__(self, "max_order_notional", D(self.max_order_notional))
        object.__setattr__(self, "max_daily_notional", D(self.max_daily_notional))
        if self.max_order_notional <= 0 or self.max_daily_notional <= 0:
            raise ValueError("notional limits must be positive")
        if self.max_market_data_age_seconds <= 0:
            raise ValueError("max_market_data_age_seconds must be positive")


@dataclass(frozen=True, slots=True)
class ApprovalGrant:
    token: str
    expires_at: datetime
    payload_digest: str


@dataclass(frozen=True, slots=True)
class ExecutionDecision:
    status: DecisionStatus
    reason: str
    payload_digest: str


class ApprovalManager:
    """In-memory, one-use approvals bound to the exact intent and a short TTL."""

    def __init__(self) -> None:
        self._grants: dict[str, tuple[str, datetime, bool]] = {}

    def issue(
        self,
        intent: OrderIntent,
        *,
        user_confirmed: bool,
        now: datetime | None = None,
        ttl_seconds: int = 60,
    ) -> ApprovalGrant:
        if not user_confirmed:
            raise PermissionError("explicit user confirmation is required")
        if intent.risk_tier != RiskTier.ELEVATED:
            raise ValueError("manual approval grants are reserved for elevated-risk orders")
        if ttl_seconds <= 0:
            raise ValueError("ttl_seconds must be positive")
        issued_at = _utc(now or datetime.now(timezone.utc))
        token = secrets.token_urlsafe(32)
        expires_at = issued_at + timedelta(seconds=ttl_seconds)
        digest = intent.payload_digest
        self._grants[token] = (digest, expires_at, False)
        return ApprovalGrant(token, expires_at, digest)

    def consume(
        self,
        intent: OrderIntent,
        token: str,
        *,
        now: datetime | None = None,
    ) -> bool:
        record = self._grants.get(token)
        if record is None:
            return False
        digest, expires_at, consumed = record
        current = _utc(now or datetime.now(timezone.utc))
        if consumed or current >= expires_at or digest != intent.payload_digest:
            return False
        self._grants[token] = (digest, expires_at, True)
        return True


def _utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("datetime must be timezone-aware")
    return value.astimezone(timezone.utc)


class HybridExecutionGate:
    """Evaluate an order without ever sending it to an exchange."""

    @staticmethod
    def evaluate(
        intent: OrderIntent,
        *,
        mode: ExecutionMode,
        limits: HybridLimits,
        verification: VerificationResult,
        market_data_at: datetime,
        current_daily_notional: Decimal,
        kill_switch_active: bool,
        now: datetime | None = None,
        approval_token: str | None = None,
        approval_manager: ApprovalManager | None = None,
    ) -> ExecutionDecision:
        digest = intent.payload_digest
        current = _utc(now or datetime.now(timezone.utc))
        data_time = _utc(market_data_at)

        def blocked(reason: str) -> ExecutionDecision:
            return ExecutionDecision(DecisionStatus.BLOCKED, reason, digest)

        if kill_switch_active:
            return blocked("Kill switch is active; new orders are stopped.")
        if mode == ExecutionMode.HYBRID and not intent.client_order_id.strip():
            return blocked("Hybrid orders require a unique client_order_id for idempotency.")
        if intent.risk_tier == RiskTier.UNKNOWN:
            return blocked("Risk tier is unknown; fail-closed policy blocks the order.")
        if intent.symbol not in limits.allowed_symbols:
            return blocked("Symbol is not in the user-approved allowlist.")
        if intent.notional > limits.max_order_notional:
            return blocked("Order notional exceeds the configured per-order limit.")
        daily = D(current_daily_notional)
        if daily < 0 or daily + intent.notional > limits.max_daily_notional:
            return blocked("Order would exceed the configured daily notional limit.")
        age = (current - data_time).total_seconds()
        if age < 0 or age > limits.max_market_data_age_seconds:
            return blocked("Market data is from the future or older than the configured limit.")
        if not verification.permits_high_impact_action:
            return blocked(
                f"Verification status '{verification.status.value}' is not sufficient for trading."
            )

        if mode == ExecutionMode.PAPER_ONLY:
            return ExecutionDecision(
                DecisionStatus.PAPER_READY,
                "Policy checks passed. This is only a paper-trading decision; no order was sent.",
                digest,
            )

        if intent.risk_tier == RiskTier.ELEVATED:
            if not approval_token or approval_manager is None:
                return ExecutionDecision(
                    DecisionStatus.REQUIRES_APPROVAL,
                    "Elevated-risk order requires a fresh, exact-payload user approval.",
                    digest,
                )
            if not approval_manager.consume(intent, approval_token, now=current):
                return blocked("Approval is missing, expired, already used, or bound to another order.")
            return ExecutionDecision(
                DecisionStatus.LIVE_APPROVED_NOT_EXECUTABLE,
                "Approval validated, but live execution is not implemented in this repository.",
                digest,
            )

        return ExecutionDecision(
            DecisionStatus.LIVE_POLICY_PASSED_NOT_EXECUTABLE,
            "Policy checks passed, but no live exchange adapter exists; no order was sent.",
            digest,
        )
