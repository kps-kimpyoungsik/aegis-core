package io.aegis.core;

import java.time.Instant;
import java.util.Objects;

public record Execution(String id, String tenantId, String taskId, long attempt, long fenceToken, ExecutionState state, Instant startedAt) {
    public Execution {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(taskId); Objects.requireNonNull(state); Objects.requireNonNull(startedAt);
        if (attempt < 1 || fenceToken < 1) throw new IllegalArgumentException("attempt/fence must be positive");
    }
}
