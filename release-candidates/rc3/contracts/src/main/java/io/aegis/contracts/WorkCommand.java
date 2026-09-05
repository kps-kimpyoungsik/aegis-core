package io.aegis.contracts;

public record WorkCommand(String commandId, String idempotencyKey, String tenantId, String objective) {
    public WorkCommand {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (objective == null || objective.isBlank()) throw new IllegalArgumentException("objective is required");
    }
}
