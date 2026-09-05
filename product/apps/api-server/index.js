import http from "node:http";
import { timingSafeEqual } from "node:crypto";
import { createTask } from "@aegis/core-domain";

const MAX_JSON_BODY_BYTES = 64 * 1024;
const MIN_BEARER_TOKEN_BYTES = 32;
const TASK_EXECUTOR_ROLE = "TASK_EXECUTOR";
const RUNTIME_VIEWER_ROLE = "RUNTIME_VIEWER";

class ApiInputError extends Error {
  constructor(status, code) {
    super(code);
    this.name = "ApiInputError";
    this.status = status;
    this.code = code;
  }
}

class ApiAuthorizationError extends Error {
  constructor(status, code) {
    super(code);
    this.name = "ApiAuthorizationError";
    this.status = status;
    this.code = code;
  }
}

function json(res, status, body, headers = {}) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    ...headers,
  });
  res.end(JSON.stringify(body));
}

function isJsonContentType(value) {
  if (typeof value !== "string") return false;
  return value.split(";", 1)[0].trim().toLowerCase() === "application/json";
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

function validatePrincipal(principal) {
  if (!principal || typeof principal !== "object") return null;
  if (typeof principal.id !== "string" || principal.id.trim().length === 0) return null;
  if (typeof principal.tenantId !== "string" || principal.tenantId.trim().length === 0) return null;
  if (!Array.isArray(principal.roles) || !principal.roles.every((role) => typeof role === "string" && role.trim().length > 0)) return null;
  return Object.freeze({
    id: principal.id.trim(),
    tenantId: principal.tenantId.trim(),
    roles: Object.freeze([...new Set(principal.roles.map((role) => role.trim()))]),
  });
}

function parseRoles(value) {
  if (typeof value !== "string") return [];
  return [...new Set(value.split(",").map((role) => role.trim()).filter(Boolean))];
}

function createEnvironmentAuthenticator(env = process.env) {
  const token = env.AEGIS_API_BEARER_TOKEN;
  if (!token) return async () => null;

  const tokenBytes = Buffer.from(token, "utf8");
  if (tokenBytes.length < MIN_BEARER_TOKEN_BYTES) throw new Error("AEGIS-API-009 BEARER_TOKEN_TOO_SHORT");

  const principalId = env.AEGIS_API_PRINCIPAL_ID?.trim();
  if (!principalId) throw new Error("AEGIS-API-010 PRINCIPAL_ID_REQUIRED");

  const roles = parseRoles(env.AEGIS_API_ROLES);
  if (roles.length === 0) throw new Error("AEGIS-API-011 PRINCIPAL_ROLES_REQUIRED");

  const tenantId = env.AEGIS_API_TENANT_ID?.trim();
  if (!tenantId) throw new Error("AEGIS-API-012 TENANT_ID_REQUIRED");

  const expected = Buffer.from(`Bearer ${token}`, "utf8");
  const principal = Object.freeze({ id: principalId, tenantId, roles: Object.freeze(roles) });
  return async (req) => {
    const authorization = req.headers.authorization;
    if (typeof authorization !== "string") return null;
    const supplied = Buffer.from(authorization, "utf8");
    if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) return null;
    return principal;
  };
}

function requireTenantBinding(req, principal) {
  const requestedTenant = req.headers["x-aegis-tenant"];
  if (typeof requestedTenant !== "string" || requestedTenant.trim().length === 0) {
    throw new ApiAuthorizationError(400, "AEGIS-API-013 TENANT_CONTEXT_REQUIRED");
  }
  if (requestedTenant.trim() !== principal.tenantId) {
    throw new ApiAuthorizationError(403, "AEGIS-API-014 TENANT_MISMATCH");
  }
}

async function requirePrincipal(req, authenticateRequest, allowedRoles) {
  const principal = validatePrincipal(await authenticateRequest(req));
  if (!principal) throw new ApiAuthorizationError(401, "AEGIS-API-006 AUTHENTICATION_REQUIRED");
  requireTenantBinding(req, principal);
  if (!allowedRoles.some((role) => principal.roles.includes(role))) {
    throw new ApiAuthorizationError(403, "AEGIS-API-007 FORBIDDEN");
  }
  return principal;
}

export function createApiServer({
  executeTask,
  getSnapshot = () => ({ tasks: [], events: [] }),
  idFactory = () => crypto.randomUUID(),
  authenticateRequest = async () => null,
} = {}) {
  if (typeof executeTask !== "function") throw new Error("AEGIS-API-001 EXECUTE_TASK_REQUIRED");
  if (typeof authenticateRequest !== "function") throw new Error("AEGIS-API-008 AUTHENTICATOR_REQUIRED");

  return http.createServer(async (req, res) => {
    try {
      if (req.method === "GET" && req.url === "/health/live") return json(res, 200, { status: "HEALTHY" });
      if (req.method === "GET" && req.url === "/health/ready") return json(res, 200, { status: "READY", contracts: "0.7.0" });
      if (req.method === "GET" && req.url === "/v1/runtime/snapshot") {
        await requirePrincipal(req, authenticateRequest, [RUNTIME_VIEWER_ROLE, TASK_EXECUTOR_ROLE]);
        return json(res, 200, getSnapshot());
      }
      if (req.method === "POST" && req.url === "/v1/tasks") {
        const principal = await requirePrincipal(req, authenticateRequest, [TASK_EXECUTOR_ROLE]);
        const body = await readJson(req);
        if (!body.goal || !body.owner || !body.responsibility) return json(res, 400, { code: "AEGIS-API-002 INVALID_TASK_COMMAND" });
        const task = createTask({ id: body.id ?? idFactory(), goal: body.goal, owner: body.owner });
        const result = await executeTask({
          task,
          principal,
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
      if (error instanceof ApiAuthorizationError) {
        const headers = error.status === 401 ? { "www-authenticate": "Bearer" } : {};
        return json(res, error.status, { code: error.code }, headers);
      }
      return json(res, 500, { code: "AEGIS-API-500 INTERNAL_ERROR" });
    }
  });
}

const defaultServer = createApiServer({
  executeTask: async ({ task }) => ({ status: "ACCEPTED", task }),
  authenticateRequest: createEnvironmentAuthenticator(),
});
const isMainModule = process.argv[1] && new URL(`file://${process.argv[1]}`).href === import.meta.url;
if (isMainModule) defaultServer.listen(Number(process.env.PORT || 8080));
export { defaultServer as server };
