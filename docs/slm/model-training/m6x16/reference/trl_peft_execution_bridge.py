"""AEGIS M6.x-16 TRL/PEFT outbound execution bridge.

The module is dependency-light at import time. GPU/framework imports are deferred
until `execute_real` so dry-run contract tests can run without CUDA/TRL/PEFT.
Scheduling, resource admission, retries, recovery and rollback are runtime-kernel
responsibilities and are intentionally absent here.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
from hashlib import sha256
import json
from pathlib import Path
import signal
from typing import Any, Mapping


def _canon(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def _hash(value: object) -> str:
    return sha256(_canon(value).encode("utf-8")).hexdigest()


def _require_pin(value: str, name: str) -> str:
    if not value or value.strip().lower() in {"latest", "main", "master", "head"}:
        raise ValueError(f"{name} must be an exact immutable revision pin")
    return value.strip()


@dataclass(frozen=True)
class ExecutionIdentity:
    job_id: str
    training_run_id: str
    run_fingerprint: str
    dataset_snapshot_fingerprint: str
    model_id: str
    model_revision: str
    tokenizer_id: str
    tokenizer_revision: str
    training_config_hash: str
    epoch: int
    attempt: int

    def validated(self) -> "ExecutionIdentity":
        if not self.job_id or not self.training_run_id or not self.run_fingerprint:
            raise ValueError("job/run identity must not be blank")
        if self.epoch < 0 or self.attempt < 0:
            raise ValueError("epoch/attempt must be >= 0")
        _require_pin(self.model_revision, "model_revision")
        _require_pin(self.tokenizer_revision, "tokenizer_revision")
        return self


@dataclass(frozen=True)
class AdapterPlan:
    identity: ExecutionIdentity
    output_dir: str
    resume_checkpoint_path: str | None
    quantization: Mapping[str, Any]
    lora: Mapping[str, Any]
    sft: Mapping[str, Any]

    @property
    def fingerprint(self) -> str:
        return _hash({
            "identity": asdict(self.identity),
            "output_dir": self.output_dir,
            "resume_checkpoint_path": self.resume_checkpoint_path,
            "quantization": dict(self.quantization),
            "lora": dict(self.lora),
            "sft": dict(self.sft),
        })


class SignalStopController:
    """Signal handler records intent only; checkpoint I/O stays in Trainer callback."""

    def __init__(self) -> None:
        self.stop_requested = False
        self.last_signal: int | None = None

    def request_stop(self, signum: int, _frame: object | None = None) -> None:
        self.stop_requested = True
        self.last_signal = int(signum)

    def install(self) -> None:
        signal.signal(signal.SIGTERM, self.request_stop)
        signal.signal(signal.SIGINT, self.request_stop)

    def callback_control(self) -> dict[str, bool]:
        if not self.stop_requested:
            return {"should_save": False, "should_training_stop": False}
        return {"should_save": True, "should_training_stop": True}


def build_plan(identity: ExecutionIdentity, *, output_dir: str,
               resume_checkpoint_path: str | None = None,
               max_seq_length: int = 4096,
               learning_rate: float = 2e-4,
               per_device_train_batch_size: int = 1,
               gradient_accumulation_steps: int = 16) -> AdapterPlan:
    identity.validated()
    if resume_checkpoint_path is not None and not resume_checkpoint_path.strip():
        raise ValueError("resume_checkpoint_path must be exact or None")
    return AdapterPlan(
        identity=identity,
        output_dir=output_dir,
        resume_checkpoint_path=resume_checkpoint_path,
        quantization={
            "load_in_4bit": True,
            "bnb_4bit_quant_type": "nf4",
            "bnb_4bit_use_double_quant": True,
            "bnb_4bit_compute_dtype": "bfloat16",
        },
        lora={"r": 16, "lora_alpha": 32, "lora_dropout": 0.05,
              "bias": "none", "task_type": "CAUSAL_LM"},
        sft={
            "learning_rate": learning_rate,
            "per_device_train_batch_size": per_device_train_batch_size,
            "gradient_accumulation_steps": gradient_accumulation_steps,
            "gradient_checkpointing": True,
            "max_length": max_seq_length,
            "save_strategy": "steps",
            "logging_strategy": "steps",
            "report_to": "none",
        },
    )


def framework_translation(plan: AdapterPlan) -> dict[str, Any]:
    """Pure translation evidence; does not import or execute GPU frameworks."""
    return {
        "model_load": {
            "model_id": plan.identity.model_id,
            "revision": plan.identity.model_revision,
            "quantization_config": dict(plan.quantization),
        },
        "tokenizer_load": {
            "tokenizer_id": plan.identity.tokenizer_id,
            "revision": plan.identity.tokenizer_revision,
        },
        "peft_config": dict(plan.lora),
        "sft_config": {**dict(plan.sft), "output_dir": plan.output_dir},
        "resume_from_checkpoint": plan.resume_checkpoint_path,
        "plan_fingerprint": plan.fingerprint,
    }


def artifact_manifest(root: str | Path) -> dict[str, Any]:
    root = Path(root)
    entries: list[dict[str, str]] = []
    if root.exists():
        for p in sorted(x for x in root.rglob("*") if x.is_file()):
            entries.append({
                "path": p.relative_to(root).as_posix(),
                "sha256": sha256(p.read_bytes()).hexdigest(),
            })
    return {"entries": entries, "artifact_fingerprint": _hash(entries)}


def validate_adapter_artifacts(manifest: Mapping[str, Any]) -> bool:
    paths = {x["path"] for x in manifest.get("entries", [])}
    has_weight = "adapter_model.safetensors" in paths or "adapter_model.bin" in paths
    return has_weight and "adapter_config.json" in paths


def completion_receipt(plan: AdapterPlan, manifest: Mapping[str, Any],
                       dependency_versions: Mapping[str, str]) -> dict[str, Any]:
    if not validate_adapter_artifacts(manifest):
        raise ValueError("required adapter artifacts missing")
    core = {
        "job_id": plan.identity.job_id,
        "training_run_id": plan.identity.training_run_id,
        "run_fingerprint": plan.identity.run_fingerprint,
        "plan_fingerprint": plan.fingerprint,
        "artifact_fingerprint": manifest["artifact_fingerprint"],
        "dependency_versions": dict(sorted(dependency_versions.items())),
    }
    fp = _hash(core)
    return {
        "execution_receipt_id": "XRC-" + fp[:12],
        **core,
        "receipt_fingerprint": fp,
        "evaluation_handoff_state": "READY_NOT_EVALUATED",
        "state": "COMPLETED_NOT_EVALUATED",
        "actual_promotion_executed": False,
    }


def execute_real(plan: AdapterPlan, train_dataset: Any, eval_dataset: Any | None = None) -> dict[str, Any]:
    """Real framework bridge. Caller must obtain runtime-kernel admission first."""
    import torch
    import transformers
    import peft
    import trl
    from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig, TrainerCallback
    from peft import LoraConfig, prepare_model_for_kbit_training
    from trl import SFTConfig, SFTTrainer

    q = dict(plan.quantization)
    q["bnb_4bit_compute_dtype"] = torch.bfloat16
    bnb = BitsAndBytesConfig(**q)
    model = AutoModelForCausalLM.from_pretrained(
        plan.identity.model_id,
        revision=plan.identity.model_revision,
        quantization_config=bnb,
    )
    model = prepare_model_for_kbit_training(model)
    tokenizer = AutoTokenizer.from_pretrained(
        plan.identity.tokenizer_id,
        revision=plan.identity.tokenizer_revision,
    )
    lora = LoraConfig(**dict(plan.lora))
    sft = SFTConfig(output_dir=plan.output_dir, **dict(plan.sft))
    stop = SignalStopController()
    stop.install()

    class SafeStopCallback(TrainerCallback):
        def on_step_end(self, args, state, control, **kwargs):
            requested = stop.callback_control()
            if requested["should_save"]:
                control.should_save = True
            if requested["should_training_stop"]:
                control.should_training_stop = True
            return control

    trainer = SFTTrainer(
        model=model,
        args=sft,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        processing_class=tokenizer,
        peft_config=lora,
        callbacks=[SafeStopCallback()],
    )
    output = trainer.train(resume_from_checkpoint=plan.resume_checkpoint_path)
    trainer.save_model(plan.output_dir)
    versions = {
        "torch": torch.__version__,
        "transformers": transformers.__version__,
        "peft": peft.__version__,
        "trl": trl.__version__,
    }
    manifest = artifact_manifest(plan.output_dir)
    receipt = completion_receipt(plan, manifest, versions)
    return {"train_metrics": dict(output.metrics), "artifact_manifest": manifest, "receipt": receipt}
