package aegis.data.registry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DatasetRegistryKernel {
    private DatasetRegistryKernel() {}

    public enum DatasetKind { RECORD, EVENT_LOG, PROJECTION, BLOB, INDEX }
    public enum RegistrationDecision { REGISTER, REUSE, BLOCK }

    public record DatasetOwnership(
            String canonicalOwner,
            String writePort,
            boolean sourceOfTruth,
            String transactionBoundary,
            String eventBoundary,
            String sideEffectOwner,
            String recoveryOwner,
            String retentionOwner,
            String migrationOwner) {
        public DatasetOwnership {
            canonicalOwner = requireText(canonicalOwner, "canonicalOwner");
            writePort = requireText(writePort, "writePort");
            transactionBoundary = requireText(transactionBoundary, "transactionBoundary");
            eventBoundary = requireText(eventBoundary, "eventBoundary");
            sideEffectOwner = requireText(sideEffectOwner, "sideEffectOwner");
            recoveryOwner = requireText(recoveryOwner, "recoveryOwner");
            retentionOwner = requireText(retentionOwner, "retentionOwner");
            migrationOwner = requireText(migrationOwner, "migrationOwner");
        }
    }

    public record DatasetDefinition(
            String datasetId,
            String logicalDatasetKey,
            DatasetKind kind,
            DatasetOwnership ownership,
            String schemaVersion,
            boolean mutable,
            String provenanceRef) {
        public DatasetDefinition {
            datasetId = requireText(datasetId, "datasetId");
            logicalDatasetKey = requireText(logicalDatasetKey, "logicalDatasetKey");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(ownership, "ownership");
            schemaVersion = requireText(schemaVersion, "schemaVersion");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
            if (kind == DatasetKind.PROJECTION && ownership.sourceOfTruth()) {
                throw new IllegalArgumentException("projection cannot be source of truth");
            }
            if (kind == DatasetKind.INDEX && ownership.sourceOfTruth()) {
                throw new IllegalArgumentException("index cannot be source of truth");
            }
        }
    }

    public record RegistrationResult(RegistrationDecision decision, String reason) {
        public RegistrationResult {
            Objects.requireNonNull(decision, "decision");
            reason = requireText(reason, "reason");
        }
    }

    public interface DatasetRegistryPort {
        DatasetDefinition save(DatasetDefinition definition);
        Optional<DatasetDefinition> findById(String datasetId);
        List<DatasetDefinition> findByLogicalDatasetKey(String logicalDatasetKey);
    }

    public static RegistrationResult evaluateRegistration(
            List<DatasetDefinition> existing,
            DatasetDefinition candidate) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(candidate, "candidate");

        for (DatasetDefinition current : existing) {
            if (current == null) continue;
            if (current.datasetId().equals(candidate.datasetId())) {
                if (current.equals(candidate)) {
                    return new RegistrationResult(RegistrationDecision.REUSE, "identical canonical dataset already registered");
                }
                return new RegistrationResult(RegistrationDecision.BLOCK, "dataset identity collision");
            }
        }

        for (DatasetDefinition current : existing) {
            if (current == null) continue;
            if (!current.logicalDatasetKey().equals(candidate.logicalDatasetKey())) continue;
            if (current.ownership().sourceOfTruth() && candidate.ownership().sourceOfTruth()) {
                return new RegistrationResult(RegistrationDecision.BLOCK, "multiple sources of truth for logical dataset");
            }
            if (current.ownership().canonicalOwner().equals(candidate.ownership().canonicalOwner())
                    && current.ownership().writePort().equals(candidate.ownership().writePort())
                    && current.kind() == candidate.kind()
                    && !current.schemaVersion().equals(candidate.schemaVersion())) {
                return new RegistrationResult(RegistrationDecision.BLOCK, "same owner/write port/kind with incompatible schema version identity");
            }
        }

        return new RegistrationResult(RegistrationDecision.REGISTER, "no canonical collision detected");
    }

    public static void validateWriteAuthority(DatasetDefinition dataset, String owner, String writePort) {
        Objects.requireNonNull(dataset, "dataset");
        owner = requireText(owner, "owner");
        writePort = requireText(writePort, "writePort");
        if (!dataset.ownership().canonicalOwner().equals(owner)) {
            throw new IllegalStateException("canonical owner mismatch");
        }
        if (!dataset.ownership().writePort().equals(writePort)) {
            throw new IllegalStateException("write port mismatch");
        }
    }

    public static boolean mayProjectFrom(DatasetDefinition source, DatasetDefinition projection) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(projection, "projection");
        return source.ownership().sourceOfTruth()
                && projection.kind() == DatasetKind.PROJECTION
                && !projection.ownership().sourceOfTruth()
                && source.logicalDatasetKey().equals(projection.logicalDatasetKey());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
