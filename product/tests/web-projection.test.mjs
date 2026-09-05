import test from "node:test";
import assert from "node:assert/strict";
import { projectRuntimeSnapshot } from "../apps/web-console/src/projection.js";

test("React projection derives operational counts without mutating canonical state",()=>{
  const snapshot={tasks:[{id:"a",goal:"A",status:"RUNNING",version:2},{id:"b",goal:"B",status:"FAILED",version:3}],events:[{type:"TASK_RUNNING"},{type:"FAILURE"}]};
  const projected=projectRuntimeSnapshot(snapshot);
  assert.deepEqual(projected.totals,{tasks:2,active:1,failures:1,events:2});
  assert.deepEqual(snapshot.tasks.map(t=>t.status),["RUNNING","FAILED"]);
  assert.ok(projected.controls.includes("Rollback"));
});
