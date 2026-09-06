# AEGIS Session / Ownership / Capability Control

## 1. Canonical rule

A session does not own a capability. A Canonical Responsibility owns a capability. A session only opens a bounded workstream against that owner.

```text
Session -> Workstream -> Capability -> Canonical Responsibility -> Canonical Paths
```

Before implementation every workstream must declare:

- workstream id
- session class
- base main SHA
- owner responsibility
- capability ids
- intended touch paths
- collision decision
- state

## 2. Registry precedence

1. `product/contracts/ownership-registry.json` — one responsibility -> one owner
2. `product/contracts/domain-registry.json` — responsibility -> architectural domain
3. `product/contracts/capability-registry.json` — capability -> domain + canonical owner + canonical paths
4. `product/contracts/active-workstream-ledger.json` — session/PR/branch -> claimed capabilities + touch paths
5. `product/tools/workstream-collision-check.mjs` — deterministic fail-closed validation

## 3. Domain classification

- `governance`: constitution, contracts, ownership, collision decisions
- `runtime-execution`: task lifecycle, harness, worker, daemon
- `portable-brain`: memory, knowledge, retrieval
- `data-plane`: persistence, transaction, storage adapters, recovery
- `api-security-edge`: HTTP, authentication, principal, tenant, RBAC, abuse controls
- `ui-ux`: React SPA operational projection
- `release-evolution`: evidence, deployment, rollback, promotion, evolution preflight
- `research-learning`: non-canonical research and learning candidates

## 4. Collision classes

- `C1 CAPABILITY`: two active workstreams implement the same capability -> BLOCK
- `C2 OWNER`: workstream owner differs from capability owner -> BLOCK
- `C3 PATH`: two active workstreams mutate overlapping canonical paths -> BLOCK or HANDOFF
- `C4 CONTRACT`: competing canonical contract definitions -> BLOCK and reuse one definition
- `C5 DATA`: competing source-of-truth/data writer -> BLOCK
- `C6 AUTHORITY`: inherited or duplicated authority surface -> BLOCK
- `C7 BASELINE`: stale main baseline -> REBASE/REVERIFY
- `C8 RELEASE`: duplicate release number/evidence lineage -> BLOCK by release evolution preflight

## 5. Decision order

```text
REUSE -> ADAPT -> COMPOSE -> HANDOFF -> MERGE/SUPERSEDE -> CREATE
```

`CREATE` is valid only when no active/canonical capability owns the responsibility.

## 6. Workstream lifecycle

```text
PLANNED -> ACTIVE -> ACTIVE_CANDIDATE -> READY_FOR_INTEGRATION
-> INTEGRATED
```

Alternative terminal states:

```text
REFERENCE / SUPERSEDED / REJECTED / BLOCKED
```

After merge, the workstream ledger entry must no longer remain `ACTIVE`; it is changed to `INTEGRATED` or `SUPERSEDED` with provenance.

## 7. Shared-function policy

Shared deterministic behavior belongs in the narrowest canonical package or reusable validator/testkit already owning that concern. Sessions must not create local copies of retry, idempotency, outbox, ownership, auth principal, tenant binding, validation, release promotion or evidence logic when a canonical owner exists.

## 8. Release interaction

A candidate may only promote after:

1. workstream collision check passes;
2. canonical deterministic/quality checks pass;
3. relevant integration/security gate passes;
4. full triggered release regression passes;
5. latest `main` is rechecked for drift and competing workstreams;
6. exact candidate head is integrated.

Fail closed: unresolved collision or stale baseline means no promotion.
