package aegis.brain.skill;

import java.util.List;
import java.util.Objects;

public final class SkillAssetKernel {
    private SkillAssetKernel() {}

    public enum QualityTier { CANDIDATE, VALIDATED, TRUSTED, CANONICAL }
    public enum AssetStatus { CANDIDATE, VALIDATED, PROMOTED, DEPRECATED, RETRACTED }

    public record SkillManifest(
            String skillId,
            String purpose,
            List<String> triggers,
            List<String> exclusions,
            List<String> inputs,
            List<String> outputs,
            List<String> requiredTools,
            String authority,
            int estimatedContextCost,
            QualityTier qualityTier,
            String version,
            String provenanceRef,
            String detailRef) {
        public SkillManifest {
            skillId = text(skillId, "skillId");
            purpose = text(purpose, "purpose");
            triggers = copyNonBlank(triggers, "triggers");
            exclusions = copyNonBlank(exclusions, "exclusions");
            inputs = copyNonBlank(inputs, "inputs");
            outputs = copyNonBlank(outputs, "outputs");
            requiredTools = copyNonBlank(requiredTools, "requiredTools");
            authority = text(authority, "authority");
            if (estimatedContextCost < 0) throw new IllegalArgumentException("estimatedContextCost must be >= 0");
            Objects.requireNonNull(qualityTier, "qualityTier");
            version = text(version, "version");
            provenanceRef = text(provenanceRef, "provenanceRef");
            detailRef = text(detailRef, "detailRef");
        }

        public boolean matches(String taskSignature) {
            String normalized = text(taskSignature, "taskSignature").toLowerCase();
            boolean excluded = exclusions.stream().map(String::toLowerCase).anyMatch(normalized::contains);
            if (excluded) return false;
            return triggers.stream().map(String::toLowerCase).anyMatch(normalized::contains);
        }
    }

    public record SkillIndexEntry(
            String skillId,
            String purpose,
            List<String> triggers,
            QualityTier qualityTier,
            String version,
            int estimatedContextCost) {}

    public interface SkillRegistryPort {
        SkillManifest save(SkillManifest manifest);
        List<SkillManifest> findAll();
    }

    public record AssetManifest(
            String assetId,
            String capabilityId,
            AssetStatus status,
            String version,
            String provenanceRef,
            String qualityHistoryRef,
            boolean deterministic,
            String packageRef) {
        public AssetManifest {
            assetId = text(assetId, "assetId");
            capabilityId = text(capabilityId, "capabilityId");
            Objects.requireNonNull(status, "status");
            version = text(version, "version");
            provenanceRef = text(provenanceRef, "provenanceRef");
            qualityHistoryRef = text(qualityHistoryRef, "qualityHistoryRef");
            packageRef = text(packageRef, "packageRef");
        }
    }

    public interface AssetRegistryPort {
        AssetManifest save(AssetManifest manifest);
        List<AssetManifest> findByCapability(String capabilityId);
    }

    public static List<SkillIndexEntry> index(List<SkillManifest> manifests) {
        Objects.requireNonNull(manifests, "manifests");
        return manifests.stream()
                .filter(Objects::nonNull)
                .map(skill -> new SkillIndexEntry(
                        skill.skillId(), skill.purpose(), skill.triggers(), skill.qualityTier(),
                        skill.version(), skill.estimatedContextCost()))
                .toList();
    }

    public static List<SkillManifest> selectTriggered(List<SkillManifest> manifests, String taskSignature, int limit) {
        Objects.requireNonNull(manifests, "manifests");
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
        return manifests.stream()
                .filter(Objects::nonNull)
                .filter(skill -> skill.matches(taskSignature))
                .sorted((left, right) -> {
                    int tier = Integer.compare(right.qualityTier().ordinal(), left.qualityTier().ordinal());
                    if (tier != 0) return tier;
                    int cost = Integer.compare(left.estimatedContextCost(), right.estimatedContextCost());
                    if (cost != 0) return cost;
                    return left.skillId().compareTo(right.skillId());
                })
                .limit(limit)
                .toList();
    }

    public static boolean promotionCandidate(AssetManifest asset) {
        Objects.requireNonNull(asset, "asset");
        return asset.status() == AssetStatus.VALIDATED
                && !asset.provenanceRef().isBlank()
                && !asset.qualityHistoryRef().isBlank();
    }

    public static AssetManifest transitionAsset(AssetManifest current, AssetStatus target) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        if (!allowed(current.status(), target)) {
            throw new IllegalStateException("illegal asset transition: " + current.status() + " -> " + target);
        }
        return new AssetManifest(
                current.assetId(), current.capabilityId(), target, current.version(), current.provenanceRef(),
                current.qualityHistoryRef(), current.deterministic(), current.packageRef());
    }

    private static boolean allowed(AssetStatus current, AssetStatus target) {
        return switch (current) {
            case CANDIDATE -> target == AssetStatus.VALIDATED || target == AssetStatus.RETRACTED;
            case VALIDATED -> target == AssetStatus.PROMOTED || target == AssetStatus.DEPRECATED || target == AssetStatus.RETRACTED;
            case PROMOTED -> target == AssetStatus.DEPRECATED || target == AssetStatus.RETRACTED;
            case DEPRECATED, RETRACTED -> false;
        };
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static List<String> copyNonBlank(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " contains blank value");
        }
        return copy;
    }
}
