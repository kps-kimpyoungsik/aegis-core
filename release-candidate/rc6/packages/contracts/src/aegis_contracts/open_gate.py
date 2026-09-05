from dataclasses import dataclass
from enum import Enum
from typing import Iterable

class EvidenceState(str, Enum):
    PASS="PASS"
    FAIL="FAIL"
    NOT_EXECUTED="NOT_EXECUTED"
    BLOCKED_BY_ENVIRONMENT="BLOCKED_BY_ENVIRONMENT"

@dataclass(frozen=True)
class OpenCheck:
    check_id: str
    state: EvidenceState
    required_for_ga: bool = True

@dataclass(frozen=True)
class OpenDecision:
    decision: str
    blockers: tuple[str, ...]
    production_open_allowed: bool

REQUIRED_GA = (
    "product_bundle_integrity",
    "python_tests",
    "frontend_lint_build",
    "dependency_lock",
    "secret_scan",
    "sast",
    "dependency_vulnerability_scan",
    "container_build",
    "container_image_scan",
    "sbom",
    "signed_provenance",
    "postgres_integration",
    "model_contract_integration",
    "migration_preflight",
    "staging_deploy",
    "readiness",
    "smoke",
    "backup_restore",
    "rollback_drill",
    "load_test",
    "security_dast",
    "asvs_review",
    "observability_alerting",
    "privacy_terms_ops_readiness",
)

def evaluate_open_gate(checks: Iterable[OpenCheck]) -> OpenDecision:
    by_id={c.check_id:c for c in checks}
    missing=[x for x in REQUIRED_GA if x not in by_id]
    failed=[x for x in REQUIRED_GA if x in by_id and by_id[x].state == EvidenceState.FAIL]
    unverified=[x for x in REQUIRED_GA if x in by_id and by_id[x].state in (
        EvidenceState.NOT_EXECUTED, EvidenceState.BLOCKED_BY_ENVIRONMENT
    )]
    blockers=tuple(missing+failed+unverified)
    if failed:
        return OpenDecision("NO_GO_FAILED",blockers,False)
    if blockers:
        return OpenDecision("NO_GO_UNVERIFIED",blockers,False)
    return OpenDecision("GO_PRODUCTION_OPEN",(),True)
