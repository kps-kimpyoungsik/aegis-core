package aegis.runtime.kernel;

import java.util.List;
import java.util.Map;

import aegis.runtime.kernel.RuntimeValidationKernel.CompareDecision;
import aegis.runtime.kernel.RuntimeValidationKernel.CompareRequest;
import aegis.runtime.kernel.RuntimeValidationKernel.DeterministicRuntimeComparator;
import aegis.runtime.kernel.RuntimeValidationKernel.IdempotencyDecision;
import aegis.runtime.kernel.RuntimeValidationKernel.IdempotencyEnvelope;
import aegis.runtime.kernel.RuntimeValidationKernel.IdempotencyKey;
import aegis.runtime.kernel.RuntimeValidationKernel.IdempotencyRecord;
import aegis.runtime.kernel.RuntimeValidationKernel.IdempotencyScope;
import aegis.runtime.kernel.RuntimeValidationKernel.IdempotencyState;
import aegis.runtime.kernel.RuntimeValidationKernel.QualityVector;
import aegis.runtime.kernel.RuntimeValidationKernel.ValidationEvidence;
import aegis.runtime.kernel.RuntimeValidationKernel.ValidationStatus;

public final class RuntimeValidationKernelTest {
    private static int passed;

    private RuntimeValidationKernelTest() {}

    public static void main(String[] args) {
        DeterministicRuntimeComparator comparator = new DeterministicRuntimeComparator();
        QualityVector baseline = new QualityVector(Map.of("correctness", 0.90, "reliability", 0.90));
        QualityVector better = new QualityVector(Map.of("correctness", 0.95, "reliability", 0.90));
        QualityVector worse = new QualityVector(Map.of("correctness", 0.85, "reliability", 0.90));
        List<ValidationEvidence> passEvidence = List.of(
                new ValidationEvidence("validator-1", ValidationStatus.PASS, List.of("evidence-1"), "ok"));

        check(comparator.validateAndCompare(new CompareRequest("exec-1", baseline, better, passEvidence)).decision()
                == CompareDecision.ACCEPT, "accept_measurable_improvement");
        check(comparator.validateAndCompare(new CompareRequest("exec-2", baseline, worse, passEvidence)).decision()
                == CompareDecision.ROLLBACK, "rollback_regression");
        check(comparator.validateAndCompare(new CompareRequest("exec-3", baseline, baseline, passEvidence)).decision()
                == CompareDecision.DEFER, "defer_no_improvement");
        check(comparator.validateAndCompare(new CompareRequest("exec-4", baseline, better, List.of())).decision()
                == CompareDecision.DEFER, "defer_missing_evidence");
        check(comparator.validateAndCompare(new CompareRequest(
                "exec-5",
                baseline,
                better,
                List.of(new ValidationEvidence("validator-2", ValidationStatus.FAIL, List.of("evidence-2"), "failed"))))
                .decision() == CompareDecision.ROLLBACK, "rollback_failed_validation");
        check(comparator.validateAndCompare(new CompareRequest(
                "exec-6",
                baseline,
                better,
                List.of(new ValidationEvidence(
                        "validator-3",
                        ValidationStatus.INSUFFICIENT_EVIDENCE,
                        List.of(),
                        "insufficient"))))
                .decision() == CompareDecision.DEFER, "defer_insufficient_evidence");

        IdempotencyKey key1 = IdempotencyKey.derive(
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "aegis.work-runtime",
                "send",
                "request-1");
        IdempotencyKey key2 = IdempotencyKey.derive(
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "aegis.work-runtime",
                "send",
                "request-1");
        check(key1.equals(key2), "deterministic_idempotency_key");

        IdempotencyEnvelope envelope = new IdempotencyEnvelope();
        check(envelope.decide(null, "aegis.work-runtime", "send").decision()
                == IdempotencyDecision.EXECUTE, "new_request_executes");
        check(envelope.decide(new IdempotencyRecord(
                key1,
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "aegis.work-runtime",
                "send",
                IdempotencyState.COMPLETED,
                "result-1"), "aegis.work-runtime", "send").decision()
                == IdempotencyDecision.RETURN_RECORDED_RESULT, "completed_duplicate_reuses_result");
        check(envelope.decide(new IdempotencyRecord(
                key1,
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "aegis.work-runtime",
                "send",
                IdempotencyState.RESERVED,
                ""), "aegis.work-runtime", "send").decision()
                == IdempotencyDecision.BLOCK, "reserved_duplicate_blocks");
        check(envelope.decide(new IdempotencyRecord(
                key1,
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "aegis.work-runtime",
                "send",
                IdempotencyState.FAILED_RETRYABLE,
                ""), "aegis.work-runtime", "send").decision()
                == IdempotencyDecision.RETRY_ALLOWED, "retryable_failure_allows_retry");
        check(envelope.decide(new IdempotencyRecord(
                key1,
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "aegis.work-runtime",
                "send",
                IdempotencyState.FAILED_FINAL,
                ""), "aegis.work-runtime", "send").decision()
                == IdempotencyDecision.BLOCK, "final_failure_blocks");
        check("OWNER_CONFLICT".equals(envelope.decide(new IdempotencyRecord(
                key1,
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "other.owner",
                "send",
                IdempotencyState.RESERVED,
                ""), "aegis.work-runtime", "send").reason()), "owner_conflict_blocks");
        check("OPERATION_CONFLICT".equals(envelope.decide(new IdempotencyRecord(
                key1,
                IdempotencyScope.TOOL_SIDE_EFFECT,
                "aegis.work-runtime",
                "write",
                IdempotencyState.RESERVED,
                ""), "aegis.work-runtime", "send").reason()), "operation_conflict_blocks");

        System.out.println("PASS " + passed + "/14");
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new IllegalStateException("FAILED: " + name);
        }
        passed++;
    }
}
