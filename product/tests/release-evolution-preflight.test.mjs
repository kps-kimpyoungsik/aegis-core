import test from "node:test";
import assert from "node:assert/strict";
import {
  assertManifestBaseline,
  findReleaseNumberCollisions,
  releasePrefix,
  shouldEnforceManifestBaseline,
} from "../tools/release-evolution-preflight.mjs";

test("release prefix is extracted from release and workflow paths", () => {
  assert.equal(releasePrefix("product/release/r1.13-oidc.json"), "r1.13");
  assert.equal(releasePrefix(".github/workflows/r1.12-product-approval.yml"), "r1.12");
  assert.equal(releasePrefix("product/apps/api-server/index.js"), null);
});

test("manifest baseline must match exact pull request base for release evidence mutations", () => {
  assert.doesNotThrow(() => assertManifestBaseline({ sourceRevision: "main@abc123" }, "abc123"));
  assert.throws(() => assertManifestBaseline({ sourceRevision: "main@old" }, "abc123"), /BASELINE_DRIFT/);
});

test("manifest baseline ownership applies to release evidence but not unrelated product or workflow paths", () => {
  assert.equal(shouldEnforceManifestBaseline(["product/release/release-manifest.candidate.json"]), true);
  assert.equal(shouldEnforceManifestBaseline(["product/release/r1.20-evidence.json"]), true);
  assert.equal(shouldEnforceManifestBaseline(["product/contracts/workstreams/WS-X.json"]), false);
  assert.equal(shouldEnforceManifestBaseline([".github/workflows/r1.18-data-plane-tenant-isolation.yml"]), false);
});

test("new release number already present on base is rejected", () => {
  assert.deepEqual(
    findReleaseNumberCollisions(
      [".github/workflows/r1.12-new.yml", "product/release/r1.14-new.json"],
      ["product/release/r1.12-existing.json", "product/release/r1.13-existing.json"],
    ),
    ["r1.12"],
  );
});

test("multiple files for a brand-new release number do not collide with base", () => {
  assert.deepEqual(
    findReleaseNumberCollisions(
      [".github/workflows/r1.14-new.yml", "product/release/r1.14-new.json"],
      ["product/release/r1.13-existing.json"],
    ),
    [],
  );
});
