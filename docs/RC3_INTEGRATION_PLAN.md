# RC3 Cross-Session Integration Plan

## Canonical baseline

Existing `main` remains the canonical implementation baseline for:

- `runtime-kernel/` — P2 runtime ownership.
- `portable-brain/` — P3 memory/knowledge ownership.
- `data-plane/` — P4 dataset/consistency/lifecycle ownership.

`release-candidates/rc3/` is an imported candidate, not a competing canonical root.

## Reconciliation map

| RC3 capability | Existing owner | Decision |
| --- | --- | --- |
| Task / execution state | runtime-kernel | REUSE + compare candidate semantics |
| Execution fencing | runtime-kernel | ADAPT if P2 lacks equivalent stale-writer fence |
| Work repository port | runtime/data-plane contract boundary | COMPOSE; no adapter ownership leak |
| PostgreSQL repository | data-plane adapter edge | ADAPT into storage contracts after physical DB E2E |
| Transactional outbox/inbox schema | data-plane consistency | REUSE P4 consistency owner; RC3 SQL is a physical candidate |
| Public API security boundary | security/server edge | NEW bounded candidate; API-key is preview only |
| React SPA | UI projection | NEW candidate; React remains projection, not canonical owner |
| Release readiness gate | distribution/governance | NEW deterministic validator candidate |

## Promotion blockers

1. Compare RC3 Task/Execution contracts with P2 before moving code into `runtime-kernel`.
2. Compare RC3 PostgreSQL SQL/transaction behavior with P4 dataset and consistency contracts before moving code into `data-plane`.
3. Run PostgreSQL migration/CRUD/outbox/inbox/restart/concurrency E2E.
4. Generate and commit the React dependency lock; execute lint/build.
5. Replace controlled-preview API-key identity with customer-grade OIDC/OAuth2/RBAC before public launch.
6. Build/scan OCI images and generate SBOM/provenance.
7. Stage deployment, smoke, recovery and rollback.

## Merge policy

The integration PR must remain draft until deterministic CI and all canonical-owner conflict checks pass. A passing RC3 test does not authorize replacing an existing canonical module. Each absorption is a separate evidence-backed change.
