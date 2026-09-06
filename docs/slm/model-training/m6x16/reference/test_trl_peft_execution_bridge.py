from pathlib import Path
import tempfile

from trl_peft_execution_bridge import (
    ExecutionIdentity, SignalStopController, artifact_manifest, build_plan,
    completion_receipt, framework_translation, validate_adapter_artifacts,
)


def identity(**overrides):
    data = dict(
        job_id="JOB-1", training_run_id="TRN-1", run_fingerprint="run-fp",
        dataset_snapshot_fingerprint="snap-fp", model_id="Qwen/model",
        model_revision="0123456789abcdef", tokenizer_id="Qwen/model",
        tokenizer_revision="fedcba9876543210", training_config_hash="cfg-fp",
        epoch=2, attempt=1,
    )
    data.update(overrides)
    return ExecutionIdentity(**data)


def test_exact_revision_and_resume_translation():
    p = build_plan(identity(), output_dir="out", resume_checkpoint_path="out/checkpoint-100")
    x = framework_translation(p)
    assert x["model_load"]["revision"] == "0123456789abcdef"
    assert x["tokenizer_load"]["revision"] == "fedcba9876543210"
    assert x["resume_from_checkpoint"] == "out/checkpoint-100"
    assert x["model_load"]["quantization_config"]["bnb_4bit_quant_type"] == "nf4"


def test_floating_revision_rejected():
    try:
        build_plan(identity(model_revision="main"), output_dir="out")
        assert False, "floating model revision must fail"
    except ValueError:
        pass


def test_signal_only_requests_safe_boundary_actions():
    c = SignalStopController()
    assert c.callback_control() == {"should_save": False, "should_training_stop": False}
    c.request_stop(15)
    assert c.callback_control() == {"should_save": True, "should_training_stop": True}


def test_artifact_hash_and_receipt_are_deterministic():
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        (root / "adapter_config.json").write_text("{}", encoding="utf-8")
        (root / "adapter_model.safetensors").write_bytes(b"adapter")
        m1 = artifact_manifest(root)
        m2 = artifact_manifest(root)
        assert m1 == m2
        assert validate_adapter_artifacts(m1)
        p = build_plan(identity(), output_dir=d)
        r1 = completion_receipt(p, m1, {"trl": "x", "peft": "y"})
        r2 = completion_receipt(p, m2, {"peft": "y", "trl": "x"})
        assert r1 == r2
        assert r1["state"] == "COMPLETED_NOT_EVALUATED"
        assert not r1["actual_promotion_executed"]
