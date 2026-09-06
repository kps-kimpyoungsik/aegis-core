import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const script = fs.readFileSync(new URL('../../scripts/precommit-verify.sh', import.meta.url), 'utf8');

test('precommit gate always runs canonical anti-duplication checks', () => {
  const ownership = script.indexOf('node tools/ownership-check.mjs');
  const workstreams = script.indexOf('node tools/workstream-collision-check.mjs');
  const duplicates = script.indexOf('node tools/duplicate-check.mjs');
  const productConditional = script.indexOf('if $GATE_SELF_CHANGED || matches_any_prefix product; then');

  assert.ok(ownership >= 0, 'ownership check must be wired');
  assert.ok(workstreams > ownership, 'workstream collision check must follow ownership');
  assert.ok(duplicates > workstreams, 'duplicate public symbol check must follow workstream collision');
  assert.ok(productConditional > duplicates, 'anti-duplication checks must run before product-only conditional');
});
