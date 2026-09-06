"""AEGIS M6.x-15 resume-safe training-job reference.

This module does not schedule jobs or acquire resources. It creates/validates the
SLM-owned envelope that is handed to canonical runtime-kernel capabilities.
"""
from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
import json
from typing import Mapping


def _fingerprint(value: object) -> str:
    raw = json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    return sha256(raw).hexdigest()


@dataclass(frozen=True)
class TrainingJob:
    job_id: str
    training_run_id: str
    run_fingerprint: str
    epoch: int
    command_id: str
    attempt: int
    max_attempts: int
    artifact_root: str
    state: str = "READY_FOR_RUNTIME_HANDOFF"


class ResumeSafeJobOrchestrator:
    TOOL_NAME = "slm.local_training_adapter"

    def dispatch_envelope(self, job: TrainingJob) -> Mapping[str, object]:
        if job.epoch < 0:
            raise ValueError("epoch must be >= 0")
        if job.attempt < 1 or job.max_attempts < 1 or job.attempt > job.max_attempts:
            raise ValueError("invalid retry attempt")
        return {
            "executionId": job.job_id,
            "commandId": job.command_id,
            "epoch": job.epoch,
            "toolName": self.TOOL_NAME,
            "handoff_owner": "runtime-kernel/RuntimeDispatchKernel",
        }

    def resource_handoff(self, job: TrainingJob, usage: Mapping[str, int]) -> Mapping[str, object]:
        return {
            "job_id": job.job_id,
            "epoch": job.epoch,
            "usage": dict(usage),
            "handoff_owner": "runtime-kernel/RuntimeResourceControlKernel",
            "state": "ADMISSION_NOT_EVALUATED_BY_SLM",
        }

    def artifact_layout(self, job: TrainingJob) -> Mapping[str, str]:
        base = f"{job.artifact_root.rstrip('/')}/{job.training_run_id}/attempt-{job.attempt}"
        return {
            "base": base,
            "checkpoints": f"{base}/checkpoints",
            "events": f"{base}/events",
            "metrics": f"{base}/metrics",
            "receipts": f"{base}/receipts",
        }

    def can_resume(self, job: TrainingJob, checkpoint: Mapping[str, object]) -> bool:
        return (
            checkpoint.get("training_run_id") == job.training_run_id
            and checkpoint.get("run_fingerprint") == job.run_fingerprint
            and checkpoint.get("epoch") == job.epoch
            and checkpoint.get("integrity_state") == "PASS"
        )

    def recovery_handoff(self, job: TrainingJob, checkpoint_id: str) -> Mapping[str, object]:
        return {
            "job_id": job.job_id,
            "checkpoint_id": checkpoint_id,
            "expected_epoch": job.epoch,
            "handoff_owner": "runtime-kernel/RuntimeRecoveryReplayKernel",
            "state": "RECOVERY_NOT_EXECUTED_BY_SLM",
        }

    def retry_decision(self, job: TrainingJob, failure: str) -> str:
        if failure in {"POLICY", "IDENTITY", "CHECKPOINT_INTEGRITY", "NAN_INF"}:
            return "BLOCK"
        if job.attempt >= job.max_attempts:
            return "EXHAUSTED"
        if failure in {"INTERRUPTED", "TRANSIENT_STORAGE", "TRANSIENT_RUNTIME"}:
            return "RETRY_SAME_RUN"
        if failure == "OOM":
            return "NEW_RUN_REQUIRED"
        return "HOLD"

    def heartbeat_state(self, observed_at: int, last_progress_at: int, stale_after_seconds: int = 120) -> str:
        if observed_at < last_progress_at:
            raise ValueError("observed_at precedes last_progress_at")
        return "FRESH" if observed_at - last_progress_at <= stale_after_seconds else "STALE"

    def completion_receipt(
        self,
        job: TrainingJob,
        final_checkpoint_hash: str,
        evaluation_handoff_ref: str,
    ) -> Mapping[str, object]:
        core = {
            "job_id": job.job_id,
            "training_run_id": job.training_run_id,
            "run_fingerprint": job.run_fingerprint,
            "final_checkpoint_hash": final_checkpoint_hash,
            "evaluation_handoff_ref": evaluation_handoff_ref,
        }
        digest = _fingerprint(core)
        return {
            "completion_receipt_id": "JCR-" + digest[:12],
            **core,
            "receipt_fingerprint": digest,
            "evaluation_trigger_state": "READY_NOT_EVALUATED",
            "trace_handoff_owner": "runtime-kernel/RuntimeTraceAuditKernel",
            "state": "COMPLETED_NOT_PROMOTED",
        }
