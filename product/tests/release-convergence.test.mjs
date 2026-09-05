import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
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
  assert.throws(() => registry.register({ assetId: "b", responsibility: "release", canonicalOwner: "@aegis/other" }), /AEGIS-REL-003/);
});

test("canary rollback rule remains active", () => {
  assert.equal(decideCanaryPromotion("INTERNAL", { securityEvents: 1, errorRateDelta: 0, p95LatencyDeltaMs: 0, rollbackReady: true }).action, "ROLLBACK");
});

test("canary promotes only with clean metrics and rollback readiness", () => {
  assert.deepEqual(decideCanaryPromotion("INTERNAL", { securityEvents: 0, errorRateDelta: 0, p95LatencyDeltaMs: 0, rollbackReady: true }), { action: "PROMOTE", level: "LIMITED_CUSTOMER" });
});

test("public open remains blocked when operational evidence is incomplete", () => {
  const evidence = Object.fromEntries(PublicOpenEvidence.map((key) => [key, true]));
  evidence.incidentResponseReady = false;
  assert.equal(evaluatePublicOpen(evidence).decision, "PUBLIC_OPEN_BLOCKED");
});

test("canonical R1.10 GA evidence matrix matches fail-closed promotion policy", () => {
  const matrix = JSON.parse(fs.readFileSync(new URL("../release/r1.10-ga-evidence-matrix.json", import.meta.url), "utf8"));
  assert.deepEqual(Object.keys(matrix.gates).sort(), [...GAGates].sort());
  const result = evaluateGAPromotion(matrix.gates);
  assert.equal(result.decision, matrix.decision);
  assert.deepEqual(result.missing, matrix.missing);
  assert.deepEqual(result.missing, ["G3_SECURITY", "G8_PRODUCTION_APPROVAL"]);
  assert.equal(matrix.gates.G3_SECURITY, "NOT_EXECUTED");
  assert.equal(matrix.gates.G4_DATA_SAFETY, "PASS");
  assert.equal(matrix.gates.G8_PRODUCTION_APPROVAL, "NOT_EXECUTED");
});
