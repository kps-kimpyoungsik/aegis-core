package aegis.runtime.kernel;

import java.time.Instant;

public final class RuntimeResourceControlKernelTest {
    private static int passed = 0;

    private static void check(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED:" + name);
        passed++;
    }

    public static void main(String[] args) {
        var budget = new RuntimeResourceControlKernel.RuntimeBudget(10, 5, 1_000, 20, 2);
        var now = Instant.parse("2026-09-05T01:00:00Z");
        var deadline = now.plusSeconds(60);

        var allow = new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, now, deadline, false, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(1, 1, 100, 1, 1));
        check(RuntimeResourceControlKernel.evaluate(allow).decision()
                == RuntimeResourceControlKernel.AdmissionDecision.ALLOW, "within limits allowed");

        var cancelled = new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, now, deadline, true, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(1, 1, 100, 1, 1));
        check(RuntimeResourceControlKernel.evaluate(cancelled).decision()
                == RuntimeResourceControlKernel.AdmissionDecision.CANCEL, "cancellation wins");

        var expired = new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, deadline, deadline, false, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(1, 1, 100, 1, 1));
        check(RuntimeResourceControlKernel.evaluate(expired).decision()
                == RuntimeResourceControlKernel.AdmissionDecision.CANCEL, "deadline exceeded");

        check(RuntimeResourceControlKernel.evaluate(new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, now, deadline, false, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(10, 1, 100, 1, 1))).reason()
                .equals("COMMAND_BUDGET_EXHAUSTED"), "command budget blocked");

        check(RuntimeResourceControlKernel.evaluate(new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, now, deadline, false, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(1, 5, 100, 1, 1))).reason()
                .equals("TOOL_BUDGET_EXHAUSTED"), "tool budget blocked");

        check(RuntimeResourceControlKernel.evaluate(new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, now, deadline, false, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(1, 1, 1_000, 1, 1))).reason()
                .equals("CPU_BUDGET_EXHAUSTED"), "cpu budget blocked");

        check(RuntimeResourceControlKernel.evaluate(new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, now, deadline, false, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(1, 1, 100, 1, 2))).reason()
                .equals("CONCURRENCY_LIMIT"), "concurrency backpressure defer");

        check(RuntimeResourceControlKernel.evaluate(new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, now, deadline, false, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(1, 1, 100, 20, 1))).reason()
                .equals("BACKPRESSURE_QUEUE_LIMIT"), "queue backpressure defer");

        check(RuntimeResourceControlKernel.preferForFairness(2, 5), "older sequence wins fairness");
        check(!RuntimeResourceControlKernel.preferForFairness(6, 5), "newer sequence does not preempt");
        check(!RuntimeResourceControlKernel.preferForFairness(5, 5), "equal sequence does not preempt");

        check(RuntimeResourceControlKernel.remainingCommands(budget,
                new RuntimeResourceControlKernel.RuntimeUsage(3, 1, 100, 1, 1)) == 7,
                "remaining command budget");
        check(RuntimeResourceControlKernel.remainingToolInvocations(budget,
                new RuntimeResourceControlKernel.RuntimeUsage(3, 2, 100, 1, 1)) == 3,
                "remaining tool budget");
        check(RuntimeResourceControlKernel.remainingCommands(budget,
                new RuntimeResourceControlKernel.RuntimeUsage(15, 1, 100, 1, 1)) == 0,
                "remaining commands floors at zero");

        boolean invalidBudgetBlocked = false;
        try {
            new RuntimeResourceControlKernel.RuntimeBudget(0, 1, 1, 1, 1);
        } catch (IllegalArgumentException expected) {
            invalidBudgetBlocked = true;
        }
        check(invalidBudgetBlocked, "invalid budget blocked");

        boolean negativeUsageBlocked = false;
        try {
            new RuntimeResourceControlKernel.RuntimeUsage(-1, 0, 0, 0, 0);
        } catch (IllegalArgumentException expected) {
            negativeUsageBlocked = true;
        }
        check(negativeUsageBlocked, "negative usage blocked");

        boolean invalidFairnessBlocked = false;
        try {
            RuntimeResourceControlKernel.preferForFairness(-1, 0);
        } catch (IllegalArgumentException expected) {
            invalidFairnessBlocked = true;
        }
        check(invalidFairnessBlocked, "negative fairness sequence blocked");

        var cancellationPrecedence = new RuntimeResourceControlKernel.ControlContext(
                "exec-1", 4, deadline.plusSeconds(1), deadline, true, budget,
                new RuntimeResourceControlKernel.RuntimeUsage(10, 5, 1_000, 20, 2));
        check(RuntimeResourceControlKernel.evaluate(cancellationPrecedence).reason()
                .equals("CANCELLATION_REQUESTED"), "cancellation precedence deterministic");

        System.out.println("PASS " + passed + "/18");
    }
}
