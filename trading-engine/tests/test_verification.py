import unittest
from datetime import datetime, timedelta, timezone

from jarvis_trading.verification import (
    Evidence, VerificationPolicy, VerificationStatus, verify_claim,
)


NOW = datetime(2026, 11, 25, 12, 0, tzinfo=timezone.utc)


def evidence(source_id, group, supports=True, primary=False, age=10):
    return Evidence(
        source_id=source_id,
        publisher=group,
        url=f"https://example.test/{source_id}",
        independence_key=group,
        retrieved_at=NOW - timedelta(seconds=age),
        supports_claim=supports,
        is_primary=primary,
    )


class VerificationTests(unittest.TestCase):
    def test_primary_source_confirms(self):
        result = verify_claim("claim-1", [evidence("a", "official", primary=True)], now=NOW)
        self.assertEqual(result.status, VerificationStatus.PRIMARY_CONFIRMED)
        self.assertTrue(result.permits_high_impact_action)

    def test_two_independent_groups_confirm(self):
        result = verify_claim("claim-1", [evidence("a", "publisher-a"), evidence("b", "publisher-b")], now=NOW)
        self.assertEqual(result.status, VerificationStatus.MULTI_SOURCE_CONFIRMED)
        self.assertTrue(result.permits_high_impact_action)

    def test_same_publisher_is_not_independent(self):
        result = verify_claim("claim-1", [evidence("a", "same"), evidence("b", "same")], now=NOW)
        self.assertEqual(result.status, VerificationStatus.PARTIALLY_CONFIRMED)
        self.assertFalse(result.permits_high_impact_action)

    def test_fresh_conflict_blocks_confirmation(self):
        result = verify_claim(
            "claim-1",
            [evidence("a", "official", primary=True), evidence("b", "other", supports=False)],
            now=NOW,
        )
        self.assertEqual(result.status, VerificationStatus.CONFLICTING)
        self.assertFalse(result.permits_high_impact_action)

    def test_stale_evidence_is_not_used(self):
        result = verify_claim("claim-1", [evidence("a", "old", age=1000)], VerificationPolicy(60), now=NOW)
        self.assertEqual(result.status, VerificationStatus.STALE)
        self.assertEqual(result.stale_source_ids, ("a",))

    def test_no_evidence_is_insufficient(self):
        result = verify_claim("claim-1", [], now=NOW)
        self.assertEqual(result.status, VerificationStatus.INSUFFICIENT)

    def test_duplicate_source_ids_rejected(self):
        with self.assertRaises(ValueError):
            verify_claim("claim-1", [evidence("a", "x"), evidence("a", "y")], now=NOW)

    def test_naive_timestamps_rejected(self):
        with self.assertRaises(ValueError):
            evidence("a", "x").__class__(
                source_id="a", publisher="x", url="https://x.test", independence_key="x",
                retrieved_at=datetime(2026, 11, 25), supports_claim=True,
            )


if __name__ == "__main__":
    unittest.main()
