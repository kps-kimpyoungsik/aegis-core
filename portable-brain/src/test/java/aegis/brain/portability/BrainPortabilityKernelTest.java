package aegis.brain.portability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import aegis.brain.knowledge.KnowledgeKernel.KnowledgeRecord;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeScope;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeStatus;
import aegis.brain.memory.MemoryKernel.MemoryRecord;
import aegis.brain.memory.MemoryKernel.MemoryStatus;
import aegis.brain.memory.MemoryKernel.MemoryType;
import aegis.brain.portability.BrainPortabilityKernel.BrainSnapshot;
import aegis.brain.portability.BrainPortabilityKernel.ImportAssessment;
import aegis.brain.portability.BrainPortabilityKernel.ImportPlan;
import aegis.brain.skill.SkillAssetKernel.AssetManifest;
import aegis.brain.skill.SkillAssetKernel.AssetStatus;
import aegis.brain.skill.SkillAssetKernel.QualityTier;
import aegis.brain.skill.SkillAssetKernel.SkillManifest;

public final class BrainPortabilityKernelTest {
    private static int assertions;

    private BrainPortabilityKernelTest() {}

    public static void main(String[] args) {
        Instant now = Instant.parse("2026-09-05T00:00:00Z");
        MemoryRecord memory = new MemoryRecord(
                "m1", MemoryType.SEMANTIC, MemoryStatus.ACTIVE, "project-a", "content", "prov://m1",
                0.9, now.minusSeconds(1), now);
        KnowledgeRecord knowledge = new KnowledgeRecord(
                "k1", KnowledgeStatus.ACTIVE, KnowledgeScope.PROJECT, "subject", "claim",
                "doc://k1", "v1", "prov://k1", 0.9, 0.9,
                now.minusSeconds(1), now, Duration.ofDays(30), "");
        SkillManifest skill = new SkillManifest(
                "s1", "purpose", List.of("task"), List.of(), List.of("input"), List.of("output"), List.of(),
                "aegis.portable-brain", 4, QualityTier.VALIDATED, "1.0.0", "prov://s1", "skill://s1");
        AssetManifest asset = new AssetManifest(
                "a1", "capability.one", AssetStatus.VALIDATED, "1.0.0",
                "prov://a1", "quality://a1", true, "pkg://a1");

        BrainSnapshot snapshot = BrainPortabilityKernel.snapshot(
                "0.5.0", now, "prov://snapshot",
                List.of(memory), List.of(knowledge), List.of(skill), List.of(asset));
        check(snapshot.schemaVersion().equals(BrainPortabilityKernel.SCHEMA_VERSION), "canonical schema fixed");
        check(snapshot.packageVersion().equals("0.5.0"), "package version preserved");
        check(snapshot.provenanceRef().equals("prov://snapshot"), "snapshot provenance preserved");
        check(snapshot.memories().size() == 1, "memory exported");
        check(snapshot.knowledge().size() == 1, "knowledge exported");
        check(snapshot.skills().size() == 1, "skill exported");
        check(snapshot.assets().size() == 1, "asset exported");

        ImportAssessment eligible = BrainPortabilityKernel.assessImport(
                snapshot, new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", false));
        check(eligible.eligible(), "same-version snapshot eligible");
        check(eligible.reason().equals("IMPORT_ELIGIBLE"), "eligible reason stable");
        check(eligible.memoryCount() == 1, "memory count measured");
        check(eligible.knowledgeCount() == 1, "knowledge count measured");
        check(eligible.skillCount() == 1, "skill count measured");
        check(eligible.assetCount() == 1, "asset count measured");

        ImportAssessment schemaMismatch = BrainPortabilityKernel.assessImport(
                snapshot, new ImportPlan("aegis.portable-brain.snapshot.v2", "0.5.0", false));
        check(!schemaMismatch.eligible(), "schema mismatch blocked");
        check(schemaMismatch.reason().equals("SCHEMA_MISMATCH"), "schema mismatch reason stable");

        BrainSnapshot duplicateMemory = BrainPortabilityKernel.snapshot(
                "0.5.0", now, "prov://dup-memory",
                List.of(memory, memory), List.of(knowledge), List.of(skill), List.of(asset));
        check(BrainPortabilityKernel.assessImport(
                duplicateMemory, new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", false))
                .reason().equals("DUPLICATE_MEMORY_ID"), "duplicate memory blocked");

        BrainSnapshot duplicateKnowledge = BrainPortabilityKernel.snapshot(
                "0.5.0", now, "prov://dup-knowledge",
                List.of(memory), List.of(knowledge, knowledge), List.of(skill), List.of(asset));
        check(BrainPortabilityKernel.assessImport(
                duplicateKnowledge, new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", false))
                .reason().equals("DUPLICATE_KNOWLEDGE_ID"), "duplicate knowledge blocked");

        BrainSnapshot duplicateSkill = BrainPortabilityKernel.snapshot(
                "0.5.0", now, "prov://dup-skill",
                List.of(memory), List.of(knowledge), List.of(skill, skill), List.of(asset));
        check(BrainPortabilityKernel.assessImport(
                duplicateSkill, new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", false))
                .reason().equals("DUPLICATE_SKILL_ID"), "duplicate skill blocked");

        BrainSnapshot duplicateAsset = BrainPortabilityKernel.snapshot(
                "0.5.0", now, "prov://dup-asset",
                List.of(memory), List.of(knowledge), List.of(skill), List.of(asset, asset));
        check(BrainPortabilityKernel.assessImport(
                duplicateAsset, new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", false))
                .reason().equals("DUPLICATE_ASSET_ID"), "duplicate asset blocked");

        BrainSnapshot newer = BrainPortabilityKernel.snapshot(
                "0.6.0", now, "prov://newer", List.of(), List.of(), List.of(), List.of());
        ImportAssessment newerBlocked = BrainPortabilityKernel.assessImport(
                newer, new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", false));
        check(!newerBlocked.eligible(), "newer package blocked by default");
        check(newerBlocked.reason().equals("SOURCE_PACKAGE_NEWER_THAN_TARGET"), "newer reason stable");
        check(BrainPortabilityKernel.assessImport(
                newer, new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", true)).eligible(),
                "explicit newer-package override accepted");

        expectFailure(() -> BrainPortabilityKernel.snapshot(
                "", now, "prov", List.of(), List.of(), List.of(), List.of()),
                "blank package version blocked");
        expectFailure(() -> new ImportPlan("", "0.5.0", false), "blank schema blocked");
        expectFailure(() -> BrainPortabilityKernel.assessImport(
                BrainPortabilityKernel.snapshot("bad.version", now, "prov", List.of(), List.of(), List.of(), List.of()),
                new ImportPlan(BrainPortabilityKernel.SCHEMA_VERSION, "0.5.0", false)),
                "nonnumeric package version blocked");

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
