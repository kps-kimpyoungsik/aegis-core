import test from "node:test";
import assert from "node:assert/strict";
import { createApiServer } from "../apps/api-server/index.js";

async function withServer(options, fn) {
  const server = createApiServer(options);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  try { await fn(`http://127.0.0.1:${port}`); }
  finally { await new Promise((resolve) => server.close(resolve)); }
}

const policy = {
  verifyBearerToken: async (token) => {
    if (token === "operator-token") return { subject:"user-1", tenantId:"tenant-a", roles:["operator"] };
    if (token === "viewer-token") return { subject:"user-2", tenantId:"tenant-a", roles:["viewer"] };
    throw new Error("invalid token");
  },
};

function taskRequest(base, token, tenant = "tenant-a") {
  const headers = { "content-type":"application/json", "x-aegis-tenant":tenant };
  if (token) headers.authorization = `Bearer ${token}`;
  return fetch(`${base}/v1/tasks`, {
    method:"POST",
    headers,
    body:JSON.stringify({ goal:"secure task", owner:"ops", responsibility:"application-runtime" }),
  });
}

test("protected task endpoint rejects missing bearer before application execution", async () => {
  let calls = 0;
  await withServer({ securityPolicy:policy, executeTask:async()=>{ calls += 1; return { status:"COMPLETED" }; } }, async (base) => {
    const response = await taskRequest(base, null);
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { code:"AEGIS-SEC-001 BEARER_REQUIRED" });
    assert.equal(calls, 0);
  });
});

test("tenant mismatch is denied before application execution", async () => {
  let calls = 0;
  await withServer({ securityPolicy:policy, executeTask:async()=>{ calls += 1; return { status:"COMPLETED" }; } }, async (base) => {
    const response = await taskRequest(base, "operator-token", "tenant-b");
    assert.equal(response.status, 403);
    assert.deepEqual(await response.json(), { code:"AEGIS-SEC-006 TENANT_MISMATCH" });
    assert.equal(calls, 0);
  });
});

test("viewer role cannot mutate tasks", async () => {
  let calls = 0;
  await withServer({ securityPolicy:policy, executeTask:async()=>{ calls += 1; return { status:"COMPLETED" }; } }, async (base) => {
    const response = await taskRequest(base, "viewer-token");
    assert.equal(response.status, 403);
    assert.deepEqual(await response.json(), { code:"AEGIS-SEC-007 ROLE_FORBIDDEN" });
    assert.equal(calls, 0);
  });
});

test("operator principal and tenant context reach the application boundary", async () => {
  let received;
  await withServer({ securityPolicy:policy, idFactory:()=>"secure-1", executeTask:async(input)=>{ received = input; return { status:"COMPLETED", task:input.task }; } }, async (base) => {
    const response = await taskRequest(base, "operator-token");
    assert.equal(response.status, 202);
    assert.equal(received.securityContext.principal.subject, "user-1");
    assert.equal(received.securityContext.tenantId, "tenant-a");
    assert.deepEqual(received.securityContext.principal.roles, ["operator"]);
  });
});

test("snapshot allows viewer but still requires matching tenant", async () => {
  await withServer({ securityPolicy:policy, executeTask:async()=>({status:"COMPLETED"}), getSnapshot:()=>({tasks:[],events:[]}) }, async (base) => {
    const response = await fetch(`${base}/v1/runtime/snapshot`, { headers:{ authorization:"Bearer viewer-token", "x-aegis-tenant":"tenant-a" } });
    assert.equal(response.status, 200);
  });
});
