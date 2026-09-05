import test from "node:test";
import assert from "node:assert/strict";
import { evaluateProductionApprovalEnvelope } from "@aegis/release-convergence";

const candidate = Object.freeze({
  revision: "sha256:source-revision",
  digest: "sha256:artifact-digest",
  now: "2026-09-06T00:00:00Z",
});

const approvedEnvelope = Object.freeze({
  status: "APPROVED",
  approverIdentity: "release-authority@example.invalid",
  authorityRef: "authority://production/release-board",
  approvedRevision: candidate.revision,
  approvedDigest: candidate.digest,
  rollbackReady: true,
  issuedAt: "2026-09-05T23:55:00Z",
  expiresAt: "2026-09-06T01:00:00Z",
  verifierPayloadRef: "evidence://approval-envelope/1",
});

test("production approval fails closed when approval envelope is absent", () => {
  const result = evaluateProductionApprovalEnvelope({}, candidate, () => true);
  assert.equal(result.passed, false);
  assert.equal(result.decision, "PRODUCTION_NOT_APPROVED");
  assert.ok(result.reasons.includes("STATUS_NOT_APPROVED"));
  assert.ok(result.reasons.includes("APPROVER_IDENTITY_MISSING"));
});

test("production approval rejects revision or digest substitution", () => {
  const result = evaluateProductionApprovalEnvelope(
    { ...approvedEnvelope, approvedRevision: "sha256:other-revision", approvedDigest: "sha256:other-digest" },
    candidate,
    () => true,
  );
  assert.equal(result.passed, false);
  assert.ok(result.reasons.includes("REVISION_MISMATCH"));
  assert.ok(result.reasons.includes("DIGEST_MISMATCH"));
});

test("production approval requires independent verifier success", () => {
  const result = evaluateProductionApprovalEnvelope(approvedEnvelope, candidate, () => false);
  assert.equal(result.passed, false);
  assert.ok(result.reasons.includes("INDEPENDENT_VERIFICATION_FAILED"));
});

test("production approval accepts only an exact, current, verified envelope", () => {
  const result = evaluateProductionApprovalEnvelope(approvedEnvelope, candidate, (envelope) => {
    assert.equal(envelope.verifierPayloadRef, "evidence://approval-envelope/1");
    return true;
  });
  assert.deepEqual(result, {
    passed: true,
    decision: "PRODUCTION_APPROVED",
    reasons: [],
  });
});
