package aegis.brain.skill;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Deterministic completion audit for delegated/handoff work. */
public final class HandoffCompletionAuditKernel {
    private HandoffCompletionAuditKernel() {}

    public enum WorkState {
        HANDOFF_CREATED,
        ACKNOWLEDGED,
        IN_PROGRESS,
        VALIDATING,
        COMPLETED,
        BLOCKED_EXTERNAL,
        BLOCKED_DEPENDENCY,
        FAILED_VALIDATION,
        STALE,
        RETRY_DUE,
        REHANDOFF_REQUIRED,
        ESCALATION_REQUIRED
    }

    public enum Action {
        NONE,
        WAIT_EXTERNAL,
        RETRY,
        REHANDOFF,
        ESCALATE,
        VALIDATE
    }

    public record HandoffStatus(
            String handoffRef,
            boolean issueOpen,
            boolean acknowledged,
            boolean workEvidencePresent,
            boolean validationEvidencePresent,
            boolean acceptanceCriteriaSatisfied,
            boolean externalBlocker,
            String externalStateFingerprint,
            boolean dependencyBlocker,
            boolean ownerChanged,
            boolean highRisk,
            int retryCount,
            int maxRetries,
            Instant lastProgressAt,
            Instant auditedAt) {
        public HandoffStatus {
            handoffRef = requireText(handoffRef, "handoffRef");
            externalStateFingerprint = externalStateFingerprint == null ? "" : externalStateFingerprint.trim();
            if (retryCount < 0) throw new IllegalArgumentException("retryCount must be >= 0");
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
            Objects.requireNonNull(lastProgressAt, "lastProgressAt");
            Objects.requireNonNull(auditedAt, "auditedAt");
            if (auditedAt.isBefore(lastProgressAt)) throw new IllegalArgumentException("auditedAt before lastProgressAt");
        }
    }

    public record AuditDecision(
            WorkState state,
            Action action,
            boolean completed,
            boolean retrySuppressed,
            int nextRetryCount,
            List<String> requiredEvidence,
            String rationale) {
        public AuditDecision {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(action, "action");
            requiredEvidence = List.copyOf(Objects.requireNonNull(requiredEvidence, "requiredEvidence"));
            rationale = requireText(rationale, "rationale");
        }
    }

    public static AuditDecision audit(HandoffStatus status, Duration staleAfter) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(staleAfter, "staleAfter");
        if (staleAfter.isNegative() || staleAfter.isZero()) throw new IllegalArgumentException("staleAfter must be positive");

        if (status.acceptanceCriteriaSatisfied() && status.validationEvidencePresent()) {
            return decision(WorkState.COMPLETED, Action.NONE, true, false, status.retryCount(), List.of(),
                    "Acceptance criteria and validation evidence are both satisfied.");
        }

        if (status.externalBlocker()) {
            return decision(WorkState.BLOCKED_EXTERNAL, Action.WAIT_EXTERNAL, false, true, status.retryCount(),
                    List.of("external state change evidence", "post-recovery canary evidence"),
                    "External authority/state blocker is unresolved; blind retry is suppressed.");
        }

        if (status.ownerChanged()) {
            return decision(WorkState.REHANDOFF_REQUIRED, Action.REHANDOFF, false, true, status.retryCount(),
                    List.of("new canonical owner", "handoff acknowledgement"),
                    "Canonical owner changed; re-handoff is required before execution continues.");
        }

        if (status.highRisk() && status.retryCount() >= status.maxRetries()) {
            return decision(WorkState.ESCALATION_REQUIRED, Action.ESCALATE, false, true, status.retryCount(),
                    List.of("independent verifier evidence", "authority decision"),
                    "High-risk work exhausted retry budget and requires escalation.");
        }

        boolean stale = Duration.between(status.lastProgressAt(), status.auditedAt()).compareTo(staleAfter) > 0;
        if (stale && !status.acknowledged()) {
            return decision(WorkState.REHANDOFF_REQUIRED, Action.REHANDOFF, false, true, status.retryCount(),
                    List.of("owner acknowledgement", "fresh progress evidence"),
                    "Handoff is stale and unacknowledged; re-handoff instead of repeated execution.");
        }

        if (status.dependencyBlocker()) {
            return decision(WorkState.BLOCKED_DEPENDENCY, Action.NONE, false, true, status.retryCount(),
                    List.of("dependency recovery evidence"),
                    "Dependency blocker prevents meaningful retry.");
        }

        if (status.validationEvidencePresent() && !status.acceptanceCriteriaSatisfied()) {
            if (status.retryCount() < status.maxRetries()) {
                return decision(WorkState.FAILED_VALIDATION, Action.RETRY, false, false, status.retryCount() + 1,
                        List.of("corrective change evidence", "target regression", "held-out regression"),
                        "Validation ran but acceptance failed; one bounded corrective retry is allowed.");
            }
            return decision(WorkState.ESCALATION_REQUIRED, Action.ESCALATE, false, true, status.retryCount(),
                    List.of("failure cluster", "independent review"),
                    "Validation failures exhausted the retry budget.");
        }

        if (status.workEvidencePresent()) {
            return decision(WorkState.VALIDATING, Action.VALIDATE, false, true, status.retryCount(),
                    List.of("validation evidence", "acceptance criteria evidence"),
                    "Implementation evidence exists but completion has not been independently validated.");
        }

        if (status.acknowledged()) {
            return decision(WorkState.IN_PROGRESS, Action.NONE, false, true, status.retryCount(),
                    List.of("implementation evidence"),
                    "Owner acknowledged the handoff but completion evidence is absent.");
        }

        return decision(WorkState.HANDOFF_CREATED, Action.NONE, false, true, status.retryCount(),
                List.of("owner acknowledgement"),
                "Delivery is confirmed, but no acknowledgement or completion evidence exists.");
    }

    private static AuditDecision decision(WorkState state, Action action, boolean completed,
            boolean retrySuppressed, int nextRetryCount, List<String> evidence, String rationale) {
        return new AuditDecision(state, action, completed, retrySuppressed, nextRetryCount, evidence, rationale);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return trimmed;
    }
}
