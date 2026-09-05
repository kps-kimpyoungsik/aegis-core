# M6.x-13 — Local Fine-Tuning / Dynamic Learning Material Optimization

Status: `COMPLETE_SESSION_CANDIDATE`

## Local fine-tuning flow
`Immutable Dataset Snapshot → Local Training Profile → Model/Tokenizer Exact Pin → Canonical QLoRA/SFT Config → Training Run Manifest → Checkpoint Provenance Contract → Evaluation Handoff`

## Dynamic learning material flow
`Evaluation Aggregate → Weakness/Coverage Analysis → LearningMaterialCandidate → Validation → Multi-Verifier Evaluation → Adaptive Material Mix Proposal → Golden Review → New Dataset Snapshot`

## Default local policy
- SFT + QLoRA
- 4-bit quantized base
- NF4
- LoRA adapters
- double quantization when backend supports
- BF16 compute where supported
- gradient checkpointing
- deterministic seed bundle
- local profiles separated for 16GB / 24GB memory classes

## Critical boundaries
- Teacher output is not Golden.
- Generator cannot self-approve.
- Protected Eval raw content is unavailable to the optimizer.
- Adaptive optimizer proposes the next mix; it never mutates the current sealed Snapshot.
- `READY_NOT_STARTED != GPU training executed`.

## Fixture evidence
Training Run: `TRN-dc0dc8fecf6b`
Run Fingerprint: `dc0dc8fecf6bb6166c9d991338747129f194f73888fd02fc6b2a9102127ae97f`
Adaptive Mix: `MIX-51ba8d962173`
Evaluation Handoff: `EVH-9b5ec6d56166`
Gate: PASS.
