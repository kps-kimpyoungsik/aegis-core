package aegis.data.consistency;

import java.util.Objects;

public final class RecordEventProjectionKernel {
    private RecordEventProjectionKernel() {}

    public enum ApplyDecision { APPLY, SKIP_DUPLICATE, BLOCK_GAP, BLOCK_MISMATCH }

    public record CommitStamp(
            String transactionId,
            String sourceDatasetId,
            String recordId,
            long recordVersion,
            String eventId,
            long eventSequence,
            String provenanceRef) {
        public CommitStamp {
            transactionId = text(transactionId, "transactionId");
            sourceDatasetId = text(sourceDatasetId, "sourceDatasetId");
            recordId = text(recordId, "recordId");
            if (recordVersion <= 0) throw new IllegalArgumentException("recordVersion must be > 0");
            eventId = text(eventId, "eventId");
            if (eventSequence <= 0) throw new IllegalArgumentException("eventSequence must be > 0");
            provenanceRef = text(provenanceRef, "provenanceRef");
        }
    }

    public record ProjectionCursor(
            String projectionDatasetId,
            String sourceDatasetId,
            long lastEventSequence,
            String lastEventId) {
        public ProjectionCursor {
            projectionDatasetId = text(projectionDatasetId, "projectionDatasetId");
            sourceDatasetId = text(sourceDatasetId, "sourceDatasetId");
            if (lastEventSequence < 0) throw new IllegalArgumentException("lastEventSequence must be >= 0");
            lastEventId = lastEventId == null ? "" : lastEventId;
            if (lastEventSequence == 0 && !lastEventId.isBlank()) {
                throw new IllegalArgumentException("zero sequence cursor cannot have event id");
            }
            if (lastEventSequence > 0 && lastEventId.isBlank()) {
                throw new IllegalArgumentException("positive sequence cursor requires event id");
            }
        }
    }

    public record ApplyResult(ApplyDecision decision, String reason, ProjectionCursor nextCursor) {
        public ApplyResult {
            Objects.requireNonNull(decision, "decision");
            reason = text(reason, "reason");
            Objects.requireNonNull(nextCursor, "nextCursor");
        }
    }

    public static ApplyResult evaluate(ProjectionCursor cursor, CommitStamp commit) {
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(commit, "commit");
        if (!cursor.sourceDatasetId().equals(commit.sourceDatasetId())) {
            return new ApplyResult(ApplyDecision.BLOCK_MISMATCH, "source dataset mismatch", cursor);
        }
        if (commit.eventSequence() < cursor.lastEventSequence()) {
            return new ApplyResult(ApplyDecision.BLOCK_MISMATCH, "event sequence regression", cursor);
        }
        if (commit.eventSequence() == cursor.lastEventSequence()) {
            if (commit.eventId().equals(cursor.lastEventId())) {
                return new ApplyResult(ApplyDecision.SKIP_DUPLICATE, "already applied event", cursor);
            }
            return new ApplyResult(ApplyDecision.BLOCK_MISMATCH, "same sequence with different event identity", cursor);
        }
        if (commit.eventSequence() > cursor.lastEventSequence() + 1) {
            return new ApplyResult(ApplyDecision.BLOCK_GAP, "projection event gap", cursor);
        }
        ProjectionCursor next = new ProjectionCursor(
                cursor.projectionDatasetId(), cursor.sourceDatasetId(), commit.eventSequence(), commit.eventId());
        return new ApplyResult(ApplyDecision.APPLY, "next ordered event", next);
    }

    public static void validateCommitCorrelation(CommitStamp left, CommitStamp right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.eventId().equals(right.eventId()) && !left.equals(right)) {
            throw new IllegalStateException("event identity reused for different commit");
        }
        if (left.transactionId().equals(right.transactionId())
                && left.recordId().equals(right.recordId())
                && left.recordVersion() == right.recordVersion()
                && !left.eventId().equals(right.eventId())) {
            throw new IllegalStateException("same record version correlated to multiple events");
        }
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
