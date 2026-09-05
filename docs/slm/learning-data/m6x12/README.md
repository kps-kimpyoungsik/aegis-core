# M6.x-12 — Immutable Dataset Snapshot / Split Assignment

Status: `COMPLETE_SESSION_CANDIDATE`

## Flow
`ACTIVE_GOLDEN → Dataset Admission → Group Key Resolution → Deterministic Split Assignment → Contamination Guard → Protected Eval Boundary → Immutable Dataset Snapshot → Training Runner Handoff`

## Group-aware split
Priority:
1. `source_lineage_ref`
2. `parent_clo_ref`
3. `semantic_cluster_ref`
4. `golden_id`

The same group is always assigned to the same split. Default target ratios are TRAIN 80%, VALIDATION 10%, REGRESSION 10%, but leakage prevention has priority over ratio precision.

## Hard boundaries
- `ADMIT_ELIGIBLE != included in snapshot`
- Protected Eval is excluded.
- Cross-split lineage leakage is blocked.
- Sealed Snapshot is immutable.
- `READY_NOT_STARTED != Training started`.

## Fixture evidence
Snapshot: `DSNAP-68a9298fe65f`
Fingerprint: `68a9298fe65fa6c610aa74820ce91fe2ae9a69f78a787e8ddc2820fe1a0d7c35`
Training Handoff: `TRH-20d4f41dd5ac`
Gate: PASS.
