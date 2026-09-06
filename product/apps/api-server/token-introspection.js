const DEFAULT_TIMEOUT_MS = 2_000;

function requireHttpsUrl(value) {
  if (typeof value !== "string" || value.trim().length === 0) throw new Error("AEGIS-OIDC-INT-001 ENDPOINT_REQUIRED");
  const url = new URL(value.trim());
  if (url.protocol !== "https:" || url.username || url.password || url.hash) throw new Error("AEGIS-OIDC-INT-002 HTTPS_ENDPOINT_REQUIRED");
  return url.href;
}

function requireCredential(value, code) {
  if (typeof value !== "string" || value.length === 0) throw new Error(code);
  return value;
}

export function createTokenIntrospectionVerifier({ endpoint, clientId, clientSecret, fetchImpl = globalThis.fetch, timeoutMs = DEFAULT_TIMEOUT_MS } = {}) {
  const canonicalEndpoint = requireHttpsUrl(endpoint);
  const canonicalClientId = requireCredential(clientId, "AEGIS-OIDC-INT-003 CLIENT_ID_REQUIRED");
  const canonicalClientSecret = requireCredential(clientSecret, "AEGIS-OIDC-INT-004 CLIENT_SECRET_REQUIRED");
  if (typeof fetchImpl !== "function") throw new Error("AEGIS-OIDC-INT-005 FETCH_REQUIRED");
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 100 || timeoutMs > 10_000) throw new Error("AEGIS-OIDC-INT-006 TIMEOUT_INVALID");
  const authorization = `Basic ${Buffer.from(`${canonicalClientId}:${canonicalClientSecret}`, "utf8").toString("base64")}`;

  return async ({ token, claims, principal }) => {
    try {
      const body = new URLSearchParams({ token, token_type_hint: "access_token" }).toString();
      const response = await fetchImpl(canonicalEndpoint, {
        method: "POST",
        headers: {
          accept: "application/json",
          authorization,
          "content-type": "application/x-www-form-urlencoded",
        },
        body,
        redirect: "error",
        signal: AbortSignal.timeout(timeoutMs),
      });
      if (!response?.ok) return false;
      const result = await response.json();
      if (!result || result.active !== true) return false;
      if (typeof result.sub === "string" && result.sub !== principal.id) return false;
      if (typeof claims.sub === "string" && claims.sub !== principal.id) return false;
      return true;
    } catch {
      return false;
    }
  };
}
