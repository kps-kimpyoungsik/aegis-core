# AEGIS SLM Design Assets

Status: `SESSION_CANDIDATE_NOT_EXECUTABLE_MAIN`

This area stores SLM-specific design, contracts, validation evidence, and session synchronization records without redefining executable ownership already held by `runtime-kernel`, `portable-brain`, `data-plane`, and `product`.

## Structure
- `common/` — shared invariants, contracts, lineage, ownership boundaries
- `learning-data/m6x12/` — immutable dataset snapshot and split assignment
- `model-training/m6x13/` — local fine-tuning and dynamic learning material optimization
- `verification/` — collision and gate evidence

## Canonical boundary
`docs/slm` is design/evidence only. Runtime code promotion requires a separate owner-resolved implementation gate.

## Learning lineage
`Local SLM / Online LLM / Knowledge / Asset / Experience → CLO → Golden → Dataset Snapshot → Training Run → Evaluation → Regression/Forgetting → Promotion Governance`.
