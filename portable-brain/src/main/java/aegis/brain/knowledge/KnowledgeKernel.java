package aegis.brain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class KnowledgeKernel {
    private KnowledgeKernel() {}

    public enum KnowledgeStatus {
        ACTIVE, CHALLENGED, SUPERSEDED, RETRACTED
    }

    public enum KnowledgeScope {
        TASK, PROJECT, DOMAIN, ORGANIZATION, GLOBAL
    }

    public record KnowledgeRecord(
            String knowledgeId,
            KnowledgeStatus status,
            KnowledgeScope scope,
            String subject,
            String claim,
            String sourceRef,
            String sourceVersion,
            String provenanceRef,
            double confidence,
            double applicability,
            Instant observedAt,
            Instant verifiedAt,
            Duration freshnessTtl,
            String supersedesId) {
        public KnowledgeRecord {
            knowledgeId = requireText(knowledgeId, "knowledgeId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(scope, "scope");
            subject = requireText(subject, "subject");
            claim = requireText(claim, "claim");
            sourceRef = requireText(sourceRef, "sourceRef");
            sourceVersion = requireText(sourceVersion, "sourceVersion");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
            requireUnitInterval(confidence, "confidence");
            requireUnitInterval(applicability, "applicability");
            Objects.requireNonNull(observedAt, "observedAt");
            Objects.requireNonNull(verifiedAt, "verifiedAt");
            Objects.requireNonNull(freshnessTtl, "freshnessTtl");
            if (verifiedAt.isBefore(observedAt)) {
                throw new IllegalArgumentException("verifiedAt before observedAt");
            }
            if (freshnessTtl.isNegative() || freshnessTtl.isZero()) {
                throw new IllegalArgumentException("freshnessTtl must be positive");
            }
            supersedesId = supersedesId == null ? "" : supersedesId;
            if (!supersedesId.isBlank() && supersedesId.equals(knowledgeId)) {
                throw new IllegalArgumentException("knowledge cannot supersede itself");
            }
        }

        public boolean retrievableAt(Instant now) {
            Objects.requireNonNull(now, "now");
            return status == KnowledgeStatus.ACTIVE
                    && !now.isAfter(verifiedAt.plus(freshnessTtl));
        }
    }

    public interface KnowledgePort {
        KnowledgeRecord save(KnowledgeRecord record);
        List<KnowledgeRecord> findByScope(KnowledgeScope scope);
    }

    public record RetrievalRequest(
            KnowledgeScope scope,
            int limit,
            double minimumConfidence,
            double minimumApplicability,
            Instant now) {
        public RetrievalRequest {
            Objects.requireNonNull(scope, "scope");
            if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
            requireUnitInterval(minimumConfidence, "minimumConfidence");
            requireUnitInterval(minimumApplicability, "minimumApplicability");
            Objects.requireNonNull(now, "now");
        }
    }

    public static List<KnowledgeRecord> selectRelevant(
            List<KnowledgeRecord> candidates,
            RetrievalRequest request) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(request, "request");
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(record -> record.scope() == request.scope())
                .filter(record -> record.retrievableAt(request.now()))
                .filter(record -> record.confidence() >= request.minimumConfidence())
                .filter(record -> record.applicability() >= request.minimumApplicability())
                .sorted(Comparator.comparingDouble(KnowledgeRecord::confidence).reversed()
                        .thenComparing(Comparator.comparingDouble(KnowledgeRecord::applicability).reversed())
                        .thenComparing(KnowledgeRecord::verifiedAt, Comparator.reverseOrder())
                        .thenComparing(KnowledgeRecord::knowledgeId))
                .limit(request.limit())
                .toList();
    }

    public static KnowledgeRecord transitionStatus(
            KnowledgeRecord current,
            KnowledgeStatus target,
            Instant verifiedAt,
            String provenanceRef) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        provenanceRef = requireText(provenanceRef, "provenanceRef");
        if (verifiedAt.isBefore(current.verifiedAt())) {
            throw new IllegalArgumentException("verification time regression");
        }
        if (!allowed(current.status(), target)) {
            throw new IllegalStateException("illegal status transition: " + current.status() + " -> " + target);
        }
        return copy(current, target, verifiedAt, provenanceRef, current.supersedesId());
    }

    public static KnowledgeRecord supersede(
            KnowledgeRecord replacement,
            KnowledgeRecord previous,
            Instant verifiedAt,
            String provenanceRef) {
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        provenanceRef = requireText(provenanceRef, "provenanceRef");
        if (replacement.knowledgeId().equals(previous.knowledgeId())) {
            throw new IllegalArgumentException("replacement must have a new identity");
        }
        if (replacement.scope() != previous.scope()) {
            throw new IllegalArgumentException("cross-scope supersede forbidden");
        }
        if (!replacement.subject().equals(previous.subject())) {
            throw new IllegalArgumentException("subject mismatch");
        }
        if (previous.status() == KnowledgeStatus.RETRACTED) {
            throw new IllegalStateException("retracted knowledge cannot be resurrected by supersede");
        }
        if (verifiedAt.isBefore(replacement.verifiedAt())) {
            throw new IllegalArgumentException("verification time regression");
        }
        return copy(replacement, KnowledgeStatus.ACTIVE, verifiedAt, provenanceRef, previous.knowledgeId());
    }

    public static boolean promotionEligible(KnowledgeRecord record, Instant now) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(now, "now");
        return record.retrievableAt(now)
                && record.confidence() >= 0.85
                && record.applicability() >= 0.70
                && !record.sourceRef().isBlank()
                && !record.sourceVersion().isBlank()
                && !record.provenanceRef().isBlank();
    }

    private static KnowledgeRecord copy(
            KnowledgeRecord current,
            KnowledgeStatus status,
            Instant verifiedAt,
            String provenanceRef,
            String supersedesId) {
        return new KnowledgeRecord(
                current.knowledgeId(),
                status,
                current.scope(),
                current.subject(),
                current.claim(),
                current.sourceRef(),
                current.sourceVersion(),
                provenanceRef,
                current.confidence(),
                current.applicability(),
                current.observedAt(),
                verifiedAt,
                current.freshnessTtl(),
                supersedesId);
    }

    private static boolean allowed(KnowledgeStatus current, KnowledgeStatus target) {
        return switch (current) {
            case ACTIVE -> target == KnowledgeStatus.CHALLENGED
                    || target == KnowledgeStatus.SUPERSEDED
                    || target == KnowledgeStatus.RETRACTED;
            case CHALLENGED -> target == KnowledgeStatus.ACTIVE
                    || target == KnowledgeStatus.SUPERSEDED
                    || target == KnowledgeStatus.RETRACTED;
            case SUPERSEDED, RETRACTED -> false;
        };
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void requireUnitInterval(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be within [0,1]");
        }
    }
}
