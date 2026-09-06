# AEGIS Edge Security Boundary

Canonical owner: `@aegis/edge-security`.

This boundary owns technology-independent edge-security policy contracts, WAF adapter integration boundaries, and evidence consumption for external edge-security qualification. It does not own API authentication/RBAC/tenant semantics, release approval, staging rollout mechanics, Root Authority, Independent Verifier, Audit Ledger, or production provider credentials.

## Initial capability

`security.edge-waf` remains `PLANNED` until a separate implementation workstream provides a replaceable adapter boundary and deterministic policy tests. Production WAF effectiveness, external penetration testing, managed-provider configuration, capacity tuning, and customer Public Open remain `NOT_EXECUTED` unless independently evidenced.

## Invariants

- Fail closed when a required edge-security policy or evidence dependency is absent.
- No vendor-specific API or object may leak into canonical policy contracts.
- No secret material is stored in source, release evidence, logs, or React SPA configuration.
- Edge decisions must preserve provenance and expose rollback configuration.
- API-server remains the owner of HTTP application transport/authentication semantics.
- Release-convergence remains the sole owner of GA/Public Open promotion decisions.
