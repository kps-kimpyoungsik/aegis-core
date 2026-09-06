import assert from 'node:assert/strict';
import test from 'node:test';
import { auditHandoff } from '../tools/handoff-completion-audit.mjs';

const item = {
  id: 'AEGIS-HANDOFF-TEST-001',
  state: 'IN_PROGRESS'
};

function evidence(overrides = {}) {
  return {
    issueOpen: true,
    acknowledged: true,
    ownerMatches: true,
    acceptanceComplete: false,
    validationEvidence: false,
    validationFailed: false,
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

test('marks complete only with acceptance, verifier evidence, and no blockers', () => {
  const result = auditHandoff(item, evidence({ acceptanceComplete: true, validationEvidence: true }));
  assert.equal(result.state, 'COMPLETED');
  assert.equal(result.completed, true);
  assert.equal(result.retryAllowed, false);
});

test('external blocker suppresses retry while state is unchanged', () => {
  const result = auditHandoff(item, evidence({ blockers: [{ id: 'billing', state: 'BLOCKED_EXTERNAL' }] }));
  assert.equal(result.state, 'BLOCKED_EXTERNAL');
  assert.equal(result.action, 'WAIT_EXTERNAL');
  assert.equal(result.retryAllowed, false);
});

test('external state change permits only canary-style bounded retry', () => {
  const result = auditHandoff(item, evidence({ blockers: [{ id: 'billing', state: 'BLOCKED_EXTERNAL' }], externalStateChanged: true }));
  assert.equal(result.state, 'RETRY_DUE');
  assert.equal(result.action, 'CANARY');
  assert.equal(result.retryAllowed, true);
});

test('deterministic validation failure requires fix before retry', () => {
  const result = auditHandoff(item, evidence({ validationFailed: true }));
  assert.equal(result.state, 'FAILED_VALIDATION');
  assert.equal(result.action, 'FIX_REQUIRED');
  assert.equal(result.retryAllowed, false);
});

test('relevant change allows bounded revalidation before retry budget is exhausted', () => {
  const result = auditHandoff(item, evidence({ validationFailed: true, relevantChange: true, retryCount: 1 }));
  assert.equal(result.state, 'RETRY_DUE');
  assert.equal(result.action, 'RETRY');
  assert.equal(result.retryAllowed, true);
});

test('retry budget exhaustion escalates', () => {
  const result = auditHandoff(item, evidence({ validationFailed: true, relevantChange: true, retryCount: 2, maxRetry: 2 }));
  assert.equal(result.state, 'ESCALATION_REQUIRED');
  assert.equal(result.action, 'ESCALATE');
});

test('owner drift requires re-handoff', () => {
  const result = auditHandoff(item, evidence({ ownerMatches: false }));
  assert.equal(result.state, 'REHANDOFF_REQUIRED');
  assert.equal(result.action, 'REHANDOFF');
});

test('closed issue without acceptance is not completion', () => {
  const result = auditHandoff(item, evidence({ issueOpen: false }));
  assert.equal(result.state, 'VALIDATING');
  assert.equal(result.completed, false);
  assert.equal(result.action, 'REOPEN_OR_VALIDATE');
});

test('acceptance without verifier evidence stays validating', () => {
  const result = auditHandoff(item, evidence({ acceptanceComplete: true }));
  assert.equal(result.state, 'VALIDATING');
  assert.equal(result.action, 'VALIDATE');
});

test('high-risk validation failure escalates immediately', () => {
  const result = auditHandoff(item, evidence({ validationFailed: true, highRisk: true }));
  assert.equal(result.state, 'ESCALATION_REQUIRED');
  assert.equal(result.retryAllowed, false);
});
