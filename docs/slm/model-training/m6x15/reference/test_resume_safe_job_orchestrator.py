from resume_safe_job_orchestrator import ResumeSafeJobOrchestrator, TrainingJob


def job(attempt=1, max_attempts=3):
    return TrainingJob(
        job_id="JOB-1",
        training_run_id="TRN-1",
        run_fingerprint="run-fingerprint-1",
        epoch=0,
        command_id="CMD-1",
        attempt=attempt,
        max_attempts=max_attempts,
        artifact_root="artifacts/slm",
    )


def test_dispatch_maps_to_runtime_kernel_contract():
    env = ResumeSafeJobOrchestrator().dispatch_envelope(job())
    assert env["executionId"] == "JOB-1"
    assert env["toolName"] == "slm.local_training_adapter"
    assert env["handoff_owner"].endswith("RuntimeDispatchKernel")


def test_resume_requires_exact_identity_and_integrity():
    orch = ResumeSafeJobOrchestrator()
    ck = {"training_run_id": "TRN-1", "run_fingerprint": "run-fingerprint-1", "epoch": 0, "integrity_state": "PASS"}
    assert orch.can_resume(job(), ck)
    assert not orch.can_resume(job(), {**ck, "run_fingerprint": "changed"})


def test_oom_requires_new_run_when_config_must_change():
    assert ResumeSafeJobOrchestrator().retry_decision(job(), "OOM") == "NEW_RUN_REQUIRED"


def test_interrupted_can_retry_same_run_subject_to_runtime_recovery():
    assert ResumeSafeJobOrchestrator().retry_decision(job(), "INTERRUPTED") == "RETRY_SAME_RUN"


def test_retry_budget_exhaustion_wins():
    assert ResumeSafeJobOrchestrator().retry_decision(job(attempt=3, max_attempts=3), "INTERRUPTED") == "EXHAUSTED"


def test_stale_heartbeat_is_evidence_not_retry_authority():
    assert ResumeSafeJobOrchestrator().heartbeat_state(300, 100, 120) == "STALE"


def test_completion_receipt_is_idempotent():
    orch = ResumeSafeJobOrchestrator()
    a = orch.completion_receipt(job(), "checkpoint-hash", "EVH-1")
    b = orch.completion_receipt(job(), "checkpoint-hash", "EVH-1")
    assert a == b
    assert a["state"] == "COMPLETED_NOT_PROMOTED"
    assert a["evaluation_trigger_state"] == "READY_NOT_EVALUATED"
