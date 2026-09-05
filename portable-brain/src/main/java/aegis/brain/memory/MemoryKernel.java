package aegis.brain.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MemoryKernel {
    private MemoryKernel() {}

    public enum MemoryType {
        WORKING, EPISODIC, SEMANTIC, LOCAL_PERSONAL, PROCEDURAL, FAILURE
    }

    public enum MemoryStatus {
        ACTIVE, CHALLENGED, SUPERSEDED, RETRACTED
    }

    public record MemoryRecord(
            String memoryId,
            MemoryType type,
            MemoryStatus status,
            String scope,
            String content,
            String provenanceRef,
            double confidence,
            Instant observedAt,
            Instant updatedAt) {
        public MemoryRecord {
            memoryId = requireText(memoryId, "memoryId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(status, "status");
            scope = requireText(scope, "scope");
            content = requireText(content, "content");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
            if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be within [0,1]");
            }
            Objects.requireNonNull(observedAt, "observedAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(observedAt)) {
                throw new IllegalArgumentException("updatedAt before observedAt");
            }
        }

        public boolean retrievable() {
            return status == MemoryStatus.ACTIVE || status == MemoryStatus.CHALLENGED;
        }
    }

    public interface MemoryPort {
        MemoryRecord save(MemoryRecord record);
        List<MemoryRecord> findByScope(String scope);
    }

    public record RetrievalRequest(String scope, MemoryType type, int limit, double minimumConfidence) {
        public RetrievalRequest {
            scope = requireText(scope, "scope");
            Objects.requireNonNull(type, "type");
            if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
            if (Double.isNaN(minimumConfidence) || minimumConfidence < 0.0 || minimumConfidence > 1.0) {
                throw new IllegalArgumentException("minimumConfidence must be within [0,1]");
            }
        }
    }

    public static List<MemoryRecord> selectRelevant(List<MemoryRecord> candidates, RetrievalRequest request) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(request, "request");
        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(MemoryRecord::retrievable)
                .filter(record -> record.scope().equals(request.scope()))
                .filter(record -> record.type() == request.type())
                .filter(record -> record.confidence() >= request.minimumConfidence())
                .sorted(Comparator.comparingDouble(MemoryRecord::confidence).reversed()
                        .thenComparing(MemoryRecord::updatedAt, Comparator.reverseOrder())
                        .thenComparing(MemoryRecord::memoryId))
                .limit(request.limit())
                .toList();
    }

    public static MemoryRecord transitionStatus(
            MemoryRecord current, MemoryStatus target, Instant now, String provenanceRef) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        provenanceRef = requireText(provenanceRef, "provenanceRef");
        if (now.isBefore(current.updatedAt())) throw new IllegalArgumentException("time regression");
        if (!allowed(current.status(), target)) {
            throw new IllegalStateException("illegal status transition: " + current.status() + " -> " + target);
        }
        return new MemoryRecord(
                current.memoryId(), current.type(), target, current.scope(), current.content(), provenanceRef,
                current.confidence(), current.observedAt(), now);
    }

    public static boolean promotionEligible(MemoryRecord record) {
        Objects.requireNonNull(record, "record");
        return record.status() == MemoryStatus.ACTIVE
                && record.type() != MemoryType.WORKING
                && record.confidence() >= 0.8
                && !record.provenanceRef().isBlank();
    }

    private static boolean allowed(MemoryStatus current, MemoryStatus target) {
        return switch (current) {
            case ACTIVE -> target == MemoryStatus.CHALLENGED
                    || target == MemoryStatus.SUPERSEDED
                    || target == MemoryStatus.RETRACTED;
            case CHALLENGED -> target == MemoryStatus.ACTIVE
                    || target == MemoryStatus.SUPERSEDED
                    || target == MemoryStatus.RETRACTED;
            case SUPERSEDED, RETRACTED -> false;
        };
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
