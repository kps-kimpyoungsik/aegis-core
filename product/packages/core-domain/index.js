export const taskStates = Object.freeze(["NEW","DIAGNOSING","PLANNED","READY","RUNNING","VALIDATING","COMPLETED","FAILED","PAUSED","CANCELLED","ROLLING_BACK","ROLLED_BACK"]);
export function createTask({id, goal, owner}) {
  if (!id || !goal || !owner) throw new Error("AEGIS-CORE-001 INVALID_TASK");
  return Object.freeze({id, goal, owner, status:"NEW", version:0});
}
export function transitionTask(task, nextStatus) {
  if (!taskStates.includes(nextStatus)) throw new Error("AEGIS-CORE-002 INVALID_TASK_STATE");
  return Object.freeze({...task, status:nextStatus, version:task.version + 1});
}
