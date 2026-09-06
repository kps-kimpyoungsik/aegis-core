const COMPLETENESS_STATES = new Set([
  'COMPLETE_FOR_DECLARED_SCOPE',
  'PARTIAL_CONTINUATION_AVAILABLE',
  'PARTIAL_LIMIT_REACHED',
  'PARTIAL_SOURCE_ERROR',
  'PARTIAL_AUTHORITY',
  'PARTIAL_TIME_UNBOUNDED',
  'UNKNOWN_COMPLETENESS',
  'NOT_EXECUTED'
]);

function requireNonEmpty(value, field) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`AEGIS-COLLECTION-001 INVALID_STRING ${field}`);
  }
  return value;
}

function requireBoolean(value, field) {
  if (typeof value !== 'boolean') {
    throw new Error(`AEGIS-COLLECTION-002 INVALID_BOOLEAN ${field}`);
  }
  return value;
}

function requireCount(value, field) {
  if (!Number.isInteger(value) || value < 0) {
    throw new Error(`AEGIS-COLLECTION-003 INVALID_COUNT ${field}`);
  }
  return value;
}

function parseInstant(value, field, { optional = false } = {}) {
  if (optional && (value === null || value === undefined || value === '')) return null;
  requireNonEmpty(value, field);
  const ms = Date.parse(value);
  if (!Number.isFinite(ms)) throw new Error(`AEGIS-COLLECTION-004 INVALID_INSTANT ${field}`);
  return ms;
}

function normalizeGaps(gaps = []) {
  if (!Array.isArray(gaps)) throw new Error('AEGIS-COLLECTION-005 GAPS_NOT_ARRAY');
  return gaps.map((gap, index) => {
    if (!gap || typeof gap !== 'object') throw new Error(`AEGIS-COLLECTION-006 INVALID_GAP ${index}`);
    return Object.freeze({
      id: requireNonEmpty(gap.id, `gaps[${index}].id`),
      state: requireNonEmpty(gap.state, `gaps[${index}].state`),
      reason: requireNonEmpty(gap.reason, `gaps[${index}].reason`)
    });
  });
}

function containsPrivateCheckpointMaterial(checkpoint) {
  const forbiddenKeys = new Set([
    'messageId', 'messageIds', 'threadId', 'threadIds', 'rawBody', 'rawSnippet',
    'continuationToken', 'pageToken', 'accessToken', 'authorization', 'attachmentId'
  ]);
  const stack = [checkpoint];
  while (stack.length) {
    const value = stack.pop();
    if (!value || typeof value !== 'object') continue;
    for (const [key, child] of Object.entries(value)) {
      if (forbiddenKeys.has(key)) return key;
      if (child && typeof child === 'object') stack.push(child);
    }
  }
  return null;
}

export function validateCollectionScope(scope) {
  if (!scope || typeof scope !== 'object') throw new Error('AEGIS-COLLECTION-007 INVALID_SCOPE');
  const started = parseInstant(scope.collectionStartedAt, 'collectionStartedAt');
  const completed = parseInstant(scope.collectionCompletedAt, 'collectionCompletedAt', { optional: true });
  const eventMin = parseInstant(scope.observedEventTimeMin, 'observedEventTimeMin', { optional: true });
  const eventMax = parseInstant(scope.observedEventTimeMax, 'observedEventTimeMax', { optional: true });
  if (completed !== null && completed < started) throw new Error('AEGIS-COLLECTION-008 COLLECTION_TIME_REVERSED');
  if (eventMin !== null && eventMax !== null && eventMax < eventMin) throw new Error('AEGIS-COLLECTION-009 EVENT_TIME_REVERSED');
  if (!COMPLETENESS_STATES.has(scope.completeness)) throw new Error(`AEGIS-COLLECTION-010 UNKNOWN_COMPLETENESS ${scope.completeness}`);

  const gaps = normalizeGaps(scope.gaps);
  const continuationPresent = requireBoolean(scope.continuationPresent, 'continuationPresent');
  const sourceExhausted = requireBoolean(scope.sourceExhausted, 'sourceExhausted');
  const watermarkAdvanced = requireBoolean(scope.watermarkAdvanced, 'watermarkAdvanced');
  const pageCount = requireCount(scope.pageCount, 'pageCount');
  const itemCount = requireCount(scope.itemCount, 'itemCount');

  requireNonEmpty(scope.collectionId, 'collectionId');
  requireNonEmpty(scope.sourceKind, 'sourceKind');
  requireNonEmpty(scope.sourceAccountScope, 'sourceAccountScope');
  requireNonEmpty(scope.querySignature, 'querySignature');
  requireNonEmpty(scope.privacyClass, 'privacyClass');

  const unresolvedGaps = gaps.filter((gap) => !['CLEARED', 'RECONCILED'].includes(gap.state));
  const complete = scope.completeness === 'COMPLETE_FOR_DECLARED_SCOPE';

  if (complete && continuationPresent) throw new Error('AEGIS-COLLECTION-011 COMPLETE_WITH_CONTINUATION');
  if (complete && !sourceExhausted) throw new Error('AEGIS-COLLECTION-012 COMPLETE_WITHOUT_SOURCE_EXHAUSTION');
  if (complete && unresolvedGaps.length > 0) throw new Error('AEGIS-COLLECTION-013 COMPLETE_WITH_UNRESOLVED_GAP');
  if (!complete && watermarkAdvanced) throw new Error('AEGIS-COLLECTION-014 PARTIAL_WATERMARK_ADVANCE_FORBIDDEN');
  if ((continuationPresent || unresolvedGaps.length > 0 || !sourceExhausted) && watermarkAdvanced) {
    throw new Error('AEGIS-COLLECTION-015 WATERMARK_ADVANCE_OVER_GAP_FORBIDDEN');
  }
  if (scope.completeness === 'NOT_EXECUTED' && (pageCount !== 0 || itemCount !== 0)) {
    throw new Error('AEGIS-COLLECTION-016 NOT_EXECUTED_WITH_RESULTS');
  }

  return Object.freeze({
    ...scope,
    gaps: Object.freeze(gaps),
    unresolvedGapCount: unresolvedGaps.length,
    canClaimComplete: complete,
    canAdvanceWatermark: complete && sourceExhausted && !continuationPresent && unresolvedGaps.length === 0
  });
}

export function validateWatermarkTransition(previous, candidate, scope) {
  const checked = validateCollectionScope(scope);
  if (!checked.canAdvanceWatermark) {
    return Object.freeze({ allowed: false, reason: 'COLLECTION_SCOPE_NOT_COMPLETE' });
  }
  if (!candidate || typeof candidate !== 'object') throw new Error('AEGIS-COLLECTION-017 INVALID_CANDIDATE_WATERMARK');
  requireNonEmpty(candidate.querySignature, 'candidate.querySignature');
  requireNonEmpty(candidate.sourceAccountScope, 'candidate.sourceAccountScope');
  requireNonEmpty(candidate.stableSourceIdTieBreaker, 'candidate.stableSourceIdTieBreaker');
  const candidateTime = parseInstant(candidate.eventTimeHighWatermark, 'candidate.eventTimeHighWatermark');

  if (candidate.querySignature !== scope.querySignature || candidate.sourceAccountScope !== scope.sourceAccountScope) {
    return Object.freeze({ allowed: false, reason: 'SCOPE_SIGNATURE_MISMATCH' });
  }

  if (previous) {
    const previousTime = parseInstant(previous.eventTimeHighWatermark, 'previous.eventTimeHighWatermark');
    if (previous.querySignature !== candidate.querySignature || previous.sourceAccountScope !== candidate.sourceAccountScope) {
      return Object.freeze({ allowed: false, reason: 'WATERMARK_CONTINUITY_INVALIDATED' });
    }
    if (candidateTime < previousTime) return Object.freeze({ allowed: false, reason: 'WATERMARK_TIME_REGRESSION' });
    if (candidateTime === previousTime && candidate.stableSourceIdTieBreaker <= previous.stableSourceIdTieBreaker) {
      return Object.freeze({ allowed: false, reason: 'WATERMARK_TIE_BREAKER_NOT_ADVANCED' });
    }
  }

  return Object.freeze({ allowed: true, reason: 'ADVANCE_SAFE' });
}

export function sanitizePublicCheckpoint(checkpoint) {
  if (!checkpoint || typeof checkpoint !== 'object') throw new Error('AEGIS-COLLECTION-018 INVALID_CHECKPOINT');
  const forbidden = containsPrivateCheckpointMaterial(checkpoint);
  if (forbidden) throw new Error(`AEGIS-COLLECTION-019 PRIVATE_FIELD_FORBIDDEN ${forbidden}`);
  return Object.freeze(structuredClone(checkpoint));
}
