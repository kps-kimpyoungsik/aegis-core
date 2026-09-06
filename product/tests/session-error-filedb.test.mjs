import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  ACTIONABLE_SESSION_ERROR_STATES,
  SessionErrorFileDb,
  assertAppendTransition,
  querySessionErrors,
  retryDecision,
  sessionShardName
} from '../tools/session-error-filedb.mjs';

function record(overrides = {}) {
  return {
    eventId: overrides.eventId ?? '00000000-0000-4000-8000-000000000001',
    sessionId: overrides.sessionId ?? 'session-a',
    workItemId: overrides.workItemId ?? 'WORK-1',
    episodeId: overrides.episodeId ?? 'episode-1',
    failureFingerprint: overrides.failureFingerprint ?? 'github-actions|ci|run|failure|boundary',
    state: overrides.state ?? 'WAITING',
    observedAt: overrides.observedAt ?? '2026-09-06T15:00:00.000Z',
    retryCount: overrides.retryCount ?? 0,
    maxRetry: overrides.maxRetry ?? 1,
    ...overrides
  };
}

test('default actionable query returns only current session records', () => {
  const rows = [
    record({ sessionId: 'session-a', state: 'WAITING' }),
    record({ sessionId: 'session-b', workItemId: 'WORK-2', state: 'WAITING' })
  ];

  const result = querySessionErrors(rows, { sessionId: 'session-a' });
  assert.equal(result.length, 1);
  assert.equal(result[0].sessionId, 'session-a');
});

test('latest terminal state removes prior waiting record from actionable query', () => {
  const rows = [
    record({ eventId: 'a', state: 'WAITING', observedAt: '2026-09-06T15:00:00.000Z' }),
    record({ eventId: 'b', state: 'COMPLETED', observedAt: '2026-09-06T15:10:00.000Z' })
  ];

  assert.deepEqual(querySessionErrors(rows, { sessionId: 'session-a' }), []);
});

test('latest waiting state remains discoverable', () => {
  const rows = [
    record({ eventId: 'a', state: 'IN_PROGRESS', observedAt: '2026-09-06T15:00:00.000Z' }),
    record({ eventId: 'b', state: 'WAITING', observedAt: '2026-09-06T15:10:00.000Z' })
  ];

  const result = querySessionErrors(rows, { sessionId: 'session-a' });
  assert.equal(result.length, 1);
  assert.equal(result[0].state, 'WAITING');
});

test('completed and cleared states never retry', () => {
  for (const state of ['COMPLETED', 'CLEARED', 'SUPERSEDED', 'FAILED_FINAL', 'CANCELLED']) {
    const decision = retryDecision(record({ state }));
    assert.equal(decision.retryAllowed, false);
    assert.equal(decision.action, 'NO_RETRY_TERMINAL');
  }
});

test('waiting and dependency-blocked records are searched but not retried', () => {
  for (const state of ['WAITING', 'BLOCKED_DEPENDENCY']) {
    assert.ok(ACTIONABLE_SESSION_ERROR_STATES.includes(state));
    const decision = retryDecision(record({ state }));
    assert.equal(decision.retryAllowed, false);
    assert.equal(decision.action, 'RECHECK_WAITING_CONDITION');
  }
});

test('unchanged external blocker suppresses retry and changed state allows canary only', () => {
  const blocked = record({ state: 'BLOCKED_EXTERNAL' });
  assert.deepEqual(retryDecision(blocked), {
    retryAllowed: false,
    action: 'WAIT_EXTERNAL_STATE_CHANGE',
    reason: 'EXTERNAL_STATE_UNCHANGED'
  });
  assert.deepEqual(retryDecision(blocked, { externalStateChanged: true }), {
    retryAllowed: true,
    action: 'CANARY_ONLY',
    reason: 'EXTERNAL_STATE_CHANGED'
  });
});

test('retry due requires both budget and corrective or transient evidence', () => {
  const retryDue = record({ state: 'RETRY_DUE', retryCount: 0, maxRetry: 1 });
  assert.equal(retryDecision(retryDue).retryAllowed, false);
  assert.equal(retryDecision(retryDue, { relevantCorrectiveChange: true }).retryAllowed, true);

  const exhausted = record({ state: 'RETRY_DUE', retryCount: 1, maxRetry: 1 });
  const decision = retryDecision(exhausted, { transientEvidence: true });
  assert.equal(decision.retryAllowed, false);
  assert.equal(decision.action, 'ESCALATE_RETRY_EXHAUSTED');
});

test('session shard filename is hashed and does not expose raw session id', () => {
  const sessionId = '../sensitive/session-id';
  const shard = sessionShardName(sessionId);
  assert.match(shard, /^[a-f0-9]{64}\.jsonl$/);
  assert.equal(shard.includes('sensitive'), false);
  assert.equal(shard.includes('..'), false);
});

test('terminal identity cannot silently reactivate within the same episode', () => {
  const history = [
    record({ eventId: 'a', state: 'COMPLETED', observedAt: '2026-09-06T15:00:00.000Z' })
  ];
  assert.throws(
    () => assertAppendTransition(history, record({
      eventId: 'b',
      state: 'WAITING',
      observedAt: '2026-09-06T15:10:00.000Z'
    })),
    /terminal error identity cannot reactivate/
  );
});

test('same fingerprint may recur only as a new episode', () => {
  const history = [
    record({ eventId: 'a', state: 'COMPLETED', observedAt: '2026-09-06T15:00:00.000Z', episodeId: 'episode-1' })
  ];
  const next = assertAppendTransition(history, record({
    eventId: 'b',
    state: 'WAITING',
    observedAt: '2026-09-06T15:10:00.000Z',
    episodeId: 'episode-2'
  }));
  assert.equal(next.episodeId, 'episode-2');
});

test('duplicate or stale append event is rejected', () => {
  const history = [
    record({ eventId: 'a', state: 'WAITING', observedAt: '2026-09-06T15:10:00.000Z' })
  ];
  assert.throws(
    () => assertAppendTransition(history, record({ eventId: 'a', state: 'WAITING', observedAt: '2026-09-06T15:20:00.000Z' })),
    /duplicate eventId/
  );
  assert.throws(
    () => assertAppendTransition(history, record({ eventId: 'b', state: 'WAITING', observedAt: '2026-09-06T15:00:00.000Z' })),
    /append event must be newer/
  );
});

test('file-backed database persists append history and queries latest actionable state', async () => {
  const rootDir = await mkdtemp(path.join(os.tmpdir(), 'aegis-filedb-'));
  try {
    const db = new SessionErrorFileDb({ rootDir, sessionId: 'session-a' });
    await db.append(record({ eventId: 'a', state: 'WAITING', observedAt: '2026-09-06T15:00:00.000Z' }));
    assert.equal((await db.queryActionable()).length, 1);

    await db.append(record({ eventId: 'b', state: 'COMPLETED', observedAt: '2026-09-06T15:10:00.000Z' }));
    assert.equal((await db.readAll()).length, 2);
    assert.equal((await db.queryActionable()).length, 0);

    await assert.rejects(
      db.append(record({ eventId: 'c', state: 'WAITING', observedAt: '2026-09-06T15:20:00.000Z' })),
      /terminal error identity cannot reactivate/
    );
  } finally {
    await rm(rootDir, { recursive: true, force: true });
  }
});

test('broad cross-session lookup is fail-closed', () => {
  assert.throws(
    () => querySessionErrors([record()], { sessionId: 'session-a', crossSession: true }),
    /CROSS_SESSION_AUDIT/
  );
  assert.throws(
    () => querySessionErrors([record()], {
      sessionId: 'session-a',
      crossSession: true,
      auditMode: 'CROSS_SESSION_AUDIT'
    }),
    /broad cross-session FileDB scanning is not supported/
  );
});
