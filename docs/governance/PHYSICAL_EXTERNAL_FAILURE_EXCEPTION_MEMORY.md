# AEGIS Physical / External Failure Exception Memory

## Status
ACTIVE_SANITIZED_FAILURE_MEMORY

This file is append-oriented sanitized failure memory for physical/environment/provider failures. It is not a substitute for live provider evidence. Every reuse requires freshness/applicability revalidation.

## Record schema
Each record preserves:
`memoryId | failureFingerprint | classification | state | confidence | firstObservedAt | lastObservedAt | occurrenceSummary | externalStateFingerprint | canonicalOwner | activeExecutor | retryPolicy | recoveryProbe | clearanceCondition | evidenceRefs | notes`

Do not store secrets, private account/payment details, Gmail message/thread IDs, raw private bodies, tokens, private provider payloads, or opaque continuation identifiers.

---

## PEF-001 — GitHub Actions pre-run account/billing admission lock

- `failureFingerprint`: `github-actions|external-platform-account|pre-run-admission|billing-lock|zero-runner-steps`
- `classification`: `EXTERNAL_PLATFORM_ACCOUNT / PRE_RUN_ADMISSION`
- `state`: `BLOCKED_EXTERNAL`
- `confidence`: `CONFIRMED`
- `firstObservedAt`: `2026-09-06`
- `lastObservedAt`: `2026-09-06`
- `occurrenceSummary`: repeated across multiple PR/head workflow families; representative jobs have zero/null executed steps and no runner workload execution
- `externalStateFingerprint`: `GITHUB_ACTIONS_ACCOUNT_ADMISSION_LOCKED`
- `canonicalOwner`: `release-convergence`
- `activeExecutor`: `UNCONFIRMED`
- `retryPolicy`: `SUPPRESS_UNTIL_EXTERNAL_STATE_CHANGES`
- `recoveryProbe`: one cheap canary; require >=1 real runner/workload step before bounded fan-out
- `clearanceCondition`: account/billing admission no longer blocks runner startup AND canary crosses execution boundary AND representative required workflow executes
- `evidenceRefs`: Issue #114; Handoff work item `AEGIS-HANDOFF-GHA-BILLING-001`
- `notes`: workflow `failure` with zero/null steps is `NOT_EXECUTED`, not repository code FAIL/PASS evidence. Do not blind rerun while the state fingerprint is unchanged.

## PEF-002 — External provider quota/capacity exhaustion

- `failureFingerprint`: `external-provider|capacity|request-admission|quota-limit|account-scope`
- `classification`: `EXTERNAL_CAPACITY`
- `state`: `BLOCKED_EXTERNAL`
- `confidence`: `CONFIRMED`
- `firstObservedAt`: `2026-09-06`
- `lastObservedAt`: `2026-09-06`
- `occurrenceSummary`: repeated Vercel deployment daily quota exhaustion and Codex code-review usage-limit notifications across multiple PRs
- `externalStateFingerprint`: `PROVIDER_QUOTA_EXHAUSTED_CURRENT_WINDOW`
- `canonicalOwner`: `release-convergence`
- `activeExecutor`: `UNCONFIRMED`
- `retryPolicy`: `SUPPRESS_UNTIL_CAPACITY_WINDOW_OR_STATE_CHANGES`
- `recoveryProbe`: recheck provider capacity/window; execute one bounded non-duplicative request per head/diff fingerprint when capacity returns
- `clearanceCondition`: authoritative provider capacity is available and bounded representative operation succeeds without thundering-herd replay
- `evidenceRefs`: Issue #111; Handoff work item `AEGIS-HANDOFF-EXTERNAL-CAPACITY-001`
- `notes`: advisory review unavailability must not fail-open mandatory security/release gates. Same head SHA/diff should not create repeated external calls.

## PEF-003 — CI fan-out amplification of an external root cause

- `failureFingerprint`: `github-actions|ci-governance|dispatch|workflow-fanout|same-head-sha`
- `classification`: `STRUCTURAL_AMPLIFIER_NOT_PHYSICAL_ROOT_CAUSE`
- `state`: `RETRY_DUE` with dependency on external admission evidence
- `confidence`: `OBSERVED`
- `firstObservedAt`: `2026-09-06`
- `lastObservedAt`: `2026-09-06`
- `occurrenceSummary`: one upstream defect/environment/quota condition fans out into many workflow failures and notifications
- `externalStateFingerprint`: `DEPENDS_ON_ACTIVE_EXTERNAL_ROOT_CAUSE`
- `canonicalOwner`: `release-convergence`
- `activeExecutor`: `UNCONFIRMED`
- `retryPolicy`: `NO_BLIND_RERUN; IMPLEMENT_FANOUT_CONTROL_SEPARATELY; VALIDATE_AFTER_EXTERNAL_RECOVERY`
- `recoveryProbe`: preflight/impact gate first; after external recovery use cheap canary then bounded downstream matrix
- `clearanceCondition`: fan-out control acceptance criteria pass and representative external-recovery path does not recreate a retry storm
- `evidenceRefs`: Issue #109; Handoff work item `AEGIS-HANDOFF-CI-FANOUT-001`
- `notes`: this is intentionally remembered beside physical exceptions to preserve causal topology, but it is not itself classified as a physical failure.

## Explicit non-physical blockers currently separated

- Issue #122 durable Audit Ledger ownership/capability ambiguity -> `GOVERNANCE_AUTHORITY_BLOCKER`, not physical/external.
- Issue #110 PostgreSQL migration/tenant isolation guarantees -> `DATA_SECURITY_IMPLEMENTATION`, not physical/external; validation may currently be blocked by PEF-001.
- Issue #112 recovery/release convergence -> implementation/recovery domain; may be blocked by PEF-001 and data dependencies but is not itself physical/external.

## Reuse rule
On every session/wakeup:
1. match observed failure to a stable fingerprint;
2. revalidate authoritative state and freshness;
3. if fingerprint and externalStateFingerprint are unchanged, reuse this memory and suppress duplicate retry/issue/branch/workstream creation;
4. if external state changed, do not mark cleared immediately—run the defined recovery probe;
5. append a new observation/reconciliation record or update through the approved canonical memory mechanism while preserving prior history/provenance;
6. never convert blocked/not-executed evidence into PASS.
