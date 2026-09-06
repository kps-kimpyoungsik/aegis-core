package aegis.brain.skill;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic first-stage classifier and routing kernel for Adaptive Error Intelligence.
 */
public final class AdaptiveErrorIntelligenceKernel {
    private static final String BILLING_LOCK_MESSAGE =
            "the job was not started because your account is locked due to a billing issue";

    private AdaptiveErrorIntelligenceKernel() {}

    public enum FailureDomain {
        CODE,
        DATA,
        SECURITY,
        CI_WORKFLOW,
        EXTERNAL_PLATFORM_ACCOUNT,
        EXTERNAL_CAPACITY,
        UNKNOWN
    }

    public enum FailurePhase {
        PRE_RUN_ADMISSION,
        RUNNER_START,
        CHECKOUT,
        BUILD,
        TEST,
        DEPLOY,
        VALIDATE,
        UNKNOWN
    }

    public enum RetryPolicy {
        SUPPRESS_UNTIL_STATE_CHANGE,
        BOUNDED_TRANSIENT_RETRY,
        REQUIRES_FIX,
        INVESTIGATE
    }

    public enum AnalysisDepth {
        L0_SURFACE,
        L1_TEMPORAL,
        L2_STRUCTURAL,
        L3_CONTROL_DATA_FLOW,
        L4_HISTORY,
        L5_RUNTIME_STATISTICAL,
        L6_RESEARCH,
        L7_LLM_CAUSAL
    }

    public record Incident(
            String source,
            String message,
            int executedStepCount,
            boolean workflowRunExists,
            boolean jobFailed,
            int relatedFailureCount,
            boolean securityBoundaryTouched,
            boolean crossDomain,
            boolean priorSimilarFailure) {
        public Incident {
            source = requireText(source, "source");
            message = requireText(message, "message");
            if (executedStepCount < 0) throw new IllegalArgumentException("executedStepCount must be >= 0");
            if (relatedFailureCount < 0) throw new IllegalArgumentException("relatedFailureCount must be >= 0");
        }
    }

    public record Classification(
            FailureDomain domain,
            FailurePhase phase,
            RetryPolicy retryPolicy,
            AnalysisDepth minimumDepth,
            String fingerprint,
            boolean codeFailure,
            boolean mandatoryChecksBlocked,
            boolean canaryRequiredAfterRecovery,
            List<String> nextActions,
            String rationale) {
        public Classification {
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(retryPolicy, "retryPolicy");
            Objects.requireNonNull(minimumDepth, "minimumDepth");
            fingerprint = requireText(fingerprint, "fingerprint");
            nextActions = List.copyOf(Objects.requireNonNull(nextActions, "nextActions"));
            rationale = requireText(rationale, "rationale");
        }
    }

    public record ErrorExperience(
            String fingerprint,
            FailureDomain domain,
            FailurePhase phase,
            AnalysisDepth depthUsed,
            String outcome,
            boolean recurrencePrevented,
            String provenanceRef) {
        public ErrorExperience {
            fingerprint = requireText(fingerprint, "fingerprint");
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(depthUsed, "depthUsed");
            outcome = requireText(outcome, "outcome");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
        }
    }

    public static Classification classify(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        if (isGitHubActionsBillingLock(incident)) {
            return new Classification(
                    FailureDomain.EXTERNAL_PLATFORM_ACCOUNT,
                    FailurePhase.PRE_RUN_ADMISSION,
                    RetryPolicy.SUPPRESS_UNTIL_STATE_CHANGE,
                    AnalysisDepth.L1_TEMPORAL,
                    "github-actions|billing-lock|account-scope|pre-run|zero-steps",
                    false,
                    true,
                    true,
                    List.of(
                            "inspect billing and payment state outside repository code",
                            "suppress workflow reruns until external account state changes",
                            "run one cheap canary after recovery before resuming CI fan-out"),
                    "Runner work never started: billing-lock signature plus zero executed steps is an external account admission failure.");
        }

        AnalysisDepth depth = recommendedDepth(incident);
        FailureDomain domain = incident.securityBoundaryTouched()
                ? FailureDomain.SECURITY
                : FailureDomain.UNKNOWN;
        return new Classification(
                domain,
                FailurePhase.UNKNOWN,
                RetryPolicy.INVESTIGATE,
                depth,
                genericFingerprint(incident),
                domain == FailureDomain.CODE,
                incident.securityBoundaryTouched(),
                false,
                List.of("reconstruct baseline", "expand impact relationships", "verify causal hypothesis"),
                "No deterministic signature matched; escalate only to the minimum evidence depth required by impact and recurrence.");
    }

    public static AnalysisDepth recommendedDepth(Incident incident) {
        Objects.requireNonNull(incident, "incident");
        if (incident.securityBoundaryTouched() || incident.crossDomain()) {
            return AnalysisDepth.L3_CONTROL_DATA_FLOW;
        }
        if (incident.priorSimilarFailure()) {
            return AnalysisDepth.L4_HISTORY;
        }
        if (incident.relatedFailureCount() >= 5) {
            return AnalysisDepth.L2_STRUCTURAL;
        }
        if (incident.relatedFailureCount() > 1) {
            return AnalysisDepth.L1_TEMPORAL;
        }
        return AnalysisDepth.L0_SURFACE;
    }

    public static boolean learningCandidate(List<ErrorExperience> experiences, String fingerprint) {
        Objects.requireNonNull(experiences, "experiences");
        String normalized = requireText(fingerprint, "fingerprint");
        long matches = experiences.stream()
                .filter(Objects::nonNull)
                .filter(e -> e.fingerprint().equals(normalized))
                .count();
        return matches >= 2;
    }

    private static boolean isGitHubActionsBillingLock(Incident incident) {
        String normalized = incident.message().toLowerCase(Locale.ROOT);
        return normalized.contains(BILLING_LOCK_MESSAGE)
                && incident.workflowRunExists()
                && incident.jobFailed()
                && incident.executedStepCount() == 0;
    }

    private static String genericFingerprint(Incident incident) {
        return incident.source().toLowerCase(Locale.ROOT)
                + "|unclassified|steps=" + incident.executedStepCount()
                + "|related=" + incident.relatedFailureCount();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return trimmed;
    }
}
