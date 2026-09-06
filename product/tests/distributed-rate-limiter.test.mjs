import test from "node:test";
import assert from "node:assert/strict";
import { createDistributedFixedWindowRateLimiter } from "../apps/api-server/distributed-rate-limiter.js";

function createAtomicStore() {
  const counters = new Map();
  const calls = [];
  return {
    calls,
    async increment(input) {
      calls.push(input);
      const identity = `${input.key}\u0000${input.windowId}`;
      const count = (counters.get(identity) ?? 0) + 1;
      counters.set(identity, count);
      return { count, retryAfterMs: input.ttlMs };
    },
  };
}

const principal = { id: "principal:executor", tenantId: "tenant-a" };

test("distributed limiter reuses the existing request-rate contract with an atomic store", async () => {
  let now = 1_000;
  const store = createAtomicStore();
  const limit = createDistributedFixedWindowRateLimiter({ limit: 2, store, now: () => now });
  const request = () => limit({ principal, method: "POST", url: "/v1/tasks" });

  assert.deepEqual(await request(), { allowed: true, remaining: 1 });
  assert.deepEqual(await request(), { allowed: true, remaining: 0 });
  assert.deepEqual(await request(), { allowed: false, retryAfterSeconds: 59 });
  assert.equal(store.calls.length, 3);
  assert.equal(store.calls[0].key, "tenant-a\u0000principal:executor\u0000POST\u0000/v1/tasks");
  assert.equal(store.calls[0].windowId, 0);

  now = 61_000;
  assert.deepEqual(await request(), { allowed: true, remaining: 1 });
  assert.equal(store.calls.at(-1).windowId, 1);
});

test("distributed limiter isolates tenant principal method and route dimensions", async () => {
  const store = createAtomicStore();
  const limit = createDistributedFixedWindowRateLimiter({ limit: 1, store, now: () => 1_000 });

  assert.equal((await limit({ principal, method: "GET", url: "/v1/runtime/snapshot" })).allowed, true);
  assert.equal((await limit({ principal, method: "POST", url: "/v1/tasks" })).allowed, true);
  assert.equal((await limit({ principal: { ...principal, tenantId: "tenant-b" }, method: "POST", url: "/v1/tasks" })).allowed, true);
  assert.equal((await limit({ principal: { ...principal, id: "principal:other" }, method: "POST", url: "/v1/tasks" })).allowed, true);
  assert.equal(store.calls.length, 4);
});

test("distributed limiter fails closed when the atomic store contract is absent or invalid", async () => {
  assert.throws(
    () => createDistributedFixedWindowRateLimiter({ limit: 1 }),
    /AEGIS-API-023 DISTRIBUTED_RATE_STORE_REQUIRED/,
  );

  const invalidStore = { async increment() { return { count: 0 }; } };
  const limit = createDistributedFixedWindowRateLimiter({ limit: 1, store: invalidStore });
  await assert.rejects(
    () => limit({ principal, method: "POST", url: "/v1/tasks" }),
    /AEGIS-API-025 INVALID_DISTRIBUTED_RATE_RESULT/,
  );
});
