# AEGIS Physical / External Failure Exception Guide

## Status
ACTIVE_GOVERNANCE

## Purpose
Define fail-closed handling for failures whose immediate cause is outside repository code and cannot be cleared by ordinary source changes alone. This guide prevents physical/environment/provider problems from being misclassified as code regressions, retried indefinitely, duplicated across sessions, or used to justify skipping mandatory security/release verification.

## Canonical distinction
`PHYSICAL_OR_EXTERNAL_FAILURE != CODE_FAILURE != GOVERNANCE_AUTHORITY_BLOCKER != DATA_CONTRACT_FAILURE`.

A failure belongs to this exception domain only when current evidence shows the immediate blocking state is external to the candidate source and source mutation alone cannot clear it.

Included classes:
- `EXTERNAL_PLATFORM_ACCOUNT`: billing/account lock, account suspension, provider-side entitlement state.
- `EXTERNAL_CAPACITY`: quota exhaustion, provider capacity exhaustion, region/provider saturation.
- `RUNNER_OR_HOST_UNAVAILABLE`: runner admission unavailable, host unavailable, machine/VM provisioning failure before workload execution.
- `NETWORK_INFRASTRUCTURE`: DNS, routing, TLS transport, provider network outage, connectivity partition where repository code has not executed.
- `PHYSICAL_RESOURCE_EXHAUSTION`: disk, memory, CPU, inode, file-descriptor, ephemeral-storage, hardware/device/resource exhaustion at the execution environment boundary.
- `PROVIDER_OUTAGE`: externally confirmed service or regional outage.

Explicit exclusions:
- deterministic build/test/lint/type/security failure after workload execution;
- application timeout caused by application logic or resource leak;
- migration/schema/tenant-isolation defect;
- authority/ownership ambiguity such as protected Audit Ledger ownership;
- stale baseline, merge conflict, duplicate workstream, or contract mismatch;
- unknown failures without sufficient evidence.

`UNKNOWN` MUST NOT be promoted to a physical/external classification merely because a job failed early.

## Required evidence dimensions
Before applying this exception, preserve where available:
- source/provider and failure fingerprint;
- affected account/region/resource class in sanitized form;
- execution phase;
- whether any repository workload step actually executed;
- runner/host identity state when available;
- authoritative provider evidence versus downstream notification evidence;
- firstObservedAt / lastObservedAt / occurrenceCount;
- externalStateFingerprint;
- confidence (`CONFIRMED`, `OBSERVED`, `INFERRED`, `UNKNOWN`);
- canonical owner and active executor/claim;
- retry policy and next recheck condition;
- impacted mandatory gates.

## Execution evidence rule
A CI/workflow conclusion of `failure` does not prove code execution.

If runner/job evidence shows zero/null executed steps, classify execution as `NOT_EXECUTED` unless stronger evidence proves otherwise. Such evidence cannot qualify repository code as PASS or FAIL.

Conversely, if repository workload steps executed and produced a deterministic failure, do not hide that failure behind an external exception merely because an external problem also exists.

Multiple causes may coexist. Preserve primary blocker, secondary effects, and causal order.

## Exception memory
Every confirmed or repeatedly observed physical/external fingerprint SHOULD be recorded in the sanitized append-oriented `docs/governance/PHYSICAL_EXTERNAL_FAILURE_EXCEPTION_MEMORY.md` or an approved private/runtime Failure Memory.

The memory record MUST contain a stable failure fingerprint, classification, state, first/last observation, evidence refs, retry suppression rule, recovery probe, owner, and supersession/clearance condition. Do not store secrets, private account identifiers, raw email IDs, payment details, tokens, private provider payloads, or opaque session credentials in public Git.

A remembered exception is evidence, not eternal truth. Every future use MUST revalidate freshness/applicability before suppressing work.

## Retry policy
Default policies:
- account/billing lock unchanged -> `SUPPRESS_UNTIL_EXTERNAL_STATE_CHANGES`;
- hard quota/capacity exhausted -> `SUPPRESS_UNTIL_CAPACITY_WINDOW_OR_STATE_CHANGES`;
- provider outage -> `SUPPRESS_UNTIL_PROVIDER_RECOVERY_EVIDENCE`;
- network/host transient with evidence of transience -> bounded retry with backoff/jitter and max-attempt budget;
- physical resource exhaustion -> do not blind retry; require capacity/config/resource correction or a bounded clean-environment canary;
- unknown cause -> no automatic physical-exception retry policy; investigate first.

Repeated identical retries while the externalStateFingerprint is unchanged are prohibited.

## Recovery gate
When the external state changes:
1. refresh exact candidate/main and external-state evidence;
2. run the cheapest representative canary appropriate to the failure class;
3. require evidence that the previously blocked execution boundary was crossed (for CI, at least one real runner/workload step);
4. if the canary passes the blocked boundary, resume only the affected work in bounded fan-out;
5. rerun required target and held-out/security/release verification;
6. preserve old blocked occurrences as `NOT_EXECUTED/BLOCKED_EXTERNAL`, not retroactive PASS/FAIL;
7. mark the exception `CLEARED` only after the clearance condition and required validation evidence are satisfied.

## Fan-out and notification control
`ONE_EXTERNAL_ROOT_CAUSE -> MANY_NOTIFICATIONS` must remain one causal family with occurrence history, not many independent code bugs.

Downstream email, workflow, deploy, and review notifications must correlate by failure fingerprint/head/workstream/topology. Duplicate notifications increment occurrence evidence; they do not automatically create new issues, branches, claims, or retries.

## Security and promotion
Physical/external exceptions never authorize fail-open behavior.

Mandatory security, tenant-isolation, supply-chain, signature, recovery, and release gates remain `BLOCKED/NOT_EXECUTED` until independently executed and verified. An unavailable advisory service may be marked `ADVISORY_UNAVAILABLE` only when repository policy already defines it as non-mandatory.

## Multi-cause ordering
When physical/external and code failures coexist:
- preserve chronological causal order;
- do not overwrite an earlier executed code failure with a later provider outage;
- do not infer a code failure from a provider failure that prevented execution;
- if evidence cannot establish ordering, classify `MULTI_CAUSE_UNRESOLVED` and fail closed.

## Canonical lifecycle
`OBSERVED -> CLASSIFIED -> EXCEPTION_ACTIVE -> RETRY_SUPPRESSED/BOUNDED_RETRY -> EXTERNAL_STATE_CHANGED -> CANARY -> VALIDATING -> CLEARED`

Alternative terminal/holding states include `BLOCKED_EXTERNAL`, `ESCALATION_REQUIRED`, `SUPERSEDED`, and `UNKNOWN`.

## Current known families
Current sanitized canonical references include:
- `github-actions|external-platform-account|pre-run-admission|billing-lock|zero-runner-steps` -> Issue #114.
- `external-provider|capacity|request-admission|quota-limit|account-scope` -> Issue #111, currently including Vercel deployment quota and Codex review quota evidence.
- CI fan-out amplification of the above is structural Issue #109 and MUST NOT be treated as a separate physical root cause.

## Session requirement
Every continuation/wakeup that encounters an error MUST search this guide and exception memory before creating a new fix/workstream. If a matching active external fingerprint exists and its state fingerprint is unchanged, reuse the existing owner/handoff, suppress duplicate implementation/retry, and continue unrelated safe work only when mandatory dependencies permit it.
