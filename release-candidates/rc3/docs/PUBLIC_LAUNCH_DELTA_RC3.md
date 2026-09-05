# AEGIS-CLI Public Launch Delta — RC3

## Closed in this increment

- Minimal server-side public authentication boundary implemented.
- Public mode fails readiness when authentication configuration is invalid.
- Protected `/api/v1/*` endpoint rejects missing/invalid Bearer credentials.
- Tenant context is mandatory after credential validation.
- Minimal scope check (`system:read`) is enforced server-side.
- API security headers are emitted (`no-store`, `nosniff`, restrictive CSP).
- Kubernetes public deployment references credentials through `secretKeyRef`; no credential literal is embedded.
- Kubernetes pod security baseline now disables service-account token automount, privilege escalation, Linux capabilities and writable root filesystem.
- NetworkPolicy changed from broad namespace ingress to true default deny plus explicitly labeled client ingress.
- Frontend `latest` dependency tags were removed where authoritative current versions were available. TypeScript is constrained to `>=6.0.0 <6.1.0`.

## Still blocking public customer launch

- `package-lock.json` could not be generated because npm registry access timed out in the original execution environment; React production build remains unverified.
- Customer-grade OIDC/OAuth2 identity federation, lifecycle, MFA/session revocation and full RBAC are not implemented/verified. The API-key boundary is a controlled-preview mechanism, not the final customer identity system.
- PostgreSQL physical E2E, migration, optimistic concurrency, outbox/inbox and restart persistence are not executed.
- Backup/restore drill is not executed.
- OCI image build/scan is not executed.
- Kubernetes staging apply/rollback is not executed.
- Full H2 security release corpus is not physically executed.
- SBOM/provenance attestation and release signing are not physically verified.

Decision remains `NO_GO` for unrestricted public customer launch.
