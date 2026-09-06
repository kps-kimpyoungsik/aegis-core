import { createHash } from 'node:crypto';

function normalizeContent(value, name) {
  if (typeof value !== 'string') {
    throw new Error(`AEGIS-WRITE-GUARD-001 ${name} must be a string`);
  }
  return value.replace(/\r\n/g, '\n');
}

export function contentDigest(content) {
  return createHash('sha256').update(normalizeContent(content, 'content')).digest('hex');
}

export function contentWriteDecision(currentContent, nextContent) {
  const current = normalizeContent(currentContent, 'currentContent');
  const next = normalizeContent(nextContent, 'nextContent');
  const currentDigest = contentDigest(current);
  const nextDigest = contentDigest(next);

  if (currentDigest === nextDigest) {
    return Object.freeze({
      writeAllowed: false,
      action: 'SKIP_NOOP_WRITE',
      reason: 'CONTENT_UNCHANGED',
      currentDigest,
      nextDigest
    });
  }

  return Object.freeze({
    writeAllowed: true,
    action: 'WRITE_CHANGED_CONTENT',
    reason: 'CONTENT_CHANGED',
    currentDigest,
    nextDigest
  });
}

export function assertContentWriteRequired(currentContent, nextContent) {
  const decision = contentWriteDecision(currentContent, nextContent);
  if (!decision.writeAllowed) {
    throw new Error('AEGIS-WRITE-GUARD-002 no-op repository write is forbidden');
  }
  return decision;
}
