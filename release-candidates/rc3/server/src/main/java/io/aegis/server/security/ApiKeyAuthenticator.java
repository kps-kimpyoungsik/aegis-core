package io.aegis.server.security;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

public final class ApiKeyAuthenticator {
    public static final String AUTHORIZATION = "Authorization";
    public static final String TENANT_HEADER = "X-Aegis-Tenant";

    private final byte[] expectedSha256;
    private final Set<String> scopes;

    public ApiKeyAuthenticator(String expectedSha256Hex, Set<String> scopes) {
        Objects.requireNonNull(expectedSha256Hex, "expectedSha256Hex");
        Objects.requireNonNull(scopes, "scopes");
        if (!expectedSha256Hex.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("AEGIS_API_KEY_SHA256 must be 64 hex characters");
        }
        this.expectedSha256 = HexFormat.of().parseHex(expectedSha256Hex);
        this.scopes = Set.copyOf(scopes);
    }

    public Authentication authenticate(HttpExchange exchange, String requiredScope) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(requiredScope, "requiredScope");
        String authorization = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Authentication.denied("MISSING_BEARER_TOKEN");
        }
        String token = authorization.substring("Bearer ".length());
        if (token.isBlank()) return Authentication.denied("EMPTY_BEARER_TOKEN");
        byte[] presented = sha256(token);
        if (!MessageDigest.isEqual(expectedSha256, presented)) {
            return Authentication.denied("INVALID_BEARER_TOKEN");
        }
        if (!scopes.contains(requiredScope)) {
            return Authentication.denied("INSUFFICIENT_SCOPE");
        }
        String tenantId = exchange.getRequestHeaders().getFirst(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            return Authentication.denied("MISSING_TENANT_CONTEXT");
        }
        return Authentication.allowed(tenantId, requiredScope);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record Authentication(boolean allowed, String tenantId, String scope, String reasonCode) {
        public static Authentication allowed(String tenantId, String scope) {
            return new Authentication(true, tenantId, scope, "ALLOW");
        }
        public static Authentication denied(String reasonCode) {
            return new Authentication(false, "", "", reasonCode);
        }
    }
}
