"""Research-first trading tools. No live order execution is included."""
from .core import BacktestConfig, Candle, Signal, SmaCrossStrategy, run_backtest

__all__ = ["BacktestConfig", "Candle", "Signal", "SmaCrossStrategy", "run_backtest"]
