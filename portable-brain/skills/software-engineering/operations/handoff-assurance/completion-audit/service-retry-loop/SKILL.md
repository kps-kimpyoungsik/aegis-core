# Handoff Completion Assurance Skill

## Purpose
Audit delegated or handed-off work until independently evidenced completion. Creating an issue, comment, task, or handoff record proves delivery only; it does not prove implementation completion.

## Canonical lifecycle
`HANDOFF_CREATED -> ACKNOWLEDGED -> IN_PROGRESS -> VALIDATING -> COMPLETED`

Alternative states:
`BLOCKED_EXTERNAL`, `BLOCKED_DEPENDENCY`, `FAILED_VALIDATION`, `STALE`, `RETRY_DUE`, `REHANDOFF_REQUIRED`, `ESCALATION_REQUIRED`.

## Completion invariant
`COMPLETED = acceptance criteria satisfied AND validation evidence present AND no unresolved blocker`.

An issue being closed is supporting evidence, not sufficient evidence by itself. An issue being open is not automatically failure; the audit classifies the current actionable state.

## Retry policy
- External/account/quota blocker: suppress retries until external state fingerprint changes; then one canary before fan-out.
- Dependency blocker: suppress execution retries until dependency recovery evidence exists.
- Validation failure: allow bounded corrective retry only while retry budget remains.
- Stale + unacknowledged: re-handoff to canonical owner; do not repeatedly execute the same work.
- Owner drift: resolve new owner and re-handoff.
- High-risk or retry-budget exhausted: escalate to independent verifier/authority.
- Security/release gates never fail-open because a retry path is blocked.

## Audit evidence
Each audit episode records handoff ref, owner, current state, last progress, acceptance evidence, validation evidence, blocker fingerprint, retry count/budget, action, rationale, and provenance.

## Initial governed handoffs
Issues #109, #110, #111, #112, #114 and #115 are the initial representative audit set. Issue #116 tracks this capability; #117 records the first audit snapshot.

## Evolution
Repeated audit outcomes may propose changes to stale thresholds, owner routing, retry budgets, evidence requirements, or escalation policy. Proposals remain candidates until representative + held-out regression evidence proves improvement. Root authority, independent verifier, security policy, and promotion gates are protected surfaces.
