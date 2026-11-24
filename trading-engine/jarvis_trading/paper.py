"""In-memory paper trading ledger. It never connects to an exchange."""
from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from .core import D


@dataclass(frozen=True, slots=True)
class PaperTrade:
    symbol: str
    side: str
    quantity: Decimal
    price: Decimal
    fee: Decimal
    cash_after: Decimal


class PaperAccount:
    """A local virtual account. Balances and trades exist only in this process."""

    mode = "PAPER_ONLY"

    def __init__(self, starting_cash: Decimal | str | int = "10000", fee_rate: Decimal | str = "0.001"):
        self.starting_cash = D(starting_cash)
        self.cash = self.starting_cash
        self.fee_rate = D(fee_rate)
        if self.starting_cash <= 0 or not Decimal("0") <= self.fee_rate < Decimal("1"):
            raise ValueError("invalid starting cash or fee rate")
        self.positions: dict[str, Decimal] = {}
        self.trades: list[PaperTrade] = []

    def execute(self, symbol: str, side: str, quantity: Decimal | str | int, price: Decimal | str | int) -> PaperTrade:
        """Simulate a fill at the supplied price; no network call is possible."""
        symbol = symbol.strip().upper()
        side = side.strip().lower()
        qty, px = D(quantity), D(price)
        if not symbol or side not in {"buy", "sell"} or qty <= 0 or px <= 0:
            raise ValueError("invalid paper trade")
        notional = qty * px
        fee = notional * self.fee_rate
        held = self.positions.get(symbol, Decimal("0"))

        if side == "buy":
            total = notional + fee
            if total > self.cash:
                raise ValueError("insufficient paper cash")
            self.cash -= total
            self.positions[symbol] = held + qty
        else:
            if qty > held:
                raise ValueError("insufficient paper position")
            self.cash += notional - fee
            remaining = held - qty
            if remaining:
                self.positions[symbol] = remaining
            else:
                self.positions.pop(symbol, None)

        trade = PaperTrade(symbol, side, qty, px, fee, self.cash)
        self.trades.append(trade)
        return trade

    def equity(self, marks: dict[str, Decimal | str | int] | None = None) -> Decimal:
        marks = marks or {}
        total = self.cash
        for symbol, quantity in self.positions.items():
            if symbol not in marks:
                raise ValueError(f"missing mark price for {symbol}")
            price = D(marks[symbol])
            if price <= 0:
                raise ValueError("mark prices must be positive")
            total += quantity * price
        return total
