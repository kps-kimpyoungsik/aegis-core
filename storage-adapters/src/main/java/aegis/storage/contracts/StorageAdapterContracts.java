package aegis.storage.contracts;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class StorageAdapterContracts {
    private StorageAdapterContracts() {}

    public record RecordKey(String datasetId, String recordId) {
        public RecordKey {
            datasetId = requireText(datasetId, "datasetId");
            recordId = requireText(recordId, "recordId");
        }
    }

    public record VersionedRecord(RecordKey key, long version, String payloadJson, String provenanceRef) {
        public VersionedRecord {
            Objects.requireNonNull(key, "key");
            if (version < 0) throw new IllegalArgumentException("version must be >= 0");
            payloadJson = requireText(payloadJson, "payloadJson");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
        }
    }

    public enum WriteDecision { COMMITTED, VERSION_CONFLICT, DUPLICATE }

    public record WriteResult(WriteDecision decision, long resultingVersion, String evidenceRef) {
        public WriteResult {
            Objects.requireNonNull(decision, "decision");
            if (resultingVersion < 0) throw new IllegalArgumentException("resultingVersion must be >= 0");
            evidenceRef = requireText(evidenceRef, "evidenceRef");
        }
    }

    public interface RecordStorePort {
        Optional<VersionedRecord> find(RecordKey key);
        WriteResult insert(VersionedRecord record, String idempotencyKey);
        WriteResult update(VersionedRecord candidate, long expectedVersion, String idempotencyKey);
    }

    public record ProjectionEntry(String key, long sourceVersion, String payloadJson) {
        public ProjectionEntry {
            key = requireText(key, "key");
            if (sourceVersion < 0) throw new IllegalArgumentException("sourceVersion must be >= 0");
            payloadJson = requireText(payloadJson, "payloadJson");
        }
    }

    public interface ProjectionStorePort {
        Optional<ProjectionEntry> get(String key);
        void put(ProjectionEntry entry, Duration ttl);
        void delete(String key);
    }

    public record LeaseGrant(String resource, String holder, long fencingToken, Duration ttl) {
        public LeaseGrant {
            resource = requireText(resource, "resource");
            holder = requireText(holder, "holder");
            if (fencingToken <= 0) throw new IllegalArgumentException("fencingToken must be > 0");
            Objects.requireNonNull(ttl, "ttl");
            if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        }
    }

    public interface LeaseLockPort {
        Optional<LeaseGrant> acquire(String resource, String holder, Duration ttl);
        boolean release(LeaseGrant grant);
    }

    public record RuntimeProbe(boolean reachable, String adapterId, String evidenceRef) {
        public RuntimeProbe {
            adapterId = requireText(adapterId, "adapterId");
            evidenceRef = requireText(evidenceRef, "evidenceRef");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
        return value;
    }
}
