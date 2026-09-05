# AEGIS Session-Based Design & Implementation Policy

## Status
ACTIVE_GOVERNANCE_CANDIDATE

## Purpose
All subsequent AEGIS design and implementation work must use the consolidated session source set as evidence while preserving the verified Canonical implementation on `main` as the executable source of truth.

## Source precedence
1. Verified Canonical implementation, tests, manifests, CI evidence on `main`.
2. `AEGIS_CORE_MASTER_IMPLEMENTATION_CONCEPT_v2.0.md` and the latest governing constitution in the archived session source set.
3. Conflict Guard, Canonical ownership/asset/package registries, Cross-Session Handoff records, Freeze evidence.
4. Verified integration/execution reports.
5. Historical simulations and session deltas as candidate evidence only.
6. Binary ZIP/JAR artifacts as reference evidence only.

## Mandatory startup sequence
Every workstream must perform:

`DISCOVER → OWNER → CONTRACT/DATA/STATE/SIDE-EFFECT/AUTHORITY COLLISION SCAN → REUSE/ADAPT/COMPOSE/HANDOFF → CREATE_LAST → VERIFY → COMPARE → PROMOTE/REJECT/ROLLBACK`

## Canonical boundaries
- One Capability → One Canonical Owner.
- One Contract → One Canonical Definition.
- One Shared Function → One Reusable Package.
- `stable core/contracts ← domain/application ← adapters`.
- Vendor SDKs stay in adapters.
- Session ownership never overrides architecture ownership.
- Simulated PASS is never physical PASS.
- Missing infrastructure remains `NOT_EXECUTED` or `BLOCKED_BY_ENVIRONMENT`.

## Current implementation baseline
The current verified repository contains physical implementations for Runtime Kernel, Portable Brain, and Data Plane P4-01 through P4-03. The next release gate remains P4-04 Live Storage Adapters, but P4-04 must first reuse/adapt any storage integration contracts found in the archived P4.5 session work before creating new adapter contracts.

## P4-04 archive-first rule
Before adding a PostgreSQL/Object/Vector/Graph/Redis adapter, inspect archived P4.5 materials for:
- existing capability signatures;
- canonical package registries;
- live integration CI contracts;
- compose/topology definitions;
- startup collision scans;
- external blocker manifests;
- cross-session handoff records.

Equivalent or structurally compatible contracts must be REUSED or ADAPTED. Historical P4.5 files remain evidence until revalidated against current `main` contracts and current CI.

## Promotion requirements
Every promotion requires:
- Before/After;
- Version;
- Evidence;
- Compatibility;
- Regression tests;
- RollbackPoint;
- Provenance;
- no unresolved C2+ collision in verified scope.

## Archive reference
`session-archive/2026-09-05/SESSION_SOURCE_OF_TRUTH_2026-09-05.md`
`session-archive/2026-09-05/SNAPSHOT_INDEX_2026-09-05.json`
