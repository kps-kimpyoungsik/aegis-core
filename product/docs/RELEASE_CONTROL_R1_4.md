# AEGIS Release Control R1.4 — Dependency Reproducibility Gate

## Baseline

Canonical main `422d772a9bc17495da4086a9b415b5704ba9b8ca` includes R1.3 supply-chain evidence (CycloneDX SBOM, HIGH/CRITICAL vulnerability scan, unsigned provenance), but `product/package-lock.json` is absent and release/container workflows still use `npm install`.

## Collision decision

REUSE current product manifests, workspace boundaries, R1.2 OCI path, and R1.3 supply-chain gate. ADAPT only dependency resolution/install behavior. No Runtime, Portable Brain, Data Plane, recovery, authority, verifier, audit, signing/key-custody, or React ownership changes.

## Promotion requirements

1. Generate a lock from current manifests in GitHub Actions.
2. Re-run lock generation and require byte-identical SHA-256 under the same controlled run.
3. Prove a clean `npm ci` consumes that lock.
4. Preserve deterministic checks, lint, release typecheck, and React build.
5. Commit the verified generated lock.
6. Migrate product CI, R1.2 container smoke, R1.3 supply-chain build, and Dockerfile from `npm install` to `npm ci`.
7. Re-run regression and container/supply-chain gates before promotion.

Until the verified lock is committed and all install paths are migrated, status remains `BOOTSTRAP_EVIDENCE_ONLY`.

## Rollback Point

`422d772a9bc17495da4086a9b415b5704ba9b8ca`.
