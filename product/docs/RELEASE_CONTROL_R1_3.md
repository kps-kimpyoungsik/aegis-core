# AEGIS Release Control R1.3 — Dependency Reproducibility Gate

## Baseline

R1.2 has a CI-verified OCI product build and container health smoke gate, but the product workspace has no committed `package-lock.json` and uses `npm install` in CI/container construction.

## Capability Signature

- responsibility: reproducible JavaScript dependency resolution for the releaseable product
- inputs: root/workspace package manifests, npm registry metadata, Node/npm toolchain
- outputs: immutable package lock, `npm ci`-consumable dependency graph, lock digest evidence
- state: candidate → verified → promotable
- side_effects: package dependency materialization only
- authority: release/build pipeline; no runtime/domain authority
- data_ownership: package metadata only; no canonical runtime dataset ownership
- contracts: npm lockfile + existing product package manifests
- failure_model: missing lock, unstable regeneration, `npm ci` mismatch, lint/typecheck/build regression
- observability: SHA-256 lock digest + GitHub Actions evidence

## Collision Decision

REUSE existing product manifests and R1.2 build path. ADAPT only the dependency installation mechanism. Do not modify Runtime, Portable Brain, Data Plane, Authority, Audit, Verifier, or React state ownership.

## Promotion Gate

1. Generate lock from current manifests.
2. Re-run lock generation and require byte-identical SHA-256 in the same controlled CI environment.
3. Remove materialized dependencies and install with `npm ci` from the generated lock.
4. Run deterministic checks, lint, release typecheck, and React build.
5. Commit the verified lock in a follow-up commit.
6. Replace release CI/Dockerfile `npm install` with `npm ci` only after the committed lock passes regression.

Until step 5 is complete, R1.3 is BOOTSTRAP_EVIDENCE_ONLY and must not be promoted as closed.

## Rollback Point

Git main `986a291a116e3231b21dcb039eaae0dc511eb544` (R1.2).
