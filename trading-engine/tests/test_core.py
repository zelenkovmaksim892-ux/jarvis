import unittest
from decimal import Decimal
from jarvis_trading.core import BacktestConfig, Candle, Signal, SmaCrossStrategy, run_backtest


def series(values):
    return [Candle(i, v, v, v, v, 1) for i, v in enumerate(values)]


class Strategy:
    def signal(self, history):
        return Signal.BUY if len(history) == 2 else Signal.HOLD


class CoreTests(unittest.TestCase):
    def test_buy_fills_next_open_and_closes_at_end(self):
        r = run_backtest(series([100, 100, 110, 120]), Strategy(), BacktestConfig(fee_rate=0, slippage_rate=0))
        self.assertEqual(len(r.trades), 1)
        self.assertEqual(r.trades[0].entry_price, Decimal("110"))
        self.assertEqual(r.trades[0].exit_price, Decimal("120"))
        self.assertGreater(r.total_return, 0)

    def test_costs_reduce_result(self):
        free = run_backtest(series([100, 100, 110]), Strategy(), BacktestConfig(fee_rate=0, slippage_rate=0))
        costly = run_backtest(series([100, 100, 110]), Strategy(), BacktestConfig(fee_rate="0.01", slippage_rate="0.01"))
        self.assertGreater(free.total_return, costly.total_return)

    def test_no_trade_keeps_cash(self):
        class Hold:
            def signal(self, history): return Signal.HOLD
        r = run_backtest(series([10, 11]), Hold())
        self.assertEqual(r.ending_cash, Decimal("10000"))

    def test_bad_timestamps_rejected(self):
        data = series([1, 2, 3])
        with self.assertRaises(ValueError): run_backtest([data[1], data[0]], Strategy())

    def test_sma_cross_and_period_validation(self):
        s = SmaCrossStrategy(2, 3)
        self.assertEqual(s.signal(series([5, 4, 3, 2, 4, 7])), Signal.BUY)
        with self.assertRaises(ValueError): SmaCrossStrategy(3, 2)

    def test_invalid_candle_rejected(self):
        with self.assertRaises(ValueError): Candle(0, 10, 9, 8, 10, 1)


if __name__ == "__main__": unittest.main()
