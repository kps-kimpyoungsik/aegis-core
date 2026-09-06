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

## Autonomous Skill Discovery & Invocation Invariant

Every session, continuation, wake-up, automation, and agent MUST read and apply `docs/governance/SKILL_AUTO_INVOCATION_GUIDE.md` before inventing a new reusable procedure or beginning a substantial subtask that may match an existing Skill.

Skill discovery is autonomous. The user does not need to name a Skill explicitly. Derive a task signature from the current goal/domain/failure fingerprint/capability/risk/tools/output/state, inspect the canonical Skill index/manifests when available, and use the existing `portable-brain` `SkillAssetKernel`/`RetrievalKernel` semantics for selective trigger-based retrieval.

The canonical selection order is:

`CONSTITUTION/POLICY/AUTHORITY -> TASK SIGNATURE -> SKILL MANIFEST DISCOVERY -> EXCLUSION/TRIGGER MATCH -> QUALITY/PROVENANCE -> REQUIRED TOOL + AUTHORITY GATE -> CONTEXT BUDGET -> LOAD detailRef FOR SELECTED SKILLS ONLY -> INVOKE -> VERIFY -> AUDIT`.

`CONSTITUTION/POLICY/AUTHORITY > SKILL`. A Skill cannot override Root Authority, protected surfaces, security policy, independent verifier, promotion gate, production permission, canonical ownership, or explicit user/tool authority.

`EXCLUSION > TRIGGER`. A matching exclusion suppresses invocation even when a trigger matches. Required tools and declared authority MUST be satisfied before invocation. If a required tool is unavailable, classify `SKILL_BLOCKED_TOOL`; if authority is insufficient or ambiguous, classify `SKILL_BLOCKED_AUTHORITY` and fail closed for mutation/high-risk execution.

Do not load all Skill details into context. Inspect manifest/index metadata first and load `detailRef` only for selected Skills. Prefer the smallest set of non-overlapping Skills that covers the task. If selected Skills conflict in authority, state ownership, contracts, data ownership, or side effects, stop with `SKILL_CONFLICT` and resolve canonical ownership before execution.

Quality-tier use is fail-closed: `CANONICAL`, `TRUSTED`, and bounded `VALIDATED` Skills may be autonomously invoked within declared authority/tool constraints; `CANDIDATE` Skills are advisory/evaluation-only by default and MUST NOT silently alter security, policy, production, promotion, or canonical state.

If the physical Skill catalog/index is unavailable in the current canonical source/runtime, record `SKILL_CATALOG_NOT_FOUND` or `SKILL_DISCOVERY_NOT_EXECUTED`; do not interpret that as proof that no Skill exists. Continue using already verified repository procedures and normal authority/security gates.

Every Skill invocation MUST preserve at minimum: task signature, Skill ID/version/quality tier, trigger/exclusion decision, required-tool result, authority decision, loaded detail reference, execution state, verifier/evidence references, provenance, and rollback point when applicable. Skill invocation alone is never completion evidence.

Repeated stable Skill work MUST evolve according to `Repeated Work -> Pattern -> Existing Asset Search -> Candidate Skill/Scaffold -> Evaluation -> Target + Held-out Regression -> Promote/Reject`; deterministic repeated behavior should be moved into Policy/Validator/Library/Component/Service instead of remaining repeated LLM work.

## Session Error Preflight Invariant

Every continuation, wake-up, resumed workstream, or `continue` execution MUST inspect the current Git evidence for errors produced by the same session/workstream before selecting the next task.

At minimum, inspect the session/workstream's own recent branches, commits, pull requests, workflow runs, review comments, handoff issues, superseded candidates, and exact-head validation results. Classify unresolved findings as one or more of: `ACTIVE_FAILURE`, `STALE_BASE`, `SUPERSEDED`, `OWNER_HANDOFF`, `DEPENDENCY_IMPACT_DRIFT`, `REGRESSION`, `UNKNOWN`, or `CLEARED`.

A session MUST NOT continue from the last conversational plan when its Git evidence contains an unresolved failure or newer corrective commit. The unresolved failure mechanism must be diagnosed and either corrected, handed off to its canonical owner, superseded, or explicitly fail-closed before unrelated implementation continues.

Generic CI symptoms such as `Process completed with exit code 1` are not root-cause classifications. The session must inspect the failing workflow/job/step/log and record the concrete violated contract, dependency, baseline, schema, ownership, capability, or environment condition when evidence is available.

Repeated failure mechanisms must be correlated with prior session failures and prevention assets. Deterministic recurring checks should be promoted into existing canonical validators/precommit gates instead of creating parallel ad-hoc checks. Historical failed/superseded candidates remain provenance and MUST NOT be treated as PASS evidence.

The continuation evidence should record, when applicable: session/workstream identifiers, failing PR/commit/workflow/job, concrete failure signature, root-cause status, corrective owner, latest corrective commit/PR, regression status, and whether the failure is safe to close.

## Handoff Discovery & Completion Assurance Invariant

Every session, continuation, wake-up, automation, and workstream MUST read and apply `docs/governance/SESSION_HANDOFF_DISCOVERY_COMPLETION_GUIDE.md` before selecting unrelated new work whenever handed-off work, unresolved failures, retries, blockers, dependencies, owner changes, or cross-session work may exist.

A session MUST NOT depend on conversational memory or a previously supplied issue number to discover its work. It MUST search by canonical domain, owner/capability, failure fingerprint, active workstream/PR, blocker/dependency, and current Git evidence. If a first lookup returns no result, it MUST perform fallback discovery across canonical registries, Failure Memory where available, open issues, open PRs, workflow/check failures, recent relevant commits, handoff/audit records, and recent notifications when available. Only after that sequence may it report `NO_ACTIVE_HANDOFF_CONFIRMED`.

`SEARCH_NOT_FOUND` is not `NO_ERROR`. `HANDOFF_CREATED` is not `COMPLETED`.

The required handoff lifecycle is `HANDOFF_CREATED -> ACKNOWLEDGED -> IN_PROGRESS -> VALIDATING -> COMPLETED`, with `BLOCKED_EXTERNAL`, `BLOCKED_DEPENDENCY`, `FAILED_VALIDATION`, `RETRY_DUE`, `REHANDOFF_REQUIRED`, `ESCALATION_REQUIRED`, `STALE`, `PAUSED`, `ROLLBACK`, and `FAILED` as explicit alternatives.

`COMPLETED` requires acceptance criteria, verifier/validation evidence tied to the exact candidate/state, no unresolved blocker, resolved canonical ownership, preserved required regression/security/release gates, and provenance/rollback evidence where applicable. Issue closure, comment creation, branch creation, or handoff delivery alone is insufficient completion evidence.

Incomplete handed-off work MUST be classified and looped by cause: deterministic failure requires correction before rerun; external state blocks retry until the state fingerprint changes and then requires a cheap canary before bounded fan-out; transient failures permit bounded retry only; unresolved dependencies remain blocked until dependency evidence changes; stale/unacknowledged or owner-drifted work requires re-handoff; exhausted retry budgets, high-risk unknown causes, security/authority conflicts, or release-critical unresolved failures require escalation. Blind or infinite retry is prohibited.

Every substantive session result involving handed-off work MUST end in one explicit state: `COMPLETED_VERIFIED`, `VALIDATING`, `RETRY_DUE`, `REHANDOFF_REQUIRED`, `BLOCKED_EXTERNAL`, `BLOCKED_DEPENDENCY`, `ESCALATION_REQUIRED`, or `NO_ACTIVE_HANDOFF_CONFIRMED`. Unresolved work may not be silently abandoned.

## Target-File Git Status Preflight Invariant

Before editing any implementation/design file, every session MUST inspect the Git state of the exact target files/directories it intends to touch.

For each target path, determine at minimum:

- whether the path already exists on current canonical `main`;
- the latest canonical blob/file revision when it exists;
- recent commits that changed the target or its owning package/contract;
- whether an open PR or active workstream already touches the same path or semantic owner;
- whether the candidate/local copy is behind, diverged, modified, staged, untracked, or otherwise not identical to the latest canonical source when a local Git worktree is available.

Classify each intended target as `UNCHANGED`, `CHANGED_BY_OTHER_SESSION`, `ACTIVE_OVERLAP`, `NEW_PATH`, or `UNKNOWN` before editing. `CHANGED_BY_OTHER_SESSION`, `ACTIVE_OVERLAP`, and `UNKNOWN` require explicit reload/reconciliation or fail-closed handoff before mutation.

When a local Git checkout is available, refresh remotes first and inspect branch/ahead-behind plus staged/unstaged/untracked target-file state. When only a remote Git surface is available, the canonical `main` file/blob revision, recent commit history, PR/workstream overlap, and exact target-path comparison are the required equivalent evidence.

No work may start from a target file copied from stale session context when its current Git state can be resolved.

## Pre-Write / Pre-Commit Revalidation Invariant

Immediately before every repository write, commit, PR promotion, or merge, every session MUST refresh/read the newest remote canonical `main`, reload intersecting target files/contracts/registries, and recheck relevant open PR/workstream overlap.

A commit is forbidden unless the candidate branch is reconciled against the latest observed `main` HEAD immediately before commit creation. Fetching `main` only at work start is insufficient. If the candidate does not contain or explicitly reconcile the latest canonical changes, the commit MUST NOT be created.

For candidate metadata, `baselineMainSha` MUST mean the exact canonical `main` parent from which the candidate branch was created or last reconciled. It MUST NOT be rewritten on `main` merely to chase the current `main` HEAD, because doing so creates a self-referential moving-baseline loop. Baseline metadata advances only when a candidate is explicitly rebased/reconciled onto a newer canonical parent.

If HEAD, target-file revision, ownership, or overlapping work changed after work began:

- inspect the intervening commits and changed PR/workstream state;
- reload every intersecting target file from the refreshed canonical source;
- rerun ownership/capability/path/contract/dataset/event/authority collision checks against the new HEAD;
- rebase/reconcile or supersede the candidate logically and technically;
- rerun affected deterministic, quality, security, integration, and release gates on the refreshed combined baseline;
- write/promote only from evidence based on the refreshed exact HEAD.

A test result obtained against an older HEAD is not sufficient promotion evidence for a newer HEAD.

For merges/promotions, use an exact expected candidate head SHA when the Git surface supports it. If the candidate head moves after validation, previous validation is stale and promotion MUST fail closed until the new exact head is verified.

If the newest remote canonical source or target-file state cannot be refreshed immediately before commit, record `PRE_COMMIT_SOURCE_SYNC_NOT_EXECUTED` and do not create or promote a canonical commit.

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
- autonomous Skill discovery/invocation state and selected Skill evidence when applicable
- session/workstream error-preflight evidence and unresolved failure status
- handoff discovery/audit state and active failure fingerprints when applicable
- blocker/dependency state and retry/re-handoff/escalation decision when applicable
- target-file Git status / revision evidence
- intervening commit assessment
- open PR / active workstream overlap assessment
- canonical owner/capability decision
- bounded intended write set / touched canonical paths
- contract/dataset/event/authority collision result where applicable
- `preCommitMainSha` or equivalent exact latest-source evidence
- validation evidence tied to exact candidate head
- resulting commit SHA when committed
- post-write latest-main verification
- rollback point

## Canonical Invariants Added

- No substantial reusable procedure before autonomous Skill discovery when a matching Skill may exist.
- No Skill invocation that bypasses exclusion, required-tool, authority, quality, provenance, context-budget, or verification gates.
- No Candidate Skill silently treated as canonical/trusted behavior.
- No bulk loading of all Skill detail content when manifest/index selection can narrow context.
- No continuation before current session/workstream Git failures are inspected and classified.
- No unresolved handoff may be ignored because a session cannot remember its issue number.
- No `SEARCH_NOT_FOUND` result may be treated as `NO_ERROR` before fallback discovery.
- No handoff, issue, comment, branch, or PR creation may be treated as completion without acceptance and verifier evidence.
- No blind or infinite retry; retry must follow failure-domain policy and budget.
- No work from an unchecked stale Git baseline.
- No target-file mutation before exact Git target status/revision inspection.
- No duplicate implementation when a newer main commit or active canonical workstream already owns the capability.
- No promotion using validation evidence from a superseded base or moved candidate HEAD.
- No repository write without a final latest-main, target-file, and active-work conflict recheck.
- No commit unless the candidate is reconciled with the latest observed canonical `main` immediately before commit creation.
- No self-referential baseline chasing on `main`; candidate baseline metadata records a reconciled parent SHA, not the commit that stores the metadata.
- No canonical commit when latest remote source refresh is unavailable.
- No merge without exact-head fencing when the Git surface supports it.
- No cross-session assumption when Git evidence can resolve it.
- No overwrite of concurrent work without explicit reconciliation.
- No claim of conflict freedom when Git synchronization was not executed.

## Mandatory Cross-Session Work Loop

`SYNC MAIN -> READ AGENTS + SKILL AUTO-INVOCATION GUIDE + HANDOFF DISCOVERY/COMPLETION GUIDE -> INSPECT THIS SESSION/WORKSTREAM ERRORS -> DISCOVER ACTIVE HANDOFFS/FINGERPRINTS/BLOCKERS -> BUILD TASK SIGNATURE -> DISCOVER/TRIGGER/AUTHORITY-GATE SKILLS -> INSPECT TARGET FILE GIT STATUS -> INSPECT RECENT COMMITS/PRS/WORKSTREAMS -> READ REGISTRIES -> CLASSIFY REUSE/ADAPT/COMPOSE/HANDOFF/SUPERSEDE/CREATE -> BOUND WRITE SET -> IMPLEMENT MINIMAL CHANGE -> REFRESH LATEST REMOTE MAIN + TARGET FILES -> RECONCILE -> VERIFY EXACT HEAD -> AUDIT COMPLETION -> COMPLETE/RETRY/REHANDOFF/WAIT/ESCALATE -> WRITE/COMMIT -> VERIFY RESULT -> PROMOTE OR FAIL CLOSED`

This loop is mandatory at the start of every continuation/wakeup cycle, not only at the beginning of a conversation.

These rules supplement, and do not weaken, the existing Constitution, protected surfaces, authority gates, Conflict Guard, release gates, or rollback requirements.
