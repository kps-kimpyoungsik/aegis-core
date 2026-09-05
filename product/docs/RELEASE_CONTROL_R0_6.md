# AEGIS RELEASE CONTROL — R0.6 Application Runtime Integration

Status: `R0_6_APPLICATION_RUNTIME_EXECUTABLE_PASS`

## Canonical scope

`@aegis/application-runtime@0.6.0` integrates the previously validated owners without redefining their semantics:

`Ownership Preflight -> Selective Retrieval -> Task State Progression -> Harness Execution -> Validation -> Episode/Failure Memory -> Canonical Lifecycle Events`

## Executed evidence

- R0.5 baseline regression before mutation: 33/33 PASS
- R0.6 application lifecycle tests: 5/5 PASS
- Full regression after mutation: 38/38 PASS
- Ownership registry: 16 canonical responsibilities PASS
- Package boundaries: 17 packages/apps PASS
- Workspace package versions: 17 PASS
- Release manifest: 25 gates PASS

## Invariants validated

1. Duplicate canonical responsibility returns `HANDOFF_REQUIRED` before harness execution.
2. Selective retrieval packages minimum point context rather than full memory.
3. Successful execution transitions through DIAGNOSING/PLANNED/READY/RUNNING/VALIDATING/COMPLETED.
4. Successful task captures validated `EPISODIC` memory only; no automatic `SEMANTIC` promotion.
5. Harness/runtime failure closes the task as `FAILED` and captures first-class `FAILURE` memory with provenance.
6. Lifecycle event ordering is preserved for the golden path.

## NOT_EXECUTED

- TypeScript compiler
- ESLint runtime
- React/Vite production build
- Physical PostgreSQL
- Real process-kill / crash recovery
- Container build
- Staging deployment
- Production deployment

Rollback Point: `aegis-r0.5-portable-brain`
