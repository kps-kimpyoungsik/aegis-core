#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..', '..');
const contractsDir = path.join(root, 'product', 'contracts');

const readJson = (name) => JSON.parse(fs.readFileSync(path.join(contractsDir, name), 'utf8'));
const registry = readJson('handoff-registry.json');
const domains = readJson('domain-registry.json');
const owners = readJson('ownership-registry.json');
const capabilities = readJson('capability-registry.json');

const domainIds = new Set(domains.domains.map((item) => item.id));
const ownerIds = new Set(owners.responsibilities.map((item) => item.id));
const capabilityIds = new Set(capabilities.capabilities.map((item) => item.id));

const allowedStates = new Set([
  'HANDOFF_CREATED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'VALIDATING', 'COMPLETED',
  'BLOCKED_EXTERNAL', 'BLOCKED_DEPENDENCY', 'FAILED_VALIDATION', 'RETRY_DUE',
  'REHANDOFF_REQUIRED', 'ESCALATION_REQUIRED', 'STALE', 'PAUSED', 'ROLLBACK', 'FAILED'
]);
const allowedClaimStates = new Set([
  'UNCLAIMED', 'CLAIMED', 'IN_PROGRESS', 'VALIDATING', 'BLOCKED_EXTERNAL',
  'BLOCKED_DEPENDENCY', 'REHANDOFF_REQUIRED', 'COMPLETED_VERIFIED'
]);
const allowedExecutorTypes = new Set(['UNCONFIRMED', 'SESSION', 'AUTOMATION', 'HUMAN', 'WORKSTREAM']);

const ids = new Set();
const fingerprints = new Set();
for (const item of registry.items) {
  if (!item.id || ids.has(item.id)) throw new Error(`AEGIS-HANDOFF-001 DUPLICATE_OR_EMPTY_ID ${item.id ?? ''}`);
  ids.add(item.id);
  if (!item.failureFingerprint || fingerprints.has(item.failureFingerprint)) throw new Error(`AEGIS-HANDOFF-002 DUPLICATE_OR_EMPTY_FINGERPRINT ${item.id}`);
  fingerprints.add(item.failureFingerprint);
  if (!domainIds.has(item.domain)) throw new Error(`AEGIS-HANDOFF-003 UNKNOWN_DOMAIN ${item.id}:${item.domain}`);
  if (!ownerIds.has(item.ownerResponsibility)) throw new Error(`AEGIS-HANDOFF-004 UNKNOWN_OWNER ${item.id}:${item.ownerResponsibility}`);
  if (!allowedStates.has(item.state)) throw new Error(`AEGIS-HANDOFF-005 UNKNOWN_STATE ${item.id}:${item.state}`);
  if (!Array.isArray(item.capabilities)) throw new Error(`AEGIS-HANDOFF-006 CAPABILITIES_NOT_ARRAY ${item.id}`);
  for (const capability of item.capabilities) {
    if (!capabilityIds.has(capability)) throw new Error(`AEGIS-HANDOFF-007 UNKNOWN_CAPABILITY ${item.id}:${capability}`);
  }
  if (!Array.isArray(item.blockedBy)) throw new Error(`AEGIS-HANDOFF-008 BLOCKED_BY_NOT_ARRAY ${item.id}`);
  if (!item.nextAction || !item.retryPolicy || !item.rootCauseStatus) throw new Error(`AEGIS-HANDOFF-009 INCOMPLETE_AUDIT_CONTRACT ${item.id}`);

  const claim = item.executionClaim;
  if (!claim || typeof claim !== 'object') throw new Error(`AEGIS-HANDOFF-012 MISSING_EXECUTION_CLAIM ${item.id}`);
  if (!allowedClaimStates.has(claim.claimState)) throw new Error(`AEGIS-HANDOFF-013 UNKNOWN_CLAIM_STATE ${item.id}:${claim.claimState}`);
  if (!allowedExecutorTypes.has(claim.activeExecutorType)) throw new Error(`AEGIS-HANDOFF-014 UNKNOWN_EXECUTOR_TYPE ${item.id}:${claim.activeExecutorType}`);
  if (!claim.activeExecutorId || typeof claim.activeExecutorId !== 'string') throw new Error(`AEGIS-HANDOFF-015 MISSING_EXECUTOR_ID ${item.id}`);
  if (!claim.claimEvidence || typeof claim.claimEvidence !== 'string') throw new Error(`AEGIS-HANDOFF-016 MISSING_CLAIM_EVIDENCE ${item.id}`);
  if (!Array.isArray(claim.intendedWriteSet)) throw new Error(`AEGIS-HANDOFF-017 WRITE_SET_NOT_ARRAY ${item.id}`);
  const executorConfirmed = claim.activeExecutorType !== 'UNCONFIRMED' && claim.activeExecutorId !== 'UNCONFIRMED';
  if (['CLAIMED', 'IN_PROGRESS', 'VALIDATING'].includes(claim.claimState) && !executorConfirmed) {
    throw new Error(`AEGIS-HANDOFF-018 ACTIVE_CLAIM_WITHOUT_EXECUTOR ${item.id}`);
  }
}
for (const item of registry.items) {
  for (const blocker of item.blockedBy) {
    if (!ids.has(blocker)) throw new Error(`AEGIS-HANDOFF-010 UNKNOWN_BLOCKER ${item.id}:${blocker}`);
    if (blocker === item.id) throw new Error(`AEGIS-HANDOFF-011 SELF_BLOCKER ${item.id}`);
  }
}

const args = process.argv.slice(2);
const options = Object.fromEntries(args.map((arg) => {
  const index = arg.indexOf('=');
  return index === -1 ? [arg.replace(/^--/, ''), 'true'] : [arg.slice(2, index), arg.slice(index + 1)];
}));

const normalize = (value) => String(value ?? '').trim().toLowerCase();
const query = normalize(options.query);
const domain = normalize(options.domain);
const owner = normalize(options.owner);
const executor = normalize(options.executor);
const claimState = normalize(options['claim-state']);
const fingerprint = normalize(options.fingerprint);
const activeOnly = options.all !== 'true';

const terminalStates = new Set(['COMPLETED']);
const matches = registry.items.filter((item) => {
  if (activeOnly && terminalStates.has(item.state)) return false;
  if (domain && normalize(item.domain) !== domain) return false;
  if (owner && normalize(item.ownerResponsibility) !== owner) return false;
  if (executor && normalize(item.executionClaim.activeExecutorId) !== executor) return false;
  if (claimState && normalize(item.executionClaim.claimState) !== claimState) return false;
  if (fingerprint && !normalize(item.failureFingerprint).includes(fingerprint)) return false;
  if (query) {
    const haystack = [
      item.id, item.failureFingerprint, item.domain, item.ownerResponsibility, item.state,
      item.executionClaim.claimState, item.executionClaim.activeExecutorType,
      item.executionClaim.activeExecutorId, item.executionClaim.activeBranch,
      item.executionClaim.activePrRef, item.executionClaim.claimEvidence,
      item.nextAction, item.retryPolicy, ...(item.capabilities ?? []),
      ...(item.executionClaim.intendedWriteSet ?? [])
    ].map(normalize).join(' ');
    if (!haystack.includes(query)) return false;
  }
  return true;
});

const result = {
  registryVersion: registry.version,
  baselineMainSha: registry.baselineMainSha,
  status: registry.status,
  matchCount: matches.length,
  fallbackRequired: matches.length === 0,
  fallbackState: matches.length === 0 ? 'SEARCH_NOT_FOUND_REQUIRES_FALLBACK' : 'NOT_REQUIRED',
  items: matches.map((item) => ({
    id: item.id,
    domain: item.domain,
    ownerResponsibility: item.ownerResponsibility,
    activeExecutor: item.executionClaim.activeExecutorId,
    activeExecutorType: item.executionClaim.activeExecutorType,
    claimState: item.executionClaim.claimState,
    activeBranch: item.executionClaim.activeBranch,
    activePrRef: item.executionClaim.activePrRef,
    failureFingerprint: item.failureFingerprint,
    issueRef: item.issueRef,
    state: item.state,
    blockedBy: item.blockedBy,
    nextAction: item.nextAction,
    retryPolicy: item.retryPolicy,
    rootCauseStatus: item.rootCauseStatus
  }))
};

process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
