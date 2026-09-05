import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign as signData } from "node:crypto";
import { createOidcJwksAuthenticator, createConfiguredAuthenticator } from "../apps/api-server/oidc-jwks-authenticator.js";

const issuer = "https://issuer.example.test";
const audience = "aegis-api";
const nowMs = 2_000_000_000_000;
const nowSeconds = Math.floor(nowMs / 1000);
const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: "jwk" }), kid: "key-1", use: "sig", alg: "RS256" };

function encode(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function jwt(claimOverrides = {}, headerOverrides = {}, signingKey = privateKey) {
  const header = { alg: "RS256", typ: "JWT", kid: "key-1", ...headerOverrides };
  const claims = {
    iss: issuer,
    aud: audience,
    sub: "user-123",
    tenant_id: "tenant-a",
    roles: ["TASK_EXECUTOR"],
    iat: nowSeconds - 10,
    nbf: nowSeconds - 10,
    exp: nowSeconds + 300,
    ...claimOverrides,
  };
  const input = `${encode(header)}.${encode(claims)}`;
  const signature = signData("RSA-SHA256", Buffer.from(input, "ascii"), signingKey).toString("base64url");
  return `${input}.${signature}`;
}

function request(token) {
  return { headers: { authorization: `Bearer ${token}` } };
}

function fakeFetch() {
  let calls = 0;
  const fetchImpl = async (url) => {
    calls += 1;
    if (url === `${issuer}/.well-known/openid-configuration`) {
      return { ok: true, json: async () => ({ issuer, jwks_uri: `${issuer}/keys` }) };
    }
    if (url === `${issuer}/keys`) return { ok: true, json: async () => ({ keys: [jwk] }) };
    return { ok: false, json: async () => ({}) };
  };
  return { fetchImpl, calls: () => calls };
}

function authenticator(overrides = {}) {
  const fixture = fakeFetch();
  return {
    authenticate: createOidcJwksAuthenticator({ issuer, audience, fetchImpl: fixture.fetchImpl, now: () => nowMs, ...overrides }),
    fixture,
  };
}

test("valid RS256 token becomes canonical tenant-bound principal", async () => {
  const { authenticate } = authenticator();
  const principal = await authenticate(request(jwt()));
  assert.deepEqual(principal, { id: "user-123", tenantId: "tenant-a", roles: ["TASK_EXECUTOR"] });
});

test("wrong issuer and wrong audience fail closed", async () => {
  const { authenticate } = authenticator();
  assert.equal(await authenticate(request(jwt({ iss: "https://evil.example" }))), null);
  assert.equal(await authenticate(request(jwt({ aud: "different-api" }))), null);
});

test("expired and not-yet-valid tokens fail closed", async () => {
  const { authenticate } = authenticator({ clockSkewSeconds: 0 });
  assert.equal(await authenticate(request(jwt({ exp: nowSeconds - 1 }))), null);
  assert.equal(await authenticate(request(jwt({ nbf: nowSeconds + 1 }))), null);
});

test("tampered payload and unknown kid fail closed", async () => {
  const { authenticate } = authenticator();
  const original = jwt();
  const [header, payload, signature] = original.split(".");
  const tampered = `${header}.${encode({ ...JSON.parse(Buffer.from(payload, "base64url").toString("utf8")), tenant_id: "tenant-b" })}.${signature}`;
  assert.equal(await authenticate(request(tampered)), null);
  assert.equal(await authenticate(request(jwt({}, { kid: "unknown" }))), null);
});

test("algorithm substitution and malformed bearer fail closed before JWKS lookup", async () => {
  const { authenticate, fixture } = authenticator();
  assert.equal(await authenticate(request(jwt({}, { alg: "HS256" }))), null);
  assert.equal(await authenticate({ headers: { authorization: "Bearer not-a-jwt" } }), null);
  assert.equal(fixture.calls(), 0);
});

test("missing subject tenant or roles cannot produce a principal", async () => {
  const { authenticate } = authenticator();
  assert.equal(await authenticate(request(jwt({ sub: "" }))), null);
  assert.equal(await authenticate(request(jwt({ tenant_id: "" }))), null);
  assert.equal(await authenticate(request(jwt({ roles: [] }))), null);
});

test("discovery issuer mismatch is an infrastructure verification failure", async () => {
  const fetchImpl = async (url) => {
    if (url.endsWith("openid-configuration")) return { ok: true, json: async () => ({ issuer: "https://other.example", jwks_uri: `${issuer}/keys` }) };
    return { ok: true, json: async () => ({ keys: [jwk] }) };
  };
  const authenticate = createOidcJwksAuthenticator({ issuer, audience, fetchImpl, now: () => nowMs });
  await assert.rejects(() => authenticate(request(jwt())), /AEGIS-OIDC-009 DISCOVERY_MISMATCH/);
});

test("JWKS discovery and keys are cached within configured TTL", async () => {
  const { authenticate, fixture } = authenticator();
  assert.ok(await authenticate(request(jwt())));
  assert.ok(await authenticate(request(jwt({ sub: "user-456" }))));
  assert.equal(fixture.calls(), 2);
});

test("OIDC environment configuration is fail-closed when partial", () => {
  assert.equal(createConfiguredAuthenticator({}), null);
  assert.throws(() => createConfiguredAuthenticator({ AEGIS_OIDC_ISSUER: issuer }), /AEGIS-OIDC-012 CONFIG_INCOMPLETE/);
  assert.throws(() => createConfiguredAuthenticator({ AEGIS_OIDC_AUDIENCE: audience }), /AEGIS-OIDC-012 CONFIG_INCOMPLETE/);
});
