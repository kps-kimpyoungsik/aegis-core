# AEGIS Session Convergence Manifest

- Target repository: `kps-kimpyoungsik/aegis-core`
- Integration branch: `release/rc3-session-convergence-20260905`
- Base commit: `effef9c6e81afb2aa370acd6c17d1eec1b6f0186`
- Canonical rule: preserve existing `runtime-kernel`, `portable-brain`, and `data-plane`; import RC-3 as a release candidate before reconciliation.
- Decision order: `REUSE -> ADAPT -> COMPOSE -> HANDOFF/RELATION -> CREATE`.
- Public customer launch status at import: `NO_GO` until release blockers are closed with runtime evidence.

## Imported source classes

1. Canonical constitutions and historical versions under `docs/canonical-source/`.
2. Session/work-history text under `docs/session-history/`.
3. Latest executable RC-3 source under `release-candidates/rc3/`.
4. Previous RC package digests are indexed under `archive/session-artifacts/`.

## Promotion rule

Nothing under `release-candidates/` becomes canonical merely by being imported. Existing canonical modules remain owners until conflict scan, contract comparison, tests, and promotion evidence pass.
