import test from "node:test";
import assert from "node:assert/strict";
import { createTask } from "@aegis/core-domain";
import { createMemoryItem } from "@aegis/memory-runtime";
import { MemoryKinds } from "@aegis/memory-contracts";
import { executeTaskLifecycle } from "@aegis/application-runtime";

const task = () => createTask({ id:"task-r06", goal:"integrate runtime", owner:"session-r06" });
const memory = (id,key,quality=.8) => createMemoryItem({ id, kind:MemoryKinds.EPISODIC, scope:"PROJECT", content:id, key, quality, freshness:1, trust:.9, provenance:{sourceRef:id,sourceType:"PAST_TASK"} });

test("golden lifecycle completes and captures validated episode", async()=>{
  const r=await executeTaskLifecycle({ task:task(), responsibility:"application-runtime", owner:"session-r06", memoryItems:[memory("m1","runtime"),memory("m2","other")], retrievalQuery:{key:"runtime"}, retrievalPolicy:{maxItems:1}, runHarness:async({pointContext})=>({status:"COMPLETED",output:{used:pointContext.length}}) });
  assert.equal(r.status,"COMPLETED"); assert.equal(r.task.status,"COMPLETED"); assert.equal(r.pointContext.length,1); assert.equal(r.memory.kind,MemoryKinds.EPISODIC); assert.ok(r.events.some(e=>e.type==="TASK_COMPLETED"));
});

test("duplicate ownership routes to handoff before harness execution", async()=>{
  let called=0; const r=await executeTaskLifecycle({ task:task(), responsibility:"storage-runtime", owner:"session-r06", activeClaims:[{responsibility:"storage-runtime",owner:"session-storage",status:"ACTIVE"}], runHarness:async()=>{called++;return{};} });
  assert.equal(r.status,"HANDOFF_REQUIRED"); assert.equal(called,0); assert.equal(r.task.status,"NEW");
});

test("harness failure produces FAILED task and failure memory", async()=>{
  const err=Object.assign(new Error("tool timeout"),{code:"AEGIS-HARNESS-TEST"}); const r=await executeTaskLifecycle({ task:task(), responsibility:"application-runtime", owner:"session-r06", runHarness:async()=>{throw err;} });
  assert.equal(r.status,"FAILED"); assert.equal(r.task.status,"FAILED"); assert.equal(r.memory.kind,MemoryKinds.FAILURE); assert.equal(r.memory.content.code,"AEGIS-HARNESS-TEST"); assert.ok(r.events.some(e=>e.type==="FAILURE"));
});

test("successful task stops at EPISODIC memory and does not auto-promote semantic knowledge", async()=>{
  const r=await executeTaskLifecycle({ task:task(), responsibility:"application-runtime", owner:"session-r06", runHarness:async()=>({status:"COMPLETED",output:"ok"}) });
  assert.equal(r.memory.kind,MemoryKinds.EPISODIC); assert.notEqual(r.memory.kind,MemoryKinds.SEMANTIC);
});

test("event path preserves ownership -> retrieval -> running -> validation -> completion ordering", async()=>{
  const r=await executeTaskLifecycle({ task:task(), responsibility:"application-runtime", owner:"session-r06", runHarness:async()=>({status:"COMPLETED"}) });
  assert.deepEqual(r.events.map(e=>e.type),["OWNERSHIP_PREFLIGHT","MEMORY_RETRIEVED","TASK_RUNNING","VALIDATION","TASK_COMPLETED"]);
});
