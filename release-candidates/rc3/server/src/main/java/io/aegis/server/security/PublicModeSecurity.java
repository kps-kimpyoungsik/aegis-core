package io.aegis.server.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class PublicModeSecurity {
    private PublicModeSecurity() {}

    public static boolean publicMode() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("AEGIS_PUBLIC_MODE", "false"));
    }

    public static boolean configurationReady() {
        if (!publicMode()) return true;
        if (!"api-key".equals(System.getenv().getOrDefault("AEGIS_AUTH_MODE", "none"))) return false;
        String hash = System.getenv().getOrDefault("AEGIS_API_KEY_SHA256", "");
        if (!hash.matches("[0-9a-fA-F]{64}")) return false;
        return scopes().contains("system:read");
    }

    public static ApiKeyAuthenticator authenticator() {
        if (!configurationReady()) throw new IllegalStateException("PUBLIC_SECURITY_CONFIGURATION_INVALID");
        return new ApiKeyAuthenticator(System.getenv("AEGIS_API_KEY_SHA256"), scopes());
    }

    private static Set<String> scopes() {
        return Arrays.stream(System.getenv().getOrDefault("AEGIS_API_KEY_SCOPES", "system:read").split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
