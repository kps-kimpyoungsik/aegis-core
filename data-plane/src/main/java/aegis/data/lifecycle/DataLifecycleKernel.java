package aegis.data.lifecycle;

import java.time.Instant;
import java.util.Objects;

public final class DataLifecycleKernel {
    private DataLifecycleKernel() {}

    public enum MigrationDecision { ALLOW, REQUIRE_APPROVAL, BLOCK }
    public enum RestoreDecision { ALLOW, BLOCK }

    public record RetentionPolicy(
            String datasetId,
            String retentionOwner,
            long retentionDays,
            boolean legalHold,
            String provenanceRef) {
        public RetentionPolicy {
            requireText(datasetId, "datasetId");
            requireText(retentionOwner, "retentionOwner");
            if (retentionDays < 0) throw new IllegalArgumentException("retentionDays must be >= 0");
            requireText(provenanceRef, "provenanceRef");
        }
    }

    public record MigrationPlan(
            String datasetId,
            String migrationOwner,
            String fromSchemaVersion,
            String toSchemaVersion,
            boolean destructive,
            boolean approvalGranted,
            String beforeEvidenceRef,
            String afterValidationRef,
            String rollbackPoint,
            String provenanceRef) {
        public MigrationPlan {
            requireText(datasetId, "datasetId");
            requireText(migrationOwner, "migrationOwner");
            requireText(fromSchemaVersion, "fromSchemaVersion");
            requireText(toSchemaVersion, "toSchemaVersion");
            requireText(beforeEvidenceRef, "beforeEvidenceRef");
            requireText(afterValidationRef, "afterValidationRef");
            requireText(rollbackPoint, "rollbackPoint");
            requireText(provenanceRef, "provenanceRef");
            if (fromSchemaVersion.equals(toSchemaVersion)) {
                throw new IllegalArgumentException("migration must change schema version");
            }
        }
    }

    public record BackupManifest(
            String backupId,
            String datasetId,
            String schemaVersion,
            Instant createdAt,
            String contentHash,
            String provenanceRef) {
        public BackupManifest {
            requireText(backupId, "backupId");
            requireText(datasetId, "datasetId");
            requireText(schemaVersion, "schemaVersion");
            Objects.requireNonNull(createdAt, "createdAt");
            requireText(contentHash, "contentHash");
            requireText(provenanceRef, "provenanceRef");
        }
    }

    public record RestoreRequest(
            String datasetId,
            String expectedSchemaVersion,
            String expectedContentHash,
            String restoreOwner,
            String integrityEvidenceRef) {
        public RestoreRequest {
            requireText(datasetId, "datasetId");
            requireText(expectedSchemaVersion, "expectedSchemaVersion");
            requireText(expectedContentHash, "expectedContentHash");
            requireText(restoreOwner, "restoreOwner");
            requireText(integrityEvidenceRef, "integrityEvidenceRef");
        }
    }

    public static MigrationDecision evaluateMigration(
            MigrationPlan plan,
            String expectedMigrationOwner) {
        Objects.requireNonNull(plan, "plan");
        requireText(expectedMigrationOwner, "expectedMigrationOwner");
        if (!plan.migrationOwner().equals(expectedMigrationOwner)) return MigrationDecision.BLOCK;
        if (plan.destructive() && !plan.approvalGranted()) return MigrationDecision.REQUIRE_APPROVAL;
        return MigrationDecision.ALLOW;
    }

    public static boolean mayDelete(RetentionPolicy policy, String expectedRetentionOwner) {
        Objects.requireNonNull(policy, "policy");
        requireText(expectedRetentionOwner, "expectedRetentionOwner");
        return policy.retentionOwner().equals(expectedRetentionOwner) && !policy.legalHold();
    }

    public static RestoreDecision evaluateRestore(
            BackupManifest backup,
            RestoreRequest request,
            String expectedRestoreOwner) {
        Objects.requireNonNull(backup, "backup");
        Objects.requireNonNull(request, "request");
        requireText(expectedRestoreOwner, "expectedRestoreOwner");
        if (!request.restoreOwner().equals(expectedRestoreOwner)) return RestoreDecision.BLOCK;
        if (!backup.datasetId().equals(request.datasetId())) return RestoreDecision.BLOCK;
        if (!backup.schemaVersion().equals(request.expectedSchemaVersion())) return RestoreDecision.BLOCK;
        if (!backup.contentHash().equals(request.expectedContentHash())) return RestoreDecision.BLOCK;
        return RestoreDecision.ALLOW;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
    }
}
