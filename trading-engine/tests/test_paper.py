import unittest
from decimal import Decimal

from jarvis_trading.paper import PaperAccount


class PaperAccountTests(unittest.TestCase):
    def test_buy_and_sell_are_local_simulated_fills(self):
        account = PaperAccount("1000", "0.01")
        buy = account.execute("BTC/USDT", "buy", "2", "100")
        self.assertEqual(buy.fee, Decimal("2.00"))
        self.assertEqual(account.cash, Decimal("798.00"))
        self.assertEqual(account.positions["BTC/USDT"], Decimal("2"))
        sell = account.execute("BTC/USDT", "sell", "1", "120")
        self.assertEqual(sell.fee, Decimal("1.20"))
        self.assertEqual(account.positions["BTC/USDT"], Decimal("1"))
        self.assertEqual(account.equity({"BTC/USDT": "120"}), Decimal("916.80"))

    def test_rejects_overspend_and_oversell(self):
        account = PaperAccount("100", "0")
        with self.assertRaises(ValueError):
            account.execute("BTC/USDT", "buy", "2", "100")
        with self.assertRaises(ValueError):
            account.execute("BTC/USDT", "sell", "1", "100")

    def test_invalid_order_is_rejected(self):
        account = PaperAccount()
        with self.assertRaises(ValueError):
            account.execute("BTC/USDT", "withdraw", "1", "100")

    def test_equity_requires_marks_for_open_positions(self):
        account = PaperAccount("1000", "0")
        account.execute("AAPL", "buy", "1", "100")
        with self.assertRaises(ValueError):
            account.equity()


if __name__ == "__main__":
    unittest.main()
