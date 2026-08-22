#!/usr/bin/env python3
"""Print and validate the committed dependency-freshness snapshot offline."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = ROOT / "scripts" / "dependency_freshness_snapshot.json"
REVIEW_HORIZON_DAYS = 30


def parse_date(value: str, label: str) -> dt.date:
    try:
        return dt.date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError(f"{label} must use YYYY-MM-DD: {value}") from exc


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--as-of",
        default=dt.date.today().isoformat(),
        help="Date used for the offline age check (default: today)",
    )
    args = parser.parse_args()
    try:
        as_of = parse_date(args.as_of, "--as-of")
        snapshot = json.loads(SNAPSHOT.read_text(encoding="utf-8"))
        policy = snapshot["policy"]
        horizon = int(policy["reviewHorizonDays"])
        reviewed_on = parse_date(snapshot["reviewedOn"], "snapshot reviewedOn")
        if horizon != REVIEW_HORIZON_DAYS:
            raise ValueError(
                f"policy reviewHorizonDays must remain {REVIEW_HORIZON_DAYS}, got {horizon}"
            )
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
        print(f"dependency freshness report failed: {exc}", file=sys.stderr)
        return 2

    print("Dependency freshness report")
    print(f"as of: {as_of.isoformat()}")
    print(f"reviewed: {reviewed_on.isoformat()} (horizon {horizon} days)")

    stale: list[str] = []
    snapshot_age = (as_of - reviewed_on).days
    if snapshot_age < 0 or snapshot_age > horizon:
        stale.append(f"snapshot reviewed {reviewed_on.isoformat()} ({snapshot_age} days old)")

    intentionally_held: list[str] = []
    facts = snapshot.get("facts", {})
    for key, fact in facts.items():
        fact_reviewed = parse_date(fact["reviewedOn"], f"facts.{key}.reviewedOn")
        age = (as_of - fact_reviewed).days
        if age < 0 or age > horizon:
            stale.append(f"fact {key} reviewed {fact_reviewed.isoformat()} ({age} days old)")

    dependencies = snapshot.get("dependencies", {})
    for key, entry in dependencies.items():
        entry_reviewed = parse_date(entry["reviewedOn"], f"{key}.reviewedOn")
        age = (as_of - entry_reviewed).days
        if age < 0 or age > horizon:
            stale.append(f"{key} reviewed {entry_reviewed.isoformat()} ({age} days old)")
        state = entry.get("state")
        if state == "held":
            decision = entry.get("candidateDecision", {})
            reason = str(entry.get("reason", "")).strip()
            probe = str(decision.get("probe", "")).strip()
            if decision.get("action") != "hold" or not reason or "scripts/probe_dependency_upgrade.py" not in probe:
                print(f"invalid held candidate provenance: {key}", file=sys.stderr)
                return 1
            intentionally_held.append(
                f"{key}: {decision.get('candidateVersion')} held at {entry.get('pinnedVersion')}"
            )
        elif state in {"pre-release", "migration-required"}:
            decision = entry.get("candidateDecision", {})
            reason = str(entry.get("reason", "")).strip()
            unblock = str(entry.get("unblockCondition", "")).strip()
            probe = str(decision.get("probe", "")).strip()
            expected_action = "retain-beta" if state == "pre-release" else "hold"
            if (
                decision.get("action") != expected_action
                or not reason
                or not unblock
                or "scripts/probe_dependency_upgrade.py" not in probe
            ):
                print(f"invalid non-current candidate provenance: {key}", file=sys.stderr)
                return 1
            intentionally_held.append(f"{key}: {state} at {entry.get('pinnedVersion')}")

    if stale:
        print("stale evidence:")
        for item in stale:
            print(f"  - {item}")
    else:
        print("stale evidence: none")

    if intentionally_held:
        print("intentionally held candidates:")
        for item in intentionally_held:
            print(f"  - {item}")
    else:
        print("intentionally held candidates: none")

    return 1 if stale else 0


if __name__ == "__main__":
    raise SystemExit(main())
