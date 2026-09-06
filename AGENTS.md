# AEGIS-CORE Global Work Session Constitution

This repository-root file is the mandatory global entry-point constitution for every human, agent, session, workstream, automation, design task, implementation task, review, release, and deployment activity in this repository. Lower-level session notes or local instructions may add stricter constraints but may not weaken or bypass this file.

## Repository-Root Scope Invariant

Every session MUST read this file before substantive work and MUST treat it as applying recursively to the full repository unless a stricter canonical contract applies. Any session that cannot read the current repository state must report `GIT_SYNC_NOT_EXECUTED` and must not claim latest-main compatibility, cross-session conflict freedom, or promotion readiness.

## Mandatory Wake-Up / Latest-Main-Before-Work Invariant

Git canonical `main` is the cross-session synchronization authority. Conversation history, local files, session archives, plans, cached repository state, and previously observed SHAs are evidence only; none may override a newer canonical Git state.

Before designing, implementing, editing, promoting, committing, releasing, or deploying any task, every session MUST:

1. Read the current `main` HEAD and recent commits.
2. Compare the current HEAD with the session's last observed baseline SHA.
3. Inspect commits added since that baseline, especially files/capabilities/owners/contracts/datasets/events/schemas/dependencies/release evidence that intersect the intended task.
4. Inspect current open/active workstreams or PRs that overlap the intended capability, owner, path, dataset, contract, authority, release number, or deployment surface.
5. Re-read the latest canonical ownership, domain, capability, and workstream registries when present.
6. Reclassify the task using `REUSE -> ADAPT -> COMPOSE -> HANDOFF -> MERGE/SUPERSEDE -> CREATE MINIMAL NEW`.
7. If another session has already solved, superseded, or materially changed the problem, consume/reuse that implementation or rebase the plan; do not recreate it from stale session context.
8. If ownership, path, contract, state, dataset, authority, compatibility, release evidence, or active-workstream overlap is unresolved, fail closed and do not mutate the canonical implementation.

A previously planned task is never authorization to proceed without this wake-up sequence.

## During-Work Drift Invariant

Long-running work MUST treat `main` as potentially moving. Before any destructive edit, shared-path mutation, release-number allocation, owner-registry mutation, or promotion-sensitive decision, re-check whether `main` or a competing workstream advanced materially. If it did, stop the stale operation and reconcile first.

Shared mutable paths are serialized by default. Parallel work is allowed only when capability/path/contract/data/authority ownership is demonstrably independent.

## Pre-Commit Revalidation Invariant

Immediately before every commit, push, PR-ready transition, merge, release promotion, or deployment promotion, every session MUST read `main` HEAD again.

If HEAD changed after work began:

- inspect the intervening commits;
- compare touched paths and capabilities with the intervening delta;
- rerun ownership/capability/path/contract/dataset/authority/release collision checks against the new HEAD;
- rebase/reconcile the candidate logically and technically without silently overwriting newer work;
- rerun affected deterministic, quality, security, integration, migration, recovery, and release gates;
- commit or promote only from evidence based on the refreshed exact HEAD.

A test result obtained against an older HEAD is not sufficient promotion evidence for a newer HEAD.

## Post-Commit / Post-Push Verification

After a write, commit, push, PR update, merge, or deployment action, verify the resulting commit SHA, branch/PR state, and applicable CI/deployment result. Never claim commit, push, deployment, CI pass, merge, or promotion without tool/evidence confirmation.

If another session advances `main` immediately after the write, the resulting commit remains valid provenance but its promotion compatibility must be reassessed against the new HEAD.

## Cross-Session Awareness

Sessions do not communicate by assumption. They communicate through canonical Git artifacts: commits, registries, workstream records, contracts, tests, evidence, issues, PRs, and provenance. Therefore every session must treat newly discovered Git commits as potentially authoritative work completed by another session.

A session owns a bounded workstream, not a capability. `One Responsibility -> One Canonical Owner` remains mandatory.

## Stale Baseline Rule

No implementation or promotion may proceed from a stale baseline when Git can be checked. Stale candidates are `REBASE_REQUIRED`, `SUPERSEDED`, `HANDOFF_REQUIRED`, or `BLOCKED`; they are never silently force-promoted.

If Git is unavailable, record `GIT_SYNC_NOT_EXECUTED` and do not make claims about latest-main compatibility or cross-session conflict freedom.

## Mandatory Work Evidence

Each substantive workstream should record at minimum:

- `baselineMainSha`
- latest-main check time or evidence reference
- intervening commit assessment
- active/competing workstream assessment
- canonical owner/capability decision
- touched canonical paths
- contract/dataset/authority impact
- collision result
- validation evidence
- resulting commit SHA when committed
- post-write verification result
- rollback point

## Mandatory Conflict Output

Every substantive session result should retain the project conflict status fields:

- `Conflict Guard`
- `Canonical Owner Changes`
- `Duplicate Work Detected`
- `Handover Required`
- `Shared Core Candidate`
- `Contract/Dataset Conflicts`
- `Regression`
- `Promotion`
- `RollbackPoint`

Unknown or unexecuted evidence remains `NOT_EXECUTED` or `BLOCKED`; it must never be upgraded by inference.

## Canonical Invariants Added

- No work from an unchecked stale Git baseline.
- No duplicate implementation when a newer main commit already owns the capability.
- No silent overwrite of work committed by another session.
- No promotion using validation evidence from a superseded HEAD.
- No commit or push without a final latest-main conflict recheck.
- No release-number or shared-path mutation without current collision assessment.
- No cross-session assumption when Git evidence can resolve it.
- No stale plan outranks newer canonical Git evidence.

These rules supplement, and do not weaken, the existing Constitution, protected surfaces, authority gates, Conflict Guard, ownership/capability governance, release gates, or rollback requirements.
