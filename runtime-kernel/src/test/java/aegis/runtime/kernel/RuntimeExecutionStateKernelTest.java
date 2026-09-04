package aegis.runtime.kernel;

import java.time.Instant;

public final class RuntimeExecutionStateKernelTest {
    private static int passed;

    private RuntimeExecutionStateKernelTest() {}

    public static void main(String[] args) {
        Instant t0 = Instant.parse("2026-09-05T00:00:00Z");
        var created = RuntimeExecutionStateKernel.create("exec-1", t0);
        check(created.state() == RuntimeExecutionStateKernel.ExecutionState.CREATED, "created state");
        check(created.epoch() == 0 && created.version() == 0, "initial epoch/version");

        var ready = RuntimeExecutionStateKernel.transition(created,
                RuntimeExecutionStateKernel.ExecutionState.READY, t0.plusSeconds(1));
        var running = RuntimeExecutionStateKernel.transition(ready,
                RuntimeExecutionStateKernel.ExecutionState.RUNNING, t0.plusSeconds(2));
        var waiting = RuntimeExecutionStateKernel.transition(running,
                RuntimeExecutionStateKernel.ExecutionState.WAITING, t0.plusSeconds(3));
        check(waiting.version() == 3, "version advances per transition");

        var resumed = RuntimeExecutionStateKernel.resumeFromCheckpoint(waiting, 0, t0.plusSeconds(4));
        check(resumed.state() == RuntimeExecutionStateKernel.ExecutionState.RUNNING, "resume to running");
        check(resumed.epoch() == 1, "resume advances epoch");
        check(RuntimeExecutionStateKernel.acceptsResult(resumed, "exec-1", 1), "current epoch result accepted");
        check(!RuntimeExecutionStateKernel.acceptsResult(resumed, "exec-1", 0), "stale epoch result rejected");
        check(!RuntimeExecutionStateKernel.acceptsResult(resumed, "exec-other", 1), "cross execution result rejected");

        var completed = RuntimeExecutionStateKernel.transition(resumed,
                RuntimeExecutionStateKernel.ExecutionState.COMPLETED, t0.plusSeconds(5));
        check(completed.state().terminal(), "completed terminal");
        check(!RuntimeExecutionStateKernel.acceptsResult(completed, "exec-1", 1), "terminal result rejected");

        checkThrowsState(() -> RuntimeExecutionStateKernel.transition(created,
                RuntimeExecutionStateKernel.ExecutionState.COMPLETED, t0.plusSeconds(1)), "illegal transition blocked");
        checkThrowsState(() -> RuntimeExecutionStateKernel.transition(completed,
                RuntimeExecutionStateKernel.ExecutionState.RUNNING, t0.plusSeconds(6)), "terminal resurrection blocked");
        checkThrowsState(() -> RuntimeExecutionStateKernel.resumeFromCheckpoint(running, 0, t0.plusSeconds(4)),
                "resume requires waiting checkpoint");
        checkThrowsState(() -> RuntimeExecutionStateKernel.resumeFromCheckpoint(waiting, 7, t0.plusSeconds(4)),
                "resume epoch mismatch blocked");
        checkThrowsArg(() -> RuntimeExecutionStateKernel.transition(waiting,
                RuntimeExecutionStateKernel.ExecutionState.RUNNING, t0.minusSeconds(1)), "time regression blocked");
        checkThrowsArg(() -> RuntimeExecutionStateKernel.create(" ", t0), "blank execution id blocked");

        System.out.println("PASS " + passed + "/16");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED: " + name);
        passed++;
    }

    private static void checkThrowsState(Runnable action, String name) {
        boolean thrown = false;
        try { action.run(); } catch (IllegalStateException expected) { thrown = true; }
        check(thrown, name);
    }

    private static void checkThrowsArg(Runnable action, String name) {
        boolean thrown = false;
        try { action.run(); } catch (IllegalArgumentException expected) { thrown = true; }
        check(thrown, name);
    }
}
