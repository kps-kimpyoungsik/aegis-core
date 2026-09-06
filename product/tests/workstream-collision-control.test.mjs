import test from "node:test";
import assert from "node:assert/strict";
import { validateControlPlane } from "../tools/workstream-collision-check.mjs";

function fixture() {
  return {
    ownership: { responsibilities: [{ id: "owner-a", owner: "pkg-a" }, { id: "owner-b", owner: "pkg-b" }] },
    domains: { domains: [{ id: "domain-a", ownerResponsibilities: ["owner-a"] }, { id: "domain-b", ownerResponsibilities: ["owner-b"] }] },
    capabilities: { capabilities: [
      { id: "cap-a", domain: "domain-a", ownerResponsibility: "owner-a" },
      { id: "cap-b", domain: "domain-b", ownerResponsibility: "owner-b" }
    ] },
    ledger: { workstreams: [
      { id: "ws-a", state: "ACTIVE", ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["a/"] },
      { id: "ws-reference", state: "REFERENCE", ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["a/"] }
    ] }
  };
}

test("reference workstream may overlap active implementation without becoming canonical", () => {
  const result = validateControlPlane(fixture());
  assert.equal(result.activeWorkstreams, 1);
});

test("duplicate capability definition fails closed", () => {
  const data = fixture();
  data.capabilities.capabilities.push({ id: "cap-a", domain: "domain-a", ownerResponsibility: "owner-a" });
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-003 DUPLICATE_CAPABILITY/);
});

test("unknown capability owner fails closed", () => {
  const data = fixture();
  data.capabilities.capabilities[0].ownerResponsibility = "missing-owner";
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-005 UNKNOWN_CAPABILITY_OWNER/);
});

test("workstream cannot claim capability owned by another responsibility", () => {
  const data = fixture();
  data.ledger.workstreams[0].ownerResponsibility = "owner-b";
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-009 OWNER_CAPABILITY_MISMATCH/);
});

test("two active sessions cannot implement the same capability", () => {
  const data = fixture();
  data.ledger.workstreams.push({ id: "ws-c", state: "ACTIVE_CANDIDATE", ownerResponsibility: "owner-a", capabilityIds: ["cap-a"], touchPaths: ["c/"] });
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-010 ACTIVE_CAPABILITY_COLLISION/);
});

test("two active sessions cannot mutate overlapping canonical paths", () => {
  const data = fixture();
  data.ledger.workstreams.push({ id: "ws-c", state: "ACTIVE_CANDIDATE", ownerResponsibility: "owner-b", capabilityIds: ["cap-b"], touchPaths: ["a/subpath/"] });
  assert.throws(() => validateControlPlane(data), /AEGIS-WS-011 ACTIVE_PATH_COLLISION/);
});
