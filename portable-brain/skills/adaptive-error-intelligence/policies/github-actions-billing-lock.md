# GitHub Actions Billing-Lock Failure Policy v0.1

Status: CANDIDATE / scoped operational failure classifier
Owner: Adaptive Error Intelligence skill; account billing authority remains external to repository code.

## Purpose

Prevent GitHub Actions account/billing locks from being misclassified as code, test, workflow, or dependency failures and from causing useless retry storms.

## Confirmed signature

Treat the following message as a pre-run platform/account gate failure:

`The job was not started because your account is locked due to a billing issue.`

Corroborating runtime signature:

- workflow run exists;
- job conclusion is failure;
- job has zero executed steps;
- no checkout/build/test command ran;
- job log may be unavailable because the runner never started.

Classification:

```yaml
failure_signature:
  domain: EXTERNAL_PLATFORM_ACCOUNT
  subtype: GITHUB_ACTIONS_BILLING_LOCK
  phase: PRE_RUN_ADMISSION
  code_failure: false
  retryable_without_state_change: false
  security_fail_open_allowed: false
  canonical_repository_owner: NONE
  external_authority_owner: GITHUB_ACCOUNT_BILLING
```

## Causal tree

Investigate in this order:

1. Failed/declined payment or authorization hold.
2. Invalid/expired payment method.
3. Paid trial/subscription billing authorization state.
4. GitHub Actions included quota exhausted without valid payment method.
5. Actions budget/spending restriction preventing additional metered use.
6. Account/plan transition leaving a server-side billing lock.
7. If account UI shows no actionable debt/payment problem, escalate to GitHub Billing Support; repository code cannot clear a server-side account lock.

Do not claim which branch is confirmed unless billing/account evidence is available.

## Deterministic prevention

When the signature is observed:

```text
BILLING_LOCK_SIGNATURE
  -> classify PRE_RUN_ACCOUNT_GATE
  -> suppress code RCA
  -> suppress automatic workflow rerun
  -> suppress fan-out reruns for the same account-state fingerprint
  -> emit one actionable billing handoff
  -> preserve required security/release checks as BLOCKED, never PASS
  -> after external account state changes, run one canary workflow
  -> only then resume bounded CI fan-out
```

Suggested failure fingerprint:

`github-actions|billing-lock|account-scope|pre-run|zero-steps`

## Recovery protocol

External action is required. Repository automation cannot unlock GitHub billing state.

1. Inspect GitHub Settings -> Billing & Licensing -> Payment information / Payment history / Budgets and alerts for the owning account or organization.
2. If payment or authorization failed, update payment information to trigger a new authorization/payment.
3. If Actions quota/budget is the blocker, set an appropriate Actions budget and ensure a valid payment method where required.
4. If the UI shows no debt/failed authorization but the lock persists, open GitHub Billing Support with the exact error, account/org, affected public repository, example run ID, and timestamp.
5. Do not mass-rerun workflows while locked.
6. After unlock, run a cheap canary workflow first. Require at least one real step to start before classifying the platform gate as recovered.
7. Then rerun only failure sets whose previous root cause was the billing lock. Do not reuse their old failure status as code-regression evidence.

## Observability / learning

Capture:

```yaml
billing_lock_episode:
  observed_at:
  repo:
  run_id:
  job_id:
  head_sha:
  step_count:
  external_message:
  billing_state_evidence:
  remediation_taken:
  canary_result:
  recovered_at:
  repeated_count:
```

If this signature repeats, prefer this policy directly before LLM analysis. It is a deterministic failure class and should not consume repeated generative debugging effort.

## Acceptance criteria

- Same billing-lock signature is identified before any code-level RCA.
- Automatic re-run count while account state is unchanged is zero.
- Mandatory security/release checks remain BLOCKED/NOT_EXECUTED, not PASS.
- After billing recovery, one canary demonstrates at least one runner step executes.
- Only affected workflow runs are retried after canary success.
- Incident output distinguishes `EXTERNAL_ACCOUNT_RECOVERY_REQUIRED` from `CODE_FIX_REQUIRED`.

## Provenance

Initial AEGIS observation: PR #113, P3 Portable Brain Verify run 34024192570, job 101462130184. The workflow run and failed job existed, but the job exposed zero executed steps, matching the user-visible billing-lock message and indicating failure before runner step execution.

External GitHub documentation is advisory evidence for account recovery; repository code does not possess authority to mutate GitHub billing state.
