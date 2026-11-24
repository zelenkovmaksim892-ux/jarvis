from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum
from typing import Protocol, Sequence


def D(value: Decimal | str | int | float) -> Decimal:
    return value if isinstance(value, Decimal) else Decimal(str(value))


class Signal(str, Enum):
    BUY = "buy"
    SELL = "sell"
    HOLD = "hold"


@dataclass(frozen=True, slots=True)
class Candle:
    timestamp_ms: int
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    volume: Decimal

    def __post_init__(self):
        for name in ("open", "high", "low", "close", "volume"):
            object.__setattr__(self, name, D(getattr(self, name)))
        if self.timestamp_ms < 0 or min(self.open, self.high, self.low, self.close) <= 0:
            raise ValueError("timestamp must be non-negative and OHLC prices positive")
        if self.volume < 0 or self.high < max(self.open, self.close, self.low) or self.low > min(self.open, self.close, self.high):
            raise ValueError("invalid OHLCV candle")


@dataclass(frozen=True, slots=True)
class BacktestConfig:
    starting_cash: Decimal = Decimal("10000")
    fee_rate: Decimal = Decimal("0.001")
    slippage_rate: Decimal = Decimal("0.0005")

    def __post_init__(self):
        for name in ("starting_cash", "fee_rate", "slippage_rate"):
            object.__setattr__(self, name, D(getattr(self, name)))
        if self.starting_cash <= 0 or not 0 <= self.fee_rate < 1 or not 0 <= self.slippage_rate < 1:
            raise ValueError("invalid cash, fee, or slippage setting")


@dataclass(frozen=True, slots=True)
class Trade:
    entry_time: int
    exit_time: int
    entry_price: Decimal
    exit_price: Decimal
    quantity: Decimal
    pnl: Decimal


@dataclass(frozen=True, slots=True)
class BacktestResult:
    ending_cash: Decimal
    total_return: Decimal
    max_drawdown: Decimal
    trades: tuple[Trade, ...]
    equity_curve: tuple[Decimal, ...]


class Strategy(Protocol):
    def signal(self, history: Sequence[Candle]) -> Signal: ...


class SmaCrossStrategy:
    """Long-only SMA crossover example, not a recommendation or profit promise."""
    def __init__(self, fast: int = 10, slow: int = 30):
        if fast < 1 or slow <= fast:
            raise ValueError("slow must be greater than fast >= 1")
        self.fast, self.slow = fast, slow

    @staticmethod
    def _mean(items: Sequence[Candle]) -> Decimal:
        return sum((x.close for x in items), Decimal(0)) / len(items)

    def signal(self, history: Sequence[Candle]) -> Signal:
        if len(history) < self.slow + 1:
            return Signal.HOLD
        pf = self._mean(history[-self.fast-1:-1])
        ps = self._mean(history[-self.slow-1:-1])
        cf = self._mean(history[-self.fast:])
        cs = self._mean(history[-self.slow:])
        if pf <= ps and cf > cs:
            return Signal.BUY
        if pf >= ps and cf < cs:
            return Signal.SELL
        return Signal.HOLD


def run_backtest(candles: Sequence[Candle], strategy: Strategy, config: BacktestConfig = BacktestConfig()) -> BacktestResult:
    """Closed-candle signals; fill on the next candle open. Simulation only."""
    if len(candles) < 2:
        raise ValueError("at least two candles required")
    times = [c.timestamp_ms for c in candles]
    if times != sorted(times) or len(set(times)) != len(times):
        raise ValueError("timestamps must be strictly increasing")

    cash, qty, invested = config.starting_cash, Decimal(0), Decimal(0)
    entry_price, entry_time = Decimal(0), 0
    trades: list[Trade] = []
    equity = [cash]

    for i in range(len(candles) - 1):
        signal, nxt = strategy.signal(candles[:i+1]), candles[i+1]
        if qty == 0 and signal == Signal.BUY:
            entry_price = nxt.open * (1 + config.slippage_rate)
            qty = cash / (entry_price * (1 + config.fee_rate))
            invested, entry_time, cash = cash, nxt.timestamp_ms, Decimal(0)
        elif qty > 0 and signal == Signal.SELL:
            exit_price = nxt.open * (1 - config.slippage_rate)
            proceeds = qty * exit_price * (1 - config.fee_rate)
            trades.append(Trade(entry_time, nxt.timestamp_ms, entry_price, exit_price, qty, proceeds - invested))
            cash, qty, invested = proceeds, Decimal(0), Decimal(0)
        equity.append(cash + qty * nxt.close)

    if qty > 0:
        last = candles[-1]
        exit_price = last.close * (1 - config.slippage_rate)
        proceeds = qty * exit_price * (1 - config.fee_rate)
        trades.append(Trade(entry_time, last.timestamp_ms, entry_price, exit_price, qty, proceeds - invested))
        cash, equity[-1] = proceeds, proceeds

    peak, drawdown = equity[0], Decimal(0)
    for value in equity:
        peak = max(peak, value)
        drawdown = max(drawdown, (peak - value) / peak)
    return BacktestResult(cash, (cash - config.starting_cash) / config.starting_cash, drawdown, tuple(trades), tuple(equity))


class PublicCcxtData:
    """Public OHLCV only. This class has no API-key or order methods."""
    def __init__(self, exchange_id: str):
        try:
            import ccxt
        except ImportError as exc:
            raise RuntimeError("Install optional dependency: pip install -e '.[market-data]'") from exc
        cls = getattr(ccxt, exchange_id, None)
        if cls is None or exchange_id.startswith("_"):
            raise ValueError("unsupported exchange")
        self.exchange = cls({"enableRateLimit": True})

    def candles(self, symbol: str, timeframe: str = "1h", limit: int = 500) -> list[Candle]:
        if not symbol or "/" not in symbol or not 1 <= limit <= 1000:
            raise ValueError("invalid symbol or limit")
        rows = self.exchange.fetch_ohlcv(symbol, timeframe=timeframe, limit=limit)
        return [Candle(int(r[0]), r[1], r[2], r[3], r[4], r[5]) for r in rows]

    def close(self):
        self.exchange.close()
