package aegis.runtime.kernel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * P2-07 Runtime Trace/Audit correlation contracts.
 *
 * Runtime owns correlation and emission only. Durable audit-ledger ownership,
 * retention, signing and external telemetry export remain behind ports/adapters.
 */
public final class RuntimeTraceAuditKernel {
    private static final int TRACE_ID_LENGTH = 32;
    private static final int SPAN_ID_LENGTH = 16;
    private static final int MAX_ATTRS = 32;
    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_VALUE_LENGTH = 256;
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "password", "passwd", "secret", "token", "access_token",
            "authorization", "cookie", "set-cookie", "api_key", "apikey",
            "private_key", "credential");

    public enum SourceKind {
        USER,
        TOOL,
        SYSTEM,
        MEMORY,
        KNOWLEDGE,
        EXTERNAL_SOURCE,
        ADAPTER
    }

    public enum Confidence {
        VERIFIED,
        CORROBORATED,
        UNVERIFIED,
        UNKNOWN
    }

    public enum AuditSeverity {
        INFO,
        WARN,
        ERROR,
        SECURITY
    }

    public record TraceContext(
            String traceId,
            String spanId,
            String parentSpanId,
            boolean sampled,
            String schemaVersion) {
        public TraceContext {
            requireHex(traceId, TRACE_ID_LENGTH, "traceId");
            requireHex(spanId, SPAN_ID_LENGTH, "spanId");
            if (parentSpanId != null && !parentSpanId.isBlank()) {
                requireHex(parentSpanId, SPAN_ID_LENGTH, "parentSpanId");
            } else {
                parentSpanId = "";
            }
            schemaVersion = requireNonblank(schemaVersion, "schemaVersion");
        }

        public String traceparent() {
            return "00-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
        }
    }

    public record SourceReference(
            SourceKind kind,
            String sourceId,
            String version,
            Confidence confidence,
            String evidenceRef) {
        public SourceReference {
            Objects.requireNonNull(kind, "kind");
            sourceId = requireNonblank(sourceId, "sourceId");
            version = version == null ? "" : version;
            Objects.requireNonNull(confidence, "confidence");
            evidenceRef = evidenceRef == null ? "" : evidenceRef;
        }
    }

    public record RuntimeTraceEvent(
            TraceContext trace,
            String executionId,
            String eventType,
            Instant occurredAt,
            Map<String, String> attributes,
            List<SourceReference> sources) {
        public RuntimeTraceEvent {
            Objects.requireNonNull(trace, "trace");
            executionId = requireNonblank(executionId, "executionId");
            eventType = requireNonblank(eventType, "eventType");
            Objects.requireNonNull(occurredAt, "occurredAt");
            attributes = sanitizeAttributes(attributes);
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record AuditEnvelope(
            String auditId,
            String canonicalOwner,
            AuditSeverity severity,
            RuntimeTraceEvent event,
            String previousHash,
            String contentHash) {
        public AuditEnvelope {
            auditId = requireNonblank(auditId, "auditId");
            canonicalOwner = requireNonblank(canonicalOwner, "canonicalOwner");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(event, "event");
            previousHash = previousHash == null ? "" : previousHash;
            contentHash = requireHex(contentHash, 64, "contentHash");
        }
    }

    public interface RuntimeAuditSinkPort {
        void append(AuditEnvelope envelope);
    }

    public static AuditEnvelope envelope(
            String auditId,
            String canonicalOwner,
            AuditSeverity severity,
            RuntimeTraceEvent event,
            String previousHash) {
        String previous = previousHash == null ? "" : previousHash;
        String canonical = auditId + "|" + canonicalOwner + "|" + severity.name() + "|"
                + event.trace().traceId() + "|" + event.trace().spanId() + "|"
                + event.executionId() + "|" + event.eventType() + "|"
                + event.occurredAt().toEpochMilli() + "|" + event.attributes() + "|"
                + event.sources() + "|" + previous;
        return new AuditEnvelope(
                auditId,
                canonicalOwner,
                severity,
                event,
                previous,
                sha256(canonical));
    }

    public static boolean verifyChain(AuditEnvelope previous, AuditEnvelope current) {
        if (current == null) {
            return false;
        }
        if (previous == null) {
            return current.previousHash().isEmpty();
        }
        if (!current.previousHash().equals(previous.contentHash())) {
            return false;
        }
        AuditEnvelope rebuilt = envelope(
                current.auditId(),
                current.canonicalOwner(),
                current.severity(),
                current.event(),
                current.previousHash());
        return rebuilt.contentHash().equals(current.contentHash());
    }

    private static Map<String, String> sanitizeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        if (attributes.size() > MAX_ATTRS) {
            throw new IllegalArgumentException("too many trace attributes");
        }
        Map<String, String> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = requireNonblank(entry.getKey(), "attribute key");
            if (key.length() > MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("attribute key too long");
            }
            String normalized = key.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            if (FORBIDDEN_KEYS.contains(normalized)) {
                throw new IllegalArgumentException("sensitive attribute forbidden: " + key);
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("attribute value too long");
            }
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("log injection characters forbidden");
            }
            copy.put(key, value);
        }
        return Map.copyOf(copy);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String requireHex(String value, int length, String name) {
        value = requireNonblank(value, name);
        if (value.length() != length || !value.matches("[0-9a-f]+")) {
            throw new IllegalArgumentException(name + " must be lowercase hex length " + length);
        }
        if (value.chars().allMatch(ch -> ch == '0')) {
            throw new IllegalArgumentException(name + " must not be all zero");
        }
        return value;
    }

    private static String requireNonblank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be nonblank");
        }
        return value;
    }
}
