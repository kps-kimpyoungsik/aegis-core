import test from "node:test";
import assert from "node:assert/strict";
import { evaluateProductionApprovalEnvelope } from "@aegis/release-convergence";

const candidate = Object.freeze({
  revision: "688d97d49a067d710c99c59253e601bf139a3c63",
  digest: "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
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
    { ...approvedEnvelope, approvedRevision: "other-revision", approvedDigest: "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" },
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

test("production approval rejects invalid or expired validity windows", () => {
  const invalidWindow = evaluateProductionApprovalEnvelope(
    { ...approvedEnvelope, issuedAt: "2026-09-06T01:00:00Z", expiresAt: "2026-09-06T00:30:00Z" },
    candidate,
    () => true,
  );
  assert.equal(invalidWindow.passed, false);
  assert.ok(invalidWindow.reasons.includes("APPROVAL_WINDOW_INVALID"));

  const expired = evaluateProductionApprovalEnvelope(
    { ...approvedEnvelope, expiresAt: "2026-09-05T23:59:59Z" },
    candidate,
    () => true,
  );
  assert.equal(expired.passed, false);
  assert.ok(expired.reasons.includes("APPROVAL_EXPIRED"));
});

test("production approval accepts only an exact current verified external envelope", () => {
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
