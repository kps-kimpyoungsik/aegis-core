import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import { createApiServer } from "../apps/api-server/index.js";
import { createOidcJwtAuthenticator } from "../apps/api-server/oidc-jwt-authenticator.js";

const ISSUER = "https://identity.example.test";
const AUDIENCE = "aegis-api";
const JWKS_URI = "https://identity.example.test/keys";
const NOW_MS = Date.parse("2026-09-06T00:00:00Z");
const NOW_SECONDS = Math.floor(NOW_MS / 1000);

function createSigningMaterial(kid = "key-1") {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const jwk = publicKey.export({ format: "jwk" });
  return {
    privateKey,
    jwk: { ...jwk, kid, alg: "RS256", use: "sig" },
    kid,
  };
}

function encodeJson(value) {
  return Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
}

function createToken({ privateKey, kid, claims = {}, header = {} }) {
  const protectedHeader = encodeJson({ alg: "RS256", typ: "JWT", kid, ...header });
  const payload = encodeJson({
    iss: ISSUER,
    aud: AUDIENCE,
    sub: "principal:oidc-user",
    tenant_id: "tenant-a",
    roles: ["RUNTIME_VIEWER"],
    iat: NOW_SECONDS - 10,
    exp: NOW_SECONDS + 300,
    ...claims,
  });
  const signingInput = `${protectedHeader}.${payload}`;
  const signature = sign("RSA-SHA256", Buffer.from(signingInput, "utf8"), privateKey).toString("base64url");
  return `${signingInput}.${signature}`;
}

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() { return body; },
  };
}

function createFetch({ jwksSequence }) {
  let jwksCalls = 0;
  const fetchImpl = async (url, options) => {
    assert.equal(options.redirect, "error");
    if (url === `${ISSUER}/.well-known/openid-configuration`) {
      return jsonResponse({ issuer: ISSUER, jwks_uri: JWKS_URI });
    }
    if (url === JWKS_URI) {
      const index = Math.min(jwksCalls, jwksSequence.length - 1);
      jwksCalls += 1;
      return jsonResponse(jwksSequence[index]);
    }
    return jsonResponse({}, 404);
  };
  return { fetchImpl, getJwksCalls: () => jwksCalls };
}

function requestWithToken(token) {
  return { headers: { authorization: `Bearer ${token}` } };
}

function createAuthenticator(fetchImpl) {
  return createOidcJwtAuthenticator({
    issuer: ISSUER,
    audience: AUDIENCE,
    fetchImpl,
    now: () => NOW_MS,
  });
}

test("valid RS256 OIDC JWT becomes a tenant-bound principal", async () => {
  const key = createSigningMaterial();
  const { fetchImpl } = createFetch({ jwksSequence: [{ keys: [key.jwk] }] });
  const authenticate = createAuthenticator(fetchImpl);
  const principal = await authenticate(requestWithToken(createToken(key)));
  assert.deepEqual(principal, {
    id: "principal:oidc-user",
    tenantId: "tenant-a",
    roles: ["RUNTIME_VIEWER"],
  });
});

test("signature tampering and algorithm substitution fail closed", async () => {
  const key = createSigningMaterial();
  const { fetchImpl } = createFetch({ jwksSequence: [{ keys: [key.jwk] }] });
  const authenticate = createAuthenticator(fetchImpl);
  const valid = createToken(key);
  const [header, payload, signature] = valid.split(".");
  const tamperedPayload = encodeJson({
    iss: ISSUER,
    aud: AUDIENCE,
    sub: "principal:attacker",
    tenant_id: "tenant-a",
    roles: ["TASK_EXECUTOR"],
    exp: NOW_SECONDS + 300,
  });
  assert.equal(await authenticate(requestWithToken(`${header}.${tamperedPayload}.${signature}`)), null);

  const noneHeader = encodeJson({ alg: "none", kid: key.kid });
  assert.equal(await authenticate(requestWithToken(`${noneHeader}.${payload}.${signature}`)), null);
});

test("issuer audience and temporal claim violations fail closed", async () => {
  const key = createSigningMaterial();
  const { fetchImpl } = createFetch({ jwksSequence: [{ keys: [key.jwk] }] });
  const authenticate = createAuthenticator(fetchImpl);
  const invalidClaims = [
    { iss: "https://evil.example.test" },
    { aud: "different-api" },
    { exp: NOW_SECONDS - 120 },
    { nbf: NOW_SECONDS + 120 },
    { iat: NOW_SECONDS + 120 },
    { tenant_id: "" },
    { roles: [] },
  ];
  for (const claims of invalidClaims) {
    const token = createToken({ ...key, claims });
    assert.equal(await authenticate(requestWithToken(token)), null);
  }
});

test("unknown kid forces one JWKS refresh and supports key rotation", async () => {
  const oldKey = createSigningMaterial("old-key");
  const newKey = createSigningMaterial("new-key");
  const { fetchImpl, getJwksCalls } = createFetch({
    jwksSequence: [{ keys: [oldKey.jwk] }, { keys: [oldKey.jwk, newKey.jwk] }],
  });
  const authenticate = createAuthenticator(fetchImpl);
  const principal = await authenticate(requestWithToken(createToken(newKey)));
  assert.equal(principal.id, "principal:oidc-user");
  assert.equal(getJwksCalls(), 2);
});

test("discovery issuer mismatch and insecure configuration are rejected", async () => {
  const key = createSigningMaterial();
  const fetchImpl = async (url) => {
    if (url.endsWith("/.well-known/openid-configuration")) {
      return jsonResponse({ issuer: "https://different.example.test", jwks_uri: JWKS_URI });
    }
    return jsonResponse({ keys: [key.jwk] });
  };
  const authenticate = createAuthenticator(fetchImpl);
  assert.equal(await authenticate(requestWithToken(createToken(key))), null);
  assert.throws(
    () => createOidcJwtAuthenticator({ issuer: "http://identity.example.test", audience: AUDIENCE, fetchImpl }),
    /HTTPS_ISSUER_REQUIRED/,
  );
});

test("OIDC authenticator integrates with existing API RBAC and tenant binding", async () => {
  const key = createSigningMaterial();
  const { fetchImpl } = createFetch({ jwksSequence: [{ keys: [key.jwk] }] });
  const authenticateRequest = createAuthenticator(fetchImpl);
  const server = createApiServer({
    executeTask: async () => ({ status: "COMPLETED" }),
    authenticateRequest,
    getSnapshot: () => ({ tasks: [], events: [] }),
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  try {
    const token = createToken(key);
    const ok = await fetch(`http://127.0.0.1:${port}/v1/runtime/snapshot`, {
      headers: { authorization: `Bearer ${token}`, "x-aegis-tenant": "tenant-a" },
    });
    assert.equal(ok.status, 200);

    const crossTenant = await fetch(`http://127.0.0.1:${port}/v1/runtime/snapshot`, {
      headers: { authorization: `Bearer ${token}`, "x-aegis-tenant": "tenant-b" },
    });
    assert.equal(crossTenant.status, 403);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
