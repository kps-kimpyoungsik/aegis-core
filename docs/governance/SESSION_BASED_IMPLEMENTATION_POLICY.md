# AEGIS Session-Based Design & Implementation Policy

## Status
ACTIVE_GOVERNANCE

## Global entry point
Repository-root `AGENTS.md` is the mandatory cross-session work constitution. This policy specializes it for session-based implementation and may not weaken its latest-main, conflict, authority, evidence, or rollback invariants.

## Purpose
All AEGIS design and implementation work must preserve the verified Canonical implementation on `main` as the executable source of truth while consuming session/history material only as evidence. Because other sessions may solve or modify the same problem at any time, no workstream may assume that a previously observed repository state is still current.

## Source precedence
1. Current verified Canonical implementation, tests, manifests, CI evidence, ownership/capability/workstream registries on latest `main`.
2. Repository-root `AGENTS.md` and the latest governing AEGIS constitution.
3. Conflict Guard, Canonical ownership/asset/package registries, Cross-Session Handoff records, Freeze evidence.
4. Verified integration/execution reports.
5. Historical simulations and session deltas as candidate evidence only.
6. Binary ZIP/JAR artifacts as reference evidence only.

A newer verified Git state supersedes stale conversation/session assumptions.

## Mandatory wake-up sequence
Before any substantive design, implementation, edit, commit, promotion, release, or deployment:

`LATEST MAIN → RECENT COMMITS → ACTIVE WORKSTREAMS/PRS → OWNER/CAPABILITY/PATH LOOKUP → CONTRACT/DATA/STATE/SIDE-EFFECT/AUTHORITY COLLISION SCAN → REUSE/ADAPT/COMPOSE/HANDOFF/MERGE-SUPERSEDE → CREATE_LAST`

Then execute:

`IMPLEMENT → VERIFY → LATEST MAIN RECHECK → RECONCILE/REBASE IF NEEDED → REGRESSION → COMPARE → PROMOTE/REJECT/ROLLBACK`

If Git cannot be checked, record `GIT_SYNC_NOT_EXECUTED`; do not claim conflict freedom or promotion readiness.

## Canonical boundaries
- One Capability → One Canonical Owner.
- One Contract → One Canonical Definition.
- One Shared Function → One Reusable Package.
- One Canonical Dataset → One Write Owner.
- `stable core/contracts ← domain/application ← adapters`.
- Vendor SDKs stay in adapters.
- Session ownership never overrides architecture ownership.
- Authority is never inherited across sessions.
- Simulated PASS is never physical PASS.
- Missing infrastructure remains `NOT_EXECUTED` or `BLOCKED_BY_ENVIRONMENT`.
- Shared mutable path or state conflicts are serialized until ownership and merge order are resolved.

## Dynamic implementation baseline
There is no permanently hard-coded "next release" or "next module" in this policy. The next executable task is derived from latest canonical `main`, current open workstreams/PRs, owner/capability registries, release evidence, blockers, and regression state at work start.

Historical statements such as "P4-04 is next" are provenance only once `main` has advanced beyond them. A session MUST not revive such a plan without checking whether another session already implemented, superseded, or invalidated it.

## Cross-session commit awareness
When latest `main` differs from the session's last observed SHA:
- enumerate and inspect the intervening commits relevant to the intended capability or touched paths;
- determine whether the intended problem is already solved, partially solved, re-owned, or structurally changed;
- prefer REUSE/HANDOFF/SUPERSEDE over duplicate implementation;
- if the candidate remains valid, rebase/reconcile it onto the refreshed baseline and rerun affected gates.

Immediately before commit/push/merge/promotion, repeat the latest-main and competing-workstream check. Validation from an older HEAD is not sufficient evidence for a newer HEAD.

## Promotion requirements
Every promotion requires:
- exact `baselineMainSha` and refreshed pre-promotion main SHA;
- Before/After;
- Version;
- Evidence;
- Compatibility;
- owner/capability/path/contract/dataset/authority collision result;
- Regression tests;
- RollbackPoint;
- Provenance;
- resulting commit/PR/CI verification;
- no unresolved material collision in verified scope.

Stale candidates are `REBASE_REQUIRED`, `HANDOFF_REQUIRED`, `SUPERSEDED`, `REJECTED`, or `BLOCKED`; they are never silently promoted.

## Mandatory session result status
Every substantive work result should report:
`Conflict Guard / Canonical Owner Changes / Duplicate Work Detected / Handover Required / Shared Core Candidate / Contract-Dataset Conflicts / Regression / Promotion / RollbackPoint`.

## Archive reference
Historical session archives remain useful provenance, but latest canonical Git always wins when they conflict with current implementation state.

`session-archive/2026-09-05/SESSION_SOURCE_OF_TRUTH_2026-09-05.md`
`session-archive/2026-09-05/SNAPSHOT_INDEX_2026-09-05.json`
