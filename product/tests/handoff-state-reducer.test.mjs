import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeGitHubEvidence } from '../tools/handoff-evidence-adapter.mjs';
import { createAuditEvent, reduceHandoff, projectRegistry } from '../tools/handoff-state-reducer.mjs';

const item = {
  id: 'AEGIS-HANDOFF-TEST-001',
  state: 'IN_PROGRESS'
};

function issue(overrides = {}) {
  return { issue_number: 999, state: 'open', comments: 1, ...overrides };
}

test('pre-run failed GitHub job is NOT_EXECUTED and not validation failure', () => {
  const evidence = normalizeGitHubEvidence({
    issue: issue(),
    workflowRuns: [{ id: 10, status: 'completed', conclusion: 'failure' }],
    jobs: [{ id: 20, conclusion: 'failure', steps: null }],
    blockers: [{ id: 'billing', state: 'BLOCKED_EXTERNAL' }]
  });
  assert.equal(evidence.executionStatus, 'NOT_EXECUTED');
  assert.equal(evidence.validationFailed, false);
  assert.equal(evidence.validationEvidence, false);
  assert.deepEqual(evidence.provenance.preRunFailureJobIds, [20]);
});

test('executed failed job is real validation failure', () => {
  const evidence = normalizeGitHubEvidence({
    issue: issue(),
    workflowRuns: [{ id: 10, status: 'completed', conclusion: 'failure' }],
    jobs: [{ id: 20, conclusion: 'failure', steps: [{ name: 'test', conclusion: 'failure' }] }]
  });
  assert.equal(evidence.executionStatus, 'EXECUTED');
  assert.equal(evidence.validationFailed, true);
});

test('successful executed jobs provide validation evidence only after all required runs complete', () => {
  const evidence = normalizeGitHubEvidence({
    issue: issue(),
    workflowRuns: [{ id: 10, status: 'completed', conclusion: 'success' }],
    jobs: [{ id: 20, conclusion: 'success', steps: [{ name: 'test', conclusion: 'success' }] }],
    acceptanceComplete: true
  });
  assert.equal(evidence.executionStatus, 'EXECUTED');
  assert.equal(evidence.validationEvidence, true);
  const event = createAuditEvent(item, evidence, '2026-09-06T12:00:00Z');
  assert.equal(event.state, 'COMPLETED');
  assert.equal(event.evidenceStatus, 'EXECUTED');
});

test('immutable audit event projects latest state without mutating registry baseline', () => {
  const waitingEvidence = normalizeGitHubEvidence({
    issue: issue(),
    workflowRuns: [{ id: 10, status: 'completed', conclusion: 'failure' }],
    jobs: [{ id: 20, conclusion: 'failure', steps: [] }],
    blockers: [{ id: 'billing', state: 'BLOCKED_EXTERNAL' }]
  });
  const event = createAuditEvent(item, waitingEvidence, '2026-09-06T12:00:00Z');
  assert.equal(Object.isFrozen(event), true);
  const projected = reduceHandoff(item, [event]);
  assert.equal(item.state, 'IN_PROGRESS');
  assert.equal(projected.state, 'BLOCKED_EXTERNAL');
  assert.equal(projected.lastAudit.action, 'WAIT_EXTERNAL');
  assert.equal(projected.lastAudit.evidenceStatus, 'NOT_EXECUTED');
});

test('out-of-order audit events fail closed', () => {
  const evidence = normalizeGitHubEvidence({ issue: issue() });
  const later = createAuditEvent(item, evidence, '2026-09-06T13:00:00Z');
  const earlier = createAuditEvent(item, evidence, '2026-09-06T12:00:00Z');
  assert.throws(() => reduceHandoff(item, [later, earlier]), /OUT_OF_ORDER_EVENT/);
});

test('registry projection preserves canonical fields and derives audit state', () => {
  const evidence = normalizeGitHubEvidence({
    issue: issue(),
    blockers: [{ id: 'dependency', state: 'IN_PROGRESS' }]
  });
  const event = createAuditEvent(item, evidence, '2026-09-06T12:00:00Z');
  const registry = { version: '1.0.0', status: 'CANDIDATE', items: [item] };
  const projected = projectRegistry(registry, [event]);
  assert.equal(projected.version, '1.0.0');
  assert.equal(projected.projectionPolicy, 'DERIVED_FROM_IMMUTABLE_HANDOFF_AUDIT_EVENTS');
  assert.equal(projected.items[0].state, 'BLOCKED_DEPENDENCY');
});
