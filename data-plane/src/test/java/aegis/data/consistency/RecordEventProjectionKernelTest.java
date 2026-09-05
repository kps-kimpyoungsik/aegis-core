package aegis.data.consistency;

import aegis.data.consistency.RecordEventProjectionKernel.ApplyDecision;
import aegis.data.consistency.RecordEventProjectionKernel.CommitStamp;
import aegis.data.consistency.RecordEventProjectionKernel.ProjectionCursor;

public final class RecordEventProjectionKernelTest {
    private static int assertions;

    private RecordEventProjectionKernelTest() {}

    public static void main(String[] args) {
        ProjectionCursor empty = new ProjectionCursor("projection.work", "dataset.work", 0, "");
        CommitStamp first = commit("tx1", "r1", 1, "e1", 1);
        var firstResult = RecordEventProjectionKernel.evaluate(empty, first);
        check(firstResult.decision() == ApplyDecision.APPLY, "first event applies");
        check(firstResult.nextCursor().lastEventSequence() == 1, "cursor advances");
        check(firstResult.nextCursor().lastEventId().equals("e1"), "cursor event identity advances");

        var duplicate = RecordEventProjectionKernel.evaluate(firstResult.nextCursor(), first);
        check(duplicate.decision() == ApplyDecision.SKIP_DUPLICATE, "duplicate event skips");

        CommitStamp gap = commit("tx3", "r1", 3, "e3", 3);
        check(RecordEventProjectionKernel.evaluate(firstResult.nextCursor(), gap).decision() == ApplyDecision.BLOCK_GAP,
                "event gap blocks");

        CommitStamp second = commit("tx2", "r1", 2, "e2", 2);
        var secondResult = RecordEventProjectionKernel.evaluate(firstResult.nextCursor(), second);
        check(secondResult.decision() == ApplyDecision.APPLY, "next event applies");
        check(secondResult.nextCursor().lastEventSequence() == 2, "second event advances cursor");

        CommitStamp regression = commit("tx0", "r1", 1, "e0", 1);
        check(RecordEventProjectionKernel.evaluate(secondResult.nextCursor(), regression).decision() == ApplyDecision.BLOCK_MISMATCH,
                "sequence regression blocks");

        CommitStamp sameSequenceDifferentId = commit("tx4", "r1", 2, "e2-other", 2);
        check(RecordEventProjectionKernel.evaluate(secondResult.nextCursor(), sameSequenceDifferentId).decision()
                        == ApplyDecision.BLOCK_MISMATCH,
                "same sequence different identity blocks");

        CommitStamp wrongSource = new CommitStamp("tx5", "dataset.other", "r1", 3, "e3", 3, "prov://e3");
        check(RecordEventProjectionKernel.evaluate(secondResult.nextCursor(), wrongSource).decision() == ApplyDecision.BLOCK_MISMATCH,
                "source mismatch blocks");

        RecordEventProjectionKernel.validateCommitCorrelation(first, first);
        check(true, "identical commit correlation allowed");

        CommitStamp sameEventDifferentCommit = new CommitStamp("tx-other", "dataset.work", "r2", 1, "e1", 1, "prov://other");
        expectFailure(() -> RecordEventProjectionKernel.validateCommitCorrelation(first, sameEventDifferentCommit),
                "event identity reuse blocks");

        CommitStamp sameRecordVersionDifferentEvent = new CommitStamp("tx1", "dataset.work", "r1", 1, "e1-other", 2, "prov://other2");
        expectFailure(() -> RecordEventProjectionKernel.validateCommitCorrelation(first, sameRecordVersionDifferentEvent),
                "same record version multiple events blocks");

        expectFailure(() -> new ProjectionCursor("projection.work", "dataset.work", 0, "e1"),
                "zero cursor with event id blocks");
        expectFailure(() -> new ProjectionCursor("projection.work", "dataset.work", 1, ""),
                "positive cursor without event id blocks");
        expectFailure(() -> new CommitStamp("tx", "dataset.work", "r", 0, "e", 1, "prov"),
                "nonpositive record version blocks");
        expectFailure(() -> new CommitStamp("tx", "dataset.work", "r", 1, "e", 0, "prov"),
                "nonpositive event sequence blocks");

        check(first.provenanceRef().equals("prov://e1"), "commit provenance preserved");
        check(first.transactionId().equals("tx1"), "transaction identity preserved");
        check(first.sourceDatasetId().equals("dataset.work"), "source dataset identity preserved");
        check(secondResult.nextCursor().projectionDatasetId().equals("projection.work"), "projection identity preserved");

        System.out.println("PASS " + assertions + "/20");
    }

    private static CommitStamp commit(String tx, String record, long version, String event, long sequence) {
        return new CommitStamp(tx, "dataset.work", record, version, event, sequence, "prov://" + event);
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
