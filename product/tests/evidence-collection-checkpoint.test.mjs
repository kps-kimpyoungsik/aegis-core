import assert from 'node:assert/strict';
import test from 'node:test';
import {
  sanitizePublicCheckpoint,
  validateCollectionScope,
  validateWatermarkTransition
} from '../tools/evidence-collection-checkpoint.mjs';

function completeScope(overrides = {}) {
  return {
    collectionId: 'MAIL-20260906-001',
    sourceKind: 'GMAIL_NOTIFICATION_EVIDENCE',
    sourceAccountScope: 'primary-account',
    querySignature: 'github-notifications-2d-v1',
    collectionStartedAt: '2026-09-06T13:00:00Z',
    collectionCompletedAt: '2026-09-06T13:05:00Z',
    observedEventTimeMin: '2026-09-06T04:00:00Z',
    observedEventTimeMax: '2026-09-06T13:00:00Z',
    pageCount: 6,
    itemCount: 600,
    completeness: 'COMPLETE_FOR_DECLARED_SCOPE',
    continuationPresent: false,
    sourceExhausted: true,
    watermarkAdvanced: false,
    privacyClass: 'PRIVATE_SOURCE_PUBLIC_SANITIZED_CHECKPOINT',
    gaps: [],
    ...overrides
  };
}

test('complete bounded collection may advance watermark', () => {
  const checked = validateCollectionScope(completeScope());
  assert.equal(checked.canClaimComplete, true);
  assert.equal(checked.canAdvanceWatermark, true);
});

test('partial continuation may not advance watermark', () => {
  assert.throws(
    () => validateCollectionScope(completeScope({
      completeness: 'PARTIAL_CONTINUATION_AVAILABLE',
      continuationPresent: true,
      sourceExhausted: false,
      watermarkAdvanced: true
    })),
    /PARTIAL_WATERMARK_ADVANCE_FORBIDDEN/
  );
});

test('complete classification is rejected when unresolved gap exists', () => {
  assert.throws(
    () => validateCollectionScope(completeScope({
      gaps: [{ id: 'page-7', state: 'OPEN', reason: 'RATE_LIMIT' }]
    })),
    /COMPLETE_WITH_UNRESOLVED_GAP/
  );
});

test('resolved historical gap does not block complete scope', () => {
  const checked = validateCollectionScope(completeScope({
    gaps: [{ id: 'page-2', state: 'RECONCILED', reason: 'RECOLLECTED' }]
  }));
  assert.equal(checked.unresolvedGapCount, 0);
  assert.equal(checked.canAdvanceWatermark, true);
});

test('query signature change invalidates watermark continuity', () => {
  const result = validateWatermarkTransition(
    {
      eventTimeHighWatermark: '2026-09-06T12:00:00Z',
      stableSourceIdTieBreaker: 'm100',
      querySignature: 'github-notifications-1d-v1',
      sourceAccountScope: 'primary-account'
    },
    {
      eventTimeHighWatermark: '2026-09-06T13:00:00Z',
      stableSourceIdTieBreaker: 'm200',
      querySignature: 'github-notifications-2d-v1',
      sourceAccountScope: 'primary-account'
    },
    completeScope()
  );
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'WATERMARK_CONTINUITY_INVALIDATED');
});

test('equal timestamp requires stable source tie-breaker advancement', () => {
  const previous = {
    eventTimeHighWatermark: '2026-09-06T13:00:00Z',
    stableSourceIdTieBreaker: 'm200',
    querySignature: 'github-notifications-2d-v1',
    sourceAccountScope: 'primary-account'
  };
  const rejected = validateWatermarkTransition(previous, { ...previous }, completeScope());
  assert.equal(rejected.allowed, false);
  assert.equal(rejected.reason, 'WATERMARK_TIE_BREAKER_NOT_ADVANCED');
  const accepted = validateWatermarkTransition(previous, { ...previous, stableSourceIdTieBreaker: 'm201' }, completeScope());
  assert.equal(accepted.allowed, true);
});

test('public checkpoint rejects private Gmail identifiers and continuation tokens', () => {
  assert.throws(
    () => sanitizePublicCheckpoint({ collectionId: 'x', messageIds: ['private-id'] }),
    /PRIVATE_FIELD_FORBIDDEN messageIds/
  );
  assert.throws(
    () => sanitizePublicCheckpoint({ collectionId: 'x', nested: { continuationToken: 'opaque' } }),
    /PRIVATE_FIELD_FORBIDDEN continuationToken/
  );
});

test('sanitized aggregate checkpoint is accepted', () => {
  const checkpoint = sanitizePublicCheckpoint({
    collectionId: 'MAIL-20260906-001',
    itemCount: 600,
    completeness: 'PARTIAL_LIMIT_REACHED',
    observedEventTimeMin: '2026-09-06T04:22:43Z',
    observedEventTimeMax: '2026-09-06T13:32:36Z',
    failureFingerprints: ['external-provider|vercel|daily-capacity']
  });
  assert.equal(Object.isFrozen(checkpoint), true);
});
