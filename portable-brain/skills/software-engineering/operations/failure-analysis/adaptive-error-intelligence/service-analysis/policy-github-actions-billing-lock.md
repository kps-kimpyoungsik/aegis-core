# GitHub Actions Billing-Lock Policy

Signature: `The job was not started because your account is locked due to a billing issue.`

Corroboration: workflow/job exists, conclusion=failure, executed step count=0, checkout/build/test never ran.

Classification:
`EXTERNAL_PLATFORM_ACCOUNT / GITHUB_ACTIONS_BILLING_LOCK / PRE_RUN_ADMISSION`.

Rules:
- do not classify as repository code/test failure;
- suppress automatic rerun while external account state is unchanged;
- keep required security/release checks BLOCKED/NOT_EXECUTED, never PASS;
- emit one external billing-authority handoff;
- after account recovery, run one cheap canary and require at least one real runner step;
- resume only bounded affected workflows after canary success;
- store the episode and fingerprint so repeated instances bypass redundant LLM debugging.

Fingerprint: `github-actions|billing-lock|account-scope|pre-run|zero-steps`.

Provenance: PR #113, run 34024192570, job 101462130184; issue #114.
