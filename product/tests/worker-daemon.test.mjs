import test from "node:test";
import assert from "node:assert/strict";
import { createWorker } from "../apps/worker/index.js";
import { createDaemon } from "../apps/daemon/index.js";

test("worker executes one envelope and acknowledges result", async()=>{
  const calls=[];
  const worker=createWorker({ dequeue:async()=>({id:"e1",command:{task:{id:"t1"}}}), executeTask:async()=>({status:"COMPLETED"}), ack:async(e,r)=>calls.push(["ack",e.id,r.status]), fail:async()=>calls.push(["fail"]) });
  const result=await worker.runOnce();
  assert.equal(result.status,"ACKED");
  assert.deepEqual(calls,[["ack","e1","COMPLETED"]]);
});

test("worker routes execution errors to fail without false ack", async()=>{
  const calls=[];
  const worker=createWorker({ dequeue:async()=>({id:"e2",command:{}}), executeTask:async()=>{throw new Error("boom");}, ack:async()=>calls.push("ack"), fail:async(e)=>calls.push(["fail",e.id]) });
  const result=await worker.runOnce();
  assert.equal(result.status,"FAILED");
  assert.deepEqual(calls,[["fail","e2"]]);
});

test("daemon tick delegates to worker and exposes health projection", async()=>{
  let runs=0;
  const daemon=createDaemon({ worker:{runOnce:async()=>{runs+=1;return{status:"IDLE"};}}, health:()=>({status:"HEALTHY"}) });
  const first=await daemon.tick(); const second=await daemon.tick();
  assert.equal(first.tick,1); assert.equal(second.tick,2); assert.equal(runs,2); assert.deepEqual(daemon.health(),{status:"HEALTHY",ticks:2});
});
