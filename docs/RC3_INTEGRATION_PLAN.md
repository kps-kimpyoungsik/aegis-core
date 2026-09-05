# RC3 Cross-Session Integration Plan

## Canonical baseline

Existing `main` remains the canonical implementation baseline for:

- `runtime-kernel/` — P2 runtime ownership.
- `portable-brain/` — P3 memory/knowledge ownership.
- `data-plane/` — P4 dataset/consistency/lifecycle ownership.

`release-candidates/rc3/` is an imported candidate, not a competing canonical root.

## Latest main reconciliation

The convergence branch has been synchronized with `main@10fc5ff76d0b753b2fb7227e01e0c23319cfbacb` through synchronization PR #27. Main now contains P4-04 live storage evidence: PostgreSQL live PASS, Redis live PASS, with the data-plane closure declaring `P4-04_live_storage_adapters = VERIFIED_READY_FOR_INTEGRATION`. Therefore the RC3 PostgreSQL implementation is retained as provenance/regression material and must **not** become a second canonical data-plane owner.

## Reconciliation map

| RC3 capability | Existing owner | Decision |
| --- | --- | --- |
| Task / execution state | runtime-kernel | REUSE + compare candidate semantics |
| Execution fencing | runtime-kernel | ADAPT only if P2 lacks an equivalent stale-writer invariant |
| Work repository port | runtime/data-plane contract boundary | COMPOSE; no adapter ownership leak |
| PostgreSQL repository | data-plane | REUSE P4-04 live adapter work; RC3 repository stays reference/candidate evidence |
| Transactional outbox/inbox schema | data-plane consistency | REUSE P4 consistency owner; no duplicate semantic owner |
| Backup/restore lifecycle | data-plane | REUSE P4-03 verified lifecycle contracts; remaining commercial recovery-live gate stays canonical P4 gap |
| Public API security boundary | security/server edge | NEW bounded candidate; API-key is preview only |
| React SPA | UI projection | NEW candidate; React remains projection, not canonical owner |
| Release readiness gate | distribution/governance | NEW deterministic validator candidate |

## Promotion blockers

1. Compare RC3 Task/Execution contracts with P2 before moving any runtime behavior into `runtime-kernel`.
2. Do not promote RC3 PostgreSQL code independently; consume P4-04/P4-RECOVERY-LIVE evidence and ports from `data-plane`.
3. Execute the remaining P4 commercial recovery-live closure before treating storage as release-complete.
4. Generate and commit the React dependency lock; execute lint/build.
5. Replace controlled-preview API-key identity with customer-grade OIDC/OAuth2/RBAC before public launch.
6. Build/scan OCI images and generate SBOM/provenance.
7. Stage deployment, smoke, recovery and rollback.

## Merge policy

The integration PR must remain draft until deterministic CI and all canonical-owner conflict checks pass. A passing RC3 test does not authorize replacing an existing canonical module. Each absorption is a separate evidence-backed change.
