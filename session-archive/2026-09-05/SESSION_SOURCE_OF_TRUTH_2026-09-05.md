# AEGIS Session Source-of-Truth Policy — 2026-09-05

## Purpose
This archive preserves every physical session work file that existed in `/mnt/data` at consolidation time. It is an evidence/archive layer and does not silently promote historical simulations or generated artifacts to Canonical runtime code.

## Design / implementation precedence
Use sources in this order when continuing AEGIS design and implementation:

1. **Canonical repository `main` implementation and its verified manifests/tests.**
2. **Latest governing constitution / master implementation concept** in this session archive.
3. **Conflict Guard, ownership registries, handoff ledgers, package registries, freeze evidence.**
4. **Verified execution/integration reports and CI evidence.**
5. **Simulation models and historical session deltas** as candidate design evidence only.
6. **ZIP/JAR archives** as immutable artifacts/reference; never as higher authority than source + verified manifest.

## Mandatory interpretation rules
- `simulation` files remain `SIMULATION_ARCHIVE`; they are not proof of physical implementation.
- Historical registry versions are lineage evidence, not automatically the current registry.
- A newer version does not override Canonical ownership unless promotion evidence exists.
- Duplicate/equivalent capability must be REUSE/ADAPT/COMPOSE/HANDOFF before CREATE.
- Physical implementation changes still require Before/After, compatibility, regression tests, provenance, and rollback point.
- Missing environment/infrastructure remains `NOT_EXECUTED` or `BLOCKED_BY_ENVIRONMENT`.
- Protected authority, verifier, audit and security boundaries are never inferred from archived session text.

## Canonical architecture boundary
`stable core/contracts ← domain/application ← adapters`

Runtime-specific vendor SDKs remain in adapters. Data/Memory/Knowledge/Skill canonical semantic ownership must continue to follow the active owner registry and verified `main` manifests.

## Snapshot completeness
See `SESSION_FILE_MANIFEST_2026-09-05.json` for per-file SHA-256, size, classification, and total count. The compressed archive contains the exact file set listed there.
