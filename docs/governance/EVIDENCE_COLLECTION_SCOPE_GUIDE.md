# AEGIS Evidence Collection Scope & Watermark Guide

## Status
ACTIVE_GOVERNANCE

## Purpose
This guide defines how AEGIS collects, remembers, resumes, deduplicates, correlates, and audits evidence streams such as Gmail notifications, GitHub Issues/PRs/Actions, Vercel/Codex notifications, logs, webhooks, queues, and other external sources without treating one page, one timestamp, one cursor, or one notification list as complete truth.

The objective is not merely to remember what was seen. The objective is to preserve the exact collection boundary so later sessions can distinguish `SEEN`, `NOT_SEEN`, `NOT_YET_SCANNED`, `SOURCE_UNAVAILABLE`, `PARTIAL_PAGE`, `LATE_ARRIVAL`, and `SUPERSEDED` evidence.

## Core invariants

- `COLLECTED != COMPLETE`.
- `FIRST_PAGE != FULL_SOURCE`.
- `MESSAGE_TIMESTAMP != COLLECTION_TIMESTAMP`.
- `SEARCH_NOT_FOUND != NO_EVENT`.
- `CURSOR_ADVANCED != SOURCE_EXHAUSTED`.
- `DUPLICATE_NOTIFICATION != DUPLICATE_ROOT_CAUSE` and `DISTINCT_NOTIFICATION != DISTINCT_ROOT_CAUSE`.
- Collection scope is evidence and MUST be versioned/auditable.
- Never advance a durable high-watermark past unprocessed or unverified gaps.
- Late-arriving and backfilled evidence must remain discoverable after a watermark advances.
- Raw private-source identifiers/content MUST NOT be copied into a public repository unless explicitly authorized and privacy-reviewed.

## Canonical CollectionScope

Every substantive collection attempt SHOULD be representable by a canonical object containing at least:

`collectionId, sourceKind, sourceAccountScope, querySignature, startedAt, completedAt, observedEventTimeMin, observedEventTimeMax, pageCount, itemCount, completeness, continuationPresent, lowerBoundPolicy, upperBoundPolicy, overlapWindow, dedupePolicy, correlationPolicy, sourceConsistency, clockBasis, privacyClass, rawEvidenceLocation, sanitizedCheckpointRef, provenanceRef`

### Time semantics

Track separately:

- `collectionStartedAt`: when AEGIS began querying.
- `collectionCompletedAt`: when that query attempt ended.
- `sourceEventTime`: timestamp supplied by the source/event.
- `sourceModifiedTime`: when available, source mutation/update timestamp.
- `ingestedAt`: when AEGIS first accepted the item into evidence processing.
- `lastObservedAt`: last time the same logical item/fingerprint was observed.

Do not collapse these into one `timestamp` field.

## Completeness states

A collection attempt MUST end in one explicit state:

- `COMPLETE_FOR_DECLARED_SCOPE`: all pages/partitions within the declared bounded query were exhausted and no known gap remains.
- `PARTIAL_CONTINUATION_AVAILABLE`: source returned more pages/cursor/continuation.
- `PARTIAL_LIMIT_REACHED`: configured budget stopped collection before exhaustion.
- `PARTIAL_SOURCE_ERROR`: source/tool failed after some evidence was collected.
- `PARTIAL_AUTHORITY`: current permission cannot see all required source scope.
- `PARTIAL_TIME_UNBOUNDED`: query has no trustworthy lower/upper event-time bound.
- `UNKNOWN_COMPLETENESS`: source semantics do not permit proof of exhaustion.
- `NOT_EXECUTED`: collection did not actually run.

Only `COMPLETE_FOR_DECLARED_SCOPE` may support claims of completeness, and only for the exact declared scope/query/version.

## Watermark model

Do not use a single naive `lastSeenTimestamp` as the sole checkpoint.

Use a compound watermark where supported:

`eventTimeHighWatermark + stableSourceIdTieBreaker + overlapWindow + querySignature + sourceVersion/accountScope`

Reasons:

- multiple events may share the same timestamp;
- source ordering may be unstable;
- events may arrive late or be backfilled;
- message/thread updates can occur without a new creation time;
- pagination order can change between requests;
- clocks can drift or use different timezones/precision;
- a query/filter change invalidates a prior watermark.

A resume query SHOULD intentionally overlap the previous range. Reprocess the overlap idempotently and dedupe by stable source identity or canonical evidence fingerprint.

## Gap safety

A durable checkpoint MUST NOT advance over a known gap.

Examples of gaps:

- continuation/page token still present;
- API rate limit/timeout between pages;
- permission error for one partition/folder/label;
- source mutation while paging;
- truncated result caused by max-results/budget;
- attachment/body retrieval failure when those fields are required for classification;
- unknown timezone or timestamp normalization failure;
- query changed before the previous scope was closed.

A later successful page does not erase an earlier missing page. Preserve a gap record until explicitly reconciled.

## Multi-dimensional evidence treatment

Every collected item should be evaluated across multiple dimensions rather than linearly converting `one notification -> one error`:

1. **Identity**: stable source item/thread/run/PR identity where available.
2. **Causality**: root-cause/failure fingerprint independent of notification text.
3. **Temporal**: event time, modification time, ingestion time, ordering confidence.
4. **Topology**: upstream/downstream fan-out, dependency, owner/workstream relation.
5. **Execution**: `EXECUTED/PASS`, `EXECUTED/FAIL`, `NOT_EXECUTED`, `BLOCKED`.
6. **Authority**: which source is authoritative for which claim.
7. **Completeness**: complete/partial/unknown collection boundary.
8. **Freshness**: current evidence versus stale/superseded evidence.
9. **Privacy/Security**: whether raw identifiers/content may be persisted or shared.
10. **Actionability**: retry, wait, handoff, re-handoff, escalation, no-op.
11. **Confidence**: confirmed/observed/inferred/unknown.
12. **Retention**: what must persist, for how long, and in which protected store.

## Mail-specific rules

For Gmail/email notification collection:

- Record the Gmail query semantics or a non-sensitive `querySignature`, labels/scope intent, collection time, observed message-time range, returned count, page count, and whether continuation exists.
- Do not treat `UNREAD` as a durable processed checkpoint: read/unread can change independently of evidence ingestion.
- Do not treat thread identity as message identity; one thread may contain multiple causally distinct updates, and multiple threads may represent one failure family.
- Do not infer source completeness from inbox position/order.
- Resent/forwarded/duplicate notifications must be deduped without losing `firstSeenAt/lastSeenAt/count` occurrence evidence.
- Notification subject/snippet is triage evidence, not necessarily root-cause evidence. Cross-check authoritative GitHub/Vercel/Codex/job state when decisions depend on execution status.
- Attachment/body retrieval may be optional for classification; if required but not read, mark evidence `PARTIAL_CONTENT` rather than assuming absence.

## Source-of-truth and cross-check matrix

Examples:

- Gmail notification -> notification delivery evidence.
- GitHub Workflow Run/Job/Steps -> actual CI execution/admission evidence.
- GitHub Issue/PR -> ownership/workstream/status evidence.
- Vercel platform response -> deployment admission/quota evidence.
- Codex service notification -> review-capacity evidence.

A downstream email saying a workflow failed does not prove a code step executed. Prefer the authoritative execution source before classifying regression.

## Privacy and persistence boundary

Public Git repositories MUST contain only sanitized collection checkpoints and public/provenance-safe fingerprints.

Do NOT persist to a public repository:

- private Gmail message IDs/thread IDs;
- personal email addresses not already intentionally public;
- raw email bodies/snippets when not required and approved;
- opaque continuation/page tokens;
- authentication/session material;
- private attachment identifiers/content;
- secrets or account-private quota metadata beyond the minimum safe classification.

Raw evidence remains in its protected source or approved private storage. Public Git may retain aggregate counts, bounded time ranges, failure-family fingerprints, completeness state, and public work references.

## Dedupe and correlation

Maintain both:

- `sourceOccurrenceKey`: identifies one source occurrence for idempotent ingestion;
- `failureFingerprint`: identifies a causal family for correlation.

Do not discard occurrence history when deduping. Preserve at least `firstSeenAt`, `lastSeenAt`, `occurrenceCount`, source kinds, affected heads/workstreams, and evidence refs where privacy permits.

## Query evolution

Any material change to source, labels/folders, sender filters, time bounds, account scope, max-results, sort/order assumptions, or classification purpose creates a new `querySignature` and invalidates direct continuity with the old watermark unless an explicit migration/reconciliation is performed.

## Clock and ordering anomalies

Assume external clocks/order may be imperfect. Normalize timezone, retain original timestamp when safe/available, and use stable identity/tie-breaker plus overlap instead of strict `eventTime > lastSeenTime` logic.

If source time moves backwards or precision is coarser than event density, widen overlap and record `CLOCK_ORDER_UNCERTAIN`.

## Partial-failure recovery

Recovery sequence:

`LOAD LAST SAFE CHECKPOINT -> REOPEN GAP/OVERLAP WINDOW -> RECOLLECT IDEMPOTENTLY -> VERIFY CONTINUATION EXHAUSTION -> CROSS-CHECK COUNTS/FINGERPRINTS -> ADVANCE CHECKPOINT -> AUDIT`

Never skip directly from an old checkpoint to the newest visible event after a partial failure.

## Collection-to-action separation

Collection and action are separate state machines.

`COLLECT -> NORMALIZE -> DEDUPE -> CORRELATE -> VERIFY AUTHORITY -> CLASSIFY -> OWNER/CLAIM RESOLVE -> ACTION`

A collection event MUST NOT by itself trigger destructive mutation, retry, issue creation, deployment, or promotion without the normal authority/risk/action gate.

## Required session evidence

When external evidence was collected, the session SHOULD report:

`Source | Query/Scope | Collection Time | Observed Event-Time Range | Items | Pages | Completeness | Continuation/Gap | Failure Families | Authoritative Cross-check | Next Safe Resume Boundary`

If the exact source contains private identifiers, report only the sanitized form.

## Current implementation direction

Prefer adapting existing AEGIS Memory/Event/Provenance/Failure/Handoff primitives rather than creating a parallel persistence system. Collection checkpoints are provenance/ingestion state; correlated error families continue to use canonical Failure/Handoff ownership.

## Canonical loop

`DEFINE SCOPE -> COLLECT -> RECORD BOUNDS/PAGES/CONTINUATION -> NORMALIZE -> DEDUPE -> CORRELATE -> CROSS-CHECK AUTHORITY -> CLOSE OR MARK PARTIAL -> SAVE SAFE CHECKPOINT -> ACT THROUGH NORMAL GATES -> NEXT RUN REOPEN OVERLAP/GAPS`
