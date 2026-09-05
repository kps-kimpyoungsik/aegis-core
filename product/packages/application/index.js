import { transitionTask } from "@aegis/core-domain";

export function startTask(task) {
  if (task.status !== "READY") throw new Error("AEGIS-APP-001 TASK_NOT_READY");
  return transitionTask(task, "RUNNING");
}
