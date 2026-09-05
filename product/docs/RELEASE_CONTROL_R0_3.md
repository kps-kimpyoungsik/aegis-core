# AEGIS Release Control R0.3

Status: `R0_3_STORAGE_REFERENCE_EXECUTABLE_PASS`

Implemented reference packages:
- `@aegis/storage-contracts@0.3.0`
- `@aegis/storage-runtime@0.3.0`
- `@aegis/storage-memory@0.3.0`

Executed storage tests: 9/9 PASS.

Validated behaviors:
1. versioned create + stale optimistic-concurrency rejection
2. record/outbox atomic commit
3. outbox failure rolls canonical write back
4. committed pending outbox remains recoverable
5. inbox duplicate delivery produces one effect
6. command idempotency replays prior result
7. checkpoint CAS/resume semantics
8. projection position monotonicity
9. lease expiry increments fencing token and rejects stale holder

Not executed:
- SQLite adapter
- PostgreSQL adapter
- real process-kill/crash recovery
- real multi-process concurrency
- physical backup/restore
- TypeScript compiler / ESLint / React build
- container/staging/production

Rollback point: `aegis-r0.2-strict-baseline`.
