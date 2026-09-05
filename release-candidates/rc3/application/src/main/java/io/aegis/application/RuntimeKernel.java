package io.aegis.application;

import io.aegis.core.Execution;
import io.aegis.core.ExecutionFence;
import io.aegis.core.ExecutionState;
import io.aegis.core.Task;
import io.aegis.core.TaskState;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RuntimeKernel {
    private final Clock clock;
    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<String, Execution> executions = new HashMap<>();
    private long fenceSequence = 100;

    public RuntimeKernel(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public synchronized Task createTask(String tenantId, String workId, String objective) {
        var task = new Task(UUID.randomUUID().toString(), tenantId, workId, objective, TaskState.NEW, 1, clock.instant());
        tasks.put(key(tenantId, task.id()), task);
        return task;
    }

    public synchronized Task transitionTask(String tenantId, String taskId, TaskState next) {
        var k = key(tenantId, taskId);
        var current = requireTask(k);
        var updated = current.transition(next);
        tasks.put(k, updated);
        return updated;
    }

    public synchronized Execution startExecution(String tenantId, String taskId) {
        var task = requireTask(key(tenantId, taskId));
        if (task.state() != TaskState.READY && task.state() != TaskState.PAUSED) throw new IllegalStateException("TASK_NOT_READY");
        long fence = ++fenceSequence;
        var execution = new Execution(UUID.randomUUID().toString(), tenantId, taskId, 1, fence, ExecutionState.RUNNING, clock.instant());
        executions.put(key(tenantId, execution.id()), execution);
        transitionTask(tenantId, taskId, TaskState.RUNNING);
        return execution;
    }

    public synchronized void validateFence(String tenantId, String executionId, long presentedFence) {
        var execution = executions.get(key(tenantId, executionId));
        if (execution == null) throw new IllegalArgumentException("execution not found");
        ExecutionFence.requireCurrent(execution.fenceToken(), presentedFence);
    }

    private Task requireTask(String key) {
        var task = tasks.get(key);
        if (task == null) throw new IllegalArgumentException("task not found");
        return task;
    }

    private static String key(String tenantId, String id) { return tenantId + "\u0000" + id; }
}
