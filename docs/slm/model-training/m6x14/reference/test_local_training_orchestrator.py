import json
from pathlib import Path

from local_training_orchestrator import CheckpointObservation, LocalTrainingGate, ResourceObservation


POLICY = json.loads((Path(__file__).parents[1] / "local_training_evaluation_policy.json").read_text())
RUN_FP = "run-fingerprint-001"


def gate():
    return LocalTrainingGate(POLICY, RUN_FP)


def resource_ok():
    return ResourceObservation(True, 20, 16, 120, True)


def checkpoint_ok():
    return CheckpointObservation("CKP-1", RUN_FP, True, True)


def baseline():
    return {"code": 0.80, "constraints": 0.78, "tool": 0.82, "domain": 0.76}


def test_good_candidate_recommends_promotion():
    out = gate().evaluate("TRN-1", resource_ok(), checkpoint_ok(), baseline(),
                          {"code": 0.86, "constraints": 0.84, "tool": 0.84, "domain": 0.81}, 1, 100, 0.005)
    assert out["recommendation"] == "RECOMMEND_PROMOTION"
    assert out["state"] == "SEALED_NOT_PROMOTED"
    assert out["dynamic_mix_feedback"]["protected_eval_raw_access"] is False


def test_false_pass_blocks():
    out = gate().evaluate("TRN-1", resource_ok(), checkpoint_ok(), baseline(),
                          {"code": 0.86, "constraints": 0.84, "tool": 0.84, "domain": 0.81}, 6, 100)
    assert out["recommendation"] == "BLOCK_FALSE_PASS"


def test_checkpoint_mismatch_blocks():
    bad = CheckpointObservation("CKP-2", "wrong", False, True)
    out = gate().evaluate("TRN-1", resource_ok(), bad, baseline(),
                          {"code": 0.86, "constraints": 0.84, "tool": 0.84, "domain": 0.81}, 1, 100)
    assert out["recommendation"] == "BLOCK_CHECKPOINT_INTEGRITY"


def test_resource_shortage_holds():
    poor = ResourceObservation(True, 8, 16, 10, True)
    out = gate().evaluate("TRN-1", poor, checkpoint_ok(), baseline(),
                          {"code": 0.86, "constraints": 0.84, "tool": 0.84, "domain": 0.81}, 1, 100)
    assert out["recommendation"] == "HOLD_REVALIDATION"


def test_regression_blocks():
    out = gate().evaluate("TRN-1", resource_ok(), checkpoint_ok(), baseline(),
                          {"code": 0.86, "constraints": 0.84, "tool": 0.70, "domain": 0.81}, 1, 100)
    assert out["recommendation"] == "BLOCK_REGRESSION"
