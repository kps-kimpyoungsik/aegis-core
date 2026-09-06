# AEGIS Session Handoff Discovery & Completion Guide

## Status
ACTIVE_GOVERNANCE

## Purpose
This guide is the canonical cross-session operating contract for discovering handed-off errors/work, resolving the canonical owner, auditing completion, and looping until verified completion or explicit fail-closed blocking.

Repository-root `AGENTS.md` is the mandatory entry point. Every session, continuation, wake-up, automation, and workstream MUST discover and apply this guide before selecting a new task whenever handed-off work, unresolved failures, blockers, retries, or cross-session ownership may exist.

## Core invariant

`DELIVERED != ACKNOWLEDGED != IN_PROGRESS != VALIDATED != COMPLETED`

A created issue, PR comment, branch, or handoff record proves delivery only. It never proves completion.

## Mandatory discovery sequence

At every session start, continuation, or wake-up:

`SYNC LATEST MAIN -> READ AGENTS.md -> READ THIS GUIDE -> INSPECT SESSION/WORKSTREAM FAILURES -> SEARCH CANONICAL REGISTRIES -> SEARCH OPEN ISSUES/PRS -> SEARCH FAILURE FINGERPRINTS -> INSPECT CI/VALIDATION EVIDENCE -> RESOLVE OWNER -> RESOLVE BLOCKERS/DEPENDENCIES -> SELECT NEXT EXECUTABLE ACTION`

If the first lookup finds nothing, the session MUST perform fallback discovery across canonical workstream registries, Failure Memory where available, open GitHub issues, open PRs, workflow/check failures, recent commits, handoff/audit issues, and recent notifications when available.

Only after those checks may the session report `NO_ACTIVE_HANDOFF_CONFIRMED`.

`SEARCH_NOT_FOUND` is not equivalent to `NO_ERROR`.

## Discovery keys

Sessions MUST be able to discover work without knowing an issue number in advance. Preferred lookup keys are:

1. canonical domain;
2. canonical owner/capability;
3. failure fingerprint;
4. related contract/data/security/recovery boundary;
5. active workstream/PR;
6. issue reference as a concrete work instance.

Issue numbers are mutable work-instance references, not permanent domain identity.

## Failure fingerprint rule

Do not use notification subjects or generic messages such as `Process completed with exit code 1` as root-cause identity.

Prefer a fingerprint composed from:

`source | failure-domain | execution-phase | failure-mechanism | affected-boundary`

Multiple notifications with the same causal fingerprint MUST converge on one canonical failure family and owner unless evidence proves distinct mechanisms.

## Canonical lifecycle

Normal lifecycle:

`HANDOFF_CREATED -> ACKNOWLEDGED -> IN_PROGRESS -> VALIDATING -> COMPLETED`

Alternative states:

- `BLOCKED_EXTERNAL`
- `BLOCKED_DEPENDENCY`
- `FAILED_VALIDATION`
- `RETRY_DUE`
- `REHANDOFF_REQUIRED`
- `ESCALATION_REQUIRED`
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
- required regression/security/release checks are preserved;
- provenance and rollback point are retained when applicable.

Issue state alone is insufficient. A closed issue without required evidence is `COMPLETION_UNVERIFIED`.

## Audit loop

Every handed-off item remains auditable until completion:

`HANDOFF -> AUDIT -> ACK/WORK -> VALIDATE -> COMPLETE?`

If incomplete, classify why:

- external account/quota/auth state unchanged -> `WAIT_EXTERNAL`;
- dependency unresolved -> `BLOCKED_DEPENDENCY`;
- deterministic implementation/validation failure -> `REQUIRES_FIX`, then bounded revalidation;
- transient failure -> bounded retry with backoff/budget;
- stale/unacknowledged or owner drift -> `REHANDOFF_REQUIRED`;
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

Re-handoff is required when the canonical owner changes, the original owner is stale/unacknowledged, the domain was misclassified, workstreams converge, or a newer canonical implementation supersedes the original target.

Re-handoff MUST preserve the original fingerprint, evidence, provenance, prior owner, and decision history. Do not duplicate the implementation or create a new failure family merely to change ownership.

## Mandatory handoff payload

A durable handoff should contain at minimum:

- handoff/work item ID;
- failure fingerprint;
- domain;
- canonical owner/capability;
- current issue/PR/workstream references;
- current state;
- root-cause status (`CONFIRMED`, `OBSERVED`, `INFERRED`, `UNKNOWN`, `NOT_EXECUTED`);
- evidence references;
- blocker/dependency references;
- next executable action;
- acceptance criteria;
- retry policy/count/budget;
- provenance and rollback point where applicable.

## Session wake-up result

Before new implementation, every substantive session should be able to state:

- latest canonical main SHA;
- its resolved domain/owner/workstream;
- active handed-off failures/work relevant to that scope;
- current audit state;
- unresolved blockers/dependencies;
- next executable action;
- whether retry is allowed, suppressed, re-handoff is required, or escalation is required.

## Cross-session communication rule

Sessions do not rely on conversational memory to communicate. Durable communication occurs through canonical Git artifacts, registries, issues/PRs, Failure Memory, audit evidence, and provenance.

`Session = replaceable execution context`

`Git + Registry + Memory + Evidence + Audit = persistent coordination state`

## Initial canonical audit references

Current handoff/completion assurance work is tracked through GitHub issues including the handoff assurance implementation/audit stream. Issue references are discoverable work instances; sessions MUST still resolve them from current GitHub state rather than hard-code historical numbers as permanent ownership identity.

## Required final status

A session handling handed-off work MUST finish with one of:

- `COMPLETED_VERIFIED`
- `VALIDATING`
- `RETRY_DUE`
- `REHANDOFF_REQUIRED`
- `BLOCKED_EXTERNAL`
- `BLOCKED_DEPENDENCY`
- `ESCALATION_REQUIRED`
- `NO_ACTIVE_HANDOFF_CONFIRMED`

It MUST NOT silently abandon an unresolved handoff.

## Canonical loop

`DISCOVER -> CLASSIFY -> OWNER_RESOLVE -> HANDOFF/ACK -> EXECUTE -> VALIDATE -> AUDIT -> COMPLETE | RETRY | REHANDOFF | WAIT | ESCALATE -> AUDIT AGAIN`
