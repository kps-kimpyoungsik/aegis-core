package aegis.brain.skill;

import java.util.List;

import aegis.brain.skill.AdaptiveErrorIntelligenceKernel.AnalysisDepth;
import aegis.brain.skill.AdaptiveErrorIntelligenceKernel.Classification;
import aegis.brain.skill.AdaptiveErrorIntelligenceKernel.ErrorExperience;
import aegis.brain.skill.AdaptiveErrorIntelligenceKernel.FailureDomain;
import aegis.brain.skill.AdaptiveErrorIntelligenceKernel.FailurePhase;
import aegis.brain.skill.AdaptiveErrorIntelligenceKernel.Incident;
import aegis.brain.skill.AdaptiveErrorIntelligenceKernel.RetryPolicy;

public final class AdaptiveErrorIntelligenceKernelTest {
    private static int assertions;

    private AdaptiveErrorIntelligenceKernelTest() {}

    public static void main(String[] args) {
        Incident billingLock = new Incident(
                "github-actions",
                "The job was not started because your account is locked due to a billing issue.",
                0, true, true, 17, false, true, true);
        Classification classified = AdaptiveErrorIntelligenceKernel.classify(billingLock);

        check(classified.domain() == FailureDomain.EXTERNAL_PLATFORM_ACCOUNT, "billing lock domain");
        check(classified.phase() == FailurePhase.PRE_RUN_ADMISSION, "billing lock phase");
        check(classified.retryPolicy() == RetryPolicy.SUPPRESS_UNTIL_STATE_CHANGE, "retry suppressed");
        check(!classified.codeFailure(), "not code failure");
        check(classified.mandatoryChecksBlocked(), "mandatory checks stay blocked");
        check(classified.canaryRequiredAfterRecovery(), "canary required");
        check(classified.fingerprint().contains("billing-lock"), "stable fingerprint");

        Incident local = new Incident(
                "unit-test", "assertion failed", 4, true, true, 1, false, false, false);
        check(AdaptiveErrorIntelligenceKernel.recommendedDepth(local) == AnalysisDepth.L0_SURFACE,
                "local deterministic symptom starts shallow");

        Incident fanout = new Incident(
                "github-actions", "multiple workflow failures", 6, true, true, 8, false, false, false);
        check(AdaptiveErrorIntelligenceKernel.recommendedDepth(fanout) == AnalysisDepth.L2_STRUCTURAL,
                "fanout expands relationships");

        Incident security = new Incident(
                "api", "authorization anomaly", 2, true, true, 2, true, true, false);
        check(AdaptiveErrorIntelligenceKernel.recommendedDepth(security) == AnalysisDepth.L3_CONTROL_DATA_FLOW,
                "security requires flow analysis");

        String fingerprint = classified.fingerprint();
        ErrorExperience first = new ErrorExperience(
                fingerprint, classified.domain(), classified.phase(), AnalysisDepth.L1_TEMPORAL,
                "EXTERNAL_ACCOUNT_RECOVERY_REQUIRED", false, "issue://114/run/34024192570");
        ErrorExperience second = new ErrorExperience(
                fingerprint, classified.domain(), classified.phase(), AnalysisDepth.L1_TEMPORAL,
                "REPEATED_SIGNATURE", false, "mail://github/billing-lock");
        check(!AdaptiveErrorIntelligenceKernel.learningCandidate(List.of(first), fingerprint),
                "single episode cannot promote pattern");
        check(AdaptiveErrorIntelligenceKernel.learningCandidate(List.of(first, second), fingerprint),
                "repeated mechanism becomes learning candidate");

        expectFailure(() -> new Incident(" ", "error", 0, true, true, 0, false, false, false),
                "blank source rejected");
        expectFailure(() -> new Incident("source", "error", -1, true, true, 0, false, false, false),
                "negative step count rejected");

        System.out.println("PASS " + assertions + "/14");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(Runnable action, String message) {
        assertions++;
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            // expected fail-closed path
        }
    }
}
