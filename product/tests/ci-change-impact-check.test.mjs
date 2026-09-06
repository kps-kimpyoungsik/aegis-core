import test from "node:test";
import assert from "node:assert/strict";
import { evaluateChangeImpact } from "../tools/ci-change-impact-check.mjs";

const registry = {
  rules: [{
    id: "data-plane.postgres-schema-fixtures",
    sourcePrefixes: ["data-plane/live-integration/sql/"],
    materialDiffPattern: "^[+-].*(CREATE TABLE|ALTER TABLE|DROP TABLE|PRIMARY KEY|UNIQUE|NOT NULL|ADD COLUMN|DROP COLUMN)",
    requiredCompanionPaths: [
      "data-plane/live-integration/scripts/test-postgres-live.sh",
      "data-plane/live-integration/scripts/test-postgres-recovery-live.sh"
    ]
  }]
};

test("ignores non-material SQL comments", () => {
  const result = evaluateChangeImpact({
    changedFiles: ["data-plane/live-integration/sql/001_init.sql"],
    diffsByPath: { "data-plane/live-integration/sql/001_init.sql": "+-- comment only" },
    registry
  });
  assert.deepEqual(result.failures, []);
});

test("fails when material DDL changes without both fixtures", () => {
  const result = evaluateChangeImpact({
    changedFiles: ["data-plane/live-integration/sql/001_init.sql", "data-plane/live-integration/scripts/test-postgres-live.sh"],
    diffsByPath: { "data-plane/live-integration/sql/001_init.sql": "+    tenant_id TEXT NOT NULL," },
    registry
  });
  assert.equal(result.failures.length, 1);
  assert.deepEqual(result.failures[0].missing, ["data-plane/live-integration/scripts/test-postgres-recovery-live.sh"]);
});

test("passes when material DDL and both fixtures move atomically", () => {
  const result = evaluateChangeImpact({
    changedFiles: [
      "data-plane/live-integration/sql/001_init.sql",
      "data-plane/live-integration/scripts/test-postgres-live.sh",
      "data-plane/live-integration/scripts/test-postgres-recovery-live.sh"
    ],
    diffsByPath: { "data-plane/live-integration/sql/001_init.sql": "+    PRIMARY KEY (tenant_id, dataset_id, record_id)" },
    registry
  });
  assert.deepEqual(result.failures, []);
  assert.deepEqual(result.triggered, ["data-plane.postgres-schema-fixtures"]);
});
