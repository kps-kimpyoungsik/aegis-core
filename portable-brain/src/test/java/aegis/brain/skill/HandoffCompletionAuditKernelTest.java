package aegis.brain.skill;

import java.time.Duration;
import java.time.Instant;

public final class HandoffCompletionAuditKernelTest {
    private static int passed;
    private static int total;

    private HandoffCompletionAuditKernelTest() {}

    public static void main(String[] args) {
        Instant now = Instant.parse("2026-09-06T10:55:00Z");
        Duration staleAfter = Duration.ofHours(24);

        var complete = HandoffCompletionAuditKernel.audit(status("#1", false, true, true, true, true,
                false, "", false, false, false, 0, 2, now.minusSeconds(60), now), staleAfter);
        check(complete.completed(), "completed requires validation + acceptance");
        check(complete.state() == HandoffCompletionAuditKernel.WorkState.COMPLETED, "completed state");

        var external = HandoffCompletionAuditKernel.audit(status("#114", true, true, false, false, false,
                true, "github-actions|billing-lock", false, false, true, 0, 2, now.minusSeconds(300), now), staleAfter);
        check(external.action() == HandoffCompletionAuditKernel.Action.WAIT_EXTERNAL, "external wait");
        check(external.retrySuppressed(), "external retry suppressed");

        var failedValidation = HandoffCompletionAuditKernel.audit(status("#110", true, true, true, true, false,
                false, "", false, false, true, 0, 2, now.minusSeconds(300), now), staleAfter);
        check(failedValidation.action() == HandoffCompletionAuditKernel.Action.RETRY, "bounded retry after failed validation");
        check(failedValidation.nextRetryCount() == 1, "retry count incremented");

        var exhausted = HandoffCompletionAuditKernel.audit(status("#110", true, true, true, true, false,
                false, "", false, false, true, 2, 2, now.minusSeconds(300), now), staleAfter);
        check(exhausted.action() == HandoffCompletionAuditKernel.Action.ESCALATE, "retry exhaustion escalates");

        var stale = HandoffCompletionAuditKernel.audit(status("#115", true, false, false, false, false,
                false, "", false, false, false, 0, 2, now.minus(Duration.ofDays(2)), now), staleAfter);
        check(stale.action() == HandoffCompletionAuditKernel.Action.REHANDOFF, "stale unacknowledged rehandoff");

        var ownerChanged = HandoffCompletionAuditKernel.audit(status("#109", true, true, false, false, false,
                false, "", false, true, false, 0, 2, now.minusSeconds(300), now), staleAfter);
        check(ownerChanged.action() == HandoffCompletionAuditKernel.Action.REHANDOFF, "owner drift rehandoff");

        var dependency = HandoffCompletionAuditKernel.audit(status("#112", true, true, true, false, false,
                false, "", true, false, true, 0, 2, now.minusSeconds(300), now), staleAfter);
        check(dependency.state() == HandoffCompletionAuditKernel.WorkState.BLOCKED_DEPENDENCY, "dependency blocker");
        check(dependency.retrySuppressed(), "dependency retry suppressed");

        var validating = HandoffCompletionAuditKernel.audit(status("#109", true, true, true, false, false,
                false, "", false, false, false, 0, 2, now.minusSeconds(300), now), staleAfter);
        check(validating.action() == HandoffCompletionAuditKernel.Action.VALIDATE, "work evidence routes to validation");

        var delivered = HandoffCompletionAuditKernel.audit(status("#111", true, false, false, false, false,
                false, "", false, false, false, 0, 2, now.minusSeconds(60), now), staleAfter);
        check(!delivered.completed(), "handoff alone is not completion");
        check(delivered.state() == HandoffCompletionAuditKernel.WorkState.HANDOFF_CREATED, "delivery state preserved");

        System.out.printf("PASS %d/%d%n", passed, total);
    }

    private static HandoffCompletionAuditKernel.HandoffStatus status(String ref, boolean open, boolean ack,
            boolean work, boolean validation, boolean acceptance, boolean external, String externalFingerprint,
            boolean dependency, boolean ownerChanged, boolean highRisk, int retryCount, int maxRetries,
            Instant lastProgressAt, Instant auditedAt) {
        return new HandoffCompletionAuditKernel.HandoffStatus(ref, open, ack, work, validation, acceptance,
                external, externalFingerprint, dependency, ownerChanged, highRisk, retryCount, maxRetries,
                lastProgressAt, auditedAt);
    }

    private static void check(boolean condition, String name) {
        total += 1;
        if (!condition) throw new AssertionError(name);
        passed += 1;
    }
}
