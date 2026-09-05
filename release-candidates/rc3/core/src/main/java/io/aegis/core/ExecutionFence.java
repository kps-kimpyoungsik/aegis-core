package io.aegis.core;

public final class ExecutionFence {
    private ExecutionFence() {}
    public static void requireCurrent(long expectedFence, long presentedFence) {
        if (presentedFence != expectedFence) throw new IllegalStateException("STALE_EXECUTION_FENCE");
    }
}
