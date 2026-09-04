package aegis.runtime.kernel;

import java.util.Objects;

public final class RuntimeDispatchKernel {
    private RuntimeDispatchKernel() {}

    public record Command(String executionId, String commandId, long epoch, String toolName) {
        public Command {
            executionId = requireText(executionId, "executionId");
            commandId = requireText(commandId, "commandId");
            toolName = requireText(toolName, "toolName");
            if (epoch < 0) throw new IllegalArgumentException("epoch must be >= 0");
        }
    }

    public record ToolInvocation(String executionId, String commandId, String invocationId, long epoch, String toolName) {
        public ToolInvocation {
            executionId = requireText(executionId, "executionId");
            commandId = requireText(commandId, "commandId");
            invocationId = requireText(invocationId, "invocationId");
            toolName = requireText(toolName, "toolName");
            if (epoch < 0) throw new IllegalArgumentException("epoch must be >= 0");
        }
    }

    public record ToolResult(String executionId, String commandId, String invocationId, long epoch, boolean success) {}

    public record DispatchState(String lastCommandId, String activeInvocationId, String lastEventId) {
        public static DispatchState empty() { return new DispatchState("", "", ""); }
    }

    public record DispatchDecision(DispatchState next, ToolInvocation invocation) {}

    public static DispatchDecision dispatch(Command command, DispatchState state, String invocationId) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(state, "state");
        invocationId = requireText(invocationId, "invocationId");
        if (command.commandId().equals(state.lastCommandId())) throw new IllegalStateException("duplicate command");
        if (!state.activeInvocationId().isBlank()) throw new IllegalStateException("active invocation exists");
        var invocation = new ToolInvocation(command.executionId(), command.commandId(), invocationId, command.epoch(), command.toolName());
        return new DispatchDecision(new DispatchState(command.commandId(), invocationId, state.lastEventId()), invocation);
    }

    public static DispatchState correlateResult(DispatchState state, ToolInvocation invocation, ToolResult result) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(result, "result");
        if (!state.activeInvocationId().equals(invocation.invocationId())) throw new IllegalStateException("invocation not active");
        if (!invocation.executionId().equals(result.executionId())) throw new IllegalStateException("execution mismatch");
        if (!invocation.commandId().equals(result.commandId())) throw new IllegalStateException("command mismatch");
        if (!invocation.invocationId().equals(result.invocationId())) throw new IllegalStateException("invocation mismatch");
        if (invocation.epoch() != result.epoch()) throw new IllegalStateException("stale result epoch");
        return new DispatchState(state.lastCommandId(), "", state.lastEventId());
    }

    public static DispatchState acceptEvent(DispatchState state, String eventId) {
        Objects.requireNonNull(state, "state");
        eventId = requireText(eventId, "eventId");
        if (eventId.equals(state.lastEventId())) return state;
        return new DispatchState(state.lastCommandId(), state.activeInvocationId(), eventId);
    }

    public static boolean schedulerWins(String leaseOwner, String contender) {
        leaseOwner = requireText(leaseOwner, "leaseOwner");
        contender = requireText(contender, "contender");
        return leaseOwner.equals(contender);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
