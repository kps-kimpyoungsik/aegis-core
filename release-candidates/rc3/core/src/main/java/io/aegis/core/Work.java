package io.aegis.core;

import java.time.Instant;
import java.util.Objects;

public record Work(String id, String tenantId, String objective, long version, Instant createdAt) {
    public Work {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(createdAt, "createdAt");
        if (id.isBlank() || tenantId.isBlank() || objective.isBlank()) throw new IllegalArgumentException("blank canonical field");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
    }
}
