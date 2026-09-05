import { transitionTask } from "@aegis/core-domain";
import { preflight } from "@aegis/responsibility";
import { retrieveMinimumContext, buildPointContext } from "@aegis/retrieval-runtime";
import { createMemoryItem, workingToEpisode, createFailureMemory } from "@aegis/memory-runtime";
import { MemoryKinds } from "@aegis/memory-contracts";

export async function executeTaskLifecycle(input) {
  const events = [];
  const emit = (type, data = {}) => events.push(Object.freeze({ type, taskId: input.task.id, ...data }));
  const ownership = preflight({ responsibility: input.responsibility, requestedOwner: input.owner, activeClaims: input.activeClaims ?? [] });
  emit("OWNERSHIP_PREFLIGHT", { decision: ownership.decision, overlap: ownership.overlap });
  if (ownership.decision === "HANDOFF") return { status: "HANDOFF_REQUIRED", ownership, task: input.task, events, pointContext: [] };

  const retrieved = retrieveMinimumContext(input.memoryItems ?? [], input.retrievalQuery ?? {}, input.retrievalPolicy ?? {});
  const pointContext = buildPointContext(retrieved, { maxItems: input.retrievalPolicy?.maxItems ?? 3 });
  emit("MEMORY_RETRIEVED", { count: pointContext.length });

  let task = input.task;
  for (const state of ["DIAGNOSING", "PLANNED", "READY", "RUNNING"]) task = transitionTask(task, state);
  emit("TASK_RUNNING", { version: task.version });

  try {
    const harnessResult = await input.runHarness({ task, pointContext });
    task = transitionTask(task, "VALIDATING");
    emit("VALIDATION", { status: "PASS" });
    task = transitionTask(task, "COMPLETED");

    const working = createMemoryItem({
      id: input.memoryIds?.working ?? `${task.id}:working`,
      kind: MemoryKinds.WORKING,
      scope: input.memoryScope ?? "PROJECT",
      content: { taskId: task.id, result: harnessResult.output ?? null },
      provenance: { sourceRef: task.id, sourceType: "PAST_TASK" },
    });
    const episode = workingToEpisode(working, {
      episodeId: input.memoryIds?.episode ?? `${task.id}:episode`,
      validationRef: input.validationRef ?? `${task.id}:validation`,
    });
    emit("TASK_COMPLETED", { version: task.version, episodeId: episode.id });
    return { status: "COMPLETED", ownership, task, events, pointContext, harnessResult, memory: episode };
  } catch (error) {
    task = transitionTask(task, "FAILED");
    const failureMemory = createFailureMemory({
      id: input.memoryIds?.failure ?? `${task.id}:failure`,
      scope: input.memoryScope ?? "PROJECT",
      content: { taskId: task.id, error: String(error?.message ?? error), code: error?.code ?? "UNKNOWN" },
      provenance: { sourceRef: input.failureEvidenceRef ?? `${task.id}:runtime-failure`, sourceType: "PAST_FAILURE" },
    });
    emit("FAILURE", { code: error?.code ?? "UNKNOWN", failureMemoryId: failureMemory.id });
    return { status: "FAILED", ownership, task, events, pointContext, error, memory: failureMemory };
  }
}

export function createRuntimeStateStore() {
  const tasks = new Map();
  const events = [];
  const memories = new Map();
  let sequence = 0;
  return Object.freeze({
    record(result) {
      if (result?.task?.id) tasks.set(result.task.id, Object.freeze({ ...result.task }));
      for (const event of result?.events ?? []) events.push(Object.freeze({ sequence: ++sequence, ...event }));
      if (result?.memory?.id) memories.set(result.memory.id, result.memory);
      return result;
    },
    snapshot() {
      return Object.freeze({
        tasks: Object.freeze([...tasks.values()].map((task) => Object.freeze({ ...task }))),
        events: Object.freeze(events.map((event) => Object.freeze({ ...event }))),
        memories: Object.freeze([...memories.values()]),
      });
    },
  });
}

export function createTaskExecutionUseCase({ runHarness, stateStore = createRuntimeStateStore(), activeClaims = [], memoryItems = [], retrievalPolicy = {} } = {}) {
  if (typeof runHarness !== 'function') throw new Error('AEGIS-APP-001 RUN_HARNESS_REQUIRED');
  return Object.freeze({
    async executeTask(command) {
      const result = await executeTaskLifecycle({
        ...command,
        runHarness,
        activeClaims: command.activeClaims ?? activeClaims,
        memoryItems: command.memoryItems ?? memoryItems,
        retrievalPolicy: { ...retrievalPolicy, ...(command.retrievalPolicy ?? {}) },
      });
      stateStore.record(result);
      return result;
    },
    getSnapshot() { return stateStore.snapshot(); },
  });
}
