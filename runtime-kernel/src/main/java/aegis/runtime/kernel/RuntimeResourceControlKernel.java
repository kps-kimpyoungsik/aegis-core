package aegis.runtime.kernel;

import java.time.Instant;
import java.util.Objects;

public final class RuntimeResourceControlKernel {
    private RuntimeResourceControlKernel() {}

    public enum AdmissionDecision { ALLOW, DEFER, CANCEL, BLOCK }

    public record RuntimeBudget(
            long maxCommands,
            long maxToolInvocations,
            long maxCpuMillis,
            long maxQueueDepth,
            long maxConcurrentInvocations) {
        public RuntimeBudget {
            if (maxCommands <= 0 || maxToolInvocations <= 0 || maxCpuMillis <= 0
                    || maxQueueDepth <= 0 || maxConcurrentInvocations <= 0) {
                throw new IllegalArgumentException("budget limits must be > 0");
            }
        }
    }

    public record RuntimeUsage(
            long commands,
            long toolInvocations,
            long cpuMillis,
            long queueDepth,
            long concurrentInvocations) {
        public RuntimeUsage {
            if (commands < 0 || toolInvocations < 0 || cpuMillis < 0
                    || queueDepth < 0 || concurrentInvocations < 0) {
                throw new IllegalArgumentException("usage must be >= 0");
            }
        }
    }

    public record ControlContext(
            String executionId,
            long epoch,
            Instant now,
            Instant deadline,
            boolean cancellationRequested,
            RuntimeBudget budget,
            RuntimeUsage usage) {
        public ControlContext {
            if (executionId == null || executionId.isBlank()) {
                throw new IllegalArgumentException("executionId must not be blank");
            }
            if (epoch < 0) throw new IllegalArgumentException("epoch must be >= 0");
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(deadline, "deadline");
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(usage, "usage");
        }
    }

    public record AdmissionResult(AdmissionDecision decision, String reason) {
        public AdmissionResult {
            Objects.requireNonNull(decision, "decision");
            reason = reason == null ? "" : reason;
        }
    }

    public static AdmissionResult evaluate(ControlContext context) {
        Objects.requireNonNull(context, "context");

        if (context.cancellationRequested()) {
            return new AdmissionResult(AdmissionDecision.CANCEL, "CANCELLATION_REQUESTED");
        }
        if (!context.now().isBefore(context.deadline())) {
            return new AdmissionResult(AdmissionDecision.CANCEL, "DEADLINE_EXCEEDED");
        }

        RuntimeBudget budget = context.budget();
        RuntimeUsage usage = context.usage();

        if (usage.commands() >= budget.maxCommands()) {
            return new AdmissionResult(AdmissionDecision.BLOCK, "COMMAND_BUDGET_EXHAUSTED");
        }
        if (usage.toolInvocations() >= budget.maxToolInvocations()) {
            return new AdmissionResult(AdmissionDecision.BLOCK, "TOOL_BUDGET_EXHAUSTED");
        }
        if (usage.cpuMillis() >= budget.maxCpuMillis()) {
            return new AdmissionResult(AdmissionDecision.BLOCK, "CPU_BUDGET_EXHAUSTED");
        }
        if (usage.concurrentInvocations() >= budget.maxConcurrentInvocations()) {
            return new AdmissionResult(AdmissionDecision.DEFER, "CONCURRENCY_LIMIT");
        }
        if (usage.queueDepth() >= budget.maxQueueDepth()) {
            return new AdmissionResult(AdmissionDecision.DEFER, "BACKPRESSURE_QUEUE_LIMIT");
        }
        return new AdmissionResult(AdmissionDecision.ALLOW, "WITHIN_LIMITS");
    }

    public static boolean preferForFairness(long contenderSequence, long currentWinnerSequence) {
        if (contenderSequence < 0 || currentWinnerSequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        return contenderSequence < currentWinnerSequence;
    }

    public static long remainingCommands(RuntimeBudget budget, RuntimeUsage usage) {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(usage, "usage");
        return Math.max(0, budget.maxCommands() - usage.commands());
    }

    public static long remainingToolInvocations(RuntimeBudget budget, RuntimeUsage usage) {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(usage, "usage");
        return Math.max(0, budget.maxToolInvocations() - usage.toolInvocations());
    }
}
