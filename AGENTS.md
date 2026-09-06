# AEGIS-CORE Global Work Session Constitution

This file is repository-wide and mandatory for every human, agent, session, workstream, automation, and implementation task.

## Latest-Main-Before-Work Invariant

Git canonical `main` is the cross-session synchronization authority. Conversation history, local files, session archives, plans, and previously observed SHAs are evidence only; none may override a newer canonical Git state.

Before designing, implementing, editing, promoting, or committing any task, every session MUST:

1. Read the current `main` HEAD and recent commits.
2. Compare the current HEAD with the session's last observed baseline SHA.
3. Inspect commits added since that baseline, especially files/capabilities/owners/contracts/datasets/events/schemas/dependencies/release evidence that intersect the intended task.
4. Re-read the latest canonical ownership, domain, capability, and active workstream registries.
5. Reclassify the task using `REUSE -> ADAPT -> COMPOSE -> HANDOFF -> MERGE/SUPERSEDE -> CREATE MINIMAL NEW`.
6. If another session has already solved or superseded the problem, consume/reuse that implementation; do not recreate it from stale session context.
7. If ownership, path, contract, state, dataset, authority, compatibility, or active-workstream overlap is unresolved, fail closed and do not mutate the canonical implementation.

## Pre-Commit Revalidation Invariant

Immediately before every commit or promotion, every session MUST read `main` HEAD again.

If HEAD changed after work began:

- inspect the intervening commits;
- rerun ownership/capability/path collision checks against the new HEAD;
- rebase/reconcile the candidate logically and technically;
- rerun affected deterministic, quality, security, integration, and release gates;
- commit only from evidence based on the refreshed exact HEAD.

A test result obtained against an older HEAD is not sufficient promotion evidence for a newer HEAD.

## Post-Commit Verification

After a write/commit, verify the resulting commit SHA and current branch state. Never claim commit, push, deployment, CI pass, or promotion without tool/evidence confirmation.

## Cross-Session Awareness

Sessions do not communicate by assumption. They communicate through canonical Git artifacts: commits, registries, workstream records, contracts, tests, evidence, and provenance. Therefore every session must treat newly discovered Git commits as potentially authoritative work completed by another session.

A session owns a bounded workstream, not a capability. One Responsibility -> One Canonical Owner remains mandatory.

## Stale Baseline Rule

No implementation or promotion may proceed from a stale baseline when Git can be checked. If Git is unavailable, record `GIT_SYNC_NOT_EXECUTED` and do not make claims about latest-main compatibility or cross-session conflict freedom.

## Mandatory Work Evidence

Each substantive workstream should record at minimum:

- `baselineMainSha`
- latest-main check time or evidence reference
- intervening commit assessment
- canonical owner/capability decision
- touched canonical paths
- collision result
- validation evidence
- resulting commit SHA when committed
- rollback point

## Canonical Invariants Added

- No work from an unchecked stale Git baseline.
- No duplicate implementation when a newer main commit already owns the capability.
- No promotion using validation evidence from a superseded HEAD.
- No commit without a final latest-main conflict recheck.
- No cross-session assumption when Git evidence can resolve it.

These rules supplement, and do not weaken, the existing Constitution, protected surfaces, authority gates, Conflict Guard, release gates, or rollback requirements.
