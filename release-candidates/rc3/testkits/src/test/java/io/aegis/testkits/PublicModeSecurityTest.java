package io.aegis.testkits;

import io.aegis.server.security.ApiKeyAuthenticator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

public final class PublicModeSecurityTest {
    private PublicModeSecurityTest() {}

    public static void main(String[] args) throws Exception {
        String token = "test-token-not-a-secret";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        var authenticator = new ApiKeyAuthenticator(hash, Set.of("system:read"));
        if (!authenticator.hasScope("system:read")) throw new AssertionError("required scope missing");
        if (authenticator.hasScope("admin:write")) throw new AssertionError("unexpected privilege expansion");
        boolean invalidHashRejected = false;
        try { new ApiKeyAuthenticator("bad", Set.of("system:read")); } catch (IllegalArgumentException expected) { invalidHashRejected = true; }
        if (!invalidHashRejected) throw new AssertionError("invalid credential hash configuration accepted");
        System.out.println("PublicModeSecurityTest PASS");
    }
}
