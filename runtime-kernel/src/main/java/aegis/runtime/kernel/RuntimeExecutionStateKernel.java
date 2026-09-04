package aegis.runtime.kernel;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class RuntimeExecutionStateKernel {
    private RuntimeExecutionStateKernel() {}

    public enum ExecutionState {
        CREATED, READY, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED;

        public boolean terminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED;
        }
    }

    public record ExecutionContext(
            String executionId,
            long epoch,
            long version,
            ExecutionState state,
            Instant createdAt,
            Instant updatedAt) {
        public ExecutionContext {
            executionId = requireText(executionId, "executionId");
            if (epoch < 0) throw new IllegalArgumentException("epoch must be >= 0");
            if (version < 0) throw new IllegalArgumentException("version must be >= 0");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt before createdAt");
        }
    }

    public static ExecutionContext create(String executionId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new ExecutionContext(executionId, 0, 0, ExecutionState.CREATED, now, now);
    }

    public static ExecutionContext transition(ExecutionContext current, ExecutionState target, Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        if (current.state().terminal()) throw new IllegalStateException("terminal state is immutable");
        if (!allowedTargets(current.state()).contains(target)) {
            throw new IllegalStateException("illegal transition: " + current.state() + " -> " + target);
        }
        if (now.isBefore(current.updatedAt())) throw new IllegalArgumentException("time regression");
        return new ExecutionContext(
                current.executionId(), current.epoch(), current.version() + 1, target,
                current.createdAt(), now);
    }

    public static ExecutionContext resumeFromCheckpoint(
            ExecutionContext checkpoint, long expectedEpoch, Instant now) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(now, "now");
        if (checkpoint.state() != ExecutionState.WAITING) {
            throw new IllegalStateException("resume requires WAITING checkpoint");
        }
        if (checkpoint.epoch() != expectedEpoch) {
            throw new IllegalStateException("checkpoint epoch mismatch");
        }
        if (now.isBefore(checkpoint.updatedAt())) throw new IllegalArgumentException("time regression");
        return new ExecutionContext(
                checkpoint.executionId(), checkpoint.epoch() + 1, checkpoint.version() + 1,
                ExecutionState.RUNNING, checkpoint.createdAt(), now);
    }

    public static boolean acceptsResult(ExecutionContext current, String executionId, long resultEpoch) {
        Objects.requireNonNull(current, "current");
        return current.executionId().equals(executionId)
                && !current.state().terminal()
                && current.epoch() == resultEpoch;
    }

    private static Set<ExecutionState> allowedTargets(ExecutionState state) {
        return switch (state) {
            case CREATED -> EnumSet.of(ExecutionState.READY, ExecutionState.FAILED, ExecutionState.CANCELLED);
            case READY -> EnumSet.of(ExecutionState.RUNNING, ExecutionState.FAILED, ExecutionState.CANCELLED);
            case RUNNING -> EnumSet.of(ExecutionState.WAITING, ExecutionState.COMPLETED,
                    ExecutionState.FAILED, ExecutionState.CANCELLED);
            case WAITING -> EnumSet.of(ExecutionState.RUNNING, ExecutionState.FAILED, ExecutionState.CANCELLED);
            case COMPLETED, FAILED, CANCELLED -> EnumSet.noneOf(ExecutionState.class);
        };
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
