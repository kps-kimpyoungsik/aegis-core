package aegis.brain.skill;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic five-depth information routing gate for managed Portable Brain skills. */
public final class InformationTreeGovernanceKernel {
    public static final String ROOT = "portable-brain/skills";
    public static final int MAX_SEMANTIC_DEPTH = 5;

    private static final Set<String> FORBIDDEN = Set.of("misc", "temp", "new", "other", "unknown");

    private InformationTreeGovernanceKernel() {}

    public enum UnitKind {
        SERVICE("service"), RESOURCE("resource"), PAGE("page");

        private final String prefix;

        UnitKind(String prefix) {
            this.prefix = prefix;
        }

        String prefix() {
            return prefix;
        }
    }

    public record Classification(
            String field,
            String area,
            String relation,
            String module,
            UnitKind unitKind,
            String unitName,
            String owner,
            List<String> relations,
            String provenanceRef) {
        public Classification {
            if (unitKind == null) throw new IllegalArgumentException("unitKind required");
            field = normalize(field, "field");
            area = normalize(area, "area");
            relation = normalize(relation, "relation");
            module = normalize(module, "module");
            unitName = normalize(unitName, "unitName");
            owner = requireText(owner, "owner");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
            relations = relations == null ? List.of() : List.copyOf(relations);
        }
    }

    public record RouteDecision(
            String canonicalDirectory,
            int semanticDepth,
            boolean overflowMerged,
            String creationState,
            List<String> relationEdges,
            String owner,
            String provenanceRef) {}

    public static RouteDecision route(Classification input) {
        String unit = input.unitKind().prefix() + "-" + input.unitName();
        String path = String.join("/", ROOT, input.field(), input.area(), input.relation(), input.module(), unit);
        validateCanonicalDirectory(path);
        return new RouteDecision(
                path,
                MAX_SEMANTIC_DEPTH,
                false,
                "ROUTED_VALIDATED",
                input.relations(),
                input.owner(),
                input.provenanceRef());
    }

    /** L6+ semantic requests are compressed into L5 metadata/files or sibling L5 units. */
    public static RouteDecision routeWithRequestedDepth(Classification input, int requestedDepth) {
        if (requestedDepth < 1) throw new IllegalArgumentException("requestedDepth must be positive");
        RouteDecision base = route(input);
        if (requestedDepth <= MAX_SEMANTIC_DEPTH) return base;
        return new RouteDecision(
                base.canonicalDirectory(),
                MAX_SEMANTIC_DEPTH,
                true,
                "ROUTED_VALIDATED_DEPTH_COMPRESSED",
                base.relationEdges(),
                base.owner(),
                base.provenanceRef());
    }

    public static void validateCanonicalDirectory(String directory) {
        String prefix = ROOT + "/";
        if (directory == null || !directory.startsWith(prefix)) {
            throw new IllegalArgumentException("managed path must be under " + ROOT);
        }
        String relative = directory.substring(prefix.length());
        String[] segments = relative.split("/", -1);
        if (segments.length != MAX_SEMANTIC_DEPTH) {
            throw new IllegalArgumentException("managed path must have exactly five semantic directories");
        }
        for (int index = 0; index < 4; index++) {
            requireCanonicalSegment(segments[index]);
        }
        String unit = segments[4];
        boolean validUnit = unit.startsWith("service-") || unit.startsWith("resource-") || unit.startsWith("page-");
        if (!validUnit) throw new IllegalArgumentException("L5 unit must be service-*, resource-* or page-*");
        requireCanonicalSegment(unit);
    }

    public static boolean mustCompressDepth(int requestedDepth) {
        return requestedDepth > MAX_SEMANTIC_DEPTH;
    }

    private static String normalize(String value, String field) {
        String text = requireText(value, field)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        while (text.contains("--")) text = text.replace("--", "-");
        requireCanonicalSegment(text);
        return text;
    }

    private static void requireCanonicalSegment(String value) {
        if (value.isBlank() || value.equals(".") || value.equals("..") || value.contains("/") || value.contains("\\")) {
            throw new IllegalArgumentException("invalid path segment");
        }
        if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("segment must be lowercase kebab-case: " + value);
        }
        if (FORBIDDEN.contains(value)) {
            throw new IllegalArgumentException("forbidden dumping-ground segment: " + value);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value;
    }
}
