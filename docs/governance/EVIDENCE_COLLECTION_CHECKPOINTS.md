# AEGIS Evidence Collection Checkpoints

## Status
ACTIVE_GOVERNANCE_CHECKPOINTS

This file stores sanitized, public-safe collection scope checkpoints only. It MUST NOT contain private Gmail message/thread IDs, personal addresses, raw bodies/snippets, opaque continuation tokens, credentials, private attachment identifiers, or other account-private material.

Raw source evidence remains authoritative in the protected source system. This file exists so later sessions can determine what range was actually inspected, whether the scan was complete, what failure families were observed, and where safe resumption must begin.

---

## MAIL-ERROR-SCAN-20260906-2228-KST

- `sourceKind`: `GMAIL_NOTIFICATION_EVIDENCE`
- `collectionPurpose`: AEGIS operational error/notification triage and correlation
- `collectionObservedAt`: `2026-09-06T22:28:29+09:00`
- `baselineMainShaAtCheckpointDesign`: `c1622d46eec989ca4c306b7a34671819efef6756`
- `privacyClass`: `PRIVATE_SOURCE / PUBLIC_SANITIZED_CHECKPOINT`
- `rawEvidenceLocation`: protected Gmail source; raw identifiers intentionally not copied to public Git
- `querySignature`: `UNRECORDED_EXACT_QUERY_CURRENT_SESSION`; this is a known provenance gap and prevents claiming reproducible full-scope completeness
- `collectionAttempts`: 2 visible paginated search result sets
- `returnedRows`: 40
- `knownCrossQueryDuplicateRows`: 3
- `minimumKnownUniqueSourceOccurrences`: 37
- `observedEventTimeMin`: `2026-09-05T22:38:41-07:00` (`2026-09-06T14:38:41+09:00`)
- `observedEventTimeMax`: `2026-09-06T06:18:00-07:00` (`2026-09-06T22:18:00+09:00`)
- `continuationPresent`: true for both visible result sets
- `completeness`: `PARTIAL_CONTINUATION_AVAILABLE`
- `sourceExhausted`: false
- `safeHighWatermarkAdvanced`: false
- `nextResumePolicy`: resume from protected source with overlapping time/query scope; exhaust continuations or record each remaining gap; dedupe idempotently; do not use this checkpoint as proof that older/newer notifications do not exist
- `crossCheckAuthority`: GitHub Workflow Run/Job/Steps used for CI execution status; provider notification text used for Vercel/Codex capacity classification

### Correlated failure families observed

1. `github-actions|external-platform-account|pre-run-admission|billing-or-account-lock|zero-runner-steps`
   - Multiple workflow-failure notifications were observed on the same candidate heads.
   - Authoritative GitHub job cross-check showed jobs with `steps=null`, so these observations are `NOT_EXECUTED/PRE_RUN_ADMISSION`, not proven code-regression failures.
   - Existing canonical handoff family: GitHub Actions external account/admission blocker and CI fan-out governance.

2. `external-provider|vercel|deployment-admission|daily-free-deployment-capacity|account-scope`
   - Repeated deployment notifications reported daily free deployment capacity exhaustion.
   - Treat repeated mail as occurrences of one external-capacity family unless provider evidence proves otherwise.

3. `external-provider|codex|review-admission|usage-limit|account-scope`
   - Repeated review notifications reported Codex code-review usage limit exhaustion.
   - Treat repeated mail as occurrences of one external-capacity family unless provider evidence proves otherwise.

### Important non-claims

- This checkpoint does NOT prove all relevant Gmail messages were collected.
- This checkpoint does NOT prove all unread/important/inbox messages were covered.
- This checkpoint does NOT prove the source has no earlier/later related notification.
- This checkpoint does NOT advance a durable Gmail high-watermark.
- This checkpoint does NOT convert 37+ source occurrences into 37 root causes.
- This checkpoint does NOT authorize retries, issue creation, deployment, or promotion.

### Resume requirements

A later collection MUST:

1. load `docs/governance/EVIDENCE_COLLECTION_SCOPE_GUIDE.md`;
2. declare the exact query/scope before collection where possible;
3. overlap this observed event-time range rather than starting strictly after the maximum timestamp;
4. continue all source pages within the declared scope or explicitly record remaining continuation/gaps;
5. preserve occurrence counts while deduping by source identity and correlating by failure fingerprint;
6. cross-check notification-derived execution claims against authoritative provider/run/job evidence;
7. create a new immutable checkpoint entry rather than rewriting this historical observation as if it had been complete.
