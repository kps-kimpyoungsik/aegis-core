import { auditHandoff } from './handoff-completion-audit.mjs';

export function createAuditEvent(item, evidence, observedAt) {
  if (!observedAt) throw new Error('AEGIS-HANDOFF-REDUCER-001 MISSING_OBSERVED_AT');
  const decision = auditHandoff(item, evidence);
  return Object.freeze({
    type: 'HANDOFF_AUDITED',
    workItemId: item.id,
    observedAt,
    previousState: item.state,
    state: decision.state,
    action: decision.action,
    retryAllowed: decision.retryAllowed,
    reason: decision.reason,
    evidenceStatus: evidence.executionStatus ?? 'UNKNOWN',
    provenance: evidence.provenance ?? {}
  });
}

export function reduceHandoff(item, auditEvents) {
  if (!Array.isArray(auditEvents)) throw new Error('AEGIS-HANDOFF-REDUCER-002 EVENTS_NOT_ARRAY');
  const relevant = auditEvents.filter((event) => event.workItemId === item.id);
  if (relevant.length === 0) return { ...item };
  for (let index = 1; index < relevant.length; index += 1) {
    if (String(relevant[index - 1].observedAt) > String(relevant[index].observedAt)) {
      throw new Error(`AEGIS-HANDOFF-REDUCER-003 OUT_OF_ORDER_EVENT ${item.id}`);
    }
  }
  const latest = relevant.at(-1);
  return {
    ...item,
    state: latest.state,
    lastAudit: {
      observedAt: latest.observedAt,
      action: latest.action,
      retryAllowed: latest.retryAllowed,
      reason: latest.reason,
      evidenceStatus: latest.evidenceStatus,
      provenance: latest.provenance
    }
  };
}

export function projectRegistry(registry, auditEvents) {
  return {
    ...registry,
    projectionPolicy: 'DERIVED_FROM_IMMUTABLE_HANDOFF_AUDIT_EVENTS',
    items: registry.items.map((item) => reduceHandoff(item, auditEvents))
  };
}
