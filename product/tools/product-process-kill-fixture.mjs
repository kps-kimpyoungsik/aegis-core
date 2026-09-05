import { createTask } from "@aegis/core-domain";
import { createTaskExecutionUseCase } from "@aegis/application-runtime";

const taskId = process.env.AEGIS_E2E_TASK_ID || "product-process-kill-1";

const runtime = createTaskExecutionUseCase({
  runHarness: async ({ task }) => {
    process.stdout.write(`AEGIS_PRODUCT_RUNTIME_ACTIVE ${task.id}\n`);
    await new Promise(() => {});
  },
});

const task = createTask({
  id: taskId,
  goal: "verify real process-kill recovery through canonical runtime owners",
  owner: "release-gate",
});

await runtime.executeTask({
  task,
  responsibility: "release",
  owner: "release-gate",
});
