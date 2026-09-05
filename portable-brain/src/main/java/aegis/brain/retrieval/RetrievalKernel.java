package aegis.brain.retrieval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import aegis.brain.knowledge.KnowledgeKernel;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeRecord;
import aegis.brain.knowledge.KnowledgeKernel.KnowledgeScope;
import aegis.brain.memory.MemoryKernel;
import aegis.brain.memory.MemoryKernel.MemoryRecord;
import aegis.brain.memory.MemoryKernel.MemoryType;
import aegis.brain.skill.SkillAssetKernel;
import aegis.brain.skill.SkillAssetKernel.SkillManifest;

public final class RetrievalKernel {
    private RetrievalKernel() {}

    public enum SourceKind { MEMORY, KNOWLEDGE, SKILL }

    public record RetrievalPlan(
            String taskSignature,
            String memoryScope,
            MemoryType memoryType,
            KnowledgeScope knowledgeScope,
            int sourceLimit,
            int maxItems,
            int contextBudget,
            double minimumConfidence,
            double minimumApplicability,
            Instant now) {
        public RetrievalPlan {
            taskSignature = text(taskSignature, "taskSignature");
            memoryScope = text(memoryScope, "memoryScope");
            Objects.requireNonNull(memoryType, "memoryType");
            Objects.requireNonNull(knowledgeScope, "knowledgeScope");
            if (sourceLimit <= 0) throw new IllegalArgumentException("sourceLimit must be > 0");
            if (maxItems <= 0) throw new IllegalArgumentException("maxItems must be > 0");
            if (contextBudget <= 0) throw new IllegalArgumentException("contextBudget must be > 0");
            unit(minimumConfidence, "minimumConfidence");
            unit(minimumApplicability, "minimumApplicability");
            Objects.requireNonNull(now, "now");
        }
    }

    public record RetrievalItem(
            SourceKind kind,
            String id,
            String text,
            String provenanceRef,
            double score,
            int contextCost) {
        public RetrievalItem {
            Objects.requireNonNull(kind, "kind");
            id = text(id, "id");
            text = text(text, "text");
            provenanceRef = text(provenanceRef, "provenanceRef");
            unit(score, "score");
            if (contextCost <= 0) throw new IllegalArgumentException("contextCost must be > 0");
        }
    }

    public record RetrievalResult(
            List<RetrievalItem> items,
            int usedBudget,
            int remainingBudget,
            boolean truncated) {
        public RetrievalResult {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (usedBudget < 0) throw new IllegalArgumentException("usedBudget must be >= 0");
            if (remainingBudget < 0) throw new IllegalArgumentException("remainingBudget must be >= 0");
        }
    }

    public static RetrievalResult retrieve(
            List<MemoryRecord> memories,
            List<KnowledgeRecord> knowledge,
            List<SkillManifest> skills,
            RetrievalPlan plan) {
        Objects.requireNonNull(memories, "memories");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(skills, "skills");
        Objects.requireNonNull(plan, "plan");

        List<RetrievalItem> candidates = new ArrayList<>();

        List<MemoryRecord> selectedMemories = MemoryKernel.selectRelevant(
                memories,
                new MemoryKernel.RetrievalRequest(
                        plan.memoryScope(), plan.memoryType(), plan.sourceLimit(), plan.minimumConfidence()));
        selectedMemories.forEach(record -> candidates.add(new RetrievalItem(
                SourceKind.MEMORY,
                record.memoryId(),
                record.content(),
                record.provenanceRef(),
                record.confidence(),
                contextCost(record.content()))));

        List<KnowledgeRecord> selectedKnowledge = KnowledgeKernel.selectRelevant(
                knowledge,
                new KnowledgeKernel.RetrievalRequest(
                        plan.knowledgeScope(), plan.sourceLimit(), plan.minimumConfidence(),
                        plan.minimumApplicability(), plan.now()));
        selectedKnowledge.forEach(record -> candidates.add(new RetrievalItem(
                SourceKind.KNOWLEDGE,
                record.knowledgeId(),
                record.claim(),
                record.provenanceRef(),
                record.confidence() * record.applicability(),
                contextCost(record.claim()))));

        List<SkillManifest> selectedSkills = SkillAssetKernel.selectTriggered(
                skills, plan.taskSignature(), plan.sourceLimit());
        selectedSkills.forEach(skill -> candidates.add(new RetrievalItem(
                SourceKind.SKILL,
                skill.skillId(),
                skill.purpose(),
                skill.provenanceRef(),
                skillScore(skill),
                Math.max(1, skill.estimatedContextCost()))));

        candidates.sort(Comparator.comparingDouble(RetrievalItem::score).reversed()
                .thenComparingInt(RetrievalItem::contextCost)
                .thenComparing(item -> item.kind().ordinal())
                .thenComparing(RetrievalItem::id));

        List<RetrievalItem> selected = new ArrayList<>();
        int used = 0;
        boolean truncated = false;
        for (RetrievalItem item : candidates) {
            if (selected.size() >= plan.maxItems()) {
                truncated = true;
                break;
            }
            if (used + item.contextCost() > plan.contextBudget()) {
                truncated = true;
                continue;
            }
            selected.add(item);
            used += item.contextCost();
        }
        if (selected.size() < candidates.size()) truncated = true;
        return new RetrievalResult(selected, used, plan.contextBudget() - used, truncated);
    }

    private static double skillScore(SkillManifest skill) {
        double tier = (skill.qualityTier().ordinal() + 1.0) / SkillAssetKernel.QualityTier.values().length;
        return Math.min(1.0, 0.5 + (tier * 0.5));
    }

    private static int contextCost(String value) {
        return Math.max(1, (value.length() + 31) / 32);
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static void unit(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite within [0,1]");
        }
    }
}
