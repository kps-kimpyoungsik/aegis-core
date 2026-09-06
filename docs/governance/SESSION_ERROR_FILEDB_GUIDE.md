# AEGIS Session Error FileDB Guide

Status: ACTIVE_GOVERNANCE

## Purpose

Manage operational error state in a local/file-backed database so each session searches and retries only its own unresolved work, while preserving completed history without re-executing it.

## Canonical invariants

- `SESSION_SCOPE_FIRST`: default error lookup MUST require the current `sessionId` and MUST NOT scan other sessions unless an explicit handoff/audit operation requests cross-session evidence.
- `COMPLETED_IS_HISTORY`: `COMPLETED`, `CLEARED`, and `SUPERSEDED` records remain immutable history/provenance but MUST be excluded from default actionable queries and MUST NOT be retried.
- `WAITING_REMAINS_DISCOVERABLE`: `WAITING`, `BLOCKED_EXTERNAL`, `BLOCKED_DEPENDENCY`, `RETRY_DUE`, `VALIDATING`, `IN_PROGRESS`, and `REHANDOFF_REQUIRED` remain searchable for the owning session.
- `RETRY_REQUIRES_STATE`: retry eligibility is derived from current state plus retry policy/evidence; existence of an old error record alone is never sufficient.
- `APPEND_ORIENTED_HISTORY`: state changes append a new record/event; do not erase prior observations or rewrite history in place.
- `STABLE_FINGERPRINT`: dedupe by `sessionId + failureFingerprint + workItemId` where available. Repeated notifications become occurrences of one causal record, not new independent errors.
- `PRIVATE_BY_DEFAULT`: FileDB content is runtime-local/private operational state. Do not commit raw private evidence, secrets, provider tokens, personal addresses, raw mail bodies, opaque cursors, or credentials to public Git.
- `PHYSICAL_EXTERNAL_REUSE`: known physical/external failures MUST resolve through `PHYSICAL_EXTERNAL_FAILURE_EXCEPTION_MEMORY.md`; unchanged external state suppresses retry.

## File layout

Recommended runtime layout:

`filedb/session-errors/<sha256(sessionId)>.jsonl`

The raw session identifier MUST NOT be used as a path segment. Hash it before deriving a file name.

Each line is one immutable event/record snapshot. Minimum safe fields:

```json
{
  "eventId": "...",
  "sessionId": "runtime-session-id",
  "workItemId": "optional-stable-work-id",
  "failureFingerprint": "source|domain|phase|mechanism|boundary",
  "state": "WAITING",
  "observedAt": "2026-09-07T00:20:00+09:00",
  "retryCount": 0,
  "maxRetry": 1,
  "externalStateFingerprint": null,
  "nextAction": "WAIT_FOR_DEPENDENCY",
  "evidenceRefs": [],
  "provenance": "..."
}
```

## Default actionable query

A normal continuation/wakeup MUST query only:

```text
sessionId == currentSessionId
AND latest state IN (
  WAITING,
  BLOCKED_EXTERNAL,
  BLOCKED_DEPENDENCY,
  RETRY_DUE,
  VALIDATING,
  IN_PROGRESS,
  REHANDOFF_REQUIRED
)
```

Default query MUST exclude:

```text
COMPLETED
CLEARED
SUPERSEDED
FAILED_FINAL
CANCELLED
```

## Retry rules

- `COMPLETED/CLEARED/SUPERSEDED/FAILED_FINAL/CANCELLED` -> retry forbidden.
- `WAITING/BLOCKED_DEPENDENCY` -> search/re-evaluate dependency; do not execute retry until dependency evidence changes.
- `BLOCKED_EXTERNAL` -> search/re-evaluate external state; unchanged fingerprint means no retry; changed state allows only the defined canary first.
- `RETRY_DUE` -> retry only if `retryCount < maxRetry` and relevant corrective/transient evidence exists.
- `VALIDATING` -> verify exact candidate/evidence; do not restart implementation by default.
- `IN_PROGRESS` -> continue existing claim/workstream; do not create duplicate implementation.
- `REHANDOFF_REQUIRED` -> resolve owner/executor before execution.

## Cross-session access exception

Cross-session FileDB search is prohibited by default. It is permitted only for explicit handoff/completion audit, canonical owner discovery, dependency impact resolution, or operator-authorized forensic analysis. Such a query MUST be marked `CROSS_SESSION_AUDIT` and MUST NOT silently claim another session's work.

## Wakeup sequence

```text
SYNC MAIN
-> LOAD currentSessionId
-> OPEN session-specific FileDB shard
-> REDUCE latest state per failure identity
-> FILTER actionable states
-> DROP completed/cleared/superseded from retry set
-> RECHECK waiting/external/dependency states
-> CONTINUE/VERIFY/WAIT/RETRY/HANDOFF
-> APPEND new state event
```

## Completion rule

Completion is terminal for retry purposes. A later recurrence with the same fingerprint is a new occurrence only when new evidence proves a new execution episode; it MUST NOT reactivate a completed record silently. Preserve the old completed lineage and create a new episode identity linked by fingerprint/provenance.
