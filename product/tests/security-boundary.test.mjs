import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { createApiServer } from "../apps/api-server/index.js";

async function withServer(options, fn) {
  const server = createApiServer(options);
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const { port } = server.address();
  try {
    return await fn(port);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

function request(port, { method = "GET", path = "/", headers = {}, body } = {}) {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: "127.0.0.1", port, method, path, headers }, (res) => {
      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end", () => resolve({
        status: res.statusCode,
        headers: res.headers,
        body: Buffer.concat(chunks).toString("utf8"),
      }));
    });
    req.on("error", reject);
    if (body !== undefined) req.write(body);
    req.end();
  });
}

const executor = Object.freeze({ id: "executor", tenantId: "tenant-a", roles: ["TASK_EXECUTOR"] });
const viewer = Object.freeze({ id: "viewer", tenantId: "tenant-a", roles: ["RUNTIME_VIEWER"] });

function auth(principal) {
  return async () => principal;
}

test("protected runtime snapshot fails closed without authentication", async () => {
  await withServer({ executeTask: async () => ({ status: "ACCEPTED" }) }, async (port) => {
    const res = await request(port, { path: "/v1/runtime/snapshot", headers: { "x-aegis-tenant": "tenant-a" } });
    assert.equal(res.status, 401);
    assert.equal(res.headers["www-authenticate"], "Bearer");
    assert.equal(res.headers["cache-control"], "no-store");
    assert.equal(res.headers["x-content-type-options"], "nosniff");
    assert.match(res.body, /AEGIS-API-006 AUTHENTICATION_REQUIRED/);
  });
});

test("tenant mismatch is rejected before protected dispatch", async () => {
  await withServer({ executeTask: async () => ({ status: "ACCEPTED" }), authenticateRequest: auth(viewer) }, async (port) => {
    const res = await request(port, { path: "/v1/runtime/snapshot", headers: { "x-aegis-tenant": "tenant-b" } });
    assert.equal(res.status, 403);
    assert.match(res.body, /AEGIS-API-014 TENANT_MISMATCH/);
  });
});

test("role mismatch rejects task execution and does not invoke application", async () => {
  let calls = 0;
  await withServer({
    executeTask: async () => { calls += 1; return { status: "ACCEPTED" }; },
    authenticateRequest: auth(viewer),
  }, async (port) => {
    const res = await request(port, {
      method: "POST",
      path: "/v1/tasks",
      headers: { "x-aegis-tenant": "tenant-a", "content-type": "application/json" },
      body: JSON.stringify({ goal: "x", owner: "owner", responsibility: "r" }),
    });
    assert.equal(res.status, 403);
    assert.equal(calls, 0);
    assert.match(res.body, /AEGIS-API-007 FORBIDDEN/);
  });
});

test("wrong content type is rejected for authenticated task command", async () => {
  await withServer({ executeTask: async () => ({ status: "ACCEPTED" }), authenticateRequest: auth(executor) }, async (port) => {
    const res = await request(port, {
      method: "POST",
      path: "/v1/tasks",
      headers: { "x-aegis-tenant": "tenant-a", "content-type": "text/plain" },
      body: "{}",
    });
    assert.equal(res.status, 415);
    assert.match(res.body, /AEGIS-API-003 JSON_CONTENT_TYPE_REQUIRED/);
  });
});

test("malformed json is rejected without internal detail leakage", async () => {
  await withServer({ executeTask: async () => ({ status: "ACCEPTED" }), authenticateRequest: auth(executor) }, async (port) => {
    const res = await request(port, {
      method: "POST",
      path: "/v1/tasks",
      headers: { "x-aegis-tenant": "tenant-a", "content-type": "application/json" },
      body: "{",
    });
    assert.equal(res.status, 400);
    assert.deepEqual(JSON.parse(res.body), { code: "AEGIS-API-005 MALFORMED_JSON" });
  });
});

test("declared oversized request is rejected before body parsing", async () => {
  await withServer({ executeTask: async () => ({ status: "ACCEPTED" }), authenticateRequest: auth(executor) }, async (port) => {
    const res = await request(port, {
      method: "POST",
      path: "/v1/tasks",
      headers: {
        "x-aegis-tenant": "tenant-a",
        "content-type": "application/json",
        "content-length": String(64 * 1024 + 1),
      },
    });
    assert.equal(res.status, 413);
    assert.match(res.body, /AEGIS-API-004 PAYLOAD_TOO_LARGE/);
  });
});

test("rate-limit denial happens before application execution", async () => {
  let calls = 0;
  await withServer({
    executeTask: async () => { calls += 1; return { status: "ACCEPTED" }; },
    authenticateRequest: auth(executor),
    rateLimitRequest: async () => ({ allowed: false, retryAfterSeconds: 7 }),
  }, async (port) => {
    const res = await request(port, {
      method: "POST",
      path: "/v1/tasks",
      headers: { "x-aegis-tenant": "tenant-a", "content-type": "application/json" },
      body: JSON.stringify({ goal: "x", owner: "owner", responsibility: "r" }),
    });
    assert.equal(res.status, 429);
    assert.equal(res.headers["retry-after"], "7");
    assert.equal(calls, 0);
  });
});

test("unknown route returns bounded error without reflecting attacker input", async () => {
  await withServer({ executeTask: async () => ({ status: "ACCEPTED" }) }, async (port) => {
    const attacker = "<script>alert(1)</script>";
    const res = await request(port, { path: `/not-found?x=${encodeURIComponent(attacker)}` });
    assert.equal(res.status, 404);
    assert.deepEqual(JSON.parse(res.body), { code: "AEGIS-API-404 NOT_FOUND" });
    assert.equal(res.body.includes(attacker), false);
  });
});
