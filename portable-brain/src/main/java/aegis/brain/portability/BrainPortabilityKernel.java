package aegis.brain.portability;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import aegis.brain.knowledge.KnowledgeKernel.KnowledgeRecord;
import aegis.brain.memory.MemoryKernel.MemoryRecord;
import aegis.brain.skill.SkillAssetKernel.AssetManifest;
import aegis.brain.skill.SkillAssetKernel.SkillManifest;

public final class BrainPortabilityKernel {
    public static final String SCHEMA_VERSION = "aegis.portable-brain.snapshot.v1";

    private BrainPortabilityKernel() {}

    public record BrainSnapshot(
            String schemaVersion,
            String packageVersion,
            Instant exportedAt,
            String provenanceRef,
            List<MemoryRecord> memories,
            List<KnowledgeRecord> knowledge,
            List<SkillManifest> skills,
            List<AssetManifest> assets) {
        public BrainSnapshot {
            schemaVersion = requireText(schemaVersion, "schemaVersion");
            packageVersion = requireText(packageVersion, "packageVersion");
            Objects.requireNonNull(exportedAt, "exportedAt");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
            memories = immutableNonNull(memories, "memories");
            knowledge = immutableNonNull(knowledge, "knowledge");
            skills = immutableNonNull(skills, "skills");
            assets = immutableNonNull(assets, "assets");
        }
    }

    public record ImportPlan(
            String expectedSchemaVersion,
            String targetPackageVersion,
            boolean allowNewerPackageVersion) {
        public ImportPlan {
            expectedSchemaVersion = requireText(expectedSchemaVersion, "expectedSchemaVersion");
            targetPackageVersion = requireText(targetPackageVersion, "targetPackageVersion");
        }
    }

    public record ImportAssessment(
            boolean eligible,
            String reason,
            int memoryCount,
            int knowledgeCount,
            int skillCount,
            int assetCount) {
        public ImportAssessment {
            reason = requireText(reason, "reason");
            if (memoryCount < 0 || knowledgeCount < 0 || skillCount < 0 || assetCount < 0) {
                throw new IllegalArgumentException("counts must be >= 0");
            }
        }
    }

    public static BrainSnapshot snapshot(
            String packageVersion,
            Instant exportedAt,
            String provenanceRef,
            List<MemoryRecord> memories,
            List<KnowledgeRecord> knowledge,
            List<SkillManifest> skills,
            List<AssetManifest> assets) {
        return new BrainSnapshot(
                SCHEMA_VERSION, packageVersion, exportedAt, provenanceRef,
                memories, knowledge, skills, assets);
    }

    public static ImportAssessment assessImport(BrainSnapshot snapshot, ImportPlan plan) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(plan, "plan");

        if (!snapshot.schemaVersion().equals(plan.expectedSchemaVersion())) {
            return assessment(false, "SCHEMA_MISMATCH", snapshot);
        }
        if (!uniqueMemoryIds(snapshot.memories())) {
            return assessment(false, "DUPLICATE_MEMORY_ID", snapshot);
        }
        if (!uniqueKnowledgeIds(snapshot.knowledge())) {
            return assessment(false, "DUPLICATE_KNOWLEDGE_ID", snapshot);
        }
        if (!uniqueSkillIds(snapshot.skills())) {
            return assessment(false, "DUPLICATE_SKILL_ID", snapshot);
        }
        if (!uniqueAssetIds(snapshot.assets())) {
            return assessment(false, "DUPLICATE_ASSET_ID", snapshot);
        }
        if (!plan.allowNewerPackageVersion()
                && compareVersion(snapshot.packageVersion(), plan.targetPackageVersion()) > 0) {
            return assessment(false, "SOURCE_PACKAGE_NEWER_THAN_TARGET", snapshot);
        }
        return assessment(true, "IMPORT_ELIGIBLE", snapshot);
    }

    private static ImportAssessment assessment(boolean eligible, String reason, BrainSnapshot snapshot) {
        return new ImportAssessment(
                eligible,
                reason,
                snapshot.memories().size(),
                snapshot.knowledge().size(),
                snapshot.skills().size(),
                snapshot.assets().size());
    }

    private static boolean uniqueMemoryIds(List<MemoryRecord> records) {
        Set<String> ids = new HashSet<>();
        return records.stream().allMatch(record -> ids.add(record.memoryId()));
    }

    private static boolean uniqueKnowledgeIds(List<KnowledgeRecord> records) {
        Set<String> ids = new HashSet<>();
        return records.stream().allMatch(record -> ids.add(record.knowledgeId()));
    }

    private static boolean uniqueSkillIds(List<SkillManifest> records) {
        Set<String> ids = new HashSet<>();
        return records.stream().allMatch(record -> ids.add(record.skillId()));
    }

    private static boolean uniqueAssetIds(List<AssetManifest> records) {
        Set<String> ids = new HashSet<>();
        return records.stream().allMatch(record -> ids.add(record.assetId()));
    }

    private static int compareVersion(String left, String right) {
        String[] leftParts = requireText(left, "leftVersion").split("\\.");
        String[] rightParts = requireText(right, "rightVersion").split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int l = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int r = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            int comparison = Integer.compare(l, r);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static int parseVersionPart(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new IllegalArgumentException("version part must be >= 0");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("version must contain numeric dot-separated parts", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static <T> List<T> immutableNonNull(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        List<T> copy = List.copyOf(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " contains null");
        }
        return copy;
    }
}
