package aegis.integration;

import aegis.data.lifecycle.DataLifecycleKernel;
import aegis.runtime.kernel.RuntimeExecutionStateKernel;
import aegis.runtime.kernel.RuntimeRecoveryReplayKernel;

import java.time.Instant;
import java.util.Set;

public final class SessionBaselineIntegrationGateTest {
    private SessionBaselineIntegrationGateTest() {}

    public static void main(String[] args) {
        validatesCheckpointResumeAndDuplicateEffectSuppression();
        blocksDestructiveMigrationWithoutApproval();
        System.out.println("SESSION_BASELINE_INTEGRATION_GATE_PASS");
    }

    static void validatesCheckpointResumeAndDuplicateEffectSuppression() {
        Instant t0 = Instant.parse("2026-09-05T00:00:00Z");
        RuntimeExecutionStateKernel.ExecutionContext created =
                RuntimeExecutionStateKernel.create("execution-rc6", t0);
        RuntimeExecutionStateKernel.ExecutionContext ready = RuntimeExecutionStateKernel.transition(
                created, RuntimeExecutionStateKernel.ExecutionState.READY, t0.plusSeconds(1));
        RuntimeExecutionStateKernel.ExecutionContext running = RuntimeExecutionStateKernel.transition(
                ready, RuntimeExecutionStateKernel.ExecutionState.RUNNING, t0.plusSeconds(2));
        RuntimeExecutionStateKernel.ExecutionContext waiting = RuntimeExecutionStateKernel.transition(
                running, RuntimeExecutionStateKernel.ExecutionState.WAITING, t0.plusSeconds(3));

        RuntimeRecoveryReplayKernel.Checkpoint checkpoint = RuntimeRecoveryReplayKernel.checkpoint(
                waiting,
                "checkpoint-rc6",
                7,
                Set.of("effect-completed"),
                t0.plusSeconds(4));

        RuntimeRecoveryReplayKernel.RecoveryResult resume = RuntimeRecoveryReplayKernel.resumeDecision(
                checkpoint,
                waiting.executionId(),
                waiting.epoch(),
                waiting.version());
        require(resume.decision() == RuntimeRecoveryReplayKernel.RecoveryDecision.RESUME,
                "RC6 restart semantics must map to canonical RESUME decision");

        RuntimeRecoveryReplayKernel.ReplayEvent duplicateEffect = new RuntimeRecoveryReplayKernel.ReplayEvent(
                "event-new",
                waiting.executionId(),
                waiting.epoch(),
                8,
                "effect-completed");
        RuntimeRecoveryReplayKernel.RecoveryResult replay = RuntimeRecoveryReplayKernel.replayDecision(
                checkpoint,
                duplicateEffect,
                Set.of());
        require(replay.decision() == RuntimeRecoveryReplayKernel.RecoveryDecision.SKIP_DUPLICATE,
                "RC6 idempotency semantics must suppress completed side effects");
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
                "rollback:rc6",
                "session-import:2026-09-05");

        DataLifecycleKernel.MigrationDecision decision = DataLifecycleKernel.evaluateMigration(
                plan, "P4_DATA_PLANE");
        require(decision == DataLifecycleKernel.MigrationDecision.REQUIRE_APPROVAL,
                "destructive migration must not bypass approval");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
