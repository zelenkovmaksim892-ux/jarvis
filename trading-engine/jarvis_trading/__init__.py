"""Research-first trading tools. No live order execution is included."""
from .core import BacktestConfig, Candle, Signal, SmaCrossStrategy, run_backtest
from .hybrid import (
    ApprovalManager,
    DecisionStatus,
    ExecutionMode,
    HybridExecutionGate,
    HybridLimits,
    OrderIntent,
    RiskTier,
)
from .verification import (
    Evidence,
    VerificationPolicy,
    VerificationResult,
    VerificationStatus,
    verify_claim,
)

__all__ = [
    "BacktestConfig",
    "Candle",
    "Signal",
    "SmaCrossStrategy",
    "run_backtest",
    "ApprovalManager",
    "DecisionStatus",
    "ExecutionMode",
    "HybridExecutionGate",
    "HybridLimits",
    "OrderIntent",
    "RiskTier",
    "Evidence",
    "VerificationPolicy",
    "VerificationResult",
    "VerificationStatus",
    "verify_claim",
]
