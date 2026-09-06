package aegis.brain.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Converts heterogeneous failure notifications into bounded, deduplicated handoff units.
 * External adapters supply evidence; this pipeline owns normalization/correlation/routing only.
 */
public final class ErrorIntakePipeline {
    private ErrorIntakePipeline() {}

    public enum SourceKind {
        GITHUB_ACTIONS,
        VERCEL,
        CODEX,
        GMAIL,
        OTHER
    }

    public enum OwnerDomain {
        CI_EXTERNAL_AUTHORITY,
        EXTERNAL_CAPACITY,
        CI_GOVERNANCE,
        DATA_SECURITY,
        RECOVERY_RELEASE,
        PORTABLE_BRAIN,
        UNKNOWN
    }

    public record Notification(
            SourceKind sourceKind,
            String sourceRef,
            String message,
            String revision,
            String workflow,
            int executedStepCount,
            boolean workflowRunExists,
            boolean jobFailed,
            boolean securityBoundaryTouched) {
        public Notification {
            Objects.requireNonNull(sourceKind, "sourceKind");
            sourceRef = requireText(sourceRef, "sourceRef");
            message = requireText(message, "message");
            revision = normalizeOptional(revision);
            workflow = normalizeOptional(workflow);
            if (executedStepCount < 0) throw new IllegalArgumentException("executedStepCount must be >= 0");
        }
    }

    public record FailureFamily(
            String fingerprint,
            OwnerDomain owner,
            String canonicalIssueRef,
            int notificationCount,
            List<String> sourceRefs,
            List<String> revisions,
            List<String> workflows,
            AdaptiveErrorIntelligenceKernel.Classification classification,
            boolean suppressDuplicateWork,
            String handoffReason) {
        public FailureFamily {
            fingerprint = requireText(fingerprint, "fingerprint");
            Objects.requireNonNull(owner, "owner");
            canonicalIssueRef = requireText(canonicalIssueRef, "canonicalIssueRef");
            if (notificationCount < 1) throw new IllegalArgumentException("notificationCount must be >= 1");
            sourceRefs = List.copyOf(sourceRefs);
            revisions = List.copyOf(revisions);
            workflows = List.copyOf(workflows);
            Objects.requireNonNull(classification, "classification");
            handoffReason = requireText(handoffReason, "handoffReason");
        }
    }

    public interface FailureMemorySink {
        void remember(FailureFamily family);
    }

    public static List<FailureFamily> correlate(List<Notification> notifications, FailureMemorySink memorySink) {
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(memorySink, "memorySink");
        Map<String, List<Notification>> groups = new LinkedHashMap<>();
        for (Notification notification : notifications) {
            Objects.requireNonNull(notification, "notification");
            groups.computeIfAbsent(fingerprint(notification), ignored -> new ArrayList<>()).add(notification);
        }

        List<FailureFamily> result = new ArrayList<>();
        for (Map.Entry<String, List<Notification>> entry : groups.entrySet()) {
            FailureFamily family = buildFamily(entry.getKey(), entry.getValue());
            memorySink.remember(family);
            result.add(family);
        }
        return List.copyOf(result);
    }

    public static String fingerprint(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        String text = notification.message().toLowerCase(Locale.ROOT);
        if (text.contains("account is locked due to a billing issue")) {
            return "github-actions|billing-lock|pre-run";
        }
        if (text.contains("resource is limited") && text.contains("api-deployments-free-per-day")) {
            return "vercel|deployment-daily-capacity|account-scope";
        }
        if (text.contains("reached your codex usage limits")) {
            return "codex|review-usage-limit|account-scope";
        }
        String workflow = notification.workflow().isEmpty() ? "unknown-workflow" : slug(notification.workflow());
        String revision = notification.revision().isEmpty() ? "unknown-revision" : notification.revision().toLowerCase(Locale.ROOT);
        return notification.sourceKind().name().toLowerCase(Locale.ROOT) + "|" + workflow + "|" + revision;
    }

    private static FailureFamily buildFamily(String fingerprint, List<Notification> notifications) {
        Notification first = notifications.get(0);
        AdaptiveErrorIntelligenceKernel.Classification classification = AdaptiveErrorIntelligenceKernel.classify(
                new AdaptiveErrorIntelligenceKernel.Incident(
                        first.sourceKind().name(),
                        first.message(),
                        first.executedStepCount(),
                        first.workflowRunExists(),
                        first.jobFailed(),
                        notifications.size(),
                        first.securityBoundaryTouched(),
                        distinctWorkflows(notifications) > 1,
                        notifications.size() > 1));

        OwnerResolution resolution = resolveOwner(fingerprint, notifications, classification);
        return new FailureFamily(
                fingerprint,
                resolution.owner(),
                resolution.issueRef(),
                notifications.size(),
                uniqueStrings(notifications.stream().map(Notification::sourceRef).toList()),
                uniqueNonBlank(notifications.stream().map(Notification::revision).toList()),
                uniqueNonBlank(notifications.stream().map(Notification::workflow).toList()),
                classification,
                notifications.size() > 1 || classification.retryPolicy() == AdaptiveErrorIntelligenceKernel.RetryPolicy.SUPPRESS_UNTIL_STATE_CHANGE,
                resolution.reason());
    }

    private static OwnerResolution resolveOwner(
            String fingerprint,
            List<Notification> notifications,
            AdaptiveErrorIntelligenceKernel.Classification classification) {
        String joinedWorkflows = String.join(" ", notifications.stream().map(Notification::workflow).toList()).toLowerCase(Locale.ROOT);
        if (fingerprint.startsWith("github-actions|billing-lock")) {
            return new OwnerResolution(OwnerDomain.CI_EXTERNAL_AUTHORITY, "#114", "External GitHub account/billing authority must recover before CI rerun.");
        }
        if (fingerprint.startsWith("vercel|") || fingerprint.startsWith("codex|")) {
            return new OwnerResolution(OwnerDomain.EXTERNAL_CAPACITY, "#111", "External quota/capacity failure; suppress repeated generative or deployment work until capacity changes.");
        }
        if (joinedWorkflows.contains("tenant") || joinedWorkflows.contains("postgresql")) {
            return new OwnerResolution(OwnerDomain.DATA_SECURITY, "#110", "Data-plane/tenant-boundary evidence belongs to the data-security owner.");
        }
        if (joinedWorkflows.contains("recovery") || joinedWorkflows.contains("rollback") || joinedWorkflows.contains("backup") || joinedWorkflows.contains("restore")) {
            return new OwnerResolution(OwnerDomain.RECOVERY_RELEASE, "#112", "Recovery/release evidence belongs to the recovery owner.");
        }
        if (notifications.size() >= 5 || distinctWorkflows(notifications) > 1) {
            return new OwnerResolution(OwnerDomain.CI_GOVERNANCE, "#109", "Correlated fan-out belongs to CI governance rather than independent per-workflow fixes.");
        }
        if (classification.domain() == AdaptiveErrorIntelligenceKernel.FailureDomain.SECURITY) {
            return new OwnerResolution(OwnerDomain.DATA_SECURITY, "#110", "Security-boundary failure requires fail-closed domain ownership.");
        }
        return new OwnerResolution(OwnerDomain.PORTABLE_BRAIN, "#113", "Unclassified failure remains with Adaptive Error Intelligence until evidence resolves a canonical owner.");
    }

    private record OwnerResolution(OwnerDomain owner, String issueRef, String reason) {}

    private static int distinctWorkflows(List<Notification> notifications) {
        return uniqueNonBlank(notifications.stream().map(Notification::workflow).toList()).size();
    }

    private static List<String> uniqueNonBlank(List<String> values) {
        return uniqueStrings(values.stream().filter(value -> !value.isBlank()).toList());
    }

    private static List<String> uniqueStrings(List<String> values) {
        return values.stream().distinct().toList();
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return trimmed;
    }
}
