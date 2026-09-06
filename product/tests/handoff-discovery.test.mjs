import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const productRoot = path.resolve(here, '..');
const tool = path.join(productRoot, 'tools', 'handoff-discovery.mjs');

function run(...args) {
  const result = spawnSync(process.execPath, [tool, ...args], {
    cwd: productRoot,
    encoding: 'utf8'
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return JSON.parse(result.stdout);
}

test('discovers CI fan-out handoff without issue number', () => {
  const result = run('--query=workflow-fanout');
  assert.equal(result.matchCount, 1);
  assert.equal(result.fallbackRequired, false);
  assert.equal(result.items[0].id, 'AEGIS-HANDOFF-CI-FANOUT-001');
  assert.equal(result.items[0].issueRef, 109);
});

test('discovers GitHub Actions billing blocker by structural fingerprint', () => {
  const result = run('--fingerprint=billing-lock');
  assert.equal(result.matchCount, 1);
  assert.equal(result.items[0].state, 'BLOCKED_EXTERNAL');
  assert.equal(result.items[0].rootCauseStatus, 'CONFIRMED');
  assert.match(result.items[0].retryPolicy, /SUPPRESS_UNTIL_EXTERNAL_STATE_CHANGES/);
});

test('discovers data-plane work by canonical domain', () => {
  const result = run('--domain=data-plane');
  assert.equal(result.matchCount, 1);
  assert.equal(result.items[0].ownerResponsibility, 'storage-runtime');
  assert.equal(result.items[0].issueRef, 110);
});

test('unknown query requires fallback instead of claiming no error', () => {
  const result = run('--query=definitely-no-such-handoff');
  assert.equal(result.matchCount, 0);
  assert.equal(result.fallbackRequired, true);
  assert.equal(result.fallbackState, 'SEARCH_NOT_FOUND_REQUIRES_FALLBACK');
});

test('governance owner lookup returns unresolved governance work', () => {
  const result = run('--owner=responsibility');
  assert.ok(result.matchCount >= 2);
  assert.ok(result.items.some((item) => item.state === 'REHANDOFF_REQUIRED'));
  assert.ok(result.items.some((item) => item.id === 'AEGIS-HANDOFF-COMPLETION-AUDIT-001'));
});
