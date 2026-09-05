from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping


GA_GATES = (
    "G0_SOURCE_ARCHITECTURE",
    "G1_CLEAN_BUILD",
    "G2_ARTIFACT_SUPPLY_CHAIN",
    "G3_SECURITY",
    "G4_DATA_SAFETY",
    "G5_STAGING",
    "G6_RELIABILITY_PERFORMANCE",
    "G7_ROLLBACK",
    "G8_PRODUCTION_APPROVAL",
)


@dataclass(frozen=True, slots=True)
class GateDecision:
    passed: bool
    decision: str
    missing: tuple[str, ...]


class GAGateRegistry:
    """Fail-closed public-release promotion gate."""

    def evaluate(self, statuses: Mapping[str, str]) -> GateDecision:
        missing = tuple(gate for gate in GA_GATES if statuses.get(gate) != "PASS")
        return GateDecision(
            passed=not missing,
            decision="GA_PROMOTE" if not missing else "NOT_PROMOTED",
            missing=missing,
        )
