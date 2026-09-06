# Protected Audit Ledger Ownership Proposal

## Status

`CANDIDATE_REVIEW_ONLY / PROTECTED_SURFACE / NO_AUTHORITY_TRANSFER`

This document is a governance proposal only. It does not assign canonical authority, mutate Root Authority, change Independent Verifier semantics, create production permission, or authorize a durable Audit Ledger implementation.

## Baseline

- `baselineMainSha`: `5f0e55949e6a657ed4769421d7319e4c4b8d25d4`
- blocking work item: GitHub Issue #122
- dependent work: Issue #116 / PR #121 handoff completion assurance
- current executable verification blocker: Issue #114 GitHub Actions billing/admission lock
- session operational error state: governed by `docs/governance/SESSION_ERROR_FILEDB_GUIDE.md`; FileDB remains runtime-local/private and is not a substitute for the protected Audit Ledger

## Failure-domain classification

Issue #122 is a `GOVERNANCE_AUTHORITY_BLOCKER`, not a physical/external failure. `PHYSICAL_EXTERNAL_FAILURE_EXCEPTION_GUIDE.md` explicitly excludes authority/ownership ambiguity such as protected Audit Ledger ownership. Therefore repository work MUST NOT classify #122 as a provider/runtime/storage exception merely to continue implementation.

The GitHub Actions billing/admission lock in Issue #114 is a separate `EXTERNAL_PLATFORM_ACCOUNT` blocker. It suppresses duplicate CI retries while unchanged but does not resolve or weaken the protected ownership decision required by #122.

## Problem

`RuntimeTraceAuditKernel` exposes `RuntimeAuditSinkPort.append(AuditEnvelope)` and explicitly limits Runtime ownership to trace/audit correlation and emission. Durable audit-ledger ownership, retention, signing, and external telemetry export remain behind ports/adapters.

Current ownership/domain/capability registries do not define a dedicated durable Audit Ledger owner or capability. Assigning durable ledger semantics to `storage-runtime`, `harness-runtime`, `release-convergence`, or another existing responsibility by inference would create implicit authority over a Protected Surface.

## Constitutional constraints

The following remain non-delegable by inheritance:

- Root Authority;
- Audit Ledger trust semantics;
- Independent Verifier independence;
- Security Policy;
- Secret/signing-key boundary;
- Promotion Gate;
- Production Permission.

A storage implementation, runtime caller, Skill, parent workstream, FileDB, failure-memory record, or release workflow MUST NOT acquire any of those authorities merely because it depends on or persists audit records.

## Proposed ownership split

### 1. Protected Audit Ledger semantic owner — explicit governance decision required

A dedicated canonical responsibility SHOULD be introduced only after protected-surface approval. Candidate responsibility name:

`audit-ledger-governance`

Bounded responsibilities:

- canonical audit-event/envelope schema ownership;
- append-only and immutability invariants;
- chain/integrity contract;
- canonical retention-policy contract;
- signing/verification policy contract;
- allowed audit-event state transitions;
- ledger read/write authority policy;
- evidence/provenance requirements;
- rollback/recovery semantics that do not erase audit history.

Explicit exclusions:

- no application/business workflow ownership;
- no storage-engine/vendor ownership;
- no runtime task orchestration;
- no self-approval of its own verifier results;
- no production permission generation;
- no secret/key custody by implication.

This proposed responsibility remains `UNASSIGNED_CANDIDATE` until the protected authority owner explicitly approves it.

### 2. Runtime emission boundary — reuse existing Runtime owner

`RuntimeTraceAuditKernel` remains responsible only for:

- correlation context;
- audit envelope construction;
- deterministic content hash generation;
- emission through `RuntimeAuditSinkPort`;
- local chain-verification utility where already defined.

Runtime MUST NOT decide durable retention, signing authority, ledger deletion, production trust, or promotion eligibility.

### 3. Physical persistence adapter — bounded Data Plane responsibility

After semantic ownership is approved, an existing Data Plane responsibility MAY own only the physical adapter implementing the approved sink/storage contract.

Candidate reuse target:

`storage-runtime` or a narrower adapter responsibility selected by governance.

Permitted scope:

- durable append mechanics;
- transaction/idempotency/fencing;
- physical storage adapter behavior;
- recovery/backup transport mechanics;
- storage health/availability evidence.

Explicit exclusions:

- no authority to reinterpret or weaken append-only semantics;
- no authority to alter signing or verification policy;
- no authority to change retention/destruction policy;
- no authority to mark audit evidence trusted/promoted;
- no authority to grant production permission.

Therefore:

`AUDIT_LEDGER_SEMANTIC_OWNER != PHYSICAL_STORAGE_ADAPTER_OWNER`

### 4. Independent verification boundary

Independent verification MUST consume ledger evidence without inheriting ledger mutation authority.

Required invariant:

`LEDGER_WRITER != INDEPENDENT_VERIFIER`

The verifier may validate chain integrity, provenance, schema, freshness, completeness, and policy conformance, but it must not silently repair, rewrite, delete, or promote the evidence it verifies.

### 5. Session FileDB boundary

`SESSION_ERROR_FILEDB_GUIDE.md` defines session-scoped operational error state. That FileDB is not the protected Audit Ledger and MUST NOT be promoted into one by reuse or naming.

Allowed relationship:

`SESSION_ERROR_FILEDB -> operational retry/discovery state`

`PROTECTED_AUDIT_LEDGER -> canonical immutable audit/evidence trust record`

The FileDB may reference sanitized audit/evidence identifiers where authorized, but it MUST NOT become the canonical trust store, signing authority, retention authority, or independent-verification source by implication.

## Candidate capability model

No registry mutation is authorized by this proposal. If approved, governance SHOULD register separate bounded capabilities rather than one broad capability:

- `audit.ledger-contract` — protected semantic contract, append-only/integrity/authority boundary;
- `audit.ledger-persistence-adapter` — physical persistence implementation only;
- `audit.ledger-verification` — independent verification consumption boundary.

The capability graph MUST forbid authority escalation through dependency edges.

## Port/adapter contract direction

Existing `RuntimeAuditSinkPort` should remain the runtime-facing emission boundary unless an approved canonical contract supersedes it.

Before any durable adapter is implemented, the canonical contract MUST define at minimum:

- append request identity/idempotency key;
- immutable audit identifier;
- previous/content hash semantics;
- canonical serialization rules used for hashing;
- tenant/scope partition semantics when applicable;
- persistence acknowledgement semantics;
- duplicate append behavior;
- concurrent writer/fencing behavior;
- read/query authorization;
- retention/destruction authority;
- signing key reference without secret material leakage;
- recovery/backup behavior preserving provenance;
- verifier-readable evidence contract;
- explicit failure signatures and fail-closed behavior.

## Handoff audit persistence implication

PR #121 `HANDOFF_AUDITED` events MUST remain candidate/ephemeral projection evidence until this ownership boundary is approved.

The handoff workstream MUST NOT implement a private parallel ledger merely to complete Issue #116. After approval it should adapt to the canonical Audit Ledger contract/adapter rather than owning a separate store.

## Decision options

Protected governance must explicitly choose one of:

1. **Approve split ownership**: create protected semantic owner + bounded physical adapter capability + independent verifier boundary.
2. **Designate an existing owner with explicit bounded scope**: only if the protected authority owner records why authority does not leak into runtime/storage/release responsibilities.
3. **Keep persistence out of scope**: retain PR #121 audit events as ephemeral candidate evidence and keep Issue #116 incomplete.

Fail-closed default if no decision is recorded: option 3.

## Acceptance criteria before implementation

- [ ] protected authority owner explicitly approves an ownership model;
- [ ] canonical responsibility/capability/domain/path registrations are updated by authorized governance work;
- [ ] runtime emission and durable ledger semantics remain separated;
- [ ] physical adapter ownership cannot mutate policy/signing/retention semantics;
- [ ] session FileDB remains operational/private state and is not treated as the protected ledger;
- [ ] independent verifier remains mutation-independent;
- [ ] duplicate Audit Ledger/store search is clean;
- [ ] exact-head deterministic and held-out regression tests execute;
- [ ] security/recovery tests cover append-only behavior, duplicate writes, concurrent writers, tamper detection, backup/restore and unauthorized mutation;
- [ ] rollback preserves immutable audit provenance;
- [ ] GitHub Actions external blocker is cleared before promotion evidence is claimed.

## Current decision state

`ESCALATION_REQUIRED / PROTECTED_OWNER_APPROVAL_REQUIRED`

No implementation, registry ownership transfer, protected-policy mutation, or merge-to-production claim is authorized by this proposal.
