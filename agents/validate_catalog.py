#!/usr/bin/env python3
"""Validate the declarative agent catalog and its fail-closed safety defaults."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "agents" / "catalog.json"
ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
STATUSES = {"planned", "prototype", "available", "disabled"}
HIGH_RISK = {
    "flash-device",
    "unlock-bootloader",
    "write-partitions",
    "erase-data",
    "repository-write",
    "release",
    "deploy",
    "live-trading",
    "live-orders",
}


def validate(data: object) -> list[str]:
    errors: list[str] = []
    if not isinstance(data, dict):
        return ["catalog root must be a JSON object"]
    if data.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    if data.get("default_policy") != "deny":
        errors.append("default_policy must be 'deny'")

    agents = data.get("agents")
    if not isinstance(agents, list) or not agents:
        return errors + ["agents must be a non-empty array"]

    seen: set[str] = set()
    for index, agent in enumerate(agents):
        prefix = f"agents[{index}]"
        if not isinstance(agent, dict):
            errors.append(f"{prefix} must be an object")
            continue

        agent_id = agent.get("id")
        if not isinstance(agent_id, str) or not ID_PATTERN.fullmatch(agent_id):
            errors.append(f"{prefix}.id must be a lowercase kebab-case identifier")
        elif agent_id in seen:
            errors.append(f"duplicate agent id: {agent_id}")
        else:
            seen.add(agent_id)

        for field in ("name", "runtime"):
            if not isinstance(agent.get(field), str) or not agent[field].strip():
                errors.append(f"{prefix}.{field} must be a non-empty string")

        if agent.get("status") not in STATUSES:
            errors.append(f"{prefix}.status must be one of {sorted(STATUSES)}")

        capabilities = agent.get("capabilities", [])
        if not isinstance(capabilities, list) or any(
            not isinstance(item, str) or not item.strip() for item in capabilities
        ):
            errors.append(f"{prefix}.capabilities must be an array of non-empty strings")
            capabilities = []

        approvals = agent.get("manual_approval", [])
        if not isinstance(approvals, list) or any(
            not isinstance(item, str) or not item.strip() for item in approvals
        ):
            errors.append(f"{prefix}.manual_approval must be an array of non-empty strings")
            approvals = []

        missing = HIGH_RISK.intersection(capabilities) - set(approvals)
        if missing:
            errors.append(f"{prefix} high-risk capabilities lack manual approval: {sorted(missing)}")

        if agent.get("live_trading_enabled") is True or agent.get("live_orders_enabled") is True:
            errors.append(f"{prefix} must not enable live trading or live orders")

        if agent_id == "freqtrade-worker":
            if agent.get("live_trading_enabled") is not False:
                errors.append("freqtrade-worker must explicitly set live_trading_enabled=false")
            if agent.get("status") == "available":
                errors.append("freqtrade-worker cannot be marked available before a separate safety review")

    return errors


def main() -> int:
    try:
        data = json.loads(CATALOG.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"Agent catalog is unreadable or invalid JSON: {exc}", file=sys.stderr)
        return 1

    errors = validate(data)
    if errors:
        print("Agent catalog validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Agent catalog valid: {len(data['agents'])} agents; default policy is deny.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
