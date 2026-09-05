# AEGIS Git Canonical Working Baseline — 2026-09-05

## Status

This branch imports cross-session evidence without changing canonical ownership by copy.

## Canonical owner resolution

- `runtime-kernel/` owns runtime state, validation, replay/recovery, rollback and trace semantics.
- `portable-brain/` owns memory, knowledge, retrieval, skill and portability semantics.
- `data-plane/` owns persistence/data lifecycle/consistency mechanisms, not domain meaning.
- `release-candidate/rc6/` is an imported **reference candidate**, not a new canonical owner.
- `session-import/2026-09-05/` is immutable provenance inventory/history.

## Import rule

Historical session assets are consumed in this order:

`REUSE → ADAPT → COMPOSE → HANDOFF → CREATE`

`CREATE` is allowed only when no existing canonical capability owns the responsibility.

## RC6 semantic mapping

| RC6 reference behavior | Git canonical owner |
| --- | --- |
| Task execution state | `RuntimeExecutionStateKernel` |
| restart/checkpoint/replay | `RuntimeRecoveryReplayKernel` |
| duplicate side-effect suppression | `RuntimeRecoveryReplayKernel` checkpoint/effect semantics |
| validation | `RuntimeValidationKernel` |
| rollback | `RuntimeRollbackKernel` |
| trace/audit | `RuntimeTraceAuditKernel` |
| persistent data lifecycle/migration/restore | `DataLifecycleKernel` |
| canonical dataset ownership | `DatasetRegistryKernel` |
| record/event/projection consistency | `RecordEventProjectionKernel` |
| memory/knowledge/retrieval | `portable-brain/` kernels |
| React operational projection | `release-candidate/rc6/apps/web/` until a UI canonical package is established |

## Promotion rule

Imported reference behavior may be promoted only when:

1. existing owner is identified;
2. no duplicate canonical contract is introduced;
3. target regression improves or preserves behavior;
4. held-out behavior does not regress;
5. evidence is executable and versioned;
6. rollback point is recorded.

## Current release state

RC6 reference semantics are imported. Production GA remains fail-closed until live persistence/model/tool, frontend reproducible build, container/staging, security, recovery and operational evidence are PASS.

## Next work pointer

`Git Integration G1 — RC6 semantics → canonical Java owner regression suite → application/server/React projection → release qualification`
