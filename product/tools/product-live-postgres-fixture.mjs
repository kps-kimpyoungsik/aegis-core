import { createTask } from "@aegis/core-domain";
import { createTaskExecutionUseCase } from "@aegis/application-runtime";

const taskId = process.env.AEGIS_E2E_TASK_ID || "product-live-postgres-1";
const runtime = createTaskExecutionUseCase({
  runHarness: async ({ task }) => ({ status: "SUCCESS", output: { taskId: task.id, persistedCandidate: true } }),
});

const task = createTask({ id: taskId, goal: "verify product runtime to canonical live postgres wiring", owner: "release-gate" });
const result = await runtime.executeTask({ task, responsibility: "release", owner: "release-gate" });
if (result.status !== "COMPLETED") throw new Error(`AEGIS-E2E-POSTGRES-001 unexpected status ${result.status}`);

const snapshot = runtime.getSnapshot();
if (snapshot.tasks.length !== 1 || snapshot.tasks[0].id !== taskId || snapshot.tasks[0].status !== "COMPLETED") {
  throw new Error("AEGIS-E2E-POSTGRES-002 invalid runtime snapshot");
}
process.stdout.write(JSON.stringify(snapshot));
