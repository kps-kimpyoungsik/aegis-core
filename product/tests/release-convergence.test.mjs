import test from "node:test";
import assert from "node:assert/strict";
import {
  GAGates,
  evaluateGAPromotion,
  CanonicalReleaseOwnerRegistry,
  decideCanaryPromotion,
  PublicOpenEvidence,
  evaluatePublicOpen,
} from "@aegis/release-convergence";

test("GA promotion fails closed when any gate is not PASS", () => {
  const statuses = Object.fromEntries(GAGates.map((gate) => [gate, "PASS"]));
  statuses.G3_SECURITY = "NOT_EXECUTED";
  const result = evaluateGAPromotion(statuses);
  assert.equal(result.passed, false);
  assert.equal(result.decision, "NOT_PROMOTED");
  assert.deepEqual(result.missing, ["G3_SECURITY"]);
});

test("GA promotion passes only when all gates PASS", () => {
  const statuses = Object.fromEntries(GAGates.map((gate) => [gate, "PASS"]));
  assert.equal(evaluateGAPromotion(statuses).decision, "GA_PROMOTE");
});

test("canonical release owner registry blocks second owner", () => {
  const registry = new CanonicalReleaseOwnerRegistry();
  registry.register({ assetId: "a", responsibility: "release", canonicalOwner: "@aegis/release-convergence" });
  assert.throws(
    () => registry.register({ assetId: "b", responsibility: "release", canonicalOwner: "@aegis/other" }),
    /AEGIS-REL-003/,
  );
});

test("canary rolls back on a security event", () => {
  assert.equal(decideCanaryPromotion("INTERNAL", {
    securityEvents: 1,
    errorRateDelta: 0,
    p95LatencyDeltaMs: 0,
    rollbackReady: true,
  }).action, "ROLLBACK");
});

test("canary promotes only with clean metrics and rollback readiness", () => {
  assert.deepEqual(decideCanaryPromotion("INTERNAL", {
    securityEvents: 0,
    errorRateDelta: 0,
    p95LatencyDeltaMs: 0,
    rollbackReady: true,
  }), { action: "PROMOTE", level: "LIMITED_CUSTOMER" });
});

test("public open remains blocked when operational evidence is incomplete", () => {
  const evidence = Object.fromEntries(PublicOpenEvidence.map((key) => [key, true]));
  evidence.incidentResponseReady = false;
  assert.equal(evaluatePublicOpen(evidence).decision, "PUBLIC_OPEN_BLOCKED");
});
