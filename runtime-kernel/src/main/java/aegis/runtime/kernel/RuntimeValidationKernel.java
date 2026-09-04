package aegis.runtime.kernel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RuntimeValidationKernel {
    private RuntimeValidationKernel() {}

    public enum ValidationStatus { PASS, FAIL, INSUFFICIENT_EVIDENCE }
    public enum CompareDecision { ACCEPT, ROLLBACK, DEFER }
    public enum IdempotencyScope { TASK, EXECUTION, TOOL_SIDE_EFFECT, EXTERNAL_COMMAND }
    public enum IdempotencyState { RESERVED, COMPLETED, FAILED_RETRYABLE, FAILED_FINAL }
    public enum IdempotencyDecision { EXECUTE, RETURN_RECORDED_RESULT, RETRY_ALLOWED, BLOCK }

    public record QualityVector(Map<String, Double> metrics) {
        public QualityVector {
            Objects.requireNonNull(metrics, "metrics");
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("metric name must be nonblank");
                }
                Double value = entry.getValue();
                if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("metric value must be finite");
                }
            }
            metrics = Map.copyOf(metrics);
        }
    }

    public record ValidationEvidence(
            String validatorId,
            ValidationStatus status,
            List<String> evidenceRefs,
            String reason) {
        public ValidationEvidence {
            if (validatorId == null || validatorId.isBlank()) {
                throw new IllegalArgumentException("validatorId must be nonblank");
            }
            Objects.requireNonNull(status, "status");
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
            reason = reason == null ? "" : reason;
        }
    }

    public record CompareRequest(
            String executionId,
            QualityVector baseline,
            QualityVector candidate,
            List<ValidationEvidence> evidence) {
        public CompareRequest {
            if (executionId == null || executionId.isBlank()) {
                throw new IllegalArgumentException("executionId must be nonblank");
            }
            Objects.requireNonNull(baseline, "baseline");
            Objects.requireNonNull(candidate, "candidate");
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record CompareResult(CompareDecision decision, List<String> reasons) {
        public CompareResult {
            Objects.requireNonNull(decision, "decision");
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public interface RuntimeValidationPort {
        CompareResult validateAndCompare(CompareRequest request);
    }

    public static final class DeterministicRuntimeComparator implements RuntimeValidationPort {
        @Override
        public CompareResult validateAndCompare(CompareRequest request) {
            Objects.requireNonNull(request, "request");
            if (request.evidence().isEmpty()) {
                return new CompareResult(CompareDecision.DEFER, List.of("NO_VALIDATION_EVIDENCE"));
            }
            if (request.evidence().stream().anyMatch(e -> e.status() == ValidationStatus.INSUFFICIENT_EVIDENCE)) {
                return new CompareResult(CompareDecision.DEFER, List.of("INSUFFICIENT_EVIDENCE"));
            }
            if (request.evidence().stream().anyMatch(e -> e.status() == ValidationStatus.FAIL)) {
                return new CompareResult(CompareDecision.ROLLBACK, List.of("VALIDATION_FAILED"));
            }

            Map<String, Double> baseline = request.baseline().metrics();
            Map<String, Double> candidate = request.candidate().metrics();
            List<String> regressions = new ArrayList<>();
            boolean improvement = false;

            for (Map.Entry<String, Double> entry : baseline.entrySet()) {
                Double candidateValue = candidate.get(entry.getKey());
                if (candidateValue == null) {
                    regressions.add("MISSING_METRIC:" + entry.getKey());
                    continue;
                }
                int comparison = Double.compare(candidateValue, entry.getValue());
                if (comparison < 0) {
                    regressions.add("REGRESSION:" + entry.getKey());
                } else if (comparison > 0) {
                    improvement = true;
                }
            }

            if (!regressions.isEmpty()) {
                return new CompareResult(CompareDecision.ROLLBACK, regressions);
            }
            if (improvement) {
                return new CompareResult(CompareDecision.ACCEPT, List.of("MEASURABLE_IMPROVEMENT"));
            }
            return new CompareResult(CompareDecision.DEFER, List.of("NO_MEASURABLE_IMPROVEMENT"));
        }
    }

    public record IdempotencyKey(String value) {
        public IdempotencyKey {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("value must be nonblank");
            }
        }

        public static IdempotencyKey derive(
                IdempotencyScope scope,
                String canonicalOwner,
                String operation,
                String requestIdentity) {
            Objects.requireNonNull(scope, "scope");
            requireNonblank(canonicalOwner, "canonicalOwner");
            requireNonblank(operation, "operation");
            requireNonblank(requestIdentity, "requestIdentity");
            String canonical = scope.name() + "|" + canonicalOwner + "|" + operation + "|" + requestIdentity;
            try {
                byte[] bytes = MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8));
                return new IdempotencyKey(HexFormat.of().formatHex(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
        }
    }

    public record IdempotencyRecord(
            IdempotencyKey key,
            IdempotencyScope scope,
            String canonicalOwner,
            String operation,
            IdempotencyState state,
            String resultRef) {
        public IdempotencyRecord {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(scope, "scope");
            requireNonblank(canonicalOwner, "canonicalOwner");
            requireNonblank(operation, "operation");
            Objects.requireNonNull(state, "state");
            resultRef = resultRef == null ? "" : resultRef;
        }
    }

    public record IdempotencyOutcome(
            IdempotencyDecision decision,
            String reason,
            String resultRef) {
        public IdempotencyOutcome {
            Objects.requireNonNull(decision, "decision");
            reason = reason == null ? "" : reason;
            resultRef = resultRef == null ? "" : resultRef;
        }
    }

    public static final class IdempotencyEnvelope {
        public IdempotencyOutcome decide(
                IdempotencyRecord existing,
                String expectedCanonicalOwner,
                String expectedOperation) {
            requireNonblank(expectedCanonicalOwner, "expectedCanonicalOwner");
            requireNonblank(expectedOperation, "expectedOperation");

            if (existing == null) {
                return new IdempotencyOutcome(IdempotencyDecision.EXECUTE, "NEW_REQUEST", "");
            }
            if (!existing.canonicalOwner().equals(expectedCanonicalOwner)) {
                return new IdempotencyOutcome(IdempotencyDecision.BLOCK, "OWNER_CONFLICT", "");
            }
            if (!existing.operation().equals(expectedOperation)) {
                return new IdempotencyOutcome(IdempotencyDecision.BLOCK, "OPERATION_CONFLICT", "");
            }

            return switch (existing.state()) {
                case COMPLETED -> new IdempotencyOutcome(
                        IdempotencyDecision.RETURN_RECORDED_RESULT,
                        "COMPLETED_DUPLICATE",
                        existing.resultRef());
                case FAILED_RETRYABLE -> new IdempotencyOutcome(
                        IdempotencyDecision.RETRY_ALLOWED,
                        "FAILED_RETRYABLE",
                        existing.resultRef());
                case RESERVED -> new IdempotencyOutcome(
                        IdempotencyDecision.BLOCK,
                        "IN_FLIGHT_DUPLICATE",
                        existing.resultRef());
                case FAILED_FINAL -> new IdempotencyOutcome(
                        IdempotencyDecision.BLOCK,
                        "FAILED_FINAL",
                        existing.resultRef());
            };
        }
    }

    private static void requireNonblank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be nonblank");
        }
    }
}
