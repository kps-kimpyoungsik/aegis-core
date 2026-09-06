# AEGIS Session / Ownership / Capability Control

## Canonical rule

A session does not own a capability. A Canonical Responsibility owns a capability. A session only opens a bounded workstream against that owner.

```text
Session -> Workstream -> Capability -> Canonical Responsibility -> Canonical Paths
```

Every workstream declares workstream id, session class, base main SHA, owner responsibility, capability ids, intended touch paths, collision decision, and state before implementation.

## Registry precedence

1. `product/contracts/ownership-registry.json` — one responsibility -> one owner
2. `product/contracts/domain-registry.json` — responsibility -> architectural domain
3. `product/contracts/capability-registry.json` — capability -> domain + canonical owner + canonical paths
4. `product/contracts/active-workstream-ledger.json` — session/PR/branch -> claimed capabilities + touch paths
5. `product/tools/workstream-collision-check.mjs` — deterministic fail-closed validation

## Domain classification

- `governance`: constitution, contracts, ownership, collision decisions
- `runtime-execution`: task lifecycle, harness, worker, daemon
- `portable-brain`: memory, knowledge, retrieval
- `data-plane`: persistence, transaction, storage adapters, recovery
- `api-security-edge`: HTTP, authentication, principal, tenant, RBAC, abuse controls
- `ui-ux`: React SPA operational projection
- `release-evolution`: evidence, deployment, rollback, promotion, evolution preflight
- `research-learning`: non-canonical research and learning candidates

## Collision classes

- `C1 CAPABILITY`: two active workstreams implement the same capability -> BLOCK
- `C2 OWNER`: workstream owner differs from capability owner -> BLOCK
- `C3 PATH`: two active workstreams mutate overlapping canonical paths -> BLOCK or HANDOFF
- `C4 CONTRACT`: competing canonical contract definitions -> BLOCK and reuse one definition
- `C5 DATA`: competing source-of-truth/data writer -> BLOCK
- `C6 AUTHORITY`: inherited or duplicated authority surface -> BLOCK
- `C7 BASELINE`: stale main baseline -> REBASE/REVERIFY
- `C8 RELEASE`: duplicate release number/evidence lineage -> BLOCK by release evolution preflight

## Decision order

```text
REUSE -> ADAPT -> COMPOSE -> HANDOFF -> MERGE/SUPERSEDE -> CREATE
```

`CREATE` is valid only when no active or canonical capability owns the responsibility.

## Workstream lifecycle

```text
PLANNED -> ACTIVE -> ACTIVE_CANDIDATE -> READY_FOR_INTEGRATION -> INTEGRATED
```

Alternative terminal states are `REFERENCE`, `SUPERSEDED`, `REJECTED`, and `BLOCKED`. After merge, a ledger entry must not remain active.

## Shared-function policy

Shared deterministic behavior belongs in the narrowest canonical package or reusable validator/testkit already owning that concern. Sessions must not create local copies of retry, idempotency, outbox, ownership, authentication principal, tenant binding, validation, release promotion, or evidence logic when a canonical owner exists.

## Promotion sequence

1. workstream collision check
2. canonical deterministic and quality checks
3. relevant integration/security gate
4. triggered release regression
5. latest main drift and competing-workstream recheck
6. exact-head integration

Unresolved collision or stale baseline means no promotion.
