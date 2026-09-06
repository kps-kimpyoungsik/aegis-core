import { createHash, randomUUID } from 'node:crypto';
import { appendFile, mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';

export const ACTIONABLE_SESSION_ERROR_STATES = Object.freeze([
  'WAITING',
  'BLOCKED_EXTERNAL',
  'BLOCKED_DEPENDENCY',
  'RETRY_DUE',
  'VALIDATING',
  'IN_PROGRESS',
  'REHANDOFF_REQUIRED'
]);

export const TERMINAL_SESSION_ERROR_STATES = Object.freeze([
  'COMPLETED',
  'CLEARED',
  'SUPERSEDED',
  'FAILED_FINAL',
  'CANCELLED'
]);

const KNOWN_STATES = new Set([
  ...ACTIONABLE_SESSION_ERROR_STATES,
  ...TERMINAL_SESSION_ERROR_STATES,
  'FAILED_VALIDATION',
  'ESCALATION_REQUIRED',
  'PAUSED'
]);

function requireText(value, name) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`AEGIS-FILEDB-001 ${name} is required`);
  }
  return value.trim();
}

function requireNonNegativeInteger(value, name) {
  if (!Number.isInteger(value) || value < 0) {
    throw new Error(`AEGIS-FILEDB-002 ${name} must be a non-negative integer`);
  }
  return value;
}

export function sessionShardName(sessionId) {
  const normalized = requireText(sessionId, 'sessionId');
  return `${createHash('sha256').update(normalized).digest('hex')}.jsonl`;
}

export function validateSessionErrorRecord(record) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) {
    throw new Error('AEGIS-FILEDB-003 record must be an object');
  }

  const sessionId = requireText(record.sessionId, 'sessionId');
  const failureFingerprint = requireText(record.failureFingerprint, 'failureFingerprint');
  const state = requireText(record.state, 'state');
  if (!KNOWN_STATES.has(state)) {
    throw new Error(`AEGIS-FILEDB-004 unknown state: ${state}`);
  }

  const observedAt = requireText(record.observedAt, 'observedAt');
  if (Number.isNaN(Date.parse(observedAt))) {
    throw new Error('AEGIS-FILEDB-005 observedAt must be an ISO-compatible timestamp');
  }

  const retryCount = requireNonNegativeInteger(record.retryCount ?? 0, 'retryCount');
  const maxRetry = requireNonNegativeInteger(record.maxRetry ?? 0, 'maxRetry');
  if (retryCount > maxRetry && state === 'RETRY_DUE') {
    throw new Error('AEGIS-FILEDB-006 RETRY_DUE cannot exceed maxRetry');
  }

  return {
    ...record,
    eventId: record.eventId ? requireText(record.eventId, 'eventId') : randomUUID(),
    sessionId,
    workItemId: typeof record.workItemId === 'string' && record.workItemId.trim() !== ''
      ? record.workItemId.trim()
      : null,
    episodeId: typeof record.episodeId === 'string' && record.episodeId.trim() !== ''
      ? record.episodeId.trim()
      : 'default',
    failureFingerprint,
    state,
    observedAt: new Date(observedAt).toISOString(),
    retryCount,
    maxRetry,
    externalStateFingerprint: record.externalStateFingerprint ?? null,
    nextAction: record.nextAction ?? null,
    evidenceRefs: Array.isArray(record.evidenceRefs) ? [...record.evidenceRefs] : [],
    provenance: record.provenance ?? null
  };
}

export function sessionErrorIdentity(record) {
  const validated = validateSessionErrorRecord(record);
  return [
    validated.sessionId,
    validated.workItemId ?? '-',
    validated.failureFingerprint,
    validated.episodeId
  ].join('|');
}

function compareRecordOrder(left, right) {
  const timeDelta = Date.parse(left.observedAt) - Date.parse(right.observedAt);
  if (timeDelta !== 0) return timeDelta;
  return left.eventId.localeCompare(right.eventId);
}

export function reduceLatestSessionErrorState(records, sessionId) {
  const currentSessionId = requireText(sessionId, 'sessionId');
  const latestByIdentity = new Map();

  for (const raw of records) {
    const record = validateSessionErrorRecord(raw);
    if (record.sessionId !== currentSessionId) continue;
    const identity = sessionErrorIdentity(record);
    const existing = latestByIdentity.get(identity);
    if (!existing || compareRecordOrder(existing, record) < 0) {
      latestByIdentity.set(identity, record);
    }
  }

  return [...latestByIdentity.values()].sort(compareRecordOrder);
}

export function querySessionErrors(records, {
  sessionId,
  states = ACTIONABLE_SESSION_ERROR_STATES,
  crossSession = false,
  auditMode = null
} = {}) {
  const currentSessionId = requireText(sessionId, 'sessionId');
  if (crossSession && auditMode !== 'CROSS_SESSION_AUDIT') {
    throw new Error('AEGIS-FILEDB-007 cross-session lookup requires CROSS_SESSION_AUDIT');
  }
  if (crossSession) {
    throw new Error('AEGIS-FILEDB-008 broad cross-session FileDB scanning is not supported by the session query API');
  }

  const allowed = new Set(states);
  return reduceLatestSessionErrorState(records, currentSessionId)
    .filter((record) => allowed.has(record.state));
}

export function retryDecision(record, {
  externalStateChanged = false,
  relevantCorrectiveChange = false,
  transientEvidence = false
} = {}) {
  const current = validateSessionErrorRecord(record);

  if (TERMINAL_SESSION_ERROR_STATES.includes(current.state)) {
    return { retryAllowed: false, action: 'NO_RETRY_TERMINAL', reason: current.state };
  }
  if (current.state === 'WAITING' || current.state === 'BLOCKED_DEPENDENCY') {
    return { retryAllowed: false, action: 'RECHECK_WAITING_CONDITION', reason: current.state };
  }
  if (current.state === 'BLOCKED_EXTERNAL') {
    return externalStateChanged
      ? { retryAllowed: true, action: 'CANARY_ONLY', reason: 'EXTERNAL_STATE_CHANGED' }
      : { retryAllowed: false, action: 'WAIT_EXTERNAL_STATE_CHANGE', reason: 'EXTERNAL_STATE_UNCHANGED' };
  }
  if (current.state === 'RETRY_DUE') {
    const budgetAvailable = current.retryCount < current.maxRetry;
    const retryEvidence = relevantCorrectiveChange || transientEvidence;
    return budgetAvailable && retryEvidence
      ? { retryAllowed: true, action: 'BOUNDED_RETRY', reason: 'RETRY_EVIDENCE_CONFIRMED' }
      : { retryAllowed: false, action: budgetAvailable ? 'WAIT_FOR_RETRY_EVIDENCE' : 'ESCALATE_RETRY_EXHAUSTED', reason: budgetAvailable ? 'NO_RETRY_EVIDENCE' : 'RETRY_BUDGET_EXHAUSTED' };
  }
  if (current.state === 'VALIDATING') {
    return { retryAllowed: false, action: 'VERIFY_EXISTING_CANDIDATE', reason: current.state };
  }
  if (current.state === 'IN_PROGRESS') {
    return { retryAllowed: false, action: 'CONTINUE_EXISTING_WORKSTREAM', reason: current.state };
  }
  if (current.state === 'REHANDOFF_REQUIRED') {
    return { retryAllowed: false, action: 'RESOLVE_OWNER_BEFORE_EXECUTION', reason: current.state };
  }
  return { retryAllowed: false, action: 'FAIL_CLOSED', reason: current.state };
}

export function assertAppendTransition(history, nextRecord) {
  const next = validateSessionErrorRecord(nextRecord);
  const sameSession = history.filter((record) => record?.sessionId === next.sessionId);
  const latest = reduceLatestSessionErrorState(sameSession, next.sessionId)
    .find((record) => sessionErrorIdentity(record) === sessionErrorIdentity(next));

  if (!latest) return next;
  if (latest.eventId === next.eventId) {
    throw new Error('AEGIS-FILEDB-010 duplicate eventId for the same error identity');
  }
  if (compareRecordOrder(next, latest) <= 0) {
    throw new Error('AEGIS-FILEDB-011 append event must be newer than the current identity state');
  }
  if (TERMINAL_SESSION_ERROR_STATES.includes(latest.state)
      && !TERMINAL_SESSION_ERROR_STATES.includes(next.state)) {
    throw new Error('AEGIS-FILEDB-012 terminal error identity cannot reactivate; use a new episodeId');
  }
  return next;
}

export class SessionErrorFileDb {
  constructor({ rootDir, sessionId }) {
    this.rootDir = path.resolve(requireText(rootDir, 'rootDir'));
    this.sessionId = requireText(sessionId, 'sessionId');
    this.filePath = path.join(this.rootDir, 'session-errors', sessionShardName(this.sessionId));
  }

  async append(record) {
    const validated = validateSessionErrorRecord({ ...record, sessionId: this.sessionId });
    const history = await this.readAll();
    assertAppendTransition(history, validated);
    await mkdir(path.dirname(this.filePath), { recursive: true });
    await appendFile(this.filePath, `${JSON.stringify(validated)}\n`, { encoding: 'utf8', flag: 'a' });
    return validated;
  }

  async readAll() {
    let content;
    try {
      content = await readFile(this.filePath, 'utf8');
    } catch (error) {
      if (error?.code === 'ENOENT') return [];
      throw error;
    }

    return content
      .split('\n')
      .filter((line) => line.trim() !== '')
      .map((line, index) => {
        try {
          return validateSessionErrorRecord(JSON.parse(line));
        } catch (error) {
          throw new Error(`AEGIS-FILEDB-009 corrupt JSONL at line ${index + 1}: ${error.message}`);
        }
      });
  }

  async queryActionable() {
    return querySessionErrors(await this.readAll(), { sessionId: this.sessionId });
  }
}
