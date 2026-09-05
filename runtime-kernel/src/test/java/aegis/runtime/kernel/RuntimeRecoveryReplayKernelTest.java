package aegis.runtime.kernel;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class RuntimeRecoveryReplayKernelTest {
    private static int passed = 0;

    private static void check(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED:" + name);
        passed++;
    }

    public static void main(String[] args) {
        Instant t0 = Instant.parse("2026-09-05T00:00:00Z");
        var context = new RuntimeExecutionStateKernel.ExecutionContext(
                "exec-1", 3, 7, RuntimeExecutionStateKernel.ExecutionState.WAITING, t0, t0);

        var checkpoint = RuntimeRecoveryReplayKernel.checkpoint(
                context, "cp-1", 10, Set.of("effect-1"), t0);
        check(checkpoint.executionId().equals("exec-1"), "checkpoint execution identity");
        check(checkpoint.eventSequence() == 10, "checkpoint sequence");

        var resume = RuntimeRecoveryReplayKernel.resumeDecision(checkpoint, "exec-1", 3, 7);
        check(resume.decision() == RuntimeRecoveryReplayKernel.RecoveryDecision.RESUME, "resume accepted");
        check(resume.nextEpoch() == 4, "resume advances epoch");

        check(RuntimeRecoveryReplayKernel.resumeDecision(checkpoint, "other", 3, 7).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.BLOCK, "cross execution resume blocked");
        check(RuntimeRecoveryReplayKernel.resumeDecision(checkpoint, "exec-1", 2, 7).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.BLOCK, "stale epoch resume blocked");
        check(RuntimeRecoveryReplayKernel.resumeDecision(checkpoint, "exec-1", 3, 6).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.BLOCK, "state version mismatch blocked");

        var replay = new RuntimeRecoveryReplayKernel.ReplayEvent("evt-11", "exec-1", 3, 11, "effect-2");
        check(RuntimeRecoveryReplayKernel.replayDecision(checkpoint, replay, Set.of()).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.REPLAY, "new event replayed");

        var oldSequence = new RuntimeRecoveryReplayKernel.ReplayEvent("evt-9", "exec-1", 3, 9, "");
        check(RuntimeRecoveryReplayKernel.replayDecision(checkpoint, oldSequence, Set.of()).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.SKIP_DUPLICATE, "checkpointed sequence skipped");

        check(RuntimeRecoveryReplayKernel.replayDecision(checkpoint, replay, Set.of("evt-11")).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.SKIP_DUPLICATE, "applied event skipped");

        var duplicateEffect = new RuntimeRecoveryReplayKernel.ReplayEvent("evt-12", "exec-1", 3, 12, "effect-1");
        check(RuntimeRecoveryReplayKernel.replayDecision(checkpoint, duplicateEffect, Set.of()).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.SKIP_DUPLICATE, "completed effect skipped");

        var staleEpoch = new RuntimeRecoveryReplayKernel.ReplayEvent("evt-13", "exec-1", 2, 13, "");
        check(RuntimeRecoveryReplayKernel.replayDecision(checkpoint, staleEpoch, Set.of()).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.BLOCK, "stale replay epoch blocked");

        var futureEpoch = new RuntimeRecoveryReplayKernel.ReplayEvent("evt-14", "exec-1", 4, 14, "");
        check(RuntimeRecoveryReplayKernel.replayDecision(checkpoint, futureEpoch, Set.of()).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.BLOCK, "future replay epoch blocked");

        var crossExecution = new RuntimeRecoveryReplayKernel.ReplayEvent("evt-15", "other", 3, 15, "");
        check(RuntimeRecoveryReplayKernel.replayDecision(checkpoint, crossExecution, Set.of()).decision()
                == RuntimeRecoveryReplayKernel.RecoveryDecision.BLOCK, "cross execution replay blocked");

        var ordered = RuntimeRecoveryReplayKernel.orderedReplay(List.of(
                new RuntimeRecoveryReplayKernel.ReplayEvent("e3", "exec-1", 3, 13, ""),
                new RuntimeRecoveryReplayKernel.ReplayEvent("e1", "exec-1", 3, 11, ""),
                new RuntimeRecoveryReplayKernel.ReplayEvent("e2", "exec-1", 3, 12, "")));
        check(ordered.get(0).sequence() == 11 && ordered.get(2).sequence() == 13, "replay order deterministic");

        boolean nonWaitingBlocked = false;
        try {
            RuntimeRecoveryReplayKernel.checkpoint(
                    new RuntimeExecutionStateKernel.ExecutionContext(
                            "exec-1", 3, 7, RuntimeExecutionStateKernel.ExecutionState.RUNNING, t0, t0),
                    "cp-bad", 10, Set.of(), t0);
        } catch (IllegalStateException expected) {
            nonWaitingBlocked = true;
        }
        check(nonWaitingBlocked, "checkpoint requires waiting");

        boolean negativeSequenceBlocked = false;
        try {
            new RuntimeRecoveryReplayKernel.ReplayEvent("bad", "exec-1", 3, -1, "");
        } catch (IllegalArgumentException expected) {
            negativeSequenceBlocked = true;
        }
        check(negativeSequenceBlocked, "negative sequence blocked");

        check(checkpoint.completedEffectIds().contains("effect-1"), "effect provenance retained");
        check(checkpoint.stateVersion() == 7, "state version retained");

        System.out.println("PASS " + passed + "/19");
    }
}
