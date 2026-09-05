package aegis.integration;

import aegis.data.lifecycle.DataLifecycleKernel;
import aegis.runtime.kernel.RuntimeExecutionStateKernel;
import aegis.runtime.kernel.RuntimeRecoveryReplayKernel;

import java.time.Instant;
import java.util.Set;

public final class CanonicalRecoveryMigrationIntegrationGateTest {
    private CanonicalRecoveryMigrationIntegrationGateTest() {}

    public static void main(String[] args) {
        validatesCheckpointResumeAndDuplicateEffectSuppression();
        blocksDestructiveMigrationWithoutApproval();
        System.out.println("CANONICAL_RECOVERY_MIGRATION_INTEGRATION_GATE=PASS");
    }

    static void validatesCheckpointResumeAndDuplicateEffectSuppression() {
        Instant t0 = Instant.parse("2026-09-05T00:00:00Z");
        RuntimeExecutionStateKernel.ExecutionContext created =
                RuntimeExecutionStateKernel.create("execution-recovery-gate", t0);
        RuntimeExecutionStateKernel.ExecutionContext ready = RuntimeExecutionStateKernel.transition(
                created, RuntimeExecutionStateKernel.ExecutionState.READY, t0.plusSeconds(1));
        RuntimeExecutionStateKernel.ExecutionContext running = RuntimeExecutionStateKernel.transition(
                ready, RuntimeExecutionStateKernel.ExecutionState.RUNNING, t0.plusSeconds(2));
        RuntimeExecutionStateKernel.ExecutionContext waiting = RuntimeExecutionStateKernel.transition(
                running, RuntimeExecutionStateKernel.ExecutionState.WAITING, t0.plusSeconds(3));

        RuntimeRecoveryReplayKernel.Checkpoint checkpoint = RuntimeRecoveryReplayKernel.checkpoint(
                waiting,
                "checkpoint-recovery-gate",
                7,
                Set.of("effect-completed"),
                t0.plusSeconds(4));

        RuntimeRecoveryReplayKernel.RecoveryResult resume = RuntimeRecoveryReplayKernel.resumeDecision(
                checkpoint,
                waiting.executionId(),
                waiting.epoch(),
                waiting.version());
        require(resume.decision() == RuntimeRecoveryReplayKernel.RecoveryDecision.RESUME,
                "checkpoint must resume only through canonical recovery decision");

        RuntimeRecoveryReplayKernel.ReplayEvent duplicateEffect = new RuntimeRecoveryReplayKernel.ReplayEvent(
                "event-replay",
                waiting.executionId(),
                waiting.epoch(),
                8,
                "effect-completed");
        RuntimeRecoveryReplayKernel.RecoveryResult replay = RuntimeRecoveryReplayKernel.replayDecision(
                checkpoint,
                duplicateEffect,
                Set.of());
        require(replay.decision() == RuntimeRecoveryReplayKernel.RecoveryDecision.SKIP_DUPLICATE,
                "completed side effects must be suppressed during replay");
    }

    static void blocksDestructiveMigrationWithoutApproval() {
        DataLifecycleKernel.MigrationPlan plan = new DataLifecycleKernel.MigrationPlan(
                "runtime-task-dataset",
                "P4_DATA_PLANE",
                "1",
                "2",
                true,
                false,
                "evidence:before",
                "evidence:after",
                "rollback:r0.8",
                "integration-gate:r0.9");

        DataLifecycleKernel.MigrationDecision decision = DataLifecycleKernel.evaluateMigration(
                plan, "P4_DATA_PLANE");
        require(decision == DataLifecycleKernel.MigrationDecision.REQUIRE_APPROVAL,
                "destructive migration must never bypass approval");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
