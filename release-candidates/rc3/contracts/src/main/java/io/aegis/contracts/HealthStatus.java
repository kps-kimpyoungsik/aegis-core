package io.aegis.contracts;

public record HealthStatus(String status, String version) {
    public HealthStatus {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("version is required");
    }
}
