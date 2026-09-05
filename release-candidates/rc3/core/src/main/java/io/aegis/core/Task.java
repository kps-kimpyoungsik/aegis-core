package io.aegis.core;

import java.time.Instant;
import java.util.Objects;

public record Task(String id, String tenantId, String workId, String objective, TaskState state, long version, Instant createdAt) {
    public Task {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId); Objects.requireNonNull(workId);
        Objects.requireNonNull(objective); Objects.requireNonNull(state); Objects.requireNonNull(createdAt);
        if (id.isBlank() || tenantId.isBlank() || workId.isBlank() || objective.isBlank()) throw new IllegalArgumentException("blank canonical field");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
    }
    public Task transition(TaskState next) {
        if (!state.canTransitionTo(next)) throw new IllegalStateException("invalid task transition: " + state + " -> " + next);
        return new Task(id, tenantId, workId, objective, next, version + 1, createdAt);
    }
}
