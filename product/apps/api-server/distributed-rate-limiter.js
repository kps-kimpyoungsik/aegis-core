const DEFAULT_WINDOW_MS = 60_000;

function requirePositiveInteger(value, code) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(code);
  return value;
}

function requireStore(store) {
  if (!store || typeof store.increment !== "function") {
    throw new Error("AEGIS-API-023 DISTRIBUTED_RATE_STORE_REQUIRED");
  }
  return store;
}

function normalizeIdentityPart(value, code) {
  if (typeof value !== "string" || value.trim().length === 0) throw new Error(code);
  return value.trim();
}

export function createDistributedFixedWindowRateLimiter({
  limit,
  windowMs = DEFAULT_WINDOW_MS,
  store,
  now = () => Date.now(),
} = {}) {
  const resolvedLimit = requirePositiveInteger(limit, "AEGIS-API-017 INVALID_RATE_LIMIT");
  const resolvedWindowMs = requirePositiveInteger(windowMs, "AEGIS-API-018 INVALID_RATE_WINDOW");
  const resolvedStore = requireStore(store);

  return async ({ principal, method, url }) => {
    const tenantId = normalizeIdentityPart(principal?.tenantId, "AEGIS-API-024 RATE_LIMIT_IDENTITY_REQUIRED");
    const principalId = normalizeIdentityPart(principal?.id, "AEGIS-API-024 RATE_LIMIT_IDENTITY_REQUIRED");
    const requestMethod = normalizeIdentityPart(method, "AEGIS-API-024 RATE_LIMIT_IDENTITY_REQUIRED");
    const requestUrl = normalizeIdentityPart(url, "AEGIS-API-024 RATE_LIMIT_IDENTITY_REQUIRED");
    const currentTime = Number(now());
    if (!Number.isFinite(currentTime) || currentTime < 0) throw new Error("AEGIS-API-026 INVALID_RATE_CLOCK");

    const windowId = Math.floor(currentTime / resolvedWindowMs);
    const remainingWindowMs = Math.max(1, resolvedWindowMs - (currentTime % resolvedWindowMs));
    const key = `${tenantId}\u0000${principalId}\u0000${requestMethod}\u0000${requestUrl}`;
    const result = await resolvedStore.increment({
      key,
      windowId,
      ttlMs: remainingWindowMs,
    });

    if (!result || !Number.isSafeInteger(result.count) || result.count <= 0) {
      throw new Error("AEGIS-API-025 INVALID_DISTRIBUTED_RATE_RESULT");
    }

    const retryAfterSeconds = Math.max(
      1,
      Math.ceil((Number.isFinite(result.retryAfterMs) && result.retryAfterMs > 0
        ? result.retryAfterMs
        : remainingWindowMs) / 1000),
    );

    if (result.count > resolvedLimit) {
      return { allowed: false, retryAfterSeconds };
    }

    return {
      allowed: true,
      remaining: Math.max(0, resolvedLimit - result.count),
    };
  };
}
