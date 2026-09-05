export function projectRuntimeSnapshot(snapshot = {}) {
  const tasks = Array.isArray(snapshot.tasks) ? snapshot.tasks : [];
  const events = Array.isArray(snapshot.events) ? snapshot.events : [];
  const failures = tasks.filter((task) => task.status === "FAILED");
  const active = tasks.filter((task) => ["DIAGNOSING", "PLANNED", "READY", "RUNNING", "VALIDATING"].includes(task.status));
  return Object.freeze({
    totals: Object.freeze({ tasks: tasks.length, active: active.length, failures: failures.length, events: events.length }),
    tasks: Object.freeze(tasks.map((task) => Object.freeze({ id: task.id, goal: task.goal, status: task.status, version: task.version }))),
    recentEvents: Object.freeze(events.slice(-20).reverse()),
    controls: Object.freeze(["Inspect", "Retry", "Cancel", "Approve", "Rollback"]),
  });
}
