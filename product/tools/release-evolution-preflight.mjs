import fs from "node:fs";
import { execFileSync } from "node:child_process";

const RELEASE_MANIFEST_PATH = "product/release/release-manifest.candidate.json";

export function releasePrefix(path) {
  const match = path.match(/(?:^|\/)(?:r)(\d+\.\d+)(?:[-_.]|$)/i);
  return match ? `r${match[1]}`.toLowerCase() : null;
}

export function assertManifestBaseline(manifest, expectedBaseSha) {
  const expected = `main@${expectedBaseSha}`;
  if (manifest.sourceRevision !== expected) {
    throw new Error(`AEGIS-EVO-REL-001 BASELINE_DRIFT expected=${expected} actual=${manifest.sourceRevision}`);
  }
}

export function shouldEnforceManifestBaseline(changedPaths) {
  return changedPaths.includes(RELEASE_MANIFEST_PATH);
}

export function findReleaseNumberCollisions(addedPaths, basePaths) {
  const basePrefixes = new Set(basePaths.map(releasePrefix).filter(Boolean));
  return [...new Set(addedPaths.map(releasePrefix).filter((prefix) => prefix && basePrefixes.has(prefix)))];
}

function lines(value) {
  return value.split("\n").map((item) => item.trim()).filter(Boolean);
}

export function runPreflight({ expectedBaseSha, manifestPath, changedPaths, addedPaths, basePaths }) {
  if (shouldEnforceManifestBaseline(changedPaths)) {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    assertManifestBaseline(manifest, expectedBaseSha);
  }
  const collisions = findReleaseNumberCollisions(addedPaths, basePaths);
  if (collisions.length > 0) throw new Error(`AEGIS-EVO-REL-002 RELEASE_NUMBER_COLLISION ${collisions.join(",")}`);
  return {
    expectedBaseSha,
    manifestBaselineEnforced: shouldEnforceManifestBaseline(changedPaths),
    addedReleasePrefixes: addedPaths.map(releasePrefix).filter(Boolean),
  };
}

if (import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const expectedBaseSha = process.env.AEGIS_PR_BASE_SHA;
  if (!expectedBaseSha) throw new Error("AEGIS-EVO-REL-000 PR_BASE_SHA_REQUIRED");
  const manifestPath = new URL("../release/release-manifest.candidate.json", import.meta.url);
  const changedPaths = lines(execFileSync("git", ["diff", "--name-only", expectedBaseSha, "HEAD"], { encoding: "utf8" }));
  const addedPaths = lines(execFileSync("git", ["diff", "--name-only", "--diff-filter=A", expectedBaseSha, "HEAD"], { encoding: "utf8" }));
  const basePaths = lines(execFileSync("git", ["ls-tree", "-r", "--name-only", expectedBaseSha, "product/release", ".github/workflows"], { encoding: "utf8" }));
  const result = runPreflight({ expectedBaseSha, manifestPath, changedPaths, addedPaths, basePaths });
  console.log(`release-evolution-preflight PASS base=${result.expectedBaseSha} manifestBaseline=${result.manifestBaselineEnforced ? "enforced" : "not-applicable"} added=${result.addedReleasePrefixes.join(",") || "none"}`);
}
