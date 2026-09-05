package io.aegis.application;

import io.aegis.core.Work;
import io.aegis.core.WorkRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryWorkRepository implements WorkRepository {
    private final Map<String, Work> works = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();

    @Override
    public synchronized Work saveNew(Work work, String idempotencyKey) {
        var existingId = idempotency.get(idempotencyKey);
        if (existingId != null) return works.get(existingId);
        works.put(key(work.tenantId(), work.id()), work);
        idempotency.put(idempotencyKey, key(work.tenantId(), work.id()));
        return work;
    }

    @Override
    public Optional<Work> findById(String tenantId, String workId) {
        return Optional.ofNullable(works.get(key(tenantId, workId)));
    }

    private static String key(String tenantId, String workId) { return tenantId + ":" + workId; }
}
