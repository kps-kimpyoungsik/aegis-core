package aegis.runtime.kernel;

public final class RuntimeDispatchKernelTest {
    private static int passed;
    private RuntimeDispatchKernelTest() {}

    public static void main(String[] args) {
        var state = RuntimeDispatchKernel.DispatchState.empty();
        var command = new RuntimeDispatchKernel.Command("exec-1", "cmd-1", 2, "search");
        var decision = RuntimeDispatchKernel.dispatch(command, state, "inv-1");
        check(decision.invocation().commandId().equals("cmd-1"), "dispatch creates correlated invocation");
        check(decision.next().activeInvocationId().equals("inv-1"), "single active invocation recorded");

        checkThrowsState(() -> RuntimeDispatchKernel.dispatch(command, decision.next(), "inv-2"), "duplicate command blocked");
        var command2 = new RuntimeDispatchKernel.Command("exec-1", "cmd-2", 2, "search");
        checkThrowsState(() -> RuntimeDispatchKernel.dispatch(command2, decision.next(), "inv-2"), "parallel active invocation blocked");

        var good = new RuntimeDispatchKernel.ToolResult("exec-1", "cmd-1", "inv-1", 2, true);
        var cleared = RuntimeDispatchKernel.correlateResult(decision.next(), decision.invocation(), good);
        check(cleared.activeInvocationId().isBlank(), "result clears active invocation");

        checkThrowsState(() -> RuntimeDispatchKernel.correlateResult(decision.next(), decision.invocation(),
                new RuntimeDispatchKernel.ToolResult("exec-X", "cmd-1", "inv-1", 2, true)), "execution mismatch blocked");
        checkThrowsState(() -> RuntimeDispatchKernel.correlateResult(decision.next(), decision.invocation(),
                new RuntimeDispatchKernel.ToolResult("exec-1", "cmd-X", "inv-1", 2, true)), "command mismatch blocked");
        checkThrowsState(() -> RuntimeDispatchKernel.correlateResult(decision.next(), decision.invocation(),
                new RuntimeDispatchKernel.ToolResult("exec-1", "cmd-1", "inv-X", 2, true)), "invocation mismatch blocked");
        checkThrowsState(() -> RuntimeDispatchKernel.correlateResult(decision.next(), decision.invocation(),
                new RuntimeDispatchKernel.ToolResult("exec-1", "cmd-1", "inv-1", 1, true)), "stale result epoch blocked");

        var e1 = RuntimeDispatchKernel.acceptEvent(cleared, "evt-1");
        var e1dup = RuntimeDispatchKernel.acceptEvent(e1, "evt-1");
        check(e1 == e1dup, "duplicate event idempotently ignored");
        var e2 = RuntimeDispatchKernel.acceptEvent(e1, "evt-2");
        check(e2.lastEventId().equals("evt-2"), "new event accepted");

        check(RuntimeDispatchKernel.schedulerWins("node-a", "node-a"), "lease owner wins scheduling");
        check(!RuntimeDispatchKernel.schedulerWins("node-a", "node-b"), "non-owner loses scheduling");
        checkThrowsArg(() -> new RuntimeDispatchKernel.Command("", "c", 0, "t"), "blank execution id blocked");
        checkThrowsArg(() -> new RuntimeDispatchKernel.Command("e", "", 0, "t"), "blank command id blocked");
        checkThrowsArg(() -> new RuntimeDispatchKernel.Command("e", "c", -1, "t"), "negative epoch blocked");
        checkThrowsArg(() -> RuntimeDispatchKernel.acceptEvent(e2, " "), "blank event id blocked");

        System.out.println("PASS " + passed + "/17");
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
