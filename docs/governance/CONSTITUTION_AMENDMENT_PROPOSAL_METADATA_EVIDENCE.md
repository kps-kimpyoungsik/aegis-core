# AEGIS Constitution Amendment Proposal — Metadata Ownership & Evidence Scope

## Status
CANDIDATE_REVIEW_ONLY

This document is a proposal only. It does not modify the Constitution, Root Authority, Independent Verifier, Promotion Gate, Production Permission Root, or any other Protected Surface.

## Baseline
`main@7bd1efcc87f3b33d49e27a4b6345a844d0ea923c`

## Why this proposal exists
The current global/session governance already requires latest-main refresh, exact-head validation, stale-candidate rejection, reference-only treatment for historical assets, ownership/capability/path checks, and fail-closed promotion. Those rules are therefore not duplicated here.

Recent cross-session execution exposed two narrower gaps that are not stated as explicit global invariants:

1. Canonical metadata can be as authority-bearing as executable code but is not always explicitly treated as an owned mutation surface.
2. PASS evidence is tied to a specific candidate and execution context, but environment/configuration/dependency changes do not yet have a single explicit invalidation rule.

## Proposal A — Canonical Metadata Ownership

### Rule
Canonical metadata MUST have an explicit owner or derive its mutation authority from an existing canonical responsibility.

Canonical metadata includes, at minimum:

- release manifests and GA evidence matrices;
- workstream, ownership, domain, capability, dataset and asset registries;
- migration metadata and schema compatibility state;
- CI/release/deployment workflow contracts;
- SBOM, provenance, signature and attestation descriptors;
- rollback and recovery evidence descriptors;
- promotion/approval envelopes and their bindings.

A session that is not the canonical owner MAY read, validate, compare, cite, request change, or hand off the metadata. It MUST NOT silently mutate that metadata merely to make its own candidate pass.

### Fail-closed behavior
`NON_OWNER_METADATA_MUTATION -> HANDOFF_REQUIRED / FREEZE`

### Derived invariant
`No cross-owner mutation of canonical metadata without explicit ownership transfer or approved shared contract.`

## Proposal B — Evidence Scope & Invalidation

### Rule
Validation evidence is non-transitive and MUST be bound to the conditions under which it was produced.

Minimum evidence binding:

- candidate/head revision;
- relevant artifact digest where applicable;
- dependency/lock state where applicable;
- configuration/security mode where applicable;
- execution environment class;
- validation gate/version;
- evidence time/freshness where materially relevant.

### Non-transitivity examples

- `PASS(old SHA) != PASS(new SHA)`
- `PASS(staging) != PASS(production)`
- `PASS(test bearer fixture) != PASS(production identity provider)`
- `PASS(memory adapter) != PASS(PostgreSQL)`
- `PASS(previous dependency lock) != PASS(changed dependency lock)`

### Invalidation rule
If a change can materially alter a validated property, the affected evidence MUST become `STALE` or `REVALIDATION_REQUIRED` until the impacted gate is rerun.

Potential invalidators include:

- code or contract change;
- configuration/security mode change;
- dependency or lockfile change;
- database/schema/migration change;
- runtime/container/base-image change;
- deployment topology or environment-class change;
- verifier/gate implementation change.

### Derived invariant
`No inherited PASS across candidate, configuration, dependency, environment, or verifier changes without explicit applicability proof.`

## Existing rules intentionally not duplicated
The following are already covered by current canonical governance and should remain single-source rather than be restated as new constitutional rules:

- Latest-Main-Before-Work / stale baseline rejection;
- exact-head validation and merge fencing;
- active PR/workstream collision inspection;
- historical/reference asset non-promotion until reconciled;
- fail-closed unresolved ownership/path/contract/authority conflict;
- rollback/provenance requirements.

## Suggested work-rule projection
If the Protected-Surface owner approves these amendments, session/runtime policy may project them into deterministic checks:

`PRE-WRITE -> RESOLVE METADATA OWNER -> CHECK WRITE AUTHORITY -> EXECUTE OWNED DELTA -> IDENTIFY AFFECTED EVIDENCE -> MARK STALE/REVALIDATION_REQUIRED -> VERIFY EXACT HEAD/ENV/CONFIG -> PROMOTE OR FAIL CLOSED`

## Suggested software assets
These are implementation candidates, not part of this constitutional proposal:

- metadata ownership entries in canonical registries;
- `evidence-impact-check` that maps changed paths/config/dependencies to affected evidence gates;
- release/evidence schema fields for candidate SHA, artifact digest, environment class, configuration fingerprint and gate version;
- deterministic rejection of stale evidence during promotion.

## Promotion conditions for this proposal
This proposal MUST NOT become constitutional text automatically. Promotion requires:

1. Protected-Surface owner review/approval;
2. overlap comparison against latest Constitution and `AGENTS.md`;
3. no conflict with Root Authority, Independent Verifier, Security Policy, Promotion Gate or Production Permission Root;
4. deterministic projection design where practical;
5. exact-head validation and provenance;
6. explicit rollback/retraction path for the amendment.

## Rollback
Delete/reject this proposal branch/PR. No runtime or Protected-Surface behavior is changed by this document alone.
