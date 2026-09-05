import fs from "node:fs";
import path from "node:path";
import { evaluateGAPromotion, evaluatePublicOpen } from "@aegis/release-convergence";

const evidencePath = path.resolve("release/r1.8-production-qualification-evidence.json");
const evidence = JSON.parse(fs.readFileSync(evidencePath, "utf8"));

const ga = evaluateGAPromotion(evidence.gaGates);
const publicOpen = evaluatePublicOpen(evidence.publicOpenEvidence);

if (ga.decision !== evidence.expectedDecision) {
  throw new Error(`AEGIS-R1.8-001 unexpected GA decision ${ga.decision}`);
}
if (publicOpen.decision !== evidence.expectedPublicOpenDecision) {
  throw new Error(`AEGIS-R1.8-002 unexpected public-open decision ${publicOpen.decision}`);
}
if (ga.passed || publicOpen.passed) {
  throw new Error("AEGIS-R1.8-003 production qualification must remain fail closed");
}

const requiredMissing = [
  "G3_SECURITY",
  "G4_DATA_SAFETY",
  "G5_STAGING",
  "G6_RELIABILITY_PERFORMANCE",
  "G7_ROLLBACK",
  "G8_PRODUCTION_APPROVAL",
];
for (const gate of requiredMissing) {
  if (!ga.missing.includes(gate)) {
    throw new Error(`AEGIS-R1.8-004 missing fail-closed gate ${gate}`);
  }
}

const result = {
  schema: "aegis.release.production-qualification-result.v1",
  gaDecision: ga.decision,
  gaMissing: ga.missing,
  publicOpenDecision: publicOpen.decision,
  publicOpenMissing: publicOpen.missing,
  productionPromoted: false,
};

process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
