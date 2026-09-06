import assert from 'node:assert/strict';
import test from 'node:test';

import {
  assertContentWriteRequired,
  contentDigest,
  contentWriteDecision
} from '../tools/content-write-guard.mjs';

test('identical content is rejected as a no-op write', () => {
  const decision = contentWriteDecision('same\ncontent\n', 'same\ncontent\n');
  assert.equal(decision.writeAllowed, false);
  assert.equal(decision.action, 'SKIP_NOOP_WRITE');
  assert.equal(decision.currentDigest, decision.nextDigest);
  assert.throws(
    () => assertContentWriteRequired('same\ncontent\n', 'same\ncontent\n'),
    /no-op repository write is forbidden/
  );
});

test('CRLF and LF are treated as the same semantic content', () => {
  const decision = contentWriteDecision('a\r\nb\r\n', 'a\nb\n');
  assert.equal(decision.writeAllowed, false);
  assert.equal(decision.reason, 'CONTENT_UNCHANGED');
});

test('changed content is allowed and produces different digests', () => {
  const decision = contentWriteDecision('before\n', 'after\n');
  assert.equal(decision.writeAllowed, true);
  assert.equal(decision.action, 'WRITE_CHANGED_CONTENT');
  assert.notEqual(decision.currentDigest, decision.nextDigest);
});

test('digest is deterministic', () => {
  assert.equal(contentDigest('abc\n'), contentDigest('abc\n'));
});
