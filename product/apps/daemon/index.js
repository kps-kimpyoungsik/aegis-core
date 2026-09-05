export function createDaemon({ worker, health = () => ({ status: "HEALTHY" }) }) {
  if (!worker || typeof worker.runOnce !== "function") throw new Error("AEGIS-DAEMON-001 WORKER_REQUIRED");
  let ticks = 0;
  return Object.freeze({
    async tick() {
      ticks += 1;
      const workerResult = await worker.runOnce();
      return { status: "TICK_COMPLETED", tick: ticks, worker: workerResult };
    },
    health() {
      return { ...health(), ticks };
    },
  });
}
