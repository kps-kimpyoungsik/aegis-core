package aegis.brain.retrieval;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import aegis.brain.knowledge.KnowledgeKernel.KnowledgeRecord;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeScope;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeStatus;
import aegis.brain.memory.MemoryKernel.MemoryRecord;
import aegis.brain.memory.MemoryKernel.MemoryStatus;
import aegis.brain.memory.MemoryKernel.MemoryType;
import aegis.brain.retrieval.RetrievalKernel.RetrievalPlan;
import aegis.brain.retrieval.RetrievalKernel.RetrievalResult;
import aegis.brain.retrieval.RetrievalKernel.SourceKind;
import aegis.brain.skill.SkillAssetKernel.QualityTier;
import aegis.brain.skill.SkillAssetKernel.SkillManifest;

public final class RetrievalKernelTest {
    private static int assertions;

    private RetrievalKernelTest() {}

    public static void main(String[] args) {
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        MemoryRecord usefulMemory = memory(
                "m1", "project-a", "storage rollback evidence", 0.95, MemoryStatus.ACTIVE, now);
        MemoryRecord wrongScope = memory(
                "m2", "project-b", "storage rollback foreign", 0.99, MemoryStatus.ACTIVE, now);
        MemoryRecord lowConfidence = memory(
                "m3", "project-a", "storage rollback uncertain", 0.40, MemoryStatus.ACTIVE, now);

        KnowledgeRecord usefulKnowledge = knowledge(
                "k1", "storage", "storage rollback requires evidence", 0.90, 0.90,
                now, Duration.ofDays(30));
        KnowledgeRecord staleKnowledge = knowledge(
                "k2", "storage", "storage rollback stale", 0.99, 0.99,
                now.minus(Duration.ofDays(60)), Duration.ofDays(1));

        SkillManifest canonicalSkill = skill(
                "s1", "storage rollback runbook", List.of("storage rollback"), List.of(), 2,
                QualityTier.CANONICAL);
        SkillManifest excludedSkill = skill(
                "s2", "dangerous storage rollback", List.of("storage"), List.of("rollback"), 1,
                QualityTier.CANONICAL);

        RetrievalPlan plan = new RetrievalPlan(
                "storage rollback", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                5, 3, 4, 0.80, 0.70, now);
        RetrievalResult result = RetrievalKernel.retrieve(
                List.of(usefulMemory, wrongScope, lowConfidence),
                List.of(usefulKnowledge, staleKnowledge),
                List.of(canonicalSkill, excludedSkill), plan);

        check(result.items().size() == 3, "minimum bounded context selected");
        check(result.items().get(0).kind() == SourceKind.SKILL, "canonical matching skill ranked first");
        check(result.items().get(1).id().equals("m1"), "eligible memory selected");
        check(result.items().get(2).id().equals("k1"), "eligible knowledge selected");
        check(result.usedBudget() == 4, "context budget accounted");
        check(result.remainingBudget() == 0, "remaining budget accounted");
        check(!result.items().stream().anyMatch(item -> item.id().equals("m2")), "memory scope isolated");
        check(!result.items().stream().anyMatch(item -> item.id().equals("m3")), "low confidence memory excluded");
        check(!result.items().stream().anyMatch(item -> item.id().equals("k2")), "stale knowledge excluded");
        check(!result.items().stream().anyMatch(item -> item.id().equals("s2")), "skill exclusion respected");

        RetrievalPlan itemLimited = new RetrievalPlan(
                "storage rollback", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                5, 2, 10, 0.80, 0.70, now);
        RetrievalResult limited = RetrievalKernel.retrieve(
                List.of(usefulMemory), List.of(usefulKnowledge), List.of(canonicalSkill), itemLimited);
        check(limited.items().size() == 2, "max item limit enforced");
        check(limited.truncated(), "item limit reports truncation");

        RetrievalPlan tightBudget = new RetrievalPlan(
                "storage rollback", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                5, 5, 1, 0.80, 0.70, now);
        RetrievalResult tight = RetrievalKernel.retrieve(
                List.of(usefulMemory), List.of(usefulKnowledge), List.of(canonicalSkill), tightBudget);
        check(tight.items().size() == 1, "oversized candidate skipped under budget");
        check(tight.items().get(0).id().equals("m1"), "lower-cost eligible item fills budget");
        check(tight.usedBudget() <= 1, "budget never exceeded");
        check(tight.truncated(), "budget pressure reports truncation");

        RetrievalResult empty = RetrievalKernel.retrieve(List.of(), List.of(), List.of(), plan);
        check(empty.items().isEmpty(), "empty candidate set safe");
        check(empty.usedBudget() == 0, "empty retrieval costs zero");
        check(empty.remainingBudget() == plan.contextBudget(), "empty retrieval preserves full budget");
        check(!empty.truncated(), "empty retrieval not truncated");

        expectFailure(() -> new RetrievalPlan(
                "storage", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                0, 1, 1, 0.8, 0.7, now), "nonpositive source limit blocked");
        expectFailure(() -> new RetrievalPlan(
                "storage", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                1, 0, 1, 0.8, 0.7, now), "nonpositive item limit blocked");
        expectFailure(() -> new RetrievalPlan(
                "storage", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                1, 1, 0, 0.8, 0.7, now), "nonpositive context budget blocked");
        expectFailure(() -> new RetrievalPlan(
                "storage", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                1, 1, 1, Double.NaN, 0.7, now), "NaN confidence blocked");
        expectFailure(() -> new RetrievalPlan(
                "storage", "project-a", MemoryType.SEMANTIC, KnowledgeScope.PROJECT,
                1, 1, 1, 0.8, Double.POSITIVE_INFINITY, now), "infinite applicability blocked");

        System.out.println("PASS " + assertions + "/25");
    }

    private static MemoryRecord memory(
            String id, String scope, String content, double confidence, MemoryStatus status, Instant now) {
        return new MemoryRecord(
                id, MemoryType.SEMANTIC, status, scope, content, "prov://" + id,
                confidence, now.minusSeconds(10), now);
    }

    private static KnowledgeRecord knowledge(
            String id, String subject, String claim, double confidence, double applicability,
            Instant verifiedAt, Duration ttl) {
        return new KnowledgeRecord(
                id, KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT, subject, claim,
                "doc://" + id, "v1", "prov://" + id, confidence, applicability,
                verifiedAt.minusSeconds(10), verifiedAt, ttl, "");
    }

    private static SkillManifest skill(
            String id, String purpose, List<String> triggers, List<String> exclusions,
            int cost, QualityTier tier) {
        return new SkillManifest(
                id, purpose, triggers, exclusions, List.of("task"), List.of("result"), List.of(),
                "aegis.portable-brain", cost, tier, "1.0.0", "prov://" + id, "skill://" + id);
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
