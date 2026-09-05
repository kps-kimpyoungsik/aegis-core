package aegis.runtime.kernel;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RuntimeRecoveryReplayKernel {
    private RuntimeRecoveryReplayKernel() {}

    public enum RecoveryDecision { RESUME, REPLAY, SKIP_DUPLICATE, BLOCK }

    public record Checkpoint(
            String checkpointId,
            String executionId,
            long epoch,
            long stateVersion,
            RuntimeExecutionStateKernel.ExecutionState state,
            long eventSequence,
            Set<String> completedEffectIds,
            Instant createdAt) {
        public Checkpoint {
            checkpointId = requireText(checkpointId, "checkpointId");
            executionId = requireText(executionId, "executionId");
            if (epoch < 0 || stateVersion < 0 || eventSequence < 0) {
                throw new IllegalArgumentException("numeric checkpoint fields must be >= 0");
            }
            Objects.requireNonNull(state, "state");
            completedEffectIds = completedEffectIds == null ? Set.of() : Set.copyOf(completedEffectIds);
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record ReplayEvent(
            String eventId,
            String executionId,
            long epoch,
            long sequence,
            String effectId) {
        public ReplayEvent {
            eventId = requireText(eventId, "eventId");
            executionId = requireText(executionId, "executionId");
            if (epoch < 0 || sequence < 0) throw new IllegalArgumentException("epoch/sequence must be >= 0");
            effectId = effectId == null ? "" : effectId;
        }
    }

    public record RecoveryResult(RecoveryDecision decision, String reason, long nextEpoch) {
        public RecoveryResult {
            Objects.requireNonNull(decision, "decision");
            reason = reason == null ? "" : reason;
            if (nextEpoch < 0) throw new IllegalArgumentException("nextEpoch must be >= 0");
        }
    }

    public interface CheckpointStorePort {
        void save(Checkpoint checkpoint);
        Checkpoint load(String checkpointId);
    }

    public static Checkpoint checkpoint(RuntimeExecutionStateKernel.ExecutionContext context,
                                        String checkpointId,
                                        long eventSequence,
                                        Set<String> completedEffectIds,
                                        Instant createdAt) {
        Objects.requireNonNull(context, "context");
        if (context.state() != RuntimeExecutionStateKernel.ExecutionState.WAITING) {
            throw new IllegalStateException("checkpoint requires WAITING execution state");
        }
        return new Checkpoint(checkpointId, context.executionId(), context.epoch(), context.version(),
                context.state(), eventSequence, completedEffectIds, createdAt);
    }

    public static RecoveryResult resumeDecision(Checkpoint checkpoint,
                                                String executionId,
                                                long expectedEpoch,
                                                long expectedStateVersion) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (!checkpoint.executionId().equals(executionId)) {
            return new RecoveryResult(RecoveryDecision.BLOCK, "EXECUTION_ID_MISMATCH", checkpoint.epoch());
        }
        if (checkpoint.state() != RuntimeExecutionStateKernel.ExecutionState.WAITING) {
            return new RecoveryResult(RecoveryDecision.BLOCK, "CHECKPOINT_NOT_WAITING", checkpoint.epoch());
        }
        if (checkpoint.epoch() != expectedEpoch) {
            return new RecoveryResult(RecoveryDecision.BLOCK, "EPOCH_MISMATCH", checkpoint.epoch());
        }
        if (checkpoint.stateVersion() != expectedStateVersion) {
            return new RecoveryResult(RecoveryDecision.BLOCK, "STATE_VERSION_MISMATCH", checkpoint.epoch());
        }
        return new RecoveryResult(RecoveryDecision.RESUME, "CHECKPOINT_ACCEPTED", checkpoint.epoch() + 1);
    }

    public static RecoveryResult replayDecision(Checkpoint checkpoint,
                                                ReplayEvent event,
                                                Set<String> appliedEventIds) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(event, "event");
        appliedEventIds = appliedEventIds == null ? Set.of() : Set.copyOf(appliedEventIds);

        if (!checkpoint.executionId().equals(event.executionId())) {
            return new RecoveryResult(RecoveryDecision.BLOCK, "EXECUTION_ID_MISMATCH", checkpoint.epoch());
        }
        if (event.epoch() != checkpoint.epoch()) {
            return new RecoveryResult(RecoveryDecision.BLOCK, "STALE_OR_FUTURE_EPOCH", checkpoint.epoch());
        }
        if (event.sequence() <= checkpoint.eventSequence()) {
            return new RecoveryResult(RecoveryDecision.SKIP_DUPLICATE, "SEQUENCE_ALREADY_CHECKPOINTED", checkpoint.epoch());
        }
        if (appliedEventIds.contains(event.eventId())) {
            return new RecoveryResult(RecoveryDecision.SKIP_DUPLICATE, "EVENT_ALREADY_APPLIED", checkpoint.epoch());
        }
        if (!event.effectId().isBlank() && checkpoint.completedEffectIds().contains(event.effectId())) {
            return new RecoveryResult(RecoveryDecision.SKIP_DUPLICATE, "EFFECT_ALREADY_COMPLETED", checkpoint.epoch());
        }
        return new RecoveryResult(RecoveryDecision.REPLAY, "EVENT_ACCEPTED", checkpoint.epoch());
    }

    public static List<ReplayEvent> orderedReplay(List<ReplayEvent> events) {
        Objects.requireNonNull(events, "events");
        return events.stream().sorted((a, b) -> Long.compare(a.sequence(), b.sequence())).toList();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
