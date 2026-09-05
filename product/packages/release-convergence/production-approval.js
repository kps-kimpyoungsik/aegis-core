/**
 * Production approval is a protected external decision. This module only
 * validates an approval envelope against the exact candidate being promoted.
 * It never creates or self-authorizes approval.
 *
 * @param {Record<string, unknown>} envelope
 * @param {{revision?: string, digest?: string, now?: string}} candidate
 * @param {(envelope: Record<string, unknown>) => boolean} verifyEnvelope
 */
export function evaluateProductionApprovalEnvelope(
  envelope = {},
  candidate = {},
  verifyEnvelope = () => false,
) {
  const reasons = [];
  const approvedRevision = typeof envelope.approvedRevision === "string" ? envelope.approvedRevision : "";
  const approvedDigest = typeof envelope.approvedDigest === "string" ? envelope.approvedDigest : "";
  const approverIdentity = typeof envelope.approverIdentity === "string" ? envelope.approverIdentity.trim() : "";
  const authorityRef = typeof envelope.authorityRef === "string" ? envelope.authorityRef.trim() : "";
  const issuedAt = typeof envelope.issuedAt === "string" ? Date.parse(envelope.issuedAt) : Number.NaN;
  const expiresAt = typeof envelope.expiresAt === "string" ? Date.parse(envelope.expiresAt) : Number.NaN;
  const now = typeof candidate.now === "string" ? Date.parse(candidate.now) : Number.NaN;

  if (envelope.status !== "APPROVED") reasons.push("STATUS_NOT_APPROVED");
  if (!approverIdentity) reasons.push("APPROVER_IDENTITY_MISSING");
  if (!authorityRef) reasons.push("AUTHORITY_REF_MISSING");
  if (!candidate.revision || approvedRevision !== candidate.revision) reasons.push("REVISION_MISMATCH");
  if (!candidate.digest || approvedDigest !== candidate.digest) reasons.push("DIGEST_MISMATCH");
  if (envelope.rollbackReady !== true) reasons.push("ROLLBACK_NOT_READY");
  if (!Number.isFinite(issuedAt)) reasons.push("ISSUED_AT_INVALID");
  if (!Number.isFinite(expiresAt)) reasons.push("EXPIRES_AT_INVALID");
  if (!Number.isFinite(now)) reasons.push("CURRENT_TIME_INVALID");
  if (Number.isFinite(issuedAt) && Number.isFinite(now) && issuedAt > now) reasons.push("APPROVAL_NOT_YET_VALID");
  if (Number.isFinite(expiresAt) && Number.isFinite(now) && expiresAt <= now) reasons.push("APPROVAL_EXPIRED");

  let verifierPassed = false;
  try {
    verifierPassed = verifyEnvelope(envelope) === true;
  } catch {
    verifierPassed = false;
  }
  if (!verifierPassed) reasons.push("INDEPENDENT_VERIFICATION_FAILED");

  const passed = reasons.length === 0;
  return Object.freeze({
    passed,
    decision: passed ? "PRODUCTION_APPROVED" : "PRODUCTION_NOT_APPROVED",
    reasons: Object.freeze(reasons),
  });
}
