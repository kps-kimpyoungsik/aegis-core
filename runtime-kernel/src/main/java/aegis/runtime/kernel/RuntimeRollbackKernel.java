package aegis.runtime.kernel;

import java.util.Objects;

/**
 * P2-08 Runtime rollback coordination contracts.
 *
 * Runtime decides whether rollback is admissible and emits a plan. Durable
 * checkpoint storage, compensating side effects and protected recovery policy
 * remain behind ports/adapters and higher-authority gates.
 */
public final class RuntimeRollbackKernel {
    private RuntimeRollbackKernel() {}

    public enum AuthorityDecision { ALLOW, APPROVAL_REQUIRED, DENY }
    public enum RollbackDecision { EXECUTE, REQUIRE_APPROVAL, BLOCK }

    public record RollbackPointRef(
            String rollbackPointId,
            String executionId,
            long executionEpoch,
            long stateVersion,
            String evidenceRef,
            String checksum) {
        public RollbackPointRef {
            rollbackPointId = requireNonblank(rollbackPointId, "rollbackPointId");
            executionId = requireNonblank(executionId, "executionId");
            if (executionEpoch < 0) throw new IllegalArgumentException("executionEpoch must be >= 0");
            if (stateVersion < 0) throw new IllegalArgumentException("stateVersion must be >= 0");
            evidenceRef = requireNonblank(evidenceRef, "evidenceRef");
            checksum = requireNonblank(checksum, "checksum");
        }
    }

    public record RollbackRequest(
            String executionId,
            long currentEpoch,
            long currentStateVersion,
            RollbackPointRef target,
            AuthorityDecision authorityDecision,
            boolean terminalState,
            boolean irreversibleSideEffectPresent,
            boolean compensationAvailable,
            String reason) {
        public RollbackRequest {
            executionId = requireNonblank(executionId, "executionId");
            if (currentEpoch < 0) throw new IllegalArgumentException("currentEpoch must be >= 0");
            if (currentStateVersion < 0) throw new IllegalArgumentException("currentStateVersion must be >= 0");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(authorityDecision, "authorityDecision");
            reason = requireNonblank(reason, "reason");
        }
    }

    public record RollbackPlan(
            RollbackDecision decision,
            String reasonCode,
            RollbackPointRef target,
            boolean compensationRequired,
            long nextEpoch) {
        public RollbackPlan {
            Objects.requireNonNull(decision, "decision");
            reasonCode = requireNonblank(reasonCode, "reasonCode");
            Objects.requireNonNull(target, "target");
            if (nextEpoch < 0) throw new IllegalArgumentException("nextEpoch must be >= 0");
        }
    }

    public interface RollbackExecutionPort {
        void execute(RollbackPlan plan);
    }

    public static RollbackPlan coordinate(RollbackRequest request) {
        Objects.requireNonNull(request, "request");
        RollbackPointRef target = request.target();

        if (!request.executionId().equals(target.executionId())) {
            return block(target, "EXECUTION_ID_MISMATCH", request.currentEpoch());
        }
        if (target.executionEpoch() > request.currentEpoch()) {
            return block(target, "FUTURE_EPOCH_TARGET", request.currentEpoch());
        }
        if (target.stateVersion() > request.currentStateVersion()) {
            return block(target, "FORWARD_STATE_TARGET", request.currentEpoch());
        }
        if (request.terminalState()) {
            return block(target, "TERMINAL_STATE_IMMUTABLE", request.currentEpoch());
        }
        if (request.irreversibleSideEffectPresent() && !request.compensationAvailable()) {
            return block(target, "UNCOMPENSATED_IRREVERSIBLE_SIDE_EFFECT", request.currentEpoch());
        }
        if (request.authorityDecision() == AuthorityDecision.DENY) {
            return block(target, "AUTHORITY_DENIED", request.currentEpoch());
        }
        if (request.authorityDecision() == AuthorityDecision.APPROVAL_REQUIRED) {
            return new RollbackPlan(
                    RollbackDecision.REQUIRE_APPROVAL,
                    "APPROVAL_REQUIRED",
                    target,
                    request.irreversibleSideEffectPresent(),
                    request.currentEpoch());
        }

        return new RollbackPlan(
                RollbackDecision.EXECUTE,
                "ROLLBACK_ALLOWED",
                target,
                request.irreversibleSideEffectPresent(),
                request.currentEpoch() + 1);
    }

    private static RollbackPlan block(RollbackPointRef target, String reasonCode, long epoch) {
        return new RollbackPlan(RollbackDecision.BLOCK, reasonCode, target, false, epoch);
    }

    private static String requireNonblank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be nonblank");
        }
        return value;
    }
}
