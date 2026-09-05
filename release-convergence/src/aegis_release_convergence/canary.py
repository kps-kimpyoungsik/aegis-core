from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping


CANARY_LEVELS = (
    "INTERNAL",
    "LIMITED_CUSTOMER",
    "P10",
    "P25",
    "P50",
    "P100",
)


@dataclass(frozen=True, slots=True)
class CanaryDecision:
    action: str
    level: str | None = None
    reason: str | None = None


class CanaryPromotionPolicy:
    def next_level(self, current: str, metrics: Mapping[str, object]) -> CanaryDecision:
        if int(metrics.get("security_events", 0)) > 0:
            return CanaryDecision("ROLLBACK", reason="security event")
        if float(metrics.get("error_rate_delta", 0.0)) > 0.0:
            return CanaryDecision("HOLD", reason="error rate regression")
        if float(metrics.get("p95_latency_delta_ms", 0.0)) > 0.0:
            return CanaryDecision("HOLD", reason="latency regression")
        if metrics.get("rollback_ready") is not True:
            return CanaryDecision("HOLD", reason="rollback unavailable")
        if current not in CANARY_LEVELS:
            return CanaryDecision("HOLD", reason="unknown canary level")
        index = CANARY_LEVELS.index(current)
        if index == len(CANARY_LEVELS) - 1:
            return CanaryDecision("COMPLETE", level=current)
        return CanaryDecision("PROMOTE", level=CANARY_LEVELS[index + 1])
