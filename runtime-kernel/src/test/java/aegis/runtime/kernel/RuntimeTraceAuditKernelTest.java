package aegis.runtime.kernel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class RuntimeTraceAuditKernelTest {
    private static int passed;

    private RuntimeTraceAuditKernelTest() {}

    public static void main(String[] args) {
        String traceId = "0123456789abcdef0123456789abcdef";
        String spanId = "0123456789abcdef";
        var trace = new RuntimeTraceAuditKernel.TraceContext(traceId, spanId, "", true, "1.0.0");
        check(trace.traceparent().equals("00-" + traceId + "-" + spanId + "-01"), "traceparent format");

        var source = new RuntimeTraceAuditKernel.SourceReference(
                RuntimeTraceAuditKernel.SourceKind.EXTERNAL_SOURCE,
                "w3c-trace-context",
                "2021-rec",
                RuntimeTraceAuditKernel.Confidence.VERIFIED,
                "https://www.w3.org/TR/trace-context/");

        var event = new RuntimeTraceAuditKernel.RuntimeTraceEvent(
                trace,
                "exec-1",
                "TOOL_CALLED",
                Instant.ofEpochMilli(1_788_530_000_000L),
                Map.of("tool.name", "search", "attempt", "1"),
                List.of(source));

        check(event.sources().size() == 1, "source provenance retained");
        check(event.attributes().size() == 2, "attributes retained");

        var first = RuntimeTraceAuditKernel.envelope(
                "audit-1", "aegis.work-runtime", RuntimeTraceAuditKernel.AuditSeverity.INFO, event, "");
        check(RuntimeTraceAuditKernel.verifyChain(null, first), "root audit verified");
        check(first.contentHash().length() == 64, "sha256 content hash");

        var secondEvent = new RuntimeTraceAuditKernel.RuntimeTraceEvent(
                new RuntimeTraceAuditKernel.TraceContext(traceId, "fedcba9876543210", spanId, true, "1.0.0"),
                "exec-1",
                "VALIDATION",
                Instant.ofEpochMilli(1_788_530_000_100L),
                Map.of("decision", "ACCEPT"),
                List.of(source));
        var second = RuntimeTraceAuditKernel.envelope(
                "audit-2", "aegis.work-runtime", RuntimeTraceAuditKernel.AuditSeverity.INFO,
                secondEvent, first.contentHash());
        check(RuntimeTraceAuditKernel.verifyChain(first, second), "linked audit verified");

        checkThrows(() -> new RuntimeTraceAuditKernel.TraceContext(
                "00000000000000000000000000000000", spanId, "", true, "1.0.0"), "zero trace id rejected");
        checkThrows(() -> new RuntimeTraceAuditKernel.TraceContext(
                traceId, "XYZ", "", true, "1.0.0"), "invalid span id rejected");
        checkThrows(() -> new RuntimeTraceAuditKernel.RuntimeTraceEvent(
                trace, "exec-1", "X", Instant.now(), Map.of("authorization", "Bearer secret"), List.of()),
                "secret attribute rejected");
        checkThrows(() -> new RuntimeTraceAuditKernel.RuntimeTraceEvent(
                trace, "exec-1", "X", Instant.now(), Map.of("message", "ok\nFORGED"), List.of()),
                "log injection rejected");
        checkThrows(() -> new RuntimeTraceAuditKernel.RuntimeTraceEvent(
                trace, "exec-1", "X", Instant.now(), Map.of("k", "x".repeat(257)), List.of()),
                "oversized value rejected");

        Map<String, String> tooMany = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 33; i++) tooMany.put("k" + i, "v");
        checkThrows(() -> new RuntimeTraceAuditKernel.RuntimeTraceEvent(
                trace, "exec-1", "X", Instant.now(), tooMany, List.of()), "attribute cardinality bounded");

        var wrongPrevious = RuntimeTraceAuditKernel.envelope(
                "audit-3", "aegis.work-runtime", RuntimeTraceAuditKernel.AuditSeverity.WARN,
                secondEvent, "deadbeef");
        check(!RuntimeTraceAuditKernel.verifyChain(second, wrongPrevious), "broken chain rejected");

        check(second.event().trace().parentSpanId().equals(spanId), "parent span correlation retained");
        check(second.canonicalOwner().equals("aegis.work-runtime"), "canonical owner explicit");
        check(second.event().trace().schemaVersion().equals("1.0.0"), "telemetry schema version explicit");

        System.out.println("PASS " + passed + "/15");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("FAILED: " + name);
        passed++;
    }

    private static void checkThrows(Runnable action, String name) {
        boolean thrown = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        check(thrown, name);
    }
}
