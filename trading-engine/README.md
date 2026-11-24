# Jarvis Trading Engine: research-first

Install and run tests:
```bash
python -m venv .venv
. .venv/bin/activate
pip install -e '.[market-data]'
PYTHONPATH=. python -m unittest discover -s tests -v
```

Includes deterministic long-only backtesting, next-candle-open fills, fee/slippage assumptions, maximum drawdown, an SMA crossover example and optional public OHLCV via CCXT.

**Safety:** this package cannot place orders, withdraw funds or manage exchange accounts. No live-trading adapter or credential storage exists. Backtests are simulations, not forecasts. Never put secrets in prompts, logs or source control. A future live connector must be separate, disabled by default, withdrawals disabled, and use exact-order approval plus hard daily/per-order limits.
