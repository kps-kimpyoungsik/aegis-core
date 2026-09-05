package aegis.brain.skill;

import java.util.List;

import aegis.brain.skill.SkillAssetKernel.AssetManifest;
import aegis.brain.skill.SkillAssetKernel.AssetStatus;
import aegis.brain.skill.SkillAssetKernel.QualityTier;
import aegis.brain.skill.SkillAssetKernel.SkillManifest;

public final class SkillAssetKernelTest {
    private static int assertions;

    private SkillAssetKernelTest() {}

    public static void main(String[] args) {
        SkillManifest canonical = skill(
                "skill.storage.recovery", QualityTier.CANONICAL, 80,
                List.of("storage recovery", "restore"), List.of("destructive production"));
        SkillManifest validated = skill(
                "skill.storage.basic", QualityTier.VALIDATED, 20,
                List.of("storage"), List.of("recovery"));

        check(canonical.matches("storage recovery task"), "trigger match");
        check(!canonical.matches("destructive production storage recovery"), "exclusion wins");
        check(validated.matches("storage task"), "generic skill matches");
        check(!validated.matches("storage recovery task"), "skill exclusion respected");

        check(SkillAssetKernel.index(List.of(canonical)).get(0).skillId().equals(canonical.skillId()),
                "index preserves identity");
        check(SkillAssetKernel.index(List.of(canonical)).get(0).estimatedContextCost() == 80,
                "index exposes routing cost");

        List<SkillManifest> selected = SkillAssetKernel.selectTriggered(
                List.of(validated, canonical), "storage recovery task", 1);
        check(selected.size() == 1, "selection bounded");
        check(selected.get(0).skillId().equals(canonical.skillId()), "higher tier selected");

        expectFailure(() -> SkillAssetKernel.selectTriggered(List.of(canonical), "storage", 0),
                "nonpositive limit blocked");
        expectFailure(() -> new SkillManifest(
                "bad", "bad", List.of(""), List.of(), List.of(), List.of(), List.of(),
                "aegis.portable-brain", 1, QualityTier.CANDIDATE, "1.0.0", "prov", "detail"),
                "blank trigger blocked");

        AssetManifest candidate = asset("a1", AssetStatus.CANDIDATE);
        AssetManifest validatedAsset = SkillAssetKernel.transitionAsset(candidate, AssetStatus.VALIDATED);
        check(validatedAsset.status() == AssetStatus.VALIDATED, "candidate validates");
        check(SkillAssetKernel.promotionCandidate(validatedAsset), "validated quality asset promotion candidate");

        AssetManifest promoted = SkillAssetKernel.transitionAsset(validatedAsset, AssetStatus.PROMOTED);
        check(promoted.status() == AssetStatus.PROMOTED, "validated asset promotes");
        AssetManifest deprecated = SkillAssetKernel.transitionAsset(promoted, AssetStatus.DEPRECATED);
        check(deprecated.status() == AssetStatus.DEPRECATED, "promoted asset deprecates");
        expectFailure(() -> SkillAssetKernel.transitionAsset(deprecated, AssetStatus.PROMOTED),
                "deprecated asset cannot resurrect");

        AssetManifest retracted = SkillAssetKernel.transitionAsset(candidate, AssetStatus.RETRACTED);
        check(retracted.status() == AssetStatus.RETRACTED, "candidate may retract");
        expectFailure(() -> SkillAssetKernel.transitionAsset(retracted, AssetStatus.VALIDATED),
                "retracted asset cannot resurrect");

        check(!SkillAssetKernel.promotionCandidate(candidate), "candidate cannot promote");
        check(validatedAsset.provenanceRef().equals("prov://asset"), "asset provenance preserved");
        check(validatedAsset.qualityHistoryRef().equals("quality://asset"), "quality history preserved");

        System.out.println("PASS " + assertions + "/20");
    }

    private static SkillManifest skill(
            String id, QualityTier tier, int cost, List<String> triggers, List<String> exclusions) {
        return new SkillManifest(
                id, "purpose", triggers, exclusions, List.of("task"), List.of("result"), List.of(),
                "aegis.portable-brain", cost, tier, "1.0.0", "prov://skill", "skill://detail");
    }

    private static AssetManifest asset(String id, AssetStatus status) {
        return new AssetManifest(
                id, "capability.storage", status, "1.0.0", "prov://asset", "quality://asset", true,
                "pkg://asset");
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
