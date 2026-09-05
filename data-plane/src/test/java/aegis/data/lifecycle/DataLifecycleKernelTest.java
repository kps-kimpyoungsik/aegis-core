package aegis.data.lifecycle;

import java.time.Instant;

import aegis.data.lifecycle.DataLifecycleKernel.BackupManifest;
import aegis.data.lifecycle.DataLifecycleKernel.MigrationDecision;
import aegis.data.lifecycle.DataLifecycleKernel.MigrationPlan;
import aegis.data.lifecycle.DataLifecycleKernel.RestoreDecision;
import aegis.data.lifecycle.DataLifecycleKernel.RestoreRequest;
import aegis.data.lifecycle.DataLifecycleKernel.RetentionPolicy;

public final class DataLifecycleKernelTest {
    private static int assertions;

    private DataLifecycleKernelTest() {}

    public static void main(String[] args) {
        RetentionPolicy retention = new RetentionPolicy("dataset.work", "aegis.data-plane", 30, false, "prov://retention");
        check(DataLifecycleKernel.mayDelete(retention, "aegis.data-plane"), "matching retention owner allows deletion");
        check(!DataLifecycleKernel.mayDelete(retention, "other.owner"), "retention owner mismatch blocks deletion");
        RetentionPolicy held = new RetentionPolicy("dataset.work", "aegis.data-plane", 30, true, "prov://hold");
        check(!DataLifecycleKernel.mayDelete(held, "aegis.data-plane"), "legal hold blocks deletion");
        expectFailure(() -> new RetentionPolicy("dataset.work", "aegis.data-plane", -1, false, "prov"), "negative retention blocked");

        MigrationPlan safe = migration(false, false, "aegis.data-plane");
        check(DataLifecycleKernel.evaluateMigration(safe, "aegis.data-plane") == MigrationDecision.ALLOW, "safe migration allowed");
        MigrationPlan destructivePending = migration(true, false, "aegis.data-plane");
        check(DataLifecycleKernel.evaluateMigration(destructivePending, "aegis.data-plane") == MigrationDecision.REQUIRE_APPROVAL,
                "destructive migration requires approval");
        MigrationPlan destructiveApproved = migration(true, true, "aegis.data-plane");
        check(DataLifecycleKernel.evaluateMigration(destructiveApproved, "aegis.data-plane") == MigrationDecision.ALLOW,
                "approved destructive migration allowed");
        check(DataLifecycleKernel.evaluateMigration(safe, "other.owner") == MigrationDecision.BLOCK, "migration owner mismatch blocks");
        expectFailure(() -> new MigrationPlan("dataset.work", "aegis.data-plane", "1", "1", false, false,
                "before", "after", "rollback", "prov"), "same schema migration blocked");
        expectFailure(() -> new MigrationPlan("dataset.work", "aegis.data-plane", "1", "2", false, false,
                "", "after", "rollback", "prov"), "missing before evidence blocked");
        expectFailure(() -> new MigrationPlan("dataset.work", "aegis.data-plane", "1", "2", false, false,
                "before", "", "rollback", "prov"), "missing after validation blocked");
        expectFailure(() -> new MigrationPlan("dataset.work", "aegis.data-plane", "1", "2", false, false,
                "before", "after", "", "prov"), "missing rollback blocked");

        BackupManifest backup = new BackupManifest("backup-1", "dataset.work", "2", Instant.parse("2026-09-05T00:00:00Z"),
                "sha256:abc", "prov://backup-1");
        RestoreRequest valid = new RestoreRequest("dataset.work", "2", "sha256:abc", "aegis.data-plane", "evidence://integrity");
        check(DataLifecycleKernel.evaluateRestore(backup, valid, "aegis.data-plane") == RestoreDecision.ALLOW, "valid restore allowed");
        RestoreRequest wrongDataset = new RestoreRequest("dataset.other", "2", "sha256:abc", "aegis.data-plane", "evidence://integrity");
        check(DataLifecycleKernel.evaluateRestore(backup, wrongDataset, "aegis.data-plane") == RestoreDecision.BLOCK, "dataset mismatch blocks restore");
        RestoreRequest wrongSchema = new RestoreRequest("dataset.work", "3", "sha256:abc", "aegis.data-plane", "evidence://integrity");
        check(DataLifecycleKernel.evaluateRestore(backup, wrongSchema, "aegis.data-plane") == RestoreDecision.BLOCK, "schema mismatch blocks restore");
        RestoreRequest wrongHash = new RestoreRequest("dataset.work", "2", "sha256:def", "aegis.data-plane", "evidence://integrity");
        check(DataLifecycleKernel.evaluateRestore(backup, wrongHash, "aegis.data-plane") == RestoreDecision.BLOCK, "integrity hash mismatch blocks restore");
        check(DataLifecycleKernel.evaluateRestore(backup, valid, "other.owner") == RestoreDecision.BLOCK, "restore owner mismatch blocks");
        expectFailure(() -> new BackupManifest("backup", "dataset.work", "2", Instant.now(), "", "prov"), "backup without hash blocked");
        expectFailure(() -> new RestoreRequest("dataset.work", "2", "sha256:abc", "aegis.data-plane", ""), "restore without integrity evidence blocked");
        check(backup.provenanceRef().equals("prov://backup-1"), "backup provenance preserved");
        check(safe.rollbackPoint().equals("rollback://dataset.work/v1"), "migration rollback preserved");

        System.out.println("PASS " + assertions + "/21");
    }

    private static MigrationPlan migration(boolean destructive, boolean approved, String owner) {
        return new MigrationPlan("dataset.work", owner, "1", "2", destructive, approved,
                "evidence://before", "evidence://after", "rollback://dataset.work/v1", "prov://migration");
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
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // expected fail-closed path
        }
    }
}
