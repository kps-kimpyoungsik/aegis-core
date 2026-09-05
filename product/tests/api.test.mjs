import test from "node:test";
import assert from "node:assert/strict";
process.env.NODE_ENV="test";
process.env.AEGIS_API_BEARER_TOKEN="r1.10-test-bearer-token-not-secret-0001";
process.env.AEGIS_API_PRINCIPAL_ID="principal:api-test";
process.env.AEGIS_API_ROLES="RUNTIME_VIEWER";
const { server } = await import("../apps/api-server/index.js");

test("health endpoints remain public and configured bearer unlocks protected snapshot", async (t)=>{
  await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
  t.after(()=>server.close());
  const {port}=server.address();
  const base=`http://127.0.0.1:${port}`;

  const live=await fetch(`${base}/health/live`);
  const l=await live.json();
  const ready=await fetch(`${base}/health/ready`);
  const r=await ready.json();
  assert.equal(l.status,"HEALTHY");
  assert.equal(r.status,"READY");

  const unauthenticated=await fetch(`${base}/v1/runtime/snapshot`);
  assert.equal(unauthenticated.status,401);

  const authenticated=await fetch(`${base}/v1/runtime/snapshot`, {
    headers:{authorization:`Bearer ${process.env.AEGIS_API_BEARER_TOKEN}`},
  });
  assert.equal(authenticated.status,200);
});
