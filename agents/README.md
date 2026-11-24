# Jarvis agent catalog design

Agent families planned:
- Firmware/build engineer: Android platform, kernel/device-tree, ROM build and log analysis in a Linux worker. Flashing, bootloader unlock and partition writes remain separate manual operations.
- Software engineer: repository analysis, code generation, tests and lint.
- Data analyst: Python, CSV/XLSX, SQL, statistics and charts.
- Market analyst: public data, indicators, news context and risk reports (read-only).
- Strategy developer: strategy templates, backtests and (planned) paper trading.
- Optional crypto-bot worker: evaluate Freqtrade as a separate Linux/Docker service. It provides strategy development, historical backtesting and a dry-run mode; Jarvis should control it through a narrow local API, with live trading disabled. See the official [Freqtrade documentation](https://www.freqtrade.io/en/latest/).
- Research agent: source-grounded web/document research.

Before enabling any agent, Jarvis must disclose repository/provider, license, runtime, resources, network destinations, data leaving device, filesystem/command scope and side effects. Default permissions are deny/read-only. Arbitrary third-party code must run sandboxed. Agent output and retrieved documents are untrusted data, never permission grants.

This is a catalog and design document, not a claim that Android can currently dynamically install arbitrary agents.
