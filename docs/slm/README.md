# AEGIS SLM Design Assets

Status: `SESSION_CANDIDATE_NOT_EXECUTABLE_MAIN`

This area stores SLM-specific design, contracts, validation evidence, and session synchronization records without redefining executable ownership already held by `runtime-kernel`, `portable-brain`, `data-plane`, and `product`.

## Structure
- `common/` — shared invariants, contracts, lineage, ownership boundaries
- `learning-data/m6x12/` — immutable dataset snapshot and split assignment
- `model-training/m6x13/` — local fine-tuning and dynamic learning material optimization
- `model-training/m6x14/` — local training execution, checkpoint integrity, regression/forgetting/false-pass evaluation, observed learning utility
- `model-training/m6x15/` — resume-safe job envelope, runtime-kernel handoff, heartbeat, bounded retry, completion receipt
- `verification/` — collision and gate evidence

## Canonical boundary
`docs/slm` is design/evidence plus executable reference code only. Production runtime ownership remains in the canonical owner modules. SLM orchestration must HANDOFF dispatch, resource control, recovery, rollback and trace/audit to `runtime-kernel` rather than duplicate them.

## Learning lineage
`Local SLM / Online LLM / Knowledge / Asset / Experience → CLO → Golden → Dataset Snapshot → Training Run → Training Job → Checkpoint → Evaluation → Regression/Forgetting/False-PASS → Promotion Governance`.
