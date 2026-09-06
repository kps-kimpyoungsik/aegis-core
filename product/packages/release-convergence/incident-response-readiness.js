const RequiredIncidentResponseFields = Object.freeze([
  "runbookRef",
  "incidentOwner",
  "severityModelRef",
  "escalationRef",
  "communicationPlanRef",
  "rollbackRunbookRef",
  "forensicEvidenceRef",
  "exerciseEvidenceRef",
]);

/** @param {unknown} value */
function nonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

/**
 * Evaluate whether incident-response evidence is structurally ready for
 * production promotion. This function validates externally produced evidence;
 * it never creates an incident exercise, assigns production authority, or
 * declares operational readiness by itself.
 *
 * @param {Record<string, unknown>} evidence
 */
export function evaluateIncidentResponseReadiness(evidence = {}) {
  const missing = RequiredIncidentResponseFields.filter((field) => !nonEmptyString(evidence[field]));
  const reasons = missing.map((field) => `MISSING_${field.replace(/[A-Z]/g, (match) => `_${match}`).toUpperCase()}`);

  if (evidence.rollbackVerified !== true) reasons.push("ROLLBACK_NOT_VERIFIED");
  if (evidence.exercisePassed !== true) reasons.push("EXERCISE_NOT_PASSED");
  if (evidence.postmortemProcessVerified !== true) reasons.push("POSTMORTEM_PROCESS_NOT_VERIFIED");

  const passed = reasons.length === 0;
  return Object.freeze({
    passed,
    decision: passed ? "INCIDENT_RESPONSE_READY" : "INCIDENT_RESPONSE_NOT_READY",
    reasons: Object.freeze(reasons),
  });
}
