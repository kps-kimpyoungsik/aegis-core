import test from "node:test";
import assert from "node:assert/strict";
import { evaluateIncidentResponseReadiness } from "@aegis/release-convergence";

const completeEvidence = Object.freeze({
  runbookRef: "runbook://incident-response/v1",
  incidentOwner: "role://incident-commander",
  severityModelRef: "policy://incident-severity/v1",
  escalationRef: "runbook://incident-escalation/v1",
  communicationPlanRef: "runbook://incident-communications/v1",
  rollbackRunbookRef: "runbook://production-rollback/v1",
  forensicEvidenceRef: "policy://forensic-evidence-retention/v1",
  exerciseEvidenceRef: "evidence://incident-drill/2026-09-06",
  rollbackVerified: true,
  exercisePassed: true,
  postmortemProcessVerified: true,
});

test("incident response readiness fails closed without evidence", () => {
  const result = evaluateIncidentResponseReadiness({});
  assert.equal(result.passed, false);
  assert.equal(result.decision, "INCIDENT_RESPONSE_NOT_READY");
  assert.ok(result.reasons.includes("MISSING_RUNBOOK_REF"));
  assert.ok(result.reasons.includes("ROLLBACK_NOT_VERIFIED"));
  assert.ok(result.reasons.includes("EXERCISE_NOT_PASSED"));
});

test("incident response readiness requires exercise and rollback verification", () => {
  const result = evaluateIncidentResponseReadiness({
    ...completeEvidence,
    rollbackVerified: false,
    exercisePassed: false,
  });
  assert.equal(result.passed, false);
  assert.ok(result.reasons.includes("ROLLBACK_NOT_VERIFIED"));
  assert.ok(result.reasons.includes("EXERCISE_NOT_PASSED"));
});

test("incident response readiness requires postmortem process evidence", () => {
  const result = evaluateIncidentResponseReadiness({
    ...completeEvidence,
    postmortemProcessVerified: false,
  });
  assert.equal(result.passed, false);
  assert.ok(result.reasons.includes("POSTMORTEM_PROCESS_NOT_VERIFIED"));
});

test("complete externally verified evidence becomes readiness candidate", () => {
  const result = evaluateIncidentResponseReadiness(completeEvidence);
  assert.deepEqual(result, {
    passed: true,
    decision: "INCIDENT_RESPONSE_READY",
    reasons: [],
  });
});
