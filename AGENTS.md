# AEGIS-CORE Global Work Session Constitution

This file is repository-wide and mandatory for every human, agent, session, workstream, automation, and implementation task.

## Latest-Main-Before-Work Invariant

Git canonical `main` is the cross-session synchronization authority. Conversation history, local files, session archives, plans, and previously observed SHAs are evidence only; none may override a newer canonical Git state.

Before designing, implementing, editing, promoting, or committing any task, every session MUST:

1. Read the current `main` HEAD and recent commits.
2. Compare the current HEAD with the session's last observed baseline SHA.
3. Inspect commits added since that baseline, especially files/capabilities/owners/contracts/datasets/events/schemas/dependencies/release evidence that intersect the intended task.
4. Inspect open/active PRs and active workstreams that may touch the intended owner, capability, paths, contracts, datasets, events, schemas, dependencies, release evidence, or authority boundary.
5. Re-read the latest canonical ownership, domain, capability, contract, dataset, and active workstream registries.
6. Reclassify the task using `REUSE -> ADAPT -> COMPOSE -> HANDOFF -> MERGE/SUPERSEDE -> CREATE MINIMAL NEW`.
7. If another session has already solved, partially solved, superseded, or is actively changing the same problem, consume/reuse/reconcile that work; do not recreate it from stale session context.
8. If ownership, path, contract, state, dataset, authority, compatibility, active PR, or active-workstream overlap is unresolved, fail closed and do not mutate the canonical implementation.

## Work-Start Git Barrier

No substantive design or implementation begins until the Latest-Main-Before-Work checks complete. The session MUST establish and retain an exact `baselineMainSha` for the work attempt.

The intended write set MUST be bounded before mutation: canonical owner, capability, paths, contracts, datasets/events, authority surface, and expected validation gates. If this write set overlaps a newly discovered session or PR, stop and reconcile ownership before editing.

## Pre-Write / Pre-Commit Revalidation Invariant

Immediately before every repository write, commit, PR promotion, or merge, every session MUST read `main` HEAD again and recheck relevant open PR/workstream overlap.

A commit is forbidden unless the candidate branch is reconciled against the latest observed `main` HEAD immediately before commit creation. Fetching `main` only at work start is insufficient. If the candidate does not contain or explicitly reconcile the latest canonical changes, the commit MUST NOT be created.

If HEAD, ownership, or overlapping work changed after work began:

- inspect the intervening commits and changed PR/workstream state;
- rerun ownership/capability/path/contract/dataset/event/authority collision checks against the new HEAD;
- rebase/reconcile or supersede the candidate logically and technically;
- rerun affected deterministic, quality, security, integration, and release gates on the refreshed combined baseline;
- write/promote only from evidence based on the refreshed exact HEAD.

A test result obtained against an older HEAD is not sufficient promotion evidence for a newer HEAD.

For merges/promotions, use an exact expected candidate head SHA when the Git surface supports it. If the candidate head moves after validation, previous validation is stale and promotion MUST fail closed until the new exact head is verified.

## Post-Write / Post-Commit Verification

After every repository write/commit, verify the resulting commit SHA, current `main`/branch state, and whether concurrent work landed during the write window. Never claim commit, push, deployment, CI pass, merge, or promotion without tool/evidence confirmation.

If concurrent canonical changes landed, classify the result as requiring convergence and repeat the collision/revalidation loop before further promotion.

## Cross-Session Awareness

Sessions do not communicate by assumption. They communicate through canonical Git artifacts: commits, PRs, registries, workstream records, contracts, tests, evidence, and provenance. Therefore every session must treat newly discovered Git commits or active PRs as potentially authoritative work completed or in progress by another session.

A session owns a bounded workstream, not a capability. One Responsibility -> One Canonical Owner remains mandatory.

Never overwrite another session merely because its work was not present in conversation context. Git evidence outranks stale conversational assumptions.

## Stale Baseline Rule

No implementation or promotion may proceed from a stale baseline when Git can be checked. If Git is unavailable, record `GIT_SYNC_NOT_EXECUTED` and do not make claims about latest-main compatibility or cross-session conflict freedom.

A stale candidate may be retained as provenance/reference, but MUST NOT be promoted as canonical until reconciled and revalidated against current `main`.

## Mandatory Work Evidence

Each substantive workstream should record at minimum:

- `baselineMainSha`
- latest-main check time or evidence reference
- intervening commit assessment
- open PR / active workstream overlap assessment
- canonical owner/capability decision
- bounded intended write set / touched canonical paths
- contract/dataset/event/authority collision result where applicable
- validation evidence tied to exact candidate head
- resulting commit SHA when committed
- post-write latest-main verification
- rollback point

## Canonical Invariants Added

- No work from an unchecked stale Git baseline.
- No duplicate implementation when a newer main commit or active canonical workstream already owns the capability.
- No promotion using validation evidence from a superseded base or moved candidate HEAD.
- No repository write without a final latest-main and active-work conflict recheck.
- No commit unless the candidate is reconciled with the latest observed canonical `main` immediately before commit creation.
- No merge without exact-head fencing when the Git surface supports it.
- No cross-session assumption when Git evidence can resolve it.
- No overwrite of concurrent work without explicit reconciliation.
- No claim of conflict freedom when Git synchronization was not executed.

## Mandatory Cross-Session Work Loop

`SYNC MAIN -> INSPECT RECENT COMMITS/PRS/WORKSTREAMS -> READ REGISTRIES -> CLASSIFY REUSE/ADAPT/COMPOSE/HANDOFF/SUPERSEDE/CREATE -> BOUND WRITE SET -> IMPLEMENT MINIMAL CHANGE -> SYNC MAIN AGAIN -> RECONCILE -> VERIFY EXACT HEAD -> WRITE/COMMIT -> VERIFY RESULT -> PROMOTE OR FAIL CLOSED`

This loop is mandatory at the start of every continuation/wakeup cycle, not only at the beginning of a conversation.

These rules supplement, and do not weaken, the existing Constitution, protected surfaces, authority gates, Conflict Guard, release gates, or rollback requirements.
