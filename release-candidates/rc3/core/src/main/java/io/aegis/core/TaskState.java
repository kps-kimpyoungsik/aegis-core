package io.aegis.core;

public enum TaskState {
    NEW, READY, RUNNING, VALIDATING, COMPLETED, FAILED, PAUSED, CANCELLED;
    public boolean canTransitionTo(TaskState next) {
        return switch (this) {
            case NEW -> next == READY || next == CANCELLED;
            case READY -> next == RUNNING || next == CANCELLED;
            case RUNNING -> next == VALIDATING || next == FAILED || next == PAUSED;
            case VALIDATING -> next == COMPLETED || next == FAILED;
            case PAUSED -> next == RUNNING || next == CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
