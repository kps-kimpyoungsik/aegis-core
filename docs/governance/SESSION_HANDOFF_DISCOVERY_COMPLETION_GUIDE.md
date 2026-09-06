# AEGIS Session Handoff Discovery & Completion Guide

## Status
ACTIVE_GOVERNANCE

## Purpose
This guide is the canonical cross-session operating contract for discovering handed-off errors/work, resolving the canonical owner, identifying the active executor/workstream, auditing completion, preventing duplicate work, and looping until verified completion or explicit fail-closed blocking.

Repository-root `AGENTS.md` is the mandatory entry point. Every session, continuation, wake-up, automation, and workstream MUST discover and apply this guide before selecting a new task whenever handed-off work, unresolved failures, blockers, retries, or cross-session ownership may exist.

## Core invariants

`DELIVERED != ACKNOWLEDGED != IN_PROGRESS != VALIDATED != COMPLETED`

`CANONICAL_OWNER != ACTIVE_EXECUTOR`

A created issue, PR comment, branch, or handoff record proves delivery only. It never proves completion. A canonical owner identifies architectural responsibility; it does not prove that a human/session/automation is currently executing the work.

## Mandatory discovery sequence

At every session start, continuation, or wake-up:

`SYNC LATEST MAIN -> READ AGENTS.md -> READ THIS GUIDE -> INSPECT SESSION/WORKSTREAM FAILURES -> SEARCH CANONICAL REGISTRIES -> SEARCH OPEN ISSUES/PRS -> SEARCH FAILURE FINGERPRINTS -> INSPECT CI/VALIDATION EVIDENCE -> RESOLVE CANONICAL OWNER -> RESOLVE ACTIVE EXECUTOR/WORK CLAIM -> RESOLVE BLOCKERS/DEPENDENCIES -> SELECT NEXT EXECUTABLE ACTION`

If the first lookup finds nothing, the session MUST perform fallback discovery across canonical workstream registries, Failure Memory where available, open GitHub issues, open PRs, workflow/check failures, recent commits, handoff/audit issues, and recent notifications when available.

Only after those checks may the session report `NO_ACTIVE_HANDOFF_CONFIRMED`.

`SEARCH_NOT_FOUND` is not equivalent to `NO_ERROR`.

## Discovery keys

Sessions MUST be able to discover work without knowing an issue number in advance. Preferred lookup keys are:

1. canonical domain;
2. canonical owner/capability;
3. failure fingerprint;
4. active executor/work claim;
5. related contract/data/security/recovery boundary;
6. active workstream/PR/branch;
7. issue reference as a concrete work instance.

Issue numbers are mutable work-instance references, not permanent domain identity.

## Canonical owner versus active executor

Every registered handoff/error MUST distinguish architectural ownership from current execution ownership.

- `canonicalOwner`: stable responsibility/domain owner. It answers **who is accountable for the capability**.
- `activeExecutor`: current human/session/automation/workstream actually performing the work. It answers **who is working on it now**.
- `activeWorkstream`: current branch/PR/workstream identifier carrying the implementation.
- `claimState`: whether execution is actually claimed.

Allowed execution claim states:

- `UNCLAIMED`: owner exists but no current executor is proven.
- `CLAIMED`: one executor/workstream has acquired the work but implementation evidence may not yet exist.
- `IN_PROGRESS`: current executor has concrete work evidence.
- `VALIDATING`: executor is validating the candidate.
- `BLOCKED_EXTERNAL`: executor cannot act until an external state changes.
- `BLOCKED_DEPENDENCY`: executor cannot proceed until a dependency changes.
- `REHANDOFF_REQUIRED`: current claim is stale, invalid, superseded, or owned by the wrong executor.
- `COMPLETED_VERIFIED`: claim may be released only after completion evidence is verified.

If `canonicalOwner` is known but `activeExecutor` is not proven, the work MUST be reported as `UNCLAIMED`, not `IN_PROGRESS`.

GitHub assignee absence, lack of a current branch/PR, or absence of recent work evidence MUST NOT be silently interpreted as an active executor.

## Mandatory active work claim

Before starting implementation on a registered error/handoff, the session MUST establish a durable Active Work Claim or prove that it is continuing the already-canonical claim.

A claim MUST contain, where available:

- handoff/work item ID;
- failure fingerprint;
- canonical owner;
- active executor ID/type (`SESSION`, `AUTOMATION`, `HUMAN`, `WORKSTREAM`);
- active branch;
- active PR/workstream reference;
- claim state;
- bounded intended write set;
- claimed capability/contracts/data/authority surfaces;
- claim creation/update evidence;
- blocker/dependency state;
- last meaningful progress evidence;
- superseded/prior claim reference when ownership moved.

A session MUST NOT create a second implementation claim for the same failure family when an active non-stale claim already covers the same capability or intersecting write set.

## Duplicate-work prevention gate

Before mutation, compare the intended task with every active claim using at least:

`failureFingerprint + canonicalOwner + capability + contracts + data ownership + authority surface + intended write set + active PR/branch`

Decision order is mandatory:

`JOIN/CONTINUE EXISTING CLAIM -> REUSE -> ADAPT -> COMPOSE -> HANDOFF -> MERGE/SUPERSEDE -> CREATE NEW CLAIM`

`CREATE NEW CLAIM` is permitted only when no active equivalent or overlapping claim exists.

If another executor owns an overlapping active claim:

- do not implement in parallel;
- do not create a duplicate branch/PR/issue;
- attach evidence or a proposal to the existing workstream;
- use `HANDOFF`, `COMPOSE`, or `REHANDOFF_REQUIRED` as appropriate;
- if ownership cannot be resolved, fail closed with `ACTIVE_WORK_CONFLICT`.

Two sessions under the same `canonicalOwner` are still duplicate work if they implement the same failure fingerprint/capability/write set independently.

## Claim staleness and release

A claim remains active until one of the following is evidenced:

- verified completion;
- explicit handoff/re-handoff;
- supersession/merge into another canonical workstream;
- explicit pause/block state with no parallel implementation permitted;
- stale determination based on configured governance policy and lack of progress evidence.

A stale claim MUST NOT be silently overwritten. Preserve its provenance, mark `REHANDOFF_REQUIRED`, then acquire/transfer execution ownership explicitly.

## Failure fingerprint rule

Do not use notification subjects or generic messages such as `Process completed with exit code 1` as root-cause identity.

Prefer a fingerprint composed from:

`source | failure-domain | execution-phase | failure-mechanism | affected-boundary`

Multiple notifications with the same causal fingerprint MUST converge on one canonical failure family and owner unless evidence proves distinct mechanisms.

## Canonical lifecycle

Normal lifecycle:

`HANDOFF_CREATED -> ACKNOWLEDGED -> CLAIMED -> IN_PROGRESS -> VALIDATING -> COMPLETED`

Alternative states:

- `UNCLAIMED`
- `BLOCKED_EXTERNAL`
- `BLOCKED_DEPENDENCY`
- `FAILED_VALIDATION`
- `RETRY_DUE`
- `REHANDOFF_REQUIRED`
- `ESCALATION_REQUIRED`
- `ACTIVE_WORK_CONFLICT`
- `STALE`
- `PAUSED`
- `ROLLBACK`
- `FAILED`

## Completion rule

`COMPLETED` requires all of:

- acceptance criteria satisfied;
- validation/verifier evidence exists and is tied to the exact candidate/state being completed;
- no unresolved blocker remains;
- canonical owner/workstream is resolved;
- active execution claim is reconciled/released;
- required regression/security/release checks are preserved;
- provenance and rollback point are retained when applicable.

Issue state alone is insufficient. A closed issue without required evidence is `COMPLETION_UNVERIFIED`.

## Audit loop

Every handed-off item remains auditable until completion:

`HANDOFF -> OWNER RESOLVE -> EXECUTOR/CLAIM RESOLVE -> AUDIT -> ACK/WORK -> VALIDATE -> COMPLETE?`

If incomplete, classify why:

- no executor proven -> `UNCLAIMED`;
- overlapping executor exists -> continue/join/handoff existing claim, never duplicate;
- external account/quota/auth state unchanged -> `WAIT_EXTERNAL`;
- dependency unresolved -> `BLOCKED_DEPENDENCY`;
- deterministic implementation/validation failure -> `REQUIRES_FIX`, then bounded revalidation;
- transient failure -> bounded retry with backoff/budget;
- stale/unacknowledged or owner/executor drift -> `REHANDOFF_REQUIRED`;
- retry budget exhausted, high-risk, authority/security conflict, or unknown high-impact cause -> `ESCALATION_REQUIRED`.

Then audit again.

## Retry constraints

No blind retry.

- Deterministic failure: fix first; rerun only after a relevant change.
- External blocker: suppress retry until external-state fingerprint changes; recover with one cheap canary before bounded fan-out.
- Transient failure: bounded retry only, with cause code and retry budget.
- Security/authority/release failure: never fail open and never auto-promote.
- Dependency blocker: wake dependent work only after dependency completion evidence changes.

Retry count exhaustion MUST escalate instead of looping indefinitely.

## Re-handoff rules

Re-handoff is required when the canonical owner changes, the active executor/claim is stale or invalid, the original owner is stale/unacknowledged, the domain was misclassified, workstreams converge, or a newer canonical implementation supersedes the original target.

Re-handoff MUST preserve the original fingerprint, evidence, provenance, prior owner, prior executor/claim, and decision history. Do not duplicate the implementation or create a new failure family merely to change ownership/executor.

## Mandatory handoff payload

A durable handoff should contain at minimum:

- handoff/work item ID;
- failure fingerprint;
- domain;
- canonical owner/capability;
- active executor and claim state;
- current branch/PR/workstream references;
- current issue reference;
- current lifecycle state;
- root-cause status (`CONFIRMED`, `OBSERVED`, `INFERRED`, `UNKNOWN`, `NOT_EXECUTED`);
- evidence references;
- blocker/dependency references;
- bounded intended write set;
- next executable action;
- acceptance criteria;
- retry policy/count/budget;
- provenance and rollback point where applicable.

## Session wake-up result

Before new implementation, every substantive session MUST be able to state:

- latest canonical main SHA;
- resolved domain/canonical owner;
- active executor/claim state for each relevant handoff;
- active branch/PR/workstream when proven;
- active handed-off failures/work relevant to that scope;
- current audit state;
- unresolved blockers/dependencies;
- intended write-set overlap result;
- next executable action;
- whether it will continue/join an existing claim, retry, wait, re-handoff, escalate, or create a new claim.

If these cannot be resolved, implementation must not start.

## Required ownership display

When reporting registered errors/handoffs, use a table or equivalent structure containing at least:

`Work Item | Failure | Canonical Owner | Active Executor | Active Workstream/PR | Claim State | Audit State | Blocker | Next Action`

Never collapse `Canonical Owner` and `Active Executor` into one column or one concept.

Use `UNCONFIRMED`/`UNCLAIMED` explicitly when current execution ownership cannot be proven from Git evidence.

## Cross-session communication rule

Sessions do not rely on conversational memory to communicate. Durable communication occurs through canonical Git artifacts, registries, issues/PRs, Failure Memory, active work claims, audit evidence, and provenance.

`Session = replaceable execution context`

`Git + Registry + Active Claim + Memory + Evidence + Audit = persistent coordination state`

## Initial canonical audit references

Current handoff/completion assurance work is tracked through GitHub issues including the handoff assurance implementation/audit stream. Issue references are discoverable work instances; sessions MUST still resolve them from current GitHub state rather than hard-code historical numbers as permanent ownership identity.

## Required final status

A session handling handed-off work MUST finish with one of:

- `COMPLETED_VERIFIED`
- `VALIDATING`
- `IN_PROGRESS`
- `UNCLAIMED`
- `RETRY_DUE`
- `REHANDOFF_REQUIRED`
- `ACTIVE_WORK_CONFLICT`
- `BLOCKED_EXTERNAL`
- `BLOCKED_DEPENDENCY`
- `ESCALATION_REQUIRED`
- `NO_ACTIVE_HANDOFF_CONFIRMED`

It MUST NOT silently abandon an unresolved handoff.

## Canonical loop

`DISCOVER -> CLASSIFY -> OWNER_RESOLVE -> ACTIVE_CLAIM_RESOLVE -> OVERLAP_GATE -> HANDOFF/ACK/CLAIM -> EXECUTE -> VALIDATE -> AUDIT -> COMPLETE | RETRY | REHANDOFF | WAIT | ESCALATE -> AUDIT AGAIN`
