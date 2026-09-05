import { createPublicKey, verify as verifySignature } from "node:crypto";

const DEFAULT_CLOCK_SKEW_SECONDS = 60;
const DEFAULT_JWKS_CACHE_MS = 5 * 60 * 1000;

function decodeBase64UrlJson(segment) {
  if (typeof segment !== "string" || segment.length === 0) throw new Error("AEGIS-OIDC-001 MALFORMED_JWT");
  try {
    return JSON.parse(Buffer.from(segment, "base64url").toString("utf8"));
  } catch {
    throw new Error("AEGIS-OIDC-001 MALFORMED_JWT");
  }
}

function parseBearer(req) {
  const value = req?.headers?.authorization;
  if (typeof value !== "string") return null;
  const match = /^Bearer ([^\s]+)$/.exec(value);
  return match ? match[1] : null;
}

function normalizeAudience(value) {
  if (typeof value === "string") return [value];
  if (Array.isArray(value) && value.every((item) => typeof item === "string")) return value;
  return [];
}

function normalizeRoles(claims, roleClaim) {
  const value = claims[roleClaim];
  if (Array.isArray(value)) return [...new Set(value.filter((role) => typeof role === "string" && role.trim()).map((role) => role.trim()))];
  if (typeof value === "string") return [...new Set(value.split(/[ ,]+/).map((role) => role.trim()).filter(Boolean))];
  return [];
}

function requireHttps(url, allowInsecureHttpForTest) {
  const parsed = new URL(url);
  if (parsed.protocol !== "https:" && !allowInsecureHttpForTest) throw new Error("AEGIS-OIDC-002 HTTPS_REQUIRED");
  return parsed;
}

export function createOidcJwksAuthenticator({
  issuer,
  audience,
  tenantClaim = "tenant_id",
  rolesClaim = "roles",
  fetchImpl = globalThis.fetch,
  now = () => Date.now(),
  clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS,
  jwksCacheMs = DEFAULT_JWKS_CACHE_MS,
  allowInsecureHttpForTest = false,
} = {}) {
  if (typeof issuer !== "string" || issuer.trim().length === 0) throw new Error("AEGIS-OIDC-003 ISSUER_REQUIRED");
  if (typeof audience !== "string" || audience.trim().length === 0) throw new Error("AEGIS-OIDC-004 AUDIENCE_REQUIRED");
  if (typeof fetchImpl !== "function") throw new Error("AEGIS-OIDC-005 FETCH_REQUIRED");
  if (!Number.isFinite(clockSkewSeconds) || clockSkewSeconds < 0) throw new Error("AEGIS-OIDC-006 INVALID_CLOCK_SKEW");
  if (!Number.isFinite(jwksCacheMs) || jwksCacheMs <= 0) throw new Error("AEGIS-OIDC-007 INVALID_CACHE_TTL");

  const canonicalIssuer = issuer.replace(/\/$/, "");
  requireHttps(canonicalIssuer, allowInsecureHttpForTest);
  let discoveryCache = null;
  let jwksCache = null;

  async function fetchJson(url, errorCode) {
    const response = await fetchImpl(url, { headers: { accept: "application/json" }, signal: AbortSignal.timeout(5000) });
    if (!response?.ok) throw new Error(errorCode);
    return response.json();
  }

  async function getDiscovery() {
    const current = now();
    if (discoveryCache && discoveryCache.expiresAt > current) return discoveryCache.value;
    const document = await fetchJson(`${canonicalIssuer}/.well-known/openid-configuration`, "AEGIS-OIDC-008 DISCOVERY_FAILED");
    if (document?.issuer !== canonicalIssuer || typeof document?.jwks_uri !== "string") {
      throw new Error("AEGIS-OIDC-009 DISCOVERY_MISMATCH");
    }
    requireHttps(document.jwks_uri, allowInsecureHttpForTest);
    discoveryCache = { value: document, expiresAt: current + jwksCacheMs };
    return document;
  }

  async function getJwks() {
    const current = now();
    if (jwksCache && jwksCache.expiresAt > current) return jwksCache.value;
    const discovery = await getDiscovery();
    const jwks = await fetchJson(discovery.jwks_uri, "AEGIS-OIDC-010 JWKS_FETCH_FAILED");
    if (!Array.isArray(jwks?.keys)) throw new Error("AEGIS-OIDC-011 JWKS_INVALID");
    jwksCache = { value: jwks, expiresAt: current + jwksCacheMs };
    return jwks;
  }

  return async function authenticateRequest(req) {
    const token = parseBearer(req);
    if (!token) return null;

    const segments = token.split(".");
    if (segments.length !== 3) return null;

    let header;
    let claims;
    try {
      header = decodeBase64UrlJson(segments[0]);
      claims = decodeBase64UrlJson(segments[1]);
    } catch {
      return null;
    }

    if (header.alg !== "RS256" || typeof header.kid !== "string" || header.kid.length === 0) return null;
    if (claims.iss !== canonicalIssuer) return null;
    if (!normalizeAudience(claims.aud).includes(audience)) return null;

    const nowSeconds = Math.floor(now() / 1000);
    if (!Number.isFinite(claims.exp) || claims.exp <= nowSeconds - clockSkewSeconds) return null;
    if (claims.nbf !== undefined && (!Number.isFinite(claims.nbf) || claims.nbf > nowSeconds + clockSkewSeconds)) return null;
    if (claims.iat !== undefined && (!Number.isFinite(claims.iat) || claims.iat > nowSeconds + clockSkewSeconds)) return null;

    const jwks = await getJwks();
    const key = jwks.keys.find((candidate) => candidate?.kid === header.kid && candidate?.kty === "RSA" && (candidate?.use === undefined || candidate.use === "sig") && (candidate?.alg === undefined || candidate.alg === "RS256"));
    if (!key) return null;

    let publicKey;
    try {
      publicKey = createPublicKey({ key, format: "jwk" });
    } catch {
      return null;
    }

    const signed = Buffer.from(`${segments[0]}.${segments[1]}`, "ascii");
    let signature;
    try {
      signature = Buffer.from(segments[2], "base64url");
    } catch {
      return null;
    }
    if (!verifySignature("RSA-SHA256", signed, publicKey, signature)) return null;

    const subject = typeof claims.sub === "string" ? claims.sub.trim() : "";
    const tenantId = typeof claims[tenantClaim] === "string" ? claims[tenantClaim].trim() : "";
    const roles = normalizeRoles(claims, rolesClaim);
    if (!subject || !tenantId || roles.length === 0) return null;

    return Object.freeze({ id: subject, tenantId, roles: Object.freeze(roles) });
  };
}

export function createConfiguredAuthenticator(env = process.env, options = {}) {
  const issuer = env.AEGIS_OIDC_ISSUER?.trim();
  const audience = env.AEGIS_OIDC_AUDIENCE?.trim();
  if (!issuer && !audience) return null;
  if (!issuer || !audience) throw new Error("AEGIS-OIDC-012 CONFIG_INCOMPLETE");
  return createOidcJwksAuthenticator({
    issuer,
    audience,
    tenantClaim: env.AEGIS_OIDC_TENANT_CLAIM?.trim() || "tenant_id",
    rolesClaim: env.AEGIS_OIDC_ROLES_CLAIM?.trim() || "roles",
    ...options,
  });
}
