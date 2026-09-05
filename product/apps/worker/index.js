export function createWorker({ dequeue, executeTask, ack, fail }) {
  for (const [name, fn] of Object.entries({ dequeue, executeTask, ack, fail })) {
    if (typeof fn !== "function") throw new Error(`AEGIS-WORKER-001 ${name.toUpperCase()}_REQUIRED`);
  }
  return Object.freeze({
    async runOnce() {
      const envelope = await dequeue();
      if (!envelope) return { status: "IDLE" };
      try {
        const result = await executeTask(envelope.command);
        await ack(envelope, result);
        return { status: "ACKED", envelopeId: envelope.id, result };
      } catch (error) {
        await fail(envelope, error);
        return { status: "FAILED", envelopeId: envelope.id, error };
      }
    },
  });
}
