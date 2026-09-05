import http from "node:http";
import { timingSafeEqual } from "node:crypto";
import { createTask } from "@aegis/core-domain";

const MAX_JSON_BODY_BYTES = 64 * 1024;

class ApiInputError extends Error {
  constructor(status, code) {
    super(code);
    this.name = "ApiInputError";
    this.status = status;
    this.code = code;
  }
}

function json(res, status, body) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  res.end(JSON.stringify(body));
}

function isJsonContentType(value) {
  if (typeof value !== "string") return false;
  return value.split(";", 1)[0].trim().toLowerCase() === "application/json";
}

function constantTimeTokenEquals(actual, expected) {
  const actualBuffer = Buffer.from(actual);
  const expectedBuffer = Buffer.from(expected);
  if (actualBuffer.length !== expectedBuffer.length) return false;
  return timingSafeEqual(actualBuffer, expectedBuffer);
}

export function createStaticBearerAuthorizer(expectedToken) {
  return (req) => {
    if (typeof expectedToken !== "string" || expectedToken.length < 16) {
      return { ok: false, status: 503, code: "AEGIS-API-SEC-001 AUTH_NOT_CONFIGURED" };
    }
    const authorization = req.headers.authorization;
    if (typeof authorization !== "string" || !authorization.startsWith("Bearer ")) {
      return { ok: false, status: 401, code: "AEGIS-API-SEC-002 AUTH_REQUIRED" };
    }
    const presented = authorization.slice("Bearer ".length);
    if (!constantTimeTokenEquals(presented, expectedToken)) {
      return { ok: false, status: 401, code: "AEGIS-API-SEC-003 INVALID_TOKEN" };
    }
    return { ok: true };
  };
}

async function readJson(req) {
  if (!isJsonContentType(req.headers["content-type"])) {
    throw new ApiInputError(415, "AEGIS-API-003 JSON_CONTENT_TYPE_REQUIRED");
  }

  const declaredLength = Number(req.headers["content-length"] ?? 0);
  if (Number.isFinite(declaredLength) && declaredLength > MAX_JSON_BODY_BYTES) {
    req.resume();
    throw new ApiInputError(413, "AEGIS-API-004 PAYLOAD_TOO_LARGE");
  }

  const chunks = [];
  let receivedBytes = 0;
  for await (const chunk of req) {
    receivedBytes += chunk.length;
    if (receivedBytes > MAX_JSON_BODY_BYTES) {
      req.resume();
      throw new ApiInputError(413, "AEGIS-API-004 PAYLOAD_TOO_LARGE");
    }
    chunks.push(chunk);
  }
  if (chunks.length === 0) return {};

  try {
    return JSON.parse(Buffer.concat(chunks, receivedBytes).toString("utf8"));
  } catch {
    throw new ApiInputError(400, "AEGIS-API-005 MALFORMED_JSON");
  }
}

export function createApiServer({
  executeTask,
  getSnapshot = () => ({ tasks: [], events: [] }),
  idFactory = () => crypto.randomUUID(),
  authorizeRequest = () => ({ ok: true }),
} = {}) {
  if (typeof executeTask !== "function") throw new Error("AEGIS-API-001 EXECUTE_TASK_REQUIRED");
  if (typeof authorizeRequest !== "function") throw new Error("AEGIS-API-SEC-004 AUTHORIZER_REQUIRED");
  return http.createServer(async (req, res) => {
    try {
      if (req.method === "GET" && req.url === "/health/live") return json(res, 200, { status: "HEALTHY" });
      if (req.method === "GET" && req.url === "/health/ready") return json(res, 200, { status: "READY", contracts: "0.7.0" });

      const isProtectedRoute =
        (req.method === "GET" && req.url === "/v1/runtime/snapshot") ||
        (req.method === "POST" && req.url === "/v1/tasks");
      if (isProtectedRoute) {
        const decision = await authorizeRequest(req);
        if (!decision || decision.ok !== true) {
          return json(res, decision?.status ?? 401, { code: decision?.code ?? "AEGIS-API-SEC-002 AUTH_REQUIRED" });
        }
      }

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
      if (error instanceof ApiInputError) return json(res, error.status, { code: error.code });
      return json(res, 500, { code: "AEGIS-API-500 INTERNAL_ERROR" });
    }
  });
}

const defaultServer = createApiServer({
  executeTask: async ({ task }) => ({ status: "ACCEPTED", task }),
  authorizeRequest: createStaticBearerAuthorizer(process.env.AEGIS_API_BEARER_TOKEN),
});
const isMainModule = process.argv[1] && new URL(`file://${process.argv[1]}`).href === import.meta.url;
if (isMainModule) defaultServer.listen(Number(process.env.PORT || 8080));
export { defaultServer as server };
