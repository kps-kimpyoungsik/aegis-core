from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping


PUBLIC_OPEN_REQUIREMENTS = (
    "ga_gate_passed",
    "customer_terms_ready",
    "privacy_notice_ready",
    "support_runbook_ready",
    "incident_response_ready",
    "status_page_ready",
    "monitoring_alerting_ready",
    "backup_restore_evidence",
    "rollback_evidence",
    "security_evidence",
)


@dataclass(frozen=True, slots=True)
class PublicOpenDecision:
    passed: bool
    decision: str
    missing: tuple[str, ...]


class CustomerOpenApprovalGate:
    def evaluate(self, evidence: Mapping[str, bool]) -> PublicOpenDecision:
        missing = tuple(name for name in PUBLIC_OPEN_REQUIREMENTS if evidence.get(name) is not True)
        return PublicOpenDecision(
            passed=not missing,
            decision="PUBLIC_OPEN_APPROVED" if not missing else "PUBLIC_OPEN_BLOCKED",
            missing=missing,
        )
