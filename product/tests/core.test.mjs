import test from "node:test";
import assert from "node:assert/strict";
import { createTask, transitionTask } from "../packages/core-domain/index.js";
import { preflight } from "../packages/responsibility/index.js";
test("task version increments on state transition",()=>{const t0=createTask({id:"t1",goal:"ship",owner:"core"});const t1=transitionTask(t0,"READY");assert.equal(t1.version,1);assert.equal(t1.status,"READY");});
test("duplicate responsibility routes to handoff",()=>{const result=preflight({responsibility:"storage",requestedOwner:"session-b",activeClaims:[{responsibility:"storage",owner:"session-a",status:"ACTIVE"}]});assert.deepEqual(result,{overlap:"D3",decision:"HANDOFF"});});
