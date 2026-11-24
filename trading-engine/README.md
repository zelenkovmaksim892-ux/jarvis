# Jarvis Trading Engine: research-first and paper-only

This package is a **read-only market-data client plus local simulation tools**. It does not contain exchange order placement, account access, withdrawals, API-key storage, or live-trading code.

## Market data without API keys

- **Crypto — Bybit:** install the optional CCXT dependency and use `BybitPublicMarketData`. Public instruments, tickers, order books and OHLCV candles are read-only. No credentials are passed to the exchange client.
- **Stocks — Yahoo Finance:** `YahooFinancePublicData` reads public chart history without a key. This endpoint is unofficial and may be rate-limited, changed, or blocked; do not treat it as a guaranteed production feed. Historical daily/weekly/monthly bars are supported by this adapter.
- **FX — Frankfurter:** `FrankfurterFXData` reads public reference exchange rates without registration or a key. These are reference rates updated by source providers, not live executable FX quotes.

## Paper trading

`PaperAccount` simulates buys and sells entirely in local memory. It does not call Bybit or any other exchange. It tracks virtual cash, positions, fees, trades and marked equity. Restarting the process resets the in-memory account; persistence is intentionally not enabled yet.

## Evidence verification and hybrid policy gate

See [VERIFICATION_AND_HYBRID.md](VERIFICATION_AND_HYBRID.md). The verification module tracks evidence provenance, freshness, explicit support/conflict assessments and source independence. The hybrid gate checks allowlists, order and daily limits, market-data age, verification status, risk tier and a kill switch. Elevated-risk approval is bound to one exact order payload, expires quickly and can be consumed once.

These are policy primitives, not a claim that an LLM can prove every statement true. Semantic assessment of whether an excerpt supports a claim must be separately validated. The gate never sends orders. Even a hybrid policy pass is labelled not executable because this repository has no live exchange adapter.

## Install and test

```bash
python -m venv .venv
. .venv/bin/activate
pip install -e '.[market-data]'
PYTHONPATH=. python -m unittest discover -s tests -v
```

The deterministic backtester uses next-candle-open fills, fee/slippage assumptions and maximum drawdown. The SMA example is educational, not a forecast or profit promise.

## Safety boundary

The package cannot place real orders, withdraw funds or manage exchange accounts. Do not add exchange credentials to this package. A future real-trading module, if ever requested, must be a separate opt-in component with separate credentials, strict limits and explicit per-order confirmation. Backtests and paper results are simulations, not evidence of future returns.
