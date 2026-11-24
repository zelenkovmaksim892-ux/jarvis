import unittest
from datetime import datetime, timedelta, timezone
from decimal import Decimal

from jarvis_trading.hybrid import (
    ApprovalManager, DecisionStatus, ExecutionMode, HybridExecutionGate,
    HybridLimits, OrderIntent, RiskTier,
)
from jarvis_trading.verification import VerificationResult, VerificationStatus


NOW = datetime(2026, 11, 25, 12, 0, tzinfo=timezone.utc)
VERIFIED = VerificationResult(
    claim_id="market-feed",
    status=VerificationStatus.PRIMARY_CONFIRMED,
    checked_at=NOW,
    source_ids=("bybit-public",),
    stale_source_ids=(),
    reasons=("Fresh primary feed.",),
)
LIMITS = HybridLimits(("BTC/USDT",), "500", "1000", 30)


def intent(risk=RiskTier.NORMAL, quantity="1", price="100", client_id="client-001"):
    return OrderIntent("BTC/USDT", "buy", Decimal(quantity), Decimal(price), risk, client_id)


class HybridGateTests(unittest.TestCase):
    def evaluate(self, order=None, **kwargs):
        return HybridExecutionGate.evaluate(
            order or intent(),
            mode=kwargs.pop("mode", ExecutionMode.HYBRID),
            limits=kwargs.pop("limits", LIMITS),
            verification=kwargs.pop("verification", VERIFIED),
            market_data_at=kwargs.pop("market_data_at", NOW - timedelta(seconds=2)),
            current_daily_notional=kwargs.pop("current_daily_notional", Decimal("0")),
            kill_switch_active=kwargs.pop("kill_switch_active", False),
            now=kwargs.pop("now", NOW),
            **kwargs,
        )

    def test_paper_mode_never_sends_order(self):
        result = self.evaluate(mode=ExecutionMode.PAPER_ONLY)
        self.assertEqual(result.status, DecisionStatus.PAPER_READY)
        self.assertIn("no order was sent", result.reason)

    def test_normal_hybrid_order_is_policy_only(self):
        result = self.evaluate()
        self.assertEqual(result.status, DecisionStatus.LIVE_POLICY_PASSED_NOT_EXECUTABLE)

    def test_hybrid_order_requires_client_order_id(self):
        result = self.evaluate(intent(client_id=""))
        self.assertEqual(result.status, DecisionStatus.BLOCKED)

    def test_elevated_order_requires_approval(self):
        result = self.evaluate(intent(RiskTier.ELEVATED))
        self.assertEqual(result.status, DecisionStatus.REQUIRES_APPROVAL)

    def test_exact_payload_approval_is_single_use(self):
        manager = ApprovalManager()
        order = intent(RiskTier.ELEVATED)
        grant = manager.issue(order, user_confirmed=True, now=NOW)
        first = self.evaluate(order, approval_token=grant.token, approval_manager=manager)
        second = self.evaluate(order, approval_token=grant.token, approval_manager=manager)
        self.assertEqual(first.status, DecisionStatus.LIVE_APPROVED_NOT_EXECUTABLE)
        self.assertEqual(second.status, DecisionStatus.BLOCKED)

    def test_approval_cannot_be_reused_for_changed_order(self):
        manager = ApprovalManager()
        order = intent(RiskTier.ELEVATED)
        grant = manager.issue(order, user_confirmed=True, now=NOW)
        changed = intent(RiskTier.ELEVATED, quantity="2")
        result = self.evaluate(changed, approval_token=grant.token, approval_manager=manager)
        self.assertEqual(result.status, DecisionStatus.BLOCKED)

    def test_approval_requires_explicit_user_confirmation(self):
        with self.assertRaises(PermissionError):
            ApprovalManager().issue(intent(RiskTier.ELEVATED), user_confirmed=False, now=NOW)

    def test_approval_is_only_for_elevated_risk(self):
        with self.assertRaises(ValueError):
            ApprovalManager().issue(intent(), user_confirmed=True, now=NOW)

    def test_expired_approval_is_rejected(self):
        manager = ApprovalManager()
        order = intent(RiskTier.ELEVATED)
        grant = manager.issue(order, user_confirmed=True, now=NOW, ttl_seconds=5)
        result = self.evaluate(
            order, approval_token=grant.token, approval_manager=manager,
            now=NOW + timedelta(seconds=5),
        )
        self.assertEqual(result.status, DecisionStatus.BLOCKED)

    def test_kill_switch_blocks_new_orders(self):
        self.assertEqual(self.evaluate(kill_switch_active=True).status, DecisionStatus.BLOCKED)

    def test_unknown_risk_blocks(self):
        self.assertEqual(self.evaluate(intent(RiskTier.UNKNOWN)).status, DecisionStatus.BLOCKED)

    def test_unverified_claim_blocks(self):
        weak = VerificationResult(
            "claim", VerificationStatus.PARTIALLY_CONFIRMED, NOW, ("one",), (),
            ("Only one source.",),
        )
        self.assertEqual(self.evaluate(verification=weak).status, DecisionStatus.BLOCKED)

    def test_stale_market_data_blocks(self):
        old = NOW - timedelta(seconds=31)
        self.assertEqual(self.evaluate(market_data_at=old).status, DecisionStatus.BLOCKED)

    def test_future_market_data_blocks(self):
        future = NOW + timedelta(seconds=1)
        self.assertEqual(self.evaluate(market_data_at=future).status, DecisionStatus.BLOCKED)

    def test_order_limit_blocks(self):
        self.assertEqual(self.evaluate(intent(quantity="6", price="100")).status, DecisionStatus.BLOCKED)

    def test_daily_limit_blocks(self):
        self.assertEqual(
            self.evaluate(current_daily_notional=Decimal("950")).status,
            DecisionStatus.BLOCKED,
        )

    def test_symbol_allowlist_blocks(self):
        other = OrderIntent("ETH/USDT", "buy", 1, 100, client_order_id="client-002")
        self.assertEqual(self.evaluate(other).status, DecisionStatus.BLOCKED)


if __name__ == "__main__":
    unittest.main()
