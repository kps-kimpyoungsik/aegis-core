#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const productRoot = path.resolve(here, '..');
const contractsDir = path.join(productRoot, 'contracts');

const ACTIVE_STATES = new Set([
  'HANDOFF_CREATED',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'VALIDATING',
  'BLOCKED_EXTERNAL',
  'BLOCKED_DEPENDENCY',
  'FAILED_VALIDATION',
  'RETRY_DUE',
  'REHANDOFF_REQUIRED',
  'ESCALATION_REQUIRED',
  'STALE',
  'PAUSED',
  'ROLLBACK',
  'FAILED'
]);

const FINAL_STATES = new Set(['COMPLETED']);

function requiredBoolean(value, field) {
  if (typeof value !== 'boolean') {
    throw new Error(`AEGIS-HANDOFF-AUDIT-001 INVALID_BOOLEAN ${field}`);
  }
  return value;
}

function requiredNonNegativeInteger(value, field) {
  if (!Number.isInteger(value) || value < 0) {
    throw new Error(`AEGIS-HANDOFF-AUDIT-002 INVALID_NON_NEGATIVE_INTEGER ${field}`);
  }
  return value;
}

function normalizeBlockers(blockers) {
  if (!Array.isArray(blockers)) {
    throw new Error('AEGIS-HANDOFF-AUDIT-003 BLOCKERS_NOT_ARRAY');
  }
  return blockers.map((blocker) => {
    if (!blocker || typeof blocker.id !== 'string' || typeof blocker.state !== 'string') {
      throw new Error('AEGIS-HANDOFF-AUDIT-004 INVALID_BLOCKER');
    }
    return { id: blocker.id, state: blocker.state };
  });
}

export function auditHandoff(item, evidence) {
  if (!item || typeof item.id !== 'string' || !item.id) {
    throw new Error('AEGIS-HANDOFF-AUDIT-005 INVALID_WORK_ITEM');
  }
  if (!ACTIVE_STATES.has(item.state) && !FINAL_STATES.has(item.state)) {
    throw new Error(`AEGIS-HANDOFF-AUDIT-006 UNKNOWN_ITEM_STATE ${item.id}:${item.state}`);
  }

  const issueOpen = requiredBoolean(evidence.issueOpen, 'issueOpen');
  const acknowledged = requiredBoolean(evidence.acknowledged, 'acknowledged');
  const ownerMatches = requiredBoolean(evidence.ownerMatches, 'ownerMatches');
  const acceptanceComplete = requiredBoolean(evidence.acceptanceComplete, 'acceptanceComplete');
  const validationEvidence = requiredBoolean(evidence.validationEvidence, 'validationEvidence');
  const validationFailed = requiredBoolean(evidence.validationFailed, 'validationFailed');
  const relevantChange = requiredBoolean(evidence.relevantChange, 'relevantChange');
  const transientFailure = requiredBoolean(evidence.transientFailure, 'transientFailure');
  const highRisk = requiredBoolean(evidence.highRisk, 'highRisk');
  const externalStateChanged = requiredBoolean(evidence.externalStateChanged, 'externalStateChanged');
  const retryCount = requiredNonNegativeInteger(evidence.retryCount, 'retryCount');
  const maxRetry = requiredNonNegativeInteger(evidence.maxRetry, 'maxRetry');
  const blockers = normalizeBlockers(evidence.blockers ?? []);

  const unresolvedExternal = blockers.some((blocker) => blocker.state === 'BLOCKED_EXTERNAL');
  const unresolvedDependency = blockers.some((blocker) => blocker.state !== 'COMPLETED' && blocker.state !== 'CLEARED' && blocker.state !== 'BLOCKED_EXTERNAL');
  const retryExhausted = retryCount >= maxRetry;

  if (!ownerMatches) {
    return decision(item, 'REHANDOFF_REQUIRED', 'REHANDOFF', false, 'CANONICAL_OWNER_DRIFT');
  }

  if (acceptanceComplete && validationEvidence && !validationFailed && blockers.length === 0) {
    return decision(item, 'COMPLETED', 'NONE', false, 'ACCEPTANCE_AND_VALIDATION_VERIFIED');
  }

  if (highRisk && (retryExhausted || validationFailed)) {
    return decision(item, 'ESCALATION_REQUIRED', 'ESCALATE', false, 'HIGH_RISK_RETRY_OR_VALIDATION_FAILURE');
  }

  if (unresolvedExternal) {
    if (!externalStateChanged) {
      return decision(item, 'BLOCKED_EXTERNAL', 'WAIT_EXTERNAL', false, 'EXTERNAL_STATE_UNCHANGED');
    }
    return decision(item, 'RETRY_DUE', 'CANARY', true, 'EXTERNAL_STATE_CHANGED_REQUIRE_CHEAP_CANARY');
  }

  if (unresolvedDependency) {
    return decision(item, 'BLOCKED_DEPENDENCY', 'WAIT_DEPENDENCY', false, 'DEPENDENCY_NOT_COMPLETED');
  }

  if (validationFailed) {
    if (retryExhausted) {
      return decision(item, 'ESCALATION_REQUIRED', 'ESCALATE', false, 'RETRY_BUDGET_EXHAUSTED');
    }
    if (!relevantChange && !transientFailure) {
      return decision(item, 'FAILED_VALIDATION', 'FIX_REQUIRED', false, 'DETERMINISTIC_FAILURE_WITHOUT_RELEVANT_CHANGE');
    }
    return decision(item, 'RETRY_DUE', 'RETRY', true, transientFailure ? 'TRANSIENT_FAILURE_BOUNDED_RETRY' : 'RELEVANT_CHANGE_READY_FOR_REVALIDATION');
  }

  if (!acknowledged && issueOpen) {
    return decision(item, 'REHANDOFF_REQUIRED', 'REHANDOFF', false, 'UNACKNOWLEDGED_HANDOFF');
  }

  if (!issueOpen && !acceptanceComplete) {
    return decision(item, 'VALIDATING', 'REOPEN_OR_VALIDATE', false, 'ISSUE_CLOSED_WITHOUT_COMPLETION_EVIDENCE');
  }

  if (acceptanceComplete && !validationEvidence) {
    return decision(item, 'VALIDATING', 'VALIDATE', false, 'ACCEPTANCE_WITHOUT_VERIFIER_EVIDENCE');
  }

  return decision(item, acknowledged ? 'IN_PROGRESS' : 'HANDOFF_CREATED', 'CONTINUE_WORK', false, 'WORK_REMAINS_INCOMPLETE');
}

function decision(item, state, action, retryAllowed, reason) {
  return {
    id: item.id,
    previousState: item.state,
    state,
    action,
    retryAllowed,
    reason,
    completed: state === 'COMPLETED'
  };
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function cli() {
  const args = process.argv.slice(2);
  const options = Object.fromEntries(args.map((arg) => {
    const index = arg.indexOf('=');
    return index === -1 ? [arg.replace(/^--/, ''), 'true'] : [arg.slice(2, index), arg.slice(index + 1)];
  }));

  if (!options.id || !options.evidence) {
    throw new Error('AEGIS-HANDOFF-AUDIT-007 REQUIRED_ARGS --id=<work-item> --evidence=<json-file>');
  }

  const registry = readJson(path.join(contractsDir, 'handoff-registry.json'));
  const item = registry.items.find((candidate) => candidate.id === options.id);
  if (!item) {
    throw new Error(`AEGIS-HANDOFF-AUDIT-008 UNKNOWN_WORK_ITEM ${options.id}`);
  }

  const evidencePath = path.resolve(process.cwd(), options.evidence);
  const evidence = readJson(evidencePath);
  process.stdout.write(`${JSON.stringify(auditHandoff(item, evidence), null, 2)}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  cli();
}
