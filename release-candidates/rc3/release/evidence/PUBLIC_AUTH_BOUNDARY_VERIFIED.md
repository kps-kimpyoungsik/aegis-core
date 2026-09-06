# Public Auth Boundary Evidence

Verified locally on 2026-09-05 with `aegis-server.jar` version `0.3.0-rc3`.

Observed behavior:
- Public mode + valid API-key configuration: `/readiness` => HTTP 200.
- Missing Bearer token on `/api/v1/system/health` => HTTP 401 `MISSING_BEARER_TOKEN`.
- Valid Bearer but missing tenant context => HTTP 401 `MISSING_TENANT_CONTEXT`.
- Valid Bearer + `X-Aegis-Tenant` => HTTP 200.
- `/api/v1/whoami` returns the authenticated tenant/scope projection.
- Public mode without valid auth configuration => `/readiness` HTTP 503 and protected API HTTP 503.
- Security response headers include `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, and restrictive CSP.

Scope limitation:
This verifies a minimal fail-closed API-key boundary only. It is NOT evidence of customer-grade OIDC/OAuth2 federation, user lifecycle, MFA, session revocation, account recovery, or enterprise RBAC.
