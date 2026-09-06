import test from "node:test";
import assert from "node:assert/strict";
import {
  classifyWorkstreamOverlap,
  validateControlPlane,
} from "../tools/workstream-collision-check.mjs";

function fixture() {
  return {
    ownership: {
      responsibilities: [
        { id: "owner-a", owner: "pkg-a" },
        { id: "owner-b", owner: "pkg-b" },
      ],
    },
    domains: {
      domains: [
        { id: "domain-a", ownerResponsibilities: ["owner-a"] },
        { id: "domain-b", ownerResponsibilities: ["owner-b"] },
      ],
    },
    capabilities: {
      capabilities: [
        { id: "cap-a", domain: "domain-a", ownerResponsibility: "owner-a" },
        { id: "cap-b", domain: "domain-b", ownerResponsibility: "owner-b" },
      ],
    },
    workstreams: [
      {
        id: "ws-a",
        state: "ACTIVE",
        ownerResponsibility: "owner-a",
        capabilityIds: ["cap-a"],
        touchPaths: ["a/"],
      },
      {
        id: "ws-ref",
        state: "REFERENCE",
        ownerResponsibility: "owner-a",
        capabilityIds: ["cap-a"],
        touchPaths: ["a/"],
      },
    ],
  };
}

function capabilityMap() {
  return new Map([
    ["cap-a", { id: "cap-a", domain: "domain-a", ownerResponsibility: "owner-a" }],
    ["cap-a2", { id: "cap-a2", domain: "domain-a", ownerResponsibility: "owner-a" }],
    ["cap-b", { id: "cap-b", domain: "domain-b", ownerResponsibility: "owner-b" }],
  ]);
}

test("reference workstream may overlap active implementation", () =>
  assert.equal(validateControlPlane(fixture()).activeWorkstreams, 1));

test("duplicate capability fails closed", () => {
  const data = fixture();
  data.capabilities.capabilities.push({
    id: "cap-a",
    domain: "domain-a",
    ownerResponsibility: "owner-a",
  });
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-003/);
});

test("owner mismatch fails closed", () => {
  const data = fixture();
  data.workstreams[0].ownerResponsibility = "owner-b";
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-009/);
});

test("capability owner outside declared domain fails closed", () => {
  const data = fixture();
  data.capabilities.capabilities[0].ownerResponsibility = "owner-b";
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-013/);
});

test("same active capability fails closed", () => {
  const data = fixture();
  data.workstreams.push({
    id: "ws-c",
    state: "ACTIVE_CANDIDATE",
    ownerResponsibility: "owner-a",
    capabilityIds: ["cap-a"],
    touchPaths: ["c/"],
  });
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-010/);
});

test("overlapping active canonical path with different owner fails closed", () => {
  const data = fixture();
  data.workstreams.push({
    id: "ws-c",
    state: "ACTIVE_CANDIDATE",
    ownerResponsibility: "owner-b",
    capabilityIds: ["cap-b"],
    touchPaths: ["a/sub/"],
  });
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-011/);
});

test("D0 classifies independent owner/domain/path work", () => {
  const result = classifyWorkstreamOverlap(
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["a/"] },
    { ownerResponsibility: "owner-b", capabilityIds: ["cap-b"], touchPaths: ["b/"] },
    capabilityMap(),
  );
  assert.deepEqual({ level: result.level, decision: result.decision }, { level: "D0", decision: "EXECUTE" });
});

test("D1 classifies related same-domain work without shared capability or path", () => {
  const result = classifyWorkstreamOverlap(
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["a/"] },
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a2"], touchPaths: ["other/"] },
    capabilityMap(),
  );
  assert.equal(result.level, "D1");
});

test("D2 classifies same-owner intersecting paths", () => {
  const result = classifyWorkstreamOverlap(
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["a/"] },
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a2"], touchPaths: ["a/sub/"] },
    capabilityMap(),
  );
  assert.deepEqual({ level: result.level, decision: result.decision }, { level: "D2", decision: "SPLIT" });
});

test("D3 classifies duplicate responsibility on shared capability", () => {
  const result = classifyWorkstreamOverlap(
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["a/"] },
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["b/"] },
    capabilityMap(),
  );
  assert.deepEqual({ level: result.level, decision: result.decision }, { level: "D3", decision: "HANDOFF" });
});

test("D4 classifies conflicting mutation across owners", () => {
  const result = classifyWorkstreamOverlap(
    { ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["shared/"] },
    { ownerResponsibility: "owner-b", capabilityIds: ["cap-b"], touchPaths: ["shared/sub/"] },
    capabilityMap(),
  );
  assert.deepEqual({ level: result.level, decision: result.decision }, { level: "D4", decision: "FREEZE" });
});
