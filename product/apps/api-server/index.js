import http from "node:http";
import { createTask } from "@aegis/core-domain";

function json(res, status, body) {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
}

async function readJson(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (chunks.length === 0) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

export function createApiServer({ executeTask, getSnapshot = () => ({ tasks: [], events: [] }), idFactory = () => crypto.randomUUID() } = {}) {
  if (typeof executeTask !== "function") throw new Error("AEGIS-API-001 EXECUTE_TASK_REQUIRED");
  return http.createServer(async (req, res) => {
    try {
      if (req.method === "GET" && req.url === "/health/live") return json(res, 200, { status: "HEALTHY" });
      if (req.method === "GET" && req.url === "/health/ready") return json(res, 200, { status: "READY", contracts: "0.7.0" });
      if (req.method === "GET" && req.url === "/v1/runtime/snapshot") return json(res, 200, getSnapshot());
      if (req.method === "POST" && req.url === "/v1/tasks") {
        const body = await readJson(req);
        if (!body.goal || !body.owner || !body.responsibility) return json(res, 400, { code: "AEGIS-API-002 INVALID_TASK_COMMAND" });
        const task = createTask({ id: body.id ?? idFactory(), goal: body.goal, owner: body.owner });
        const result = await executeTask({
          task,
          responsibility: body.responsibility,
          owner: body.owner,
          retrievalQuery: body.retrievalQuery ?? {},
          retrievalPolicy: body.retrievalPolicy ?? {},
        });
        return json(res, result.status === "HANDOFF_REQUIRED" ? 409 : result.status === "FAILED" ? 422 : 202, result);
      }
      return json(res, 404, { code: "AEGIS-API-404 NOT_FOUND" });
    } catch (error) {
      return json(res, 500, { code: "AEGIS-API-500 INTERNAL_ERROR", message: String(error?.message ?? error) });
    }
  });
}

const defaultServer = createApiServer({
  executeTask: async ({ task }) => ({ status: "ACCEPTED", task }),
});
const isMainModule = process.argv[1] && new URL(`file://${process.argv[1]}`).href === import.meta.url;
if (isMainModule) defaultServer.listen(Number(process.env.PORT || 8080));
export { defaultServer as server };
