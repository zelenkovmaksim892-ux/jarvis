import unittest
from decimal import Decimal

from jarvis_trading.market_data import MarketDataError, parse_yahoo_chart


class MarketDataTests(unittest.TestCase):
    def test_parse_yahoo_chart(self):
        payload = {
            "chart": {
                "result": [{
                    "timestamp": [1700000000, 1700003600],
                    "indicators": {"quote": [{
                        "open": [10, 11], "high": [12, 13], "low": [9, 10],
                        "close": [11, 12], "volume": [100, 200]
                    }]}
                }],
                "error": None
            }
        }
        candles = parse_yahoo_chart(payload)
        self.assertEqual(len(candles), 2)
        self.assertEqual(candles[0].close, Decimal("11"))
        self.assertEqual(candles[1].volume, Decimal("200"))

    def test_parse_skips_incomplete_candle(self):
        payload = {"chart": {"result": [{
            "timestamp": [1700000000],
            "indicators": {"quote": [{"open": [None], "high": [2], "low": [1], "close": [2], "volume": [5]}]}
        }]}}
        self.assertEqual(parse_yahoo_chart(payload), [])

    def test_yahoo_error_is_not_silently_accepted(self):
        with self.assertRaises(MarketDataError):
            parse_yahoo_chart({"chart": {"error": {"description": "blocked"}, "result": None}})

    def test_empty_yahoo_response_is_rejected(self):
        with self.assertRaises(MarketDataError):
            parse_yahoo_chart({"chart": {"result": []}})


if __name__ == "__main__":
    unittest.main()
