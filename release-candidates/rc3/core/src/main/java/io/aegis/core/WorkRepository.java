package io.aegis.core;

import java.util.Optional;

public interface WorkRepository {
    Work saveNew(Work work, String idempotencyKey);
    Optional<Work> findById(String tenantId, String workId);
}
