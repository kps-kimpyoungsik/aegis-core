# AEGIS SLM Design Assets

Status: `SESSION_CANDIDATE_NOT_EXECUTABLE_MAIN`

This area stores SLM-specific design, contracts, validation evidence, and executable reference adapters without redefining canonical ownership held by `runtime-kernel`, `portable-brain`, `data-plane`, and `product`.

## Structure
- `common/` — shared invariants, contracts, lineage, ownership boundaries
- `learning-data/m6x12/` — immutable dataset snapshot and split assignment
- `model-training/m6x13/` — local fine-tuning and dynamic learning material optimization
- `model-training/m6x14/` — checkpoint integrity, regression/forgetting/false-pass evaluation, observed learning utility
- `model-training/m6x15/` — resume-safe training job envelope and runtime-kernel handoff
- `model-training/m6x16/` — actual TRL/PEFT/QLoRA outbound execution bridge, safe stop/checkpoint callback, artifact hashing and execution receipt
- `verification/` — collision and gate evidence

## Canonical boundary
`docs/slm` contains SLM design/evidence/reference adapter code only. Scheduling, runtime admission, lease/resource control, recovery/replay, rollback and canonical trace/audit remain owned by `runtime-kernel`. Dataset lifecycle remains `data-plane` owned and knowledge/evidence retrieval remains `portable-brain` owned.

## Learning lineage
`Local SLM / Online LLM / Knowledge / Asset / Experience → CLO → Golden → Dataset Snapshot → Training Run → Runtime Job → TRL/PEFT Adapter → Checkpoint → Evaluation → Regression/Forgetting/False-PASS → Promotion Governance`.
