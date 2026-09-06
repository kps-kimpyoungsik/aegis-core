# AEGIS Session / Ownership / Capability Control

A session does not own a capability. A Canonical Responsibility owns a capability. A session opens only a bounded workstream against that owner.

`Session -> Workstream -> Capability -> Canonical Responsibility -> Canonical Paths`

Registry precedence: existing `ownership-registry.json` -> `domain-registry.json` -> `capability-registry.json` -> per-workstream files under `product/contracts/workstreams/` -> deterministic collision check.

Domains: governance; runtime-execution; portable-brain; data-plane; api-security-edge; ui-ux; release-evolution; research-learning.

Collision classes: C1 capability duplication; C2 owner mismatch; C3 canonical path overlap; C4 contract duplication; C5 competing data writer/source of truth; C6 authority duplication/escalation; C7 stale baseline; C8 release-number/evidence collision.

Decision order: `REUSE -> ADAPT -> COMPOSE -> HANDOFF -> MERGE/SUPERSEDE -> CREATE`.

Workstream states: `PLANNED -> ACTIVE -> ACTIVE_CANDIDATE -> READY_FOR_INTEGRATION -> INTEGRATED`, with `REFERENCE`, `SUPERSEDED`, `REJECTED`, `BLOCKED` as alternatives. Each session owns only its own workstream JSON file, avoiding a central active-ledger write hotspot.

Shared deterministic behavior belongs in the narrowest existing canonical owner. Local copies of retry, idempotency, outbox, ownership, authentication principal, tenant binding, validation, release promotion or evidence logic are prohibited when a canonical asset exists.

Promotion requires collision check, deterministic/quality verification, relevant integration/security gate, full triggered release regression, latest-main/competing-workstream recheck, and exact-head integration. Unresolved collision or stale baseline means no promotion.

Known structural issue: `product/release/release-manifest.candidate.json` is currently a serialized shared coordination path because Evolution Release Preflight requires base-SHA alignment. Its owner remains `release-convergence`; redesign of this hotspot must occur in a separate Release-owner workstream, not inside Governance.
