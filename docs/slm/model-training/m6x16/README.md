# M6.x-16 — Actual Local Training Adapter / TRL·PEFT Execution Bridge

Status: `IMPLEMENTED_REFERENCE_DRY_RUN_VALIDATED_NOT_GPU_EXECUTED`

## Goal
Translate the M6.x-15 `TrainingJobEnvelope` and M6.x-13 exact Training Run identity into current TRL/Transformers/PEFT/bitsandbytes execution arguments without moving framework-specific APIs into AEGIS Core.

## Flow
`TrainingJobEnvelope → Exact Identity Check → Runtime Admission Handoff → TRL/PEFT Plan → 4-bit Model/Tokenizer Load → SFTTrainer → Progress Callback → SIGTERM/SIGINT Stop Request → Safe Checkpoint Boundary → Resume From Exact Checkpoint → Adapter Artifact Manifest/Hash → Execution Receipt → M6.x-14 Evaluation Handoff`.

## Critical boundaries
- No scheduler, lease manager, retry engine or recovery engine is implemented here.
- Model and tokenizer revisions are exact pins; floating `latest` is rejected by policy.
- Resume uses the exact checkpoint path and the same run fingerprint.
- A signal handler only requests stop; checkpoint/save occurs at Trainer callback boundary, not directly inside the signal handler.
- QLoRA/framework imports are deferred until real execution so contract tests do not require a GPU stack.
- Adapter artifact hashes are sealed before evaluation handoff.
- Completion receipt is not Promotion approval.

## Execution status
Reference bridge and dependency-free fixture tests are implemented. Long-running GPU fine-tuning was not executed in this phase.
