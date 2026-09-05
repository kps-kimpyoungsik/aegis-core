package aegis.brain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import aegis.brain.knowledge.KnowledgeKernel;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeRecord;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeScope;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeStatus;
import aegis.brain.memory.MemoryKernel;
import aegis.brain.memory.MemoryKernel.MemoryRecord;
import aegis.brain.memory.MemoryKernel.MemoryStatus;
import aegis.brain.memory.MemoryKernel.MemoryType;
import aegis.brain.portability.BrainPortabilityKernel;
import aegis.brain.portability.BrainPortabilityKernel.BrainSnapshot;
import aegis.brain.portability.BrainPortabilityKernel.ImportPlan;
import aegis.brain.retrieval.RetrievalKernel;
import aegis.brain.retrieval.RetrievalKernel.RetrievalPlan;
import aegis.brain.retrieval.RetrievalKernel.RetrievalResult;
import aegis.brain.skill.SkillAssetKernel;
import aegis.brain.skill.SkillAssetKernel.AssetManifest;
import aegis.brain.skill.SkillAssetKernel.AssetStatus;
import aegis.brain.skill.SkillAssetKernel.QualityTier;
import aegis.brain.skill.SkillAssetKernel.SkillManifest;

public final class PortableBrainCompositeTest {
    private static int assertions;

    private PortableBrainCompositeTest() {}

    public static void main(String[] args) {
        Instant now = Instant.parse("2026-09-05T00:00:00Z");

        MemoryRecord memory = new MemoryRecord(
                "m-composite", MemoryType.SEMANTIC, MemoryStatus.ACTIVE, "project-a",
                "portable brain recovery evidence", "prov://memory", 0.95,
                now.minusSeconds(10), now);
        KnowledgeRecord knowledge = new KnowledgeRecord(
                "k-composite", KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT,
                "portable brain", "portable brain recovery requires provenance",
                "doc://knowledge", "v1", "prov://knowledge", 0.95, 0.90,
                now.minusSeconds(10), now, Duration.ofDays(30), "");
        SkillManifest skill = new SkillManifest(
                "skill.composite", "portable brain recovery runbook",
                List.of("portable brain recovery"), List.of("destructive production"),
                List.of("task"), List.of("result"), List.of(),
                "aegis.portable-brain", 2, QualityTier.CANONICAL,
                "1.0.0", "prov://skill", "skill://composite");
        AssetManifest asset = new AssetManifest(
                "asset.composite", "capability.portable-brain", AssetStatus.VALIDATED,
                "1.0.0", "prov://asset", "quality://asset", true, "pkg://asset");

        check(MemoryKernel.promotionEligible(memory), "memory remains promotion eligible");
        check(KnowledgeKernel.promotionEligible(knowledge, now), "knowledge remains promotion eligible");
        check(SkillAssetKernel.promotionCandidate(asset), "asset remains promotion candidate only");
        check(skill.matches("portable brain recovery"), "skill routing remains active");
        check(!skill.matches("destructive production portable brain recovery"),
                "skill exclusion remains stronger than trigger");

        RetrievalPlan retrievalPlan = new RetrievalPlan(
                "portable brain recovery", "project-a", MemoryType.SEMANTIC,
                KnowledgeScope.PROJECT, 5, 3, 5, 0.80, 0.70, now);
        RetrievalResult retrieval = RetrievalKernel.retrieve(
                List.of(memory), List.of(knowledge), List.of(skill), retrievalPlan);
        check(retrieval.items().size() == 3, "memory knowledge skill compose into bounded context");
        check(retrieval.usedBudget() <= retrievalPlan.contextBudget(), "retrieval never exceeds budget");
        check(retrieval.items().stream().allMatch(item -> !item.provenanceRef().isBlank()),
                "retrieval preserves provenance across all source kinds");

        BrainSnapshot snapshot = BrainPortabilityKernel.snapshot(
                "0.6.0", now, "prov://snapshot",
                List.of(memory), List.of(knowledge), List.of(skill), List.of(asset));
        check(snapshot.memories().equals(List.of(memory)), "snapshot preserves memory identity");
        check(snapshot.knowledge().equals(List.of(knowledge)), "snapshot preserves knowledge identity");
        check(snapshot.skills().equals(List.of(skill)), "snapshot preserves skill identity");
        check(snapshot.assets().equals(List.of(asset)), "snapshot preserves asset identity");
        check(BrainPortabilityKernel.assessImport(
                snapshot,
                new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.6.0", false)).eligible(),
                "same-version composite snapshot import eligible");

        MemoryRecord retractedMemory = MemoryKernel.transitionStatus(
                memory, MemoryStatus.RETRACTED, now.plusSeconds(1), "prov://memory-retract");
        check(!retractedMemory.retrievable(), "retracted memory remains nonretrievable");
        expectFailure(() -> MemoryKernel.transitionStatus(
                retractedMemory, MemoryStatus.ACTIVE, now.plusSeconds(2), "prov://memory-resurrect"),
                "retracted memory cannot resurrect");

        KnowledgeRecord retractedKnowledge = KnowledgeKernel.transitionStatus(
                knowledge, KnowledgeStatus.RETRACTED, now.plusSeconds(1), "prov://knowledge-retract");
        check(!retractedKnowledge.retrievableAt(now.plusSeconds(2)),
                "retracted knowledge remains nonretrievable");
        expectFailure(() -> KnowledgeKernel.transitionStatus(
                retractedKnowledge, KnowledgeStatus.ACTIVE, now.plusSeconds(2), "prov://knowledge-resurrect"),
                "retracted knowledge cannot resurrect");

        AssetManifest retractedAsset = SkillAssetKernel.transitionAsset(asset, AssetStatus.RETRACTED);
        check(retractedAsset.status() == AssetStatus.RETRACTED, "asset retraction preserved");
        expectFailure(() -> SkillAssetKernel.transitionAsset(retractedAsset, AssetStatus.VALIDATED),
                "retracted asset cannot resurrect");

        RetrievalResult failClosedRetrieval = RetrievalKernel.retrieve(
                List.of(retractedMemory), List.of(retractedKnowledge), List.of(skill), retrievalPlan);
        check(failClosedRetrieval.items().stream().noneMatch(item -> item.id().equals("m-composite")),
                "retracted memory excluded from composite retrieval");
        check(failClosedRetrieval.items().stream().noneMatch(item -> item.id().equals("k-composite")),
                "retracted knowledge excluded from composite retrieval");

        BrainSnapshot duplicateSnapshot = BrainPortabilityKernel.snapshot(
                "0.6.0", now, "prov://duplicate",
                List.of(memory, memory), List.of(knowledge), List.of(skill), List.of(asset));
        check(!BrainPortabilityKernel.assessImport(
                duplicateSnapshot,
                new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.6.0", false)).eligible(),
                "duplicate canonical identity blocks composite import");

        check(BrainPortabilityKernel.SCHEMA_VERSION.startsWith("aegis.portable-brain."),
                "portable schema remains technology independent");
        check(skill.authority().equals("aegis.portable-brain"), "skill semantic authority remains portable brain");
        check(!asset.packageRef().isBlank(), "asset package reference remains explicit");

        System.out.println("PASS " + assertions + "/25");
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
