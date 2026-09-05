package io.aegis.testkits;

import io.aegis.application.RuntimeKernel;
import io.aegis.core.TaskState;
import java.time.Clock;

public final class RuntimeKernelTest {
    private RuntimeKernelTest() {}
    public static void main(String[] args) {
        var kernel = new RuntimeKernel(Clock.systemUTC());
        var task = kernel.createTask("tenant-a", "work-1", "release rc2");
        kernel.transitionTask("tenant-a", task.id(), TaskState.READY);
        var execution = kernel.startExecution("tenant-a", task.id());
        kernel.validateFence("tenant-a", execution.id(), execution.fenceToken());
        boolean staleRejected = false;
        try { kernel.validateFence("tenant-a", execution.id(), execution.fenceToken() - 1); } catch (IllegalStateException expected) { staleRejected = true; }
        if (!staleRejected) throw new AssertionError("stale fence was accepted");
        boolean tenantRejected = false;
        try { kernel.validateFence("tenant-b", execution.id(), execution.fenceToken()); } catch (IllegalArgumentException expected) { tenantRejected = true; }
        if (!tenantRejected) throw new AssertionError("cross-tenant execution lookup was accepted");
        System.out.println("RuntimeKernelTest PASS");
    }
}
