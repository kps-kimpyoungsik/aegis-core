package aegis.brain.skill;

import java.util.ArrayList;
import java.util.List;

public final class ErrorIntakePipelineTest {
    private ErrorIntakePipelineTest() {}

    public static void main(String[] args) {
        int passed = 0;
        int total = 12;

        List<ErrorIntakePipeline.FailureFamily> remembered = new ArrayList<>();
        ErrorIntakePipeline.FailureMemorySink sink = remembered::add;

        var billingA = notification(
                ErrorIntakePipeline.SourceKind.GITHUB_ACTIONS,
                "run-1",
                "The job was not started because your account is locked due to a billing issue.",
                "abc123",
                "P3 Portable Brain Verify",
                0,
                true,
                true,
                false);
        var billingB = notification(
                ErrorIntakePipeline.SourceKind.GITHUB_ACTIONS,
                "run-2",
                "The job was not started because your account is locked due to a billing issue.",
                "def456",
                "precommit-regression-gate",
                0,
                true,
                true,
                false);

        var vercel = notification(
                ErrorIntakePipeline.SourceKind.VERCEL,
                "mail-vercel",
                "Resource is limited - try again in 24 hours (more than 100, code: api-deployments-free-per-day)",
                "8fb7593",
                "preview-deploy",
                0,
                false,
                true,
                false);

        var codex = notification(
                ErrorIntakePipeline.SourceKind.CODEX,
                "mail-codex",
                "You have reached your Codex usage limits for code reviews.",
                "8fb7593",
                "code-review",
                0,
                false,
                true,
                false);

        List<ErrorIntakePipeline.FailureFamily> families = ErrorIntakePipeline.correlate(
                List.of(billingA, billingB, vercel, codex), sink);

        passed += check(families.size() == 3, "known external failures collapse into three families");
        passed += check(remembered.size() == 3, "one memory write per correlated family");

        ErrorIntakePipeline.FailureFamily billing = find(families, "github-actions|billing-lock|pre-run");
        passed += check(billing.notificationCount() == 2, "billing duplicates correlate across revisions and workflows");
        passed += check(billing.owner() == ErrorIntakePipeline.OwnerDomain.CI_EXTERNAL_AUTHORITY, "billing routes to external authority owner");
        passed += check("#114".equals(billing.canonicalIssueRef()), "billing reuses issue 114");
        passed += check(billing.suppressDuplicateWork(), "billing suppresses duplicate work");

        ErrorIntakePipeline.FailureFamily vercelFamily = find(families, "vercel|deployment-daily-capacity|account-scope");
        passed += check(vercelFamily.owner() == ErrorIntakePipeline.OwnerDomain.EXTERNAL_CAPACITY, "vercel routes to external capacity");
        passed += check("#111".equals(vercelFamily.canonicalIssueRef()), "vercel reuses issue 111");

        ErrorIntakePipeline.FailureFamily codexFamily = find(families, "codex|review-usage-limit|account-scope");
        passed += check(codexFamily.owner() == ErrorIntakePipeline.OwnerDomain.EXTERNAL_CAPACITY, "codex routes to external capacity");
        passed += check("#111".equals(codexFamily.canonicalIssueRef()), "codex reuses issue 111");

        var tenant = notification(
                ErrorIntakePipeline.SourceKind.GITHUB_ACTIONS,
                "run-tenant",
                "workflow failed",
                "8fb7593",
                "R1.18 Data Plane Tenant Isolation",
                3,
                true,
                true,
                true);
        var tenantFamily = ErrorIntakePipeline.correlate(List.of(tenant), ignored -> {}).get(0);
        passed += check(tenantFamily.owner() == ErrorIntakePipeline.OwnerDomain.DATA_SECURITY, "tenant workflow routes to data/security");
        passed += check("#110".equals(tenantFamily.canonicalIssueRef()), "tenant workflow reuses issue 110");

        System.out.printf("PASS %d/%d%n", passed, total);
        if (passed != total) throw new AssertionError("Expected all tests to pass");
    }

    private static ErrorIntakePipeline.Notification notification(
            ErrorIntakePipeline.SourceKind sourceKind,
            String sourceRef,
            String message,
            String revision,
            String workflow,
            int executedStepCount,
            boolean workflowRunExists,
            boolean jobFailed,
            boolean securityBoundaryTouched) {
        return new ErrorIntakePipeline.Notification(
                sourceKind,
                sourceRef,
                message,
                revision,
                workflow,
                executedStepCount,
                workflowRunExists,
                jobFailed,
                securityBoundaryTouched);
    }

    private static ErrorIntakePipeline.FailureFamily find(
            List<ErrorIntakePipeline.FailureFamily> families,
            String fingerprint) {
        return families.stream()
                .filter(family -> family.fingerprint().equals(fingerprint))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing family " + fingerprint));
    }

    private static int check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        return 1;
    }
}
