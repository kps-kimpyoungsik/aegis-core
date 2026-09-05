# AEGIS RELEASE CONTROL — R0.9 Application E2E Convergence Gate

Status: `R0_9_APPLICATION_E2E_EXECUTABLE_PASS`

## Scope

`API -> Application Runtime -> Ownership/Retrieval -> Harness -> Validation -> Episode/Failure Memory -> Canonical Events -> Runtime Snapshot -> React SPA Projection`

## Executed evidence

- Fresh archive extraction initially failed because local workspace symlinks are not preserved by tar packaging.
- R0.9 promotes that repeated failure into deterministic `bootstrap:workspace` software capability.
- After bootstrap, R0.7 baseline regression: 45/45 PASS.
- R0.9 real success E2E validates selective memory retrieval, real harness/tool execution, validation, episodic memory, provenance, runtime snapshot and React projection.
- R0.9 real failure E2E validates authority DENY -> FAILED task -> FAILURE memory -> canonical FAILURE event -> UI failure projection.
- Full regression after mutation: 47/47 PASS.
- Clean archive round-trip (no node_modules) re-bootstrap + full regression: 47/47 PASS.
- Main also contains separately owned R0.8 live PostgreSQL/Redis storage evidence; this candidate consumes that baseline without redefining storage ownership.

## Invariants

1. Import/transport layers do not own domain semantics.
2. React SPA remains a read-only projection of canonical runtime state.
3. Successful execution captures EPISODIC memory only; no automatic semantic promotion.
4. Authority DENY fails closed and is visible in memory/event/UI projections.
5. Release archive can rebuild workspace links deterministically without external package downloads.
6. Storage/Data Plane responsibility is reused from main; this change does not redefine physical storage ownership.

## NOT_EXECUTED FOR THIS NEW CANDIDATE SHA

- ESLint revalidation
- release-surface typecheck revalidation
- React/Vite production build revalidation
- product-to-live-PostgreSQL E2E wiring
- real process-kill recovery through product runtime
- container build
- staging
- production

Rollback Point: `main@aba979151aa9f981271038fcbf8d2688f043e007`
