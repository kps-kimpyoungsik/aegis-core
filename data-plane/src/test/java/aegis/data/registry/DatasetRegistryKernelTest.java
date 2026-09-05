package aegis.data.registry;

import java.util.List;

import aegis.data.registry.DatasetRegistryKernel.DatasetDefinition;
import aegis.data.registry.DatasetRegistryKernel.DatasetKind;
import aegis.data.registry.DatasetRegistryKernel.DatasetOwnership;
import aegis.data.registry.DatasetRegistryKernel.RegistrationDecision;

public final class DatasetRegistryKernelTest {
    private static int assertions;

    private DatasetRegistryKernelTest() {}

    public static void main(String[] args) {
        DatasetDefinition source = dataset(
                "dataset.work", "work", DatasetKind.RECORD, true,
                "aegis.work-runtime", "WorkStorePort", "1");
        DatasetDefinition projection = dataset(
                "dataset.work.read-model", "work", DatasetKind.PROJECTION, false,
                "aegis.data-plane", "ProjectionStorePort", "1");

        check(DatasetRegistryKernel.evaluateRegistration(List.of(), source).decision() == RegistrationDecision.REGISTER,
                "new dataset registers");
        check(DatasetRegistryKernel.evaluateRegistration(List.of(source), source).decision() == RegistrationDecision.REUSE,
                "identical definition reuses");

        DatasetDefinition sameIdDifferentOwner = dataset(
                "dataset.work", "work", DatasetKind.RECORD, true,
                "other.owner", "OtherWritePort", "1");
        check(DatasetRegistryKernel.evaluateRegistration(List.of(source), sameIdDifferentOwner).decision() == RegistrationDecision.BLOCK,
                "dataset identity collision blocks");

        DatasetDefinition secondSource = dataset(
                "dataset.work.alt", "work", DatasetKind.EVENT_LOG, true,
                "aegis.data-plane", "EventStorePort", "1");
        check(DatasetRegistryKernel.evaluateRegistration(List.of(source), secondSource).decision() == RegistrationDecision.BLOCK,
                "multiple source of truth blocks");

        check(DatasetRegistryKernel.evaluateRegistration(List.of(source), projection).decision() == RegistrationDecision.REGISTER,
                "projection may coexist with source");
        check(DatasetRegistryKernel.mayProjectFrom(source, projection), "valid projection linkage");
        check(!DatasetRegistryKernel.mayProjectFrom(projection, source), "reverse projection linkage denied");

        DatasetDefinition wrongLogicalProjection = dataset(
                "dataset.other.read-model", "other", DatasetKind.PROJECTION, false,
                "aegis.data-plane", "ProjectionStorePort", "1");
        check(!DatasetRegistryKernel.mayProjectFrom(source, wrongLogicalProjection), "cross logical dataset projection denied");

        DatasetRegistryKernel.validateWriteAuthority(source, "aegis.work-runtime", "WorkStorePort");
        check(true, "matching owner and write port accepted");
        expectFailure(() -> DatasetRegistryKernel.validateWriteAuthority(source, "aegis.data-plane", "WorkStorePort"),
                "owner mismatch blocked");
        expectFailure(() -> DatasetRegistryKernel.validateWriteAuthority(source, "aegis.work-runtime", "ProjectionStorePort"),
                "write port mismatch blocked");

        DatasetDefinition sameOwnerPortNewSchema = dataset(
                "dataset.work.v2", "work-v2", DatasetKind.RECORD, false,
                "aegis.work-runtime", "WorkStorePort", "2");
        DatasetDefinition sameOwnerPortOldSchema = dataset(
                "dataset.work.v1", "work-v2", DatasetKind.RECORD, false,
                "aegis.work-runtime", "WorkStorePort", "1");
        check(DatasetRegistryKernel.evaluateRegistration(List.of(sameOwnerPortOldSchema), sameOwnerPortNewSchema).decision()
                        == RegistrationDecision.BLOCK,
                "ambiguous schema identity blocks");

        expectFailure(() -> dataset(
                "dataset.bad.projection", "bad", DatasetKind.PROJECTION, true,
                "aegis.data-plane", "ProjectionStorePort", "1"),
                "projection cannot source truth");
        expectFailure(() -> dataset(
                "dataset.bad.index", "bad-index", DatasetKind.INDEX, true,
                "aegis.data-plane", "IndexStorePort", "1"),
                "index cannot source truth");

        DatasetOwnership ownership = source.ownership();
        check(ownership.transactionBoundary().equals("tx://work"), "transaction boundary explicit");
        check(ownership.eventBoundary().equals("event://work"), "event boundary explicit");
        check(ownership.sideEffectOwner().equals("aegis.work-runtime"), "side effect owner explicit");
        check(ownership.recoveryOwner().equals("aegis.work-runtime"), "recovery owner explicit");
        check(ownership.retentionOwner().equals("aegis.data-plane"), "retention owner explicit");
        check(ownership.migrationOwner().equals("aegis.data-plane"), "migration owner explicit");
        check(source.provenanceRef().equals("prov://dataset.work"), "provenance preserved");

        expectFailure(() -> new DatasetOwnership(
                "", "WritePort", true, "tx", "event", "side", "recovery", "retention", "migration"),
                "blank owner blocked");
        expectFailure(() -> new DatasetDefinition(
                "", "logical", DatasetKind.RECORD, ownership, "1", true, "prov"),
                "blank dataset id blocked");

        System.out.println("PASS " + assertions + "/22");
    }

    private static DatasetDefinition dataset(
            String id,
            String logicalKey,
            DatasetKind kind,
            boolean sourceOfTruth,
            String owner,
            String writePort,
            String schemaVersion) {
        DatasetOwnership ownership = new DatasetOwnership(
                owner,
                writePort,
                sourceOfTruth,
                "tx://" + logicalKey,
                "event://" + logicalKey,
                owner,
                owner,
                "aegis.data-plane",
                "aegis.data-plane");
        return new DatasetDefinition(
                id, logicalKey, kind, ownership, schemaVersion, true, "prov://" + id);
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
