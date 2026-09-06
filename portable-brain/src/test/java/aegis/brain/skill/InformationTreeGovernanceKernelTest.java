package aegis.brain.skill;

import java.util.List;

import aegis.brain.skill.InformationTreeGovernanceKernel.Classification;
import aegis.brain.skill.InformationTreeGovernanceKernel.RouteDecision;
import aegis.brain.skill.InformationTreeGovernanceKernel.UnitKind;

public final class InformationTreeGovernanceKernelTest {
    private static int assertions;

    private InformationTreeGovernanceKernelTest() {}

    public static void main(String[] args) {
        Classification input = new Classification(
                "Software Engineering", "Operations", "Failure Analysis", "Adaptive Error Intelligence",
                UnitKind.SERVICE, "Analysis", "portable-brain", List.of("DEPENDS_ON:failure-memory"),
                "prov://pr-113");

        RouteDecision routed = InformationTreeGovernanceKernel.route(input);
        check(routed.canonicalDirectory().equals(
                "portable-brain/skills/software-engineering/operations/failure-analysis/adaptive-error-intelligence/service-analysis"),
                "canonical five-depth route");
        check(routed.semanticDepth() == 5, "depth bounded to five");
        check(!routed.overflowMerged(), "normal route not compressed");
        check(routed.owner().equals("portable-brain"), "owner preserved");
        check(routed.provenanceRef().equals("prov://pr-113"), "provenance preserved");
        check(routed.relationEdges().size() == 1, "relationship metadata preserved");

        RouteDecision compressed = InformationTreeGovernanceKernel.routeWithRequestedDepth(input, 8);
        check(compressed.overflowMerged(), "depth overflow compressed");
        check(compressed.semanticDepth() == 5, "compressed route remains depth five");
        check(InformationTreeGovernanceKernel.mustCompressDepth(6), "depth six requires compression");
        check(!InformationTreeGovernanceKernel.mustCompressDepth(5), "depth five accepted");

        expectFailure(() -> InformationTreeGovernanceKernel.validateCanonicalDirectory(
                "portable-brain/skills/software-engineering/operations/failure-analysis/adaptive-error-intelligence/service-analysis/deeper"),
                "sixth semantic folder blocked");
        expectFailure(() -> new Classification(
                "misc", "ops", "relation", "module", UnitKind.RESOURCE, "policy", "owner", List.of(), "prov"),
                "dumping-ground field blocked");
        expectFailure(() -> new Classification(
                "security", "ops", "relation", "module", UnitKind.PAGE, "../escape", "owner", List.of(), "prov"),
                "path traversal blocked");
        expectFailure(() -> InformationTreeGovernanceKernel.validateCanonicalDirectory(
                "portable-brain/skills/security/operations/dependency/auth/resource"),
                "untyped L5 blocked");

        System.out.println("PASS " + assertions + "/14");
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
        } catch (IllegalArgumentException expected) {
            // expected fail-closed path
        }
    }
}
