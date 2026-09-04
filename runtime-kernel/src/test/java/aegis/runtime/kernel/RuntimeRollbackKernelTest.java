package aegis.runtime.kernel;

public final class RuntimeRollbackKernelTest {
    private static int passed;

    private RuntimeRollbackKernelTest() {}

    public static void main(String[] args) {
        var target = new RuntimeRollbackKernel.RollbackPointRef(
                "rp-1", "exec-1", 2, 10, "evidence://rp-1", "sha256:abc");

        var allowed = RuntimeRollbackKernel.coordinate(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, target,
                RuntimeRollbackKernel.AuthorityDecision.ALLOW,
                false, false, false, "regression"));
        check(allowed.decision() == RuntimeRollbackKernel.RollbackDecision.EXECUTE, "allowed rollback executes");
        check(allowed.nextEpoch() == 4, "successful rollback advances epoch");
        check(!allowed.compensationRequired(), "no compensation when no irreversible side effect");

        var approval = RuntimeRollbackKernel.coordinate(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, target,
                RuntimeRollbackKernel.AuthorityDecision.APPROVAL_REQUIRED,
                false, true, true, "high risk"));
        check(approval.decision() == RuntimeRollbackKernel.RollbackDecision.REQUIRE_APPROVAL, "approval gate retained");
        check(approval.compensationRequired(), "compensation requirement retained");
        check(approval.nextEpoch() == 3, "approval does not mutate epoch");

        var denied = RuntimeRollbackKernel.coordinate(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, target,
                RuntimeRollbackKernel.AuthorityDecision.DENY,
                false, false, false, "denied"));
        check(denied.decision() == RuntimeRollbackKernel.RollbackDecision.BLOCK, "authority deny blocks");

        var wrongExecution = new RuntimeRollbackKernel.RollbackPointRef(
                "rp-2", "exec-other", 2, 10, "evidence://rp-2", "sha256:def");
        check(blocked(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, wrongExecution,
                RuntimeRollbackKernel.AuthorityDecision.ALLOW,
                false, false, false, "wrong execution"), "EXECUTION_ID_MISMATCH"),
                "cross execution rollback blocked");

        var futureEpoch = new RuntimeRollbackKernel.RollbackPointRef(
                "rp-3", "exec-1", 4, 10, "evidence://rp-3", "sha256:ghi");
        check(blocked(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, futureEpoch,
                RuntimeRollbackKernel.AuthorityDecision.ALLOW,
                false, false, false, "future epoch"), "FUTURE_EPOCH_TARGET"),
                "future epoch target blocked");

        var forwardState = new RuntimeRollbackKernel.RollbackPointRef(
                "rp-4", "exec-1", 2, 13, "evidence://rp-4", "sha256:jkl");
        check(blocked(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, forwardState,
                RuntimeRollbackKernel.AuthorityDecision.ALLOW,
                false, false, false, "forward state"), "FORWARD_STATE_TARGET"),
                "forward state target blocked");

        check(blocked(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, target,
                RuntimeRollbackKernel.AuthorityDecision.ALLOW,
                true, false, false, "terminal"), "TERMINAL_STATE_IMMUTABLE"),
                "terminal resurrection blocked");

        check(blocked(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, target,
                RuntimeRollbackKernel.AuthorityDecision.ALLOW,
                false, true, false, "irreversible"), "UNCOMPENSATED_IRREVERSIBLE_SIDE_EFFECT"),
                "uncompensated side effect blocked");

        var compensated = RuntimeRollbackKernel.coordinate(new RuntimeRollbackKernel.RollbackRequest(
                "exec-1", 3, 12, target,
                RuntimeRollbackKernel.AuthorityDecision.ALLOW,
                false, true, true, "compensated"));
        check(compensated.decision() == RuntimeRollbackKernel.RollbackDecision.EXECUTE, "compensated rollback executes");
        check(compensated.compensationRequired(), "compensation marked required");

        checkThrows(() -> new RuntimeRollbackKernel.RollbackPointRef(
                "", "exec-1", 1, 1, "e", "c"), "blank rollback point rejected");
        checkThrows(() -> new RuntimeRollbackKernel.RollbackPointRef(
                "rp", "exec-1", -1, 1, "e", "c"), "negative epoch rejected");
        checkThrows(() -> new RuntimeRollbackKernel.RollbackPointRef(
                "rp", "exec-1", 1, -1, "e", "c"), "negative version rejected");

        System.out.println("PASS " + passed + "/17");
    }

    private static boolean blocked(RuntimeRollbackKernel.RollbackRequest request, String reason) {
        var plan = RuntimeRollbackKernel.coordinate(request);
        return plan.decision() == RuntimeRollbackKernel.RollbackDecision.BLOCK
                && plan.reasonCode().equals(reason);
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED: " + name);
        passed++;
    }

    private static void checkThrows(Runnable action, String name) {
        boolean thrown = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check(thrown, name);
    }
}
