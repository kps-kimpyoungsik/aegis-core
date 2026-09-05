package aegis.brain.memory;

import java.time.Instant;
import java.util.List;

public final class MemoryKernelTest {
    private static int passed = 0;

    private static void check(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED:" + name);
        passed++;
    }

    public static void main(String[] args) {
        Instant t0 = Instant.parse("2026-09-05T00:00:00Z");
        Instant t1 = t0.plusSeconds(60);
        var active = new MemoryKernel.MemoryRecord(
                "m1", MemoryKernel.MemoryType.SEMANTIC, MemoryKernel.MemoryStatus.ACTIVE,
                "project:aegis", "validated lesson", "prov:1", 0.95, t0, t1);
        var challenged = new MemoryKernel.MemoryRecord(
                "m2", MemoryKernel.MemoryType.SEMANTIC, MemoryKernel.MemoryStatus.CHALLENGED,
                "project:aegis", "challenged lesson", "prov:2", 0.90, t0, t0);
        var retracted = new MemoryKernel.MemoryRecord(
                "m3", MemoryKernel.MemoryType.SEMANTIC, MemoryKernel.MemoryStatus.RETRACTED,
                "project:aegis", "retracted lesson", "prov:3", 1.0, t0, t1);
        var otherScope = new MemoryKernel.MemoryRecord(
                "m4", MemoryKernel.MemoryType.SEMANTIC, MemoryKernel.MemoryStatus.ACTIVE,
                "project:other", "other scope", "prov:4", 0.99, t0, t1);
        var lowConfidence = new MemoryKernel.MemoryRecord(
                "m5", MemoryKernel.MemoryType.SEMANTIC, MemoryKernel.MemoryStatus.ACTIVE,
                "project:aegis", "weak lesson", "prov:5", 0.50, t0, t1);

        var request = new MemoryKernel.RetrievalRequest("project:aegis", MemoryKernel.MemoryType.SEMANTIC, 2, 0.8);
        List<MemoryKernel.MemoryRecord> selected = MemoryKernel.selectRelevant(
                List.of(challenged, retracted, otherScope, lowConfidence, active), request);
        check(selected.size() == 2, "minimum relevant context limit");
        check(selected.get(0).memoryId().equals("m1"), "confidence ordering");
        check(selected.get(1).memoryId().equals("m2"), "challenged remains retrievable");
        check(selected.stream().noneMatch(r -> r.status() == MemoryKernel.MemoryStatus.RETRACTED), "retracted excluded");
        check(selected.stream().allMatch(r -> r.scope().equals("project:aegis")), "scope isolation");
        check(selected.stream().allMatch(r -> r.confidence() >= 0.8), "confidence threshold");

        var challengedState = MemoryKernel.transitionStatus(active, MemoryKernel.MemoryStatus.CHALLENGED, t1.plusSeconds(1), "prov:6");
        check(challengedState.status() == MemoryKernel.MemoryStatus.CHALLENGED, "active to challenged");
        check(challengedState.provenanceRef().equals("prov:6"), "transition provenance replaced");

        var retractedState = MemoryKernel.transitionStatus(challengedState, MemoryKernel.MemoryStatus.RETRACTED, t1.plusSeconds(2), "prov:7");
        check(retractedState.status() == MemoryKernel.MemoryStatus.RETRACTED, "challenged to retracted");
        check(!retractedState.retrievable(), "retracted not retrievable");

        boolean terminalBlocked = false;
        try {
            MemoryKernel.transitionStatus(retractedState, MemoryKernel.MemoryStatus.ACTIVE, t1.plusSeconds(3), "prov:8");
        } catch (IllegalStateException expected) {
            terminalBlocked = true;
        }
        check(terminalBlocked, "retracted resurrection blocked");

        check(MemoryKernel.promotionEligible(active), "high confidence semantic eligible");
        var working = new MemoryKernel.MemoryRecord(
                "m6", MemoryKernel.MemoryType.WORKING, MemoryKernel.MemoryStatus.ACTIVE,
                "project:aegis", "temporary", "prov:6", 1.0, t0, t0);
        check(!MemoryKernel.promotionEligible(working), "working memory not directly promotable");
        check(!MemoryKernel.promotionEligible(challenged), "challenged not promotable");

        boolean invalidConfidenceBlocked = false;
        try {
            new MemoryKernel.MemoryRecord(
                    "bad", MemoryKernel.MemoryType.SEMANTIC, MemoryKernel.MemoryStatus.ACTIVE,
                    "project:aegis", "bad", "prov:x", 1.1, t0, t0);
        } catch (IllegalArgumentException expected) {
            invalidConfidenceBlocked = true;
        }
        check(invalidConfidenceBlocked, "invalid confidence blocked");

        boolean blankProvenanceBlocked = false;
        try {
            new MemoryKernel.MemoryRecord(
                    "bad2", MemoryKernel.MemoryType.SEMANTIC, MemoryKernel.MemoryStatus.ACTIVE,
                    "project:aegis", "bad", " ", 0.9, t0, t0);
        } catch (IllegalArgumentException expected) {
            blankProvenanceBlocked = true;
        }
        check(blankProvenanceBlocked, "blank provenance blocked");

        boolean invalidRequestBlocked = false;
        try {
            new MemoryKernel.RetrievalRequest("project:aegis", MemoryKernel.MemoryType.SEMANTIC, 0, 0.8);
        } catch (IllegalArgumentException expected) {
            invalidRequestBlocked = true;
        }
        check(invalidRequestBlocked, "invalid retrieval limit blocked");

        boolean timeRegressionBlocked = false;
        try {
            MemoryKernel.transitionStatus(active, MemoryKernel.MemoryStatus.CHALLENGED, t0, "prov:9");
        } catch (IllegalArgumentException expected) {
            timeRegressionBlocked = true;
        }
        check(timeRegressionBlocked, "time regression blocked");

        System.out.println("PASS " + passed + "/18");
    }
}
