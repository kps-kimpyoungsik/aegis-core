# AEGIS R0.5 — Portable Brain MVP Executable Baseline

Release marker: `R0_5_PORTABLE_BRAIN_EXECUTABLE_PASS`

This package extends R0.4 with executable Portable Brain primitives while preserving the canonical Harness/Brain separation.

## Added packages
- `@aegis/memory-contracts@0.5.0`
- `@aegis/memory-runtime@0.5.0`
- `@aegis/knowledge-contracts@0.5.0`
- `@aegis/retrieval-runtime@0.5.0`

## Executed evidence
- Portable Brain tests: **11/11 PASS**
- Full regression suite: **33/33 PASS**
- Ownership: **15 canonical responsibilities PASS**
- Package boundaries: **16 packages/apps PASS**
- Duplicate public symbols: **41 PASS**
- Workspace versions: **16 PASS**
- Release manifest: **23 gates PASS**

## Important packaging finding
The first full regression after extracting the R0.4 tarball failed because local workspace symlinks under `node_modules/@aegis` are intentionally not preserved in the release archive. Recreating workspace links restored the expected module resolution and the full regression passed 33/33. This is an environment/bootstrap concern, not a Runtime semantic regression.

## Still NOT_EXECUTED
TypeScript compiler, ESLint runtime, React/Vite build, physical PostgreSQL, durable memory persistence, real semantic/vector retrieval, container, staging and production.
