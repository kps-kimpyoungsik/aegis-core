import test from "node:test";
import assert from "node:assert/strict";
import { createApiServer } from "../apps/api-server/index.js";

const TENANT_A = "tenant-a";
const executorAuthenticator = async () => ({ id:"principal:executor", tenantId:TENANT_A, roles:["TASK_EXECUTOR"] });
const viewerAuthenticator = async () => ({ id:"principal:viewer", tenantId:TENANT_A, roles:["RUNTIME_VIEWER"] });
const tenantHeaders = (extra = {}) => ({ "x-aegis-tenant":TENANT_A, ...extra });

async function withServer(options, fn) {
  const server = createApiServer(options);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  try { await fn(`http://127.0.0.1:${port}`); }
  finally { await new Promise((resolve) => server.close(resolve)); }
}

test("typed task command reaches injected application use case with tenant-bound principal", async () => {
  let received;
  await withServer({ executeTask: async (input) => { received = input; return { status:"COMPLETED", task:{...input.task,status:"COMPLETED"} }; }, authenticateRequest:executorAuthenticator, idFactory:()=>"api-task-1" }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json"}), body:JSON.stringify({goal:"ship R0.7",owner:"ops",responsibility:"application-runtime"}) });
    assert.equal(response.status, 202);
    assert.equal(received.task.id, "api-task-1");
    assert.equal(received.responsibility, "application-runtime");
    assert.deepEqual(received.principal, { id:"principal:executor", tenantId:TENANT_A, roles:["TASK_EXECUTOR"] });
  });
});

test("invalid task command is rejected before application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:executorAuthenticator }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json"}), body:JSON.stringify({goal:"missing owner"}) });
    assert.equal(response.status, 400);
    assert.equal(calls, 0);
  });
});

test("runtime snapshot is a read-only projection endpoint for matching viewer tenant", async () => {
  await withServer({ executeTask: async()=>({status:"COMPLETED"}), authenticateRequest:viewerAuthenticator, getSnapshot:()=>({tasks:[{id:"t1",status:"RUNNING"}],events:[{type:"TASK_RUNNING"}]}) }, async (base) => {
    const response = await fetch(`${base}/v1/runtime/snapshot`, { headers:tenantHeaders() });
    assert.equal(response.status,200);
    const body=await response.json();
    assert.equal(body.tasks[0].status,"RUNNING");
    assert.equal(body.events[0].type,"TASK_RUNNING");
  });
});

test("task command rejects unauthenticated request before tenant evaluation or application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};} }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json"}, body:"{}" });
    assert.equal(response.status, 401);
    assert.equal(response.headers.get("www-authenticate"), "Bearer");
    assert.deepEqual(await response.json(), { code:"AEGIS-API-006 AUTHENTICATION_REQUIRED" });
    assert.equal(calls, 0);
  });
});

test("principal without tenant identity is rejected as unauthenticated", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:async()=>({id:"principal:no-tenant",roles:["TASK_EXECUTOR"]}) }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json"}), body:"{}" });
    assert.equal(response.status, 401);
    assert.equal(calls, 0);
  });
});

test("forged bearer credential rejected by authenticator cannot reach application", async () => {
  let calls=0;
  const authenticateRequest = async (req) => req.headers.authorization === "Bearer verified-token" ? { id:"principal:verified", tenantId:TENANT_A, roles:["TASK_EXECUTOR"] } : null;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json","authorization":"Bearer forged-token"}), body:"{}" });
    assert.equal(response.status, 401);
    assert.equal(calls, 0);
  });
});

test("authenticated request without tenant context is rejected before application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:executorAuthenticator }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json"}, body:"{}" });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-013 TENANT_CONTEXT_REQUIRED" });
    assert.equal(calls, 0);
  });
});

test("tenant mismatch is denied before application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:executorAuthenticator }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json","x-aegis-tenant":"tenant-b"}, body:"{}" });
    assert.equal(response.status, 403);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-014 TENANT_MISMATCH" });
    assert.equal(calls, 0);
  });
});

test("viewer principal cannot execute task command", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:viewerAuthenticator }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json"}), body:"{}" });
    assert.equal(response.status, 403);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-007 FORBIDDEN" });
    assert.equal(calls, 0);
  });
});

test("runtime snapshot rejects unauthenticated request", async () => {
  await withServer({ executeTask: async()=>({status:"COMPLETED"}) }, async (base) => {
    const response = await fetch(`${base}/v1/runtime/snapshot`);
    assert.equal(response.status,401);
  });
});

test("task command rejects non-JSON content type after tenant authorization", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:executorAuthenticator }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"text/plain"}), body:"{}" });
    assert.equal(response.status, 415);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-003 JSON_CONTENT_TYPE_REQUIRED" });
    assert.equal(calls, 0);
  });
});

test("malformed JSON is rejected as a client error", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:executorAuthenticator }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json; charset=utf-8"}), body:'{"goal":' });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-005 MALFORMED_JSON" });
    assert.equal(calls, 0);
  });
});

test("oversized JSON is rejected before application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authenticateRequest:executorAuthenticator }, async (base) => {
    const body = JSON.stringify({ goal:"x".repeat(70 * 1024), owner:"ops", responsibility:"application-runtime" });
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json"}), body });
    assert.equal(response.status, 413);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-004 PAYLOAD_TOO_LARGE" });
    assert.equal(calls, 0);
  });
});

test("internal execution errors do not expose exception details", async () => {
  await withServer({ executeTask: async()=>{throw new Error("database password=do-not-leak");}, authenticateRequest:executorAuthenticator }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:tenantHeaders({"content-type":"application/json"}), body:JSON.stringify({goal:"fail safely",owner:"ops",responsibility:"application-runtime"}) });
    assert.equal(response.status, 500);
    const body = await response.json();
    assert.deepEqual(body, { code:"AEGIS-API-500 INTERNAL_ERROR" });
    assert.equal(JSON.stringify(body).includes("do-not-leak"), false);
  });
});
