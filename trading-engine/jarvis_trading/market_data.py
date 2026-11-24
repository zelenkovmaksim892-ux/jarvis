"""Public, read-only market-data adapters.

No adapter in this module accepts credentials or exposes order/account methods.
Bybit uses CCXT's public endpoints; Yahoo Finance's chart endpoint is unofficial
and best-effort; Frankfurter supplies reference FX rates, not intraday quotes.
"""
from __future__ import annotations

import json
from decimal import Decimal
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

from .core import Candle, D


class MarketDataError(RuntimeError):
    """A public market-data source failed or returned unusable data."""


def _get_json(url: str, timeout: float = 12.0):
    request = Request(url, headers={"User-Agent": "JarvisResearch/0.1"})
    try:
        with urlopen(request, timeout=timeout) as response:
            if response.status != 200:
                raise MarketDataError(f"HTTP status {response.status}")
            return json.loads(response.read().decode("utf-8"))
    except MarketDataError:
        raise
    except Exception as exc:
        raise MarketDataError(f"market-data request failed: {exc}") from exc


class BybitPublicMarketData:
    """Read-only Bybit adapter. It creates a CCXT client without credentials."""

    def __init__(self, market_type: str = "spot"):
        if market_type not in {"spot", "linear", "inverse"}:
            raise ValueError("market_type must be spot, linear, or inverse")
        try:
            import ccxt
        except ImportError as exc:
            raise RuntimeError("Install optional dependency: pip install -e '.[market-data]'") from exc
        self.exchange = ccxt.bybit({
            "enableRateLimit": True,
            "options": {"defaultType": market_type},
        })
        self.market_type = market_type

    def symbols(self, limit: int = 100) -> list[str]:
        if not 1 <= limit <= 1000:
            raise ValueError("limit must be between 1 and 1000")
        markets = self.exchange.load_markets()
        selected = []
        for symbol, market in markets.items():
            if not market.get("active", True):
                continue
            if self.market_type == "spot" and market.get("spot"):
                selected.append(symbol)
            elif self.market_type == "linear" and market.get("swap") and market.get("linear"):
                selected.append(symbol)
            elif self.market_type == "inverse" and market.get("swap") and market.get("inverse"):
                selected.append(symbol)
            if len(selected) >= limit:
                break
        return selected

    def candles(self, symbol: str = "BTC/USDT", timeframe: str = "1h", limit: int = 200) -> list[Candle]:
        if not symbol or "/" not in symbol or not 1 <= limit <= 1000:
            raise ValueError("invalid symbol or limit")
        rows = self.exchange.fetch_ohlcv(symbol, timeframe=timeframe, limit=limit)
        return [Candle(int(row[0]), row[1], row[2], row[3], row[4], row[5]) for row in rows]

    def ticker(self, symbol: str = "BTC/USDT") -> dict:
        if not symbol or "/" not in symbol:
            raise ValueError("invalid symbol")
        return self.exchange.fetch_ticker(symbol)

    def order_book(self, symbol: str = "BTC/USDT", limit: int = 50) -> dict:
        if not symbol or "/" not in symbol or not 1 <= limit <= 1000:
            raise ValueError("invalid symbol or limit")
        return self.exchange.fetch_order_book(symbol, limit)

    def close(self) -> None:
        self.exchange.close()


def parse_yahoo_chart(payload: dict) -> list[Candle]:
    """Convert a Yahoo chart JSON payload into validated candles."""
    chart = payload.get("chart") or {}
    if chart.get("error"):
        raise MarketDataError(str(chart["error"]))
    results = chart.get("result") or []
    if not results:
        raise MarketDataError("Yahoo Finance returned no chart data")
    result = results[0]
    timestamps = result.get("timestamp") or []
    quote_data = ((result.get("indicators") or {}).get("quote") or [{}])[0]
    fields = [quote_data.get(name) or [] for name in ("open", "high", "low", "close", "volume")]
    candles = []
    for index, timestamp in enumerate(timestamps):
        values = [field[index] if index < len(field) else None for field in fields]
        if any(value is None for value in values[:4]):
            continue
        volume = values[4] if values[4] is not None else 0
        candles.append(Candle(int(timestamp) * 1000, *values[:4], volume))
    return candles


class YahooFinancePublicData:
    """Best-effort public stock history; unofficial endpoint, no API key."""

    ALLOWED_RANGES = {"1d", "5d", "1mo", "3mo", "6mo", "1y", "2y", "5y", "10y", "ytd", "max"}
    ALLOWED_INTERVALS = {"1d", "1wk", "1mo"}

    def candles(self, symbol: str = "AAPL", period: str = "1mo", interval: str = "1d") -> list[Candle]:
        if not symbol or period not in self.ALLOWED_RANGES or interval not in self.ALLOWED_INTERVALS:
            raise ValueError("unsupported symbol, period, or interval")
        encoded_symbol = quote(symbol.strip(), safe="")
        query = urlencode({"range": period, "interval": interval})
        payload = _get_json(f"https://query1.finance.yahoo.com/v8/finance/chart/{encoded_symbol}?{query}")
        return parse_yahoo_chart(payload)


class FrankfurterFXData:
    """No-key reference FX rates. These are not real-time trading quotes."""

    BASE_URL = "https://api.frankfurter.dev/v2"

    def rates(self, base: str = "EUR", quotes: tuple[str, ...] = ("USD", "GBP", "RUB")) -> dict[str, Decimal]:
        base = base.strip().upper()
        normalized = tuple(code.strip().upper() for code in quotes if code.strip())
        if not base or not normalized:
            raise ValueError("base and at least one quote currency are required")
        query = urlencode({"base": base, "quotes": ",".join(normalized)})
        payload = _get_json(f"{self.BASE_URL}/rates?{query}")
        rows = payload if isinstance(payload, list) else [payload]
        result = {}
        for row in rows:
            if row.get("base") == base and row.get("quote"):
                result[row["quote"]] = D(row["rate"])
        return result

    def rate(self, base: str, quote_currency: str) -> Decimal:
        rates = self.rates(base, (quote_currency,))
        try:
            return rates[quote_currency.strip().upper()]
        except KeyError as exc:
            raise MarketDataError(f"no reference rate for {base}/{quote_currency}") from exc
