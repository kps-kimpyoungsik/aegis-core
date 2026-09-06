import { createPublicKey, verify as verifySignature } from "node:crypto";

const DEFAULT_CLOCK_SKEW_SECONDS = 60;
const DEFAULT_CACHE_TTL_MS = 5 * 60 * 1000;
const DEFAULT_MAX_TOKEN_BYTES = 16 * 1024;
const MAX_JWKS_KEYS = 64;

function normalizeIssuer(value) {
  if (typeof value !== "string" || value.trim().length === 0) throw new Error("AEGIS-OIDC-001 ISSUER_REQUIRED");
  const issuer = value.trim();
  const url = new URL(issuer);
  if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash) throw new Error("AEGIS-OIDC-002 HTTPS_ISSUER_REQUIRED");
  return issuer.endsWith("/") ? issuer.slice(0, -1) : issuer;
}

function normalizeAudience(value) {
  if (typeof value !== "string" || value.trim().length === 0) throw new Error("AEGIS-OIDC-003 AUDIENCE_REQUIRED");
  return value.trim();
}

function decodeJsonSegment(segment) {
  return JSON.parse(Buffer.from(segment, "base64url").toString("utf8"));
}

function audienceMatches(claim, expected) {
  if (typeof claim === "string") return claim === expected;
  return Array.isArray(claim) && claim.some((value) => value === expected);
}

function rolesFromClaim(claim) {
  if (!Array.isArray(claim)) return null;
  const roles = claim.map((value) => typeof value === "string" ? value.trim() : "").filter(Boolean);
  if (roles.length === 0 || roles.length !== claim.length) return null;
  return Object.freeze([...new Set(roles)]);
}

function validateClaims(claims, config) {
  const nowSeconds = Math.floor(config.now() / 1000);
  const skew = config.clockSkewSeconds;
  if (claims.iss !== config.issuer) return null;
  if (!audienceMatches(claims.aud, config.audience)) return null;
  if (!Number.isFinite(claims.exp) || nowSeconds - skew >= claims.exp) return null;
  if (claims.nbf !== undefined && (!Number.isFinite(claims.nbf) || nowSeconds + skew < claims.nbf)) return null;
  if (claims.iat !== undefined && (!Number.isFinite(claims.iat) || claims.iat > nowSeconds + skew)) return null;
  if (typeof claims.sub !== "string" || claims.sub.trim().length === 0) return null;
  const tenantId = claims[config.tenantClaim];
  if (typeof tenantId !== "string" || tenantId.trim().length === 0) return null;
  const roles = rolesFromClaim(claims[config.rolesClaim]);
  if (!roles) return null;
  return Object.freeze({ id: claims.sub.trim(), tenantId: tenantId.trim(), roles });
}

function acceptableJwk(jwk, kid) {
  return jwk && typeof jwk === "object" && jwk.kid === kid && jwk.kty === "RSA" &&
    (jwk.use === undefined || jwk.use === "sig") && (jwk.alg === undefined || jwk.alg === "RS256");
}

function createJsonFetcher(fetchImpl) {
  if (typeof fetchImpl !== "function") throw new Error("AEGIS-OIDC-004 FETCH_REQUIRED");
  return async (url) => {
    const response = await fetchImpl(url, { method: "GET", headers: { accept: "application/json" }, redirect: "error" });
    if (!response?.ok) throw new Error("AEGIS-OIDC-005 METADATA_FETCH_FAILED");
    return response.json();
  };
}

export function createOidcJwtAuthenticator({
  issuer,
  audience,
  fetchImpl = globalThis.fetch,
  now = () => Date.now(),
  tenantClaim = "tenant_id",
  rolesClaim = "roles",
  clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS,
  cacheTtlMs = DEFAULT_CACHE_TTL_MS,
  maxTokenBytes = DEFAULT_MAX_TOKEN_BYTES,
  tokenStatusVerifier = null,
} = {}) {
  const canonicalIssuer = normalizeIssuer(issuer);
  const canonicalAudience = normalizeAudience(audience);
  if (!Number.isFinite(clockSkewSeconds) || clockSkewSeconds < 0 || clockSkewSeconds > 300) throw new Error("AEGIS-OIDC-006 CLOCK_SKEW_INVALID");
  if (!Number.isFinite(cacheTtlMs) || cacheTtlMs <= 0 || cacheTtlMs > 60 * 60 * 1000) throw new Error("AEGIS-OIDC-007 CACHE_TTL_INVALID");
  if (!Number.isFinite(maxTokenBytes) || maxTokenBytes < 256 || maxTokenBytes > 64 * 1024) throw new Error("AEGIS-OIDC-008 MAX_TOKEN_BYTES_INVALID");
  if (tokenStatusVerifier !== null && typeof tokenStatusVerifier !== "function") throw new Error("AEGIS-OIDC-012 TOKEN_STATUS_VERIFIER_INVALID");

  const fetchJson = createJsonFetcher(fetchImpl);
  const config = { issuer: canonicalIssuer, audience: canonicalAudience, tenantClaim, rolesClaim, clockSkewSeconds, now };
  let metadataCache = null;
  let jwksCache = null;

  async function getMetadata(forceRefresh = false) {
    const current = now();
    if (!forceRefresh && metadataCache && metadataCache.expiresAt > current) return metadataCache.value;
    const metadata = await fetchJson(`${canonicalIssuer}/.well-known/openid-configuration`);
    if (!metadata || metadata.issuer !== canonicalIssuer || typeof metadata.jwks_uri !== "string") throw new Error("AEGIS-OIDC-009 METADATA_INVALID");
    const jwksUrl = new URL(metadata.jwks_uri);
    if (jwksUrl.protocol !== "https:" || jwksUrl.username || jwksUrl.password) throw new Error("AEGIS-OIDC-010 JWKS_URI_INVALID");
    metadataCache = { value: Object.freeze({ issuer: metadata.issuer, jwksUri: jwksUrl.href }), expiresAt: current + cacheTtlMs };
    return metadataCache.value;
  }

  async function getJwks(forceRefresh = false) {
    const current = now();
    if (!forceRefresh && jwksCache && jwksCache.expiresAt > current) return jwksCache.value;
    const metadata = await getMetadata(forceRefresh);
    const jwks = await fetchJson(metadata.jwksUri);
    if (!jwks || !Array.isArray(jwks.keys) || jwks.keys.length === 0 || jwks.keys.length > MAX_JWKS_KEYS) throw new Error("AEGIS-OIDC-011 JWKS_INVALID");
    const value = Object.freeze({ keys: Object.freeze([...jwks.keys]) });
    jwksCache = { value, expiresAt: current + cacheTtlMs };
    return value;
  }

  async function findVerificationKey(kid) {
    let jwks = await getJwks(false);
    let jwk = jwks.keys.find((candidate) => acceptableJwk(candidate, kid));
    if (!jwk) {
      jwks = await getJwks(true);
      jwk = jwks.keys.find((candidate) => acceptableJwk(candidate, kid));
    }
    return jwk ?? null;
  }

  return async (req) => {
    try {
      const authorization = req?.headers?.authorization;
      if (typeof authorization !== "string" || !authorization.startsWith("Bearer ")) return null;
      const token = authorization.slice("Bearer ".length);
      if (Buffer.byteLength(token, "utf8") > maxTokenBytes) return null;
      const segments = token.split(".");
      if (segments.length !== 3 || segments.some((segment) => segment.length === 0)) return null;
      const [encodedHeader, encodedClaims, encodedSignature] = segments;
      const header = decodeJsonSegment(encodedHeader);
      if (header?.alg !== "RS256" || typeof header.kid !== "string" || header.kid.length === 0) return null;
      const jwk = await findVerificationKey(header.kid);
      if (!jwk) return null;
      const publicKey = createPublicKey({ key: jwk, format: "jwk" });
      const signingInput = Buffer.from(`${encodedHeader}.${encodedClaims}`, "utf8");
      const signature = Buffer.from(encodedSignature, "base64url");
      if (!verifySignature("RSA-SHA256", signingInput, publicKey, signature)) return null;
      const claims = decodeJsonSegment(encodedClaims);
      const principal = validateClaims(claims, config);
      if (!principal) return null;
      if (tokenStatusVerifier && await tokenStatusVerifier({ token, claims, principal }) !== true) return null;
      return principal;
    } catch {
      return null;
    }
  };
}
