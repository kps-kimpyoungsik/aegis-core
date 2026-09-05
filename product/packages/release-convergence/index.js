export const GAGates = Object.freeze([
  "G0_SOURCE_ARCHITECTURE",
  "G1_CLEAN_BUILD",
  "G2_ARTIFACT_SUPPLY_CHAIN",
  "G3_SECURITY",
  "G4_DATA_SAFETY",
  "G5_STAGING",
  "G6_RELIABILITY_PERFORMANCE",
  "G7_ROLLBACK",
  "G8_PRODUCTION_APPROVAL",
]);

export function evaluateGAPromotion(statuses = {}) {
  const missing = GAGates.filter((gate) => statuses[gate] !== "PASS");
  return Object.freeze({
    passed: missing.length === 0,
    missing: Object.freeze(missing),
    decision: missing.length === 0 ? "GA_PROMOTE" : "NOT_PROMOTED",
  });
}

export class CanonicalReleaseOwnerRegistry {
  #items = new Map();
  #byResponsibility = new Map();

  register(asset) {
    if (!asset?.assetId || !asset?.responsibility || !asset?.canonicalOwner) {
      throw new Error("AEGIS-REL-001 INVALID_RELEASE_ASSET");
    }
    if (this.#items.has(asset.assetId)) {
      throw new Error("AEGIS-REL-002 DUPLICATE_ASSET_ID");
    }
    const existing = this.#byResponsibility.get(asset.responsibility);
    if (existing && existing.canonicalOwner !== asset.canonicalOwner) {
      throw new Error("AEGIS-REL-003 CANONICAL_OWNER_CONFLICT");
    }
    const frozen = Object.freeze({ ...asset });
    this.#items.set(asset.assetId, frozen);
    this.#byResponsibility.set(asset.responsibility, frozen);
    return frozen;
  }
}

export const CanaryLevels = Object.freeze([
  "INTERNAL",
  "LIMITED_CUSTOMER",
  "P10",
  "P25",
  "P50",
  "P100",
]);

export function decideCanaryPromotion(current, metrics = {}) {
  if ((metrics.securityEvents ?? 0) > 0) return Object.freeze({ action: "ROLLBACK", reason: "SECURITY_EVENT" });
  if ((metrics.errorRateDelta ?? 0) > 0) return Object.freeze({ action: "HOLD", reason: "ERROR_RATE_REGRESSION" });
  if ((metrics.p95LatencyDeltaMs ?? 0) > 0) return Object.freeze({ action: "HOLD", reason: "LATENCY_REGRESSION" });
  if (metrics.rollbackReady !== true) return Object.freeze({ action: "HOLD", reason: "ROLLBACK_NOT_READY" });

  const index = CanaryLevels.indexOf(current);
  if (index < 0) return Object.freeze({ action: "HOLD", reason: "UNKNOWN_CANARY_LEVEL" });
  if (index === CanaryLevels.length - 1) return Object.freeze({ action: "COMPLETE", level: current });
  return Object.freeze({ action: "PROMOTE", level: CanaryLevels[index + 1] });
}

export const PublicOpenEvidence = Object.freeze([
  "gaGatePassed",
  "customerTermsReady",
  "privacyNoticeReady",
  "supportRunbookReady",
  "incidentResponseReady",
  "statusPageReady",
  "monitoringAlertingReady",
  "backupRestoreEvidence",
  "rollbackEvidence",
  "securityEvidence",
]);

export function evaluatePublicOpen(evidence = {}) {
  const missing = PublicOpenEvidence.filter((key) => evidence[key] !== true);
  return Object.freeze({
    passed: missing.length === 0,
    missing: Object.freeze(missing),
    decision: missing.length === 0 ? "PUBLIC_OPEN_APPROVED" : "PUBLIC_OPEN_BLOCKED",
  });
}
