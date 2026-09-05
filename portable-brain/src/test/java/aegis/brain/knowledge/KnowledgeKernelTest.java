package aegis.brain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import aegis.brain.knowledge.KnowledgeKernel.KnowledgeRecord;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeScope;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeStatus;
import aegis.brain.knowledge.KnowledgeKernel.RetrievalRequest;

public final class KnowledgeKernelTest {
    private static int assertions;

    private KnowledgeKernelTest() {}

    public static void main(String[] args) {
        Instant t0 = Instant.parse("2026-09-05T00:00:00Z");
        KnowledgeRecord active = record(
                "k1", KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT,
                "storage", "canonical source owns meaning", "doc://constitution", "v1",
                "prov://k1", 0.95, 0.90, t0, t0, Duration.ofDays(30), "");

        check(active.retrievableAt(t0.plus(Duration.ofDays(1))), "active fresh knowledge retrievable");
        check(!active.retrievableAt(t0.plus(Duration.ofDays(31))), "stale knowledge excluded");

        KnowledgeRecord challenged = KnowledgeKernel.transitionStatus(
                active, KnowledgeStatus.CHALLENGED, t0.plusSeconds(1), "prov://challenge");
        check(challenged.status() == KnowledgeStatus.CHALLENGED, "active can be challenged");
        check(!challenged.retrievableAt(t0.plusSeconds(2)), "challenged excluded from active retrieval");

        KnowledgeRecord reactivated = KnowledgeKernel.transitionStatus(
                challenged, KnowledgeStatus.ACTIVE, t0.plusSeconds(2), "prov://reactivate");
        check(reactivated.status() == KnowledgeStatus.ACTIVE, "challenged may return active with provenance");

        KnowledgeRecord retracted = KnowledgeKernel.transitionStatus(
                active, KnowledgeStatus.RETRACTED, t0.plusSeconds(3), "prov://retract");
        check(retracted.status() == KnowledgeStatus.RETRACTED, "active can retract");
        check(!retracted.retrievableAt(t0.plusSeconds(4)), "retracted never retrieved");
        expectFailure(() -> KnowledgeKernel.transitionStatus(
                retracted, KnowledgeStatus.ACTIVE, t0.plusSeconds(5), "prov://bad"),
                "retracted cannot resurrect");

        KnowledgeRecord replacement = record(
                "k2", KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT,
                "storage", "canonical source owns meaning and lineage", "doc://constitution", "v2",
                "prov://k2", 0.97, 0.92, t0, t0.plusSeconds(10), Duration.ofDays(30), "");
        KnowledgeRecord linked = KnowledgeKernel.supersede(
                replacement, active, t0.plusSeconds(11), "prov://supersede");
        check(linked.supersedesId().equals("k1"), "supersede preserves lineage identity");
        check(linked.status() == KnowledgeStatus.ACTIVE, "replacement remains active");

        KnowledgeRecord otherScope = record(
                "k3", KnowledgeStatus.ACTIVE, KnowledgeScope.GLOBAL,
                "storage", "other", "doc://other", "v1", "prov://k3",
                0.99, 0.99, t0, t0, Duration.ofDays(30), "");
        expectFailure(() -> KnowledgeKernel.supersede(
                otherScope, active, t0.plusSeconds(12), "prov://bad-scope"),
                "cross-scope supersede blocked");
        expectFailure(() -> KnowledgeKernel.supersede(
                replacement, retracted, t0.plusSeconds(12), "prov://bad-resurrect"),
                "supersede cannot resurrect retracted source");

        KnowledgeRecord lowConfidence = record(
                "k4", KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT,
                "storage", "low confidence", "doc://low", "v1", "prov://k4",
                0.50, 0.90, t0, t0, Duration.ofDays(30), "");
        KnowledgeRecord lowApplicability = record(
                "k5", KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT,
                "storage", "low applicability", "doc://low-app", "v1", "prov://k5",
                0.99, 0.20, t0, t0, Duration.ofDays(30), "");
        List<KnowledgeRecord> selected = KnowledgeKernel.selectRelevant(
                List.of(lowConfidence, lowApplicability, active, linked, otherScope, retracted),
                new RetrievalRequest(KnowledgeScope.PROJECT, 2, 0.80, 0.70, t0.plusSeconds(20)));
        check(selected.size() == 2, "retrieval obeys explicit limit");
        check(selected.get(0).knowledgeId().equals("k2"), "retrieval ranks confidence then applicability");
        check(selected.stream().noneMatch(item -> item.scope() == KnowledgeScope.GLOBAL),
                "retrieval is scope isolated");
        check(selected.stream().noneMatch(item -> item.status() == KnowledgeStatus.RETRACTED),
                "retrieval excludes retracted knowledge");
        check(selected.stream().noneMatch(item -> item.knowledgeId().equals("k4")),
                "retrieval filters low confidence");
        check(selected.stream().noneMatch(item -> item.knowledgeId().equals("k5")),
                "retrieval filters low applicability");

        check(KnowledgeKernel.promotionEligible(active, t0.plusSeconds(20)),
                "fresh high-quality knowledge promotion eligible");
        check(!KnowledgeKernel.promotionEligible(lowConfidence, t0.plusSeconds(20)),
                "low confidence blocks promotion");
        check(!KnowledgeKernel.promotionEligible(active, t0.plus(Duration.ofDays(31))),
                "staleness blocks promotion");

        expectFailure(() -> record(
                "k6", KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT,
                "storage", "bad freshness", "doc://x", "v1", "prov://k6",
                0.9, 0.9, t0, t0, Duration.ZERO, ""),
                "nonpositive freshness blocked");

        System.out.println("PASS " + assertions + "/20");
    }

    private static KnowledgeRecord record(
            String id,
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
        return new KnowledgeRecord(
                id, status, scope, subject, claim, sourceRef, sourceVersion, provenanceRef,
                confidence, applicability, observedAt, verifiedAt, freshnessTtl, supersedesId);
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
