import test from "node:test";
import assert from "node:assert/strict";
process.env.NODE_ENV="test";
const { server } = await import("../apps/api-server/index.js");
test("health endpoints expose liveness and readiness", async (t)=>{ await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve)); t.after(()=>server.close()); const {port}=server.address(); const live=await fetch(`http://127.0.0.1:${port}/health/live`); const l=await live.json(); const ready=await fetch(`http://127.0.0.1:${port}/health/ready`); const r=await ready.json(); assert.equal(l.status,"HEALTHY"); assert.equal(r.status,"READY"); });
