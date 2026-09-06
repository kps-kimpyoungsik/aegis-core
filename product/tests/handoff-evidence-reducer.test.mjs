import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeGitHubEvidence } from '../tools/handoff-evidence-adapter.mjs';
import { createAuditEvent, projectRegistry } from '../tools/handoff-state-reducer.mjs';

const item = {
  id: 'AEGIS-HANDOFF-TEST-001',
  state: 'IN_PROGRESS'
};

function baseIssue(overrides = {}) {
  return { state: 'open', issue_number: 999, comments: 1, ...overrides };
}

function options(overrides = {}) {
  return {
    issue: baseIssue(),
    workflowRuns: [],
    jobs: [],
    ownerMatches: true,
    acceptanceComplete: false,
    relevantChange: false,
    transientFailure: false,
    highRisk: false,
    externalStateChanged: false,
    retryCount: 0,
    maxRetry: 2,
    blockers: [],
    ...overrides
  };
}

test('pre-run GitHub failure is NOT_EXECUTED and not a code validation failure', () => {
  const evidence = normalizeGitHubEvidence(options({
    workflowRuns: [{ id: 10, status: 'completed', conclusion: 'failure' }],
    jobs: [{ id: 20, conclusion: 'failure', steps: [] }]
  }));
  assert.equal(evidence.executionStatus, 'NOT_EXECUTED');
  assert.equal(evidence.validationFailed, false);
  assert.equal(evidence.validationEvidence, false);
  assert.deepEqual(evidence.provenance.preRunFailureJobIds, [20]);
});

test('executed failing step is a real validation failure', () => {
  const evidence = normalizeGitHubEvidence(options({
    workflowRuns: [{ id: 11, status: 'completed', conclusion: 'failure' }],
    jobs: [{ id: 21, conclusion: 'failure', steps: [{ name: 'test', conclusion: 'failure' }] }]
  }));
  assert.equal(evidence.executionStatus, 'EXECUTED');
  assert.equal(evidence.validationFailed, true);
});

test('successful executed validation can become completion evidence', () => {
  const evidence = normalizeGitHubEvidence(options({
    acceptanceComplete: true,
    workflowRuns: [{ id: 12, status: 'completed', conclusion: 'success' }],
    jobs: [{ id: 22, conclusion: 'success', steps: [{ name: 'verify', conclusion: 'success' }] }]
  }));
  assert.equal(evidence.validationEvidence, true);
  const event = createAuditEvent(item, evidence, '2026-09-06T12:00:00Z');
  assert.equal(event.state, 'COMPLETED');
  assert.equal(event.evidenceStatus, 'EXECUTED');
});

test('external blocker stays blocked and is projected from immutable event', () => {
  const evidence = normalizeGitHubEvidence(options({
    blockers: [{ id: 'billing', state: 'BLOCKED_EXTERNAL' }]
  }));
  const event = createAuditEvent(item, evidence, '2026-09-06T12:01:00Z');
  assert.equal(event.state, 'BLOCKED_EXTERNAL');
  assert.equal(event.action, 'WAIT_EXTERNAL');
  const projected = projectRegistry({ version: '1', items: [item] }, [event]);
  assert.equal(projected.items[0].state, 'BLOCKED_EXTERNAL');
  assert.equal(projected.items[0].lastAudit.reason, 'EXTERNAL_STATE_UNCHANGED');
});

test('state reducer rejects out-of-order audit provenance', () => {
  const evidence = normalizeGitHubEvidence(options());
  const later = createAuditEvent(item, evidence, '2026-09-06T12:05:00Z');
  const earlier = createAuditEvent(item, evidence, '2026-09-06T12:04:00Z');
  assert.throws(
    () => projectRegistry({ version: '1', items: [item] }, [later, earlier]),
    /OUT_OF_ORDER_EVENT/
  );
});
