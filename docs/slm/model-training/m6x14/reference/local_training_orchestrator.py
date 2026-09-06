"""AEGIS M6.x-14 executable reference.

This module intentionally does NOT schedule GPU jobs, lock resources, retry jobs,
rollback deployments, or persist audit logs. Those are canonical runtime-kernel
responsibilities. It implements deterministic evaluation/gating contracts only.
"""
from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
import json
from typing import Dict, Mapping


def _fingerprint(value: object) -> str:
    raw = json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    return sha256(raw).hexdigest()


@dataclass(frozen=True)
class ResourceObservation:
    gpu_present: bool
    free_vram_gb: float
    estimated_peak_vram_gb: float
    free_disk_gb: float
    runtime_compatible: bool


@dataclass(frozen=True)
class CheckpointObservation:
    checkpoint_id: str
    run_fingerprint: str
    adapter_hash_match: bool
    manifest_complete: bool


class LocalTrainingGate:
    def __init__(self, policy: Mapping[str, object], expected_run_fingerprint: str):
        self.policy = policy
        self.expected_run_fingerprint = expected_run_fingerprint

    def preflight(self, obs: ResourceObservation) -> Dict[str, object]:
        reasons = []
        if obs.gpu_present and obs.free_vram_gb < obs.estimated_peak_vram_gb:
            reasons.append("INSUFFICIENT_VRAM")
        min_disk = float(self.policy["preflight"]["min_free_disk_gb"])
        if obs.free_disk_gb < min_disk:
            reasons.append("INSUFFICIENT_DISK")
        if not obs.runtime_compatible:
            reasons.append("RUNTIME_MISMATCH")
        return {
            "state": "PASS" if not reasons else "HOLD_" + "_".join(reasons),
            "reasons": reasons,
            "resource_control_handoff": "runtime-kernel/RuntimeResourceControlKernel"
        }

    def checkpoint_integrity(self, ck: CheckpointObservation) -> bool:
        return (
            ck.run_fingerprint == self.expected_run_fingerprint
            and ck.adapter_hash_match
            and ck.manifest_complete
        )

    def evaluate(
        self,
        training_run_ref: str,
        resource: ResourceObservation,
        checkpoint: CheckpointObservation,
        baseline_scores: Mapping[str, float],
        candidate_scores: Mapping[str, float],
        false_pass_count: int,
        verified_negative_count: int,
        normalized_training_cost: float = 0.0,
    ) -> Dict[str, object]:
        preflight = self.preflight(resource)
        ck_ok = self.checkpoint_integrity(checkpoint)
        drops = {k: baseline_scores[k] - candidate_scores[k] for k in baseline_scores}
        max_drop = max(drops.values())
        critical_drop = float(self.policy["regression"]["critical_drop"])
        material_drop = float(self.policy["regression"]["material_drop"])
        if max_drop >= critical_drop:
            regression = "CRITICAL_REGRESSION"
        elif max_drop >= material_drop:
            regression = "MATERIAL_REGRESSION"
        elif max_drop > 0:
            regression = "MILD_REGRESSION"
        else:
            regression = "NO_CRITICAL_REGRESSION"

        retention = {k: candidate_scores[k] / baseline_scores[k] for k in baseline_scores}
        min_retention = min(retention.values())
        f = self.policy["forgetting"]
        if min_retention >= float(f["retained_ratio"]):
            forgetting = "RETAINED"
        elif min_retention >= float(f["mild_ratio"]):
            forgetting = "MILD_FORGETTING"
        elif min_retention >= float(f["material_ratio"]):
            forgetting = "MATERIAL_FORGETTING"
        else:
            forgetting = "CRITICAL_FORGETTING"

        false_pass_rate = false_pass_count / max(1, verified_negative_count)
        fp_block = false_pass_rate > float(self.policy["false_pass"]["critical_max_rate"])
        baseline_mean = sum(baseline_scores.values()) / len(baseline_scores)
        candidate_mean = sum(candidate_scores.values()) / len(candidate_scores)
        observed_utility = (
            candidate_mean - baseline_mean
            - max(0.0, max_drop)
            - max(0.0, 1.0 - min_retention)
            - false_pass_rate
            - normalized_training_cost
        )

        reasons = []
        if preflight["state"] != "PASS": reasons.append("PREFLIGHT_NOT_PASS")
        if not ck_ok: reasons.append("CHECKPOINT_INTEGRITY")
        if regression == "CRITICAL_REGRESSION": reasons.append("CRITICAL_REGRESSION")
        if forgetting == "CRITICAL_FORGETTING": reasons.append("CRITICAL_FORGETTING")
        if fp_block: reasons.append("FALSE_PASS_RATE")

        if "CHECKPOINT_INTEGRITY" in reasons:
            recommendation = "BLOCK_CHECKPOINT_INTEGRITY"
        elif "CRITICAL_REGRESSION" in reasons:
            recommendation = "BLOCK_REGRESSION"
        elif "CRITICAL_FORGETTING" in reasons:
            recommendation = "BLOCK_FORGETTING"
        elif "FALSE_PASS_RATE" in reasons:
            recommendation = "BLOCK_FALSE_PASS"
        elif reasons:
            recommendation = "HOLD_REVALIDATION"
        else:
            recommendation = "RECOMMEND_PROMOTION"

        core = {
            "training_run_ref": training_run_ref,
            "checkpoint_ref": checkpoint.checkpoint_id,
            "preflight_state": preflight["state"],
            "checkpoint_integrity": "PASS" if ck_ok else "FAIL",
            "regression_state": regression,
            "forgetting_state": forgetting,
            "false_pass_rate": round(false_pass_rate, 6),
            "observed_learning_utility": round(observed_utility, 6),
            "recommendation": recommendation,
        }
        evidence_id = "EVE-" + _fingerprint(core)[:12]
        return {
            "evaluation_evidence_id": evidence_id,
            **core,
            "capability_retention": {k: round(v, 6) for k, v in retention.items()},
            "reason": reasons,
            "promotion_handoff": {
                "promotion_handoff_id": "PGH-" + _fingerprint({"evidence": evidence_id})[:12],
                "recommendation": recommendation,
                "state": "READY_FOR_GOVERNANCE_REVIEW",
            },
            "dynamic_mix_feedback": {
                "observed_learning_utility": round(observed_utility, 6),
                "protected_eval_raw_access": False,
                "sealed_snapshot_mutation": False,
                "state": "READY_FOR_NEXT_MATERIAL_OPTIMIZATION",
            },
            "runtime_handoffs": {
                "resource_control": "RuntimeResourceControlKernel",
                "recovery": "RuntimeRecoveryReplayKernel",
                "rollback": "RuntimeRollbackKernel",
                "trace_audit": "RuntimeTraceAuditKernel",
            },
            "state": "SEALED_NOT_PROMOTED",
        }
