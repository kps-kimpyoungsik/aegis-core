export class ApiSecurityError extends Error {
  constructor(status, code) {
    super(code);
    this.name = "ApiSecurityError";
    this.status = status;
    this.code = code;
  }
}

export function readBearerToken(headers) {
  const value = headers.authorization;
  if (typeof value !== "string") throw new ApiSecurityError(401, "AEGIS-SEC-001 BEARER_REQUIRED");
  const [scheme, token, extra] = value.trim().split(/\s+/);
  if (scheme?.toLowerCase() !== "bearer" || !token || extra) {
    throw new ApiSecurityError(401, "AEGIS-SEC-002 INVALID_AUTHORIZATION_HEADER");
  }
  return token;
}

export function normalizePrincipal(value) {
  if (!value || typeof value !== "object") throw new ApiSecurityError(401, "AEGIS-SEC-003 INVALID_PRINCIPAL");
  const subject = typeof value.subject === "string" ? value.subject.trim() : "";
  const tenantId = typeof value.tenantId === "string" ? value.tenantId.trim() : "";
  const roles = Array.isArray(value.roles) ? [...new Set(value.roles.filter((role) => typeof role === "string" && role.trim()).map((role) => role.trim()))] : [];
  if (!subject || !tenantId) throw new ApiSecurityError(401, "AEGIS-SEC-003 INVALID_PRINCIPAL");
  return Object.freeze({ subject, tenantId, roles: Object.freeze(roles) });
}

async function verifyBearerToken(policy, token) {
  try {
    return await policy.verifyBearerToken(token);
  } catch (error) {
    if (error instanceof ApiSecurityError) throw error;
    throw new ApiSecurityError(401, "AEGIS-SEC-008 TOKEN_REJECTED");
  }
}

export async function establishSecurityContext(req, policy, requirement) {
  if (!policy) return null;
  if (typeof policy.verifyBearerToken !== "function") {
    throw new ApiSecurityError(503, "AEGIS-SEC-004 VERIFIER_NOT_CONFIGURED");
  }
  const token = readBearerToken(req.headers);
  const principal = normalizePrincipal(await verifyBearerToken(policy, token));
  const requestedTenant = req.headers["x-aegis-tenant"];
  if (typeof requestedTenant !== "string" || !requestedTenant.trim()) {
    throw new ApiSecurityError(401, "AEGIS-SEC-005 TENANT_CONTEXT_REQUIRED");
  }
  if (requestedTenant.trim() !== principal.tenantId) {
    throw new ApiSecurityError(403, "AEGIS-SEC-006 TENANT_MISMATCH");
  }
  if (requirement?.roles?.length) {
    const allowed = requirement.roles.some((role) => principal.roles.includes(role));
    if (!allowed) throw new ApiSecurityError(403, "AEGIS-SEC-007 ROLE_FORBIDDEN");
  }
  return Object.freeze({ principal, tenantId: principal.tenantId });
}
