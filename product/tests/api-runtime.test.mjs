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
