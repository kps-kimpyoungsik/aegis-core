import test from "node:test";
import assert from "node:assert/strict";
import { createApiServer, createStaticBearerAuthorizer } from "../apps/api-server/index.js";

async function withServer(options, fn) {
  const server = createApiServer(options);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  try { await fn(`http://127.0.0.1:${port}`); }
  finally { await new Promise((resolve) => server.close(resolve)); }
}

test("typed task command reaches injected application use case", async () => {
  let received;
  await withServer({ executeTask: async (input) => { received = input; return { status:"COMPLETED", task:{...input.task,status:"COMPLETED"} }; }, idFactory:()=>"api-task-1" }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json"}, body:JSON.stringify({goal:"ship R0.7",owner:"ops",responsibility:"application-runtime"}) });
    assert.equal(response.status, 202);
    assert.equal(received.task.id, "api-task-1");
    assert.equal(received.responsibility, "application-runtime");
  });
});

test("invalid task command is rejected before application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};} }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json"}, body:JSON.stringify({goal:"missing owner"}) });
    assert.equal(response.status, 400);
    assert.equal(calls, 0);
  });
});

test("runtime snapshot is a read-only projection endpoint", async () => {
  await withServer({ executeTask: async()=>({status:"COMPLETED"}), getSnapshot:()=>({tasks:[{id:"t1",status:"RUNNING"}],events:[{type:"TASK_RUNNING"}]}) }, async (base) => {
    const response = await fetch(`${base}/v1/runtime/snapshot`);
    assert.equal(response.status,200);
    const body=await response.json();
    assert.equal(body.tasks[0].status,"RUNNING");
    assert.equal(body.events[0].type,"TASK_RUNNING");
  });
});

test("task command rejects non-JSON content type before application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};} }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"text/plain"}, body:"{}" });
    assert.equal(response.status, 415);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-003 JSON_CONTENT_TYPE_REQUIRED" });
    assert.equal(calls, 0);
  });
});

test("malformed JSON is rejected as a client error", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};} }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json; charset=utf-8"}, body:'{"goal":' });
    assert.equal(response.status, 400);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-005 MALFORMED_JSON" });
    assert.equal(calls, 0);
  });
});

test("oversized JSON is rejected before application execution", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};} }, async (base) => {
    const body = JSON.stringify({ goal:"x".repeat(70 * 1024), owner:"ops", responsibility:"application-runtime" });
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json"}, body });
    assert.equal(response.status, 413);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-004 PAYLOAD_TOO_LARGE" });
    assert.equal(calls, 0);
  });
});

test("internal execution errors do not expose exception details", async () => {
  await withServer({ executeTask: async()=>{throw new Error("database password=do-not-leak");} }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json"}, body:JSON.stringify({goal:"fail safely",owner:"ops",responsibility:"application-runtime"}) });
    assert.equal(response.status, 500);
    const body = await response.json();
    assert.deepEqual(body, { code:"AEGIS-API-500 INTERNAL_ERROR" });
    assert.equal(JSON.stringify(body).includes("do-not-leak"), false);
  });
});

test("protected API fails closed when authentication is not configured", async () => {
  let calls=0;
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authorizeRequest:createStaticBearerAuthorizer(undefined) }, async (base) => {
    const health = await fetch(`${base}/health/ready`);
    assert.equal(health.status, 200);
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"content-type":"application/json"}, body:JSON.stringify({goal:"must not execute",owner:"ops",responsibility:"application-runtime"}) });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-SEC-001 AUTH_NOT_CONFIGURED" });
    assert.equal(calls, 0);
  });
});

test("invalid bearer token is rejected before body parsing and execution", async () => {
  let calls=0;
  const expectedToken="r1.11-test-token-0123456789";
  await withServer({ executeTask: async()=>{calls+=1;return{};}, authorizeRequest:createStaticBearerAuthorizer(expectedToken) }, async (base) => {
    const response = await fetch(`${base}/v1/tasks`, { method:"POST", headers:{"authorization":"Bearer wrong-token-0123456789","content-type":"text/plain"}, body:"not-json" });
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { code:"AEGIS-API-SEC-003 INVALID_TOKEN" });
    assert.equal(calls, 0);
  });
});

test("valid bearer token permits protected task and snapshot routes", async () => {
  const expectedToken="r1.11-test-token-0123456789";
  let calls=0;
  const authorizeRequest=createStaticBearerAuthorizer(expectedToken);
  await withServer({
    executeTask: async()=>{calls+=1;return{status:"COMPLETED"};},
    getSnapshot:()=>({tasks:[{id:"secure-task"}],events:[]}),
    authorizeRequest,
  }, async (base) => {
    const headers={"authorization":`Bearer ${expectedToken}`};
    const snapshot = await fetch(`${base}/v1/runtime/snapshot`, { headers });
    assert.equal(snapshot.status, 200);
    assert.equal((await snapshot.json()).tasks[0].id, "secure-task");

    const task = await fetch(`${base}/v1/tasks`, {
      method:"POST",
      headers:{...headers,"content-type":"application/json"},
      body:JSON.stringify({goal:"authorized",owner:"ops",responsibility:"application-runtime"}),
    });
    assert.equal(task.status, 202);
    assert.equal(calls, 1);
  });
});
