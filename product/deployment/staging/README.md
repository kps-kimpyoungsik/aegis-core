# AEGIS Product Staging / Rollback Gate

Ownership: `product/deployment`

This directory owns staging rollout mechanics, health verification, and rollback drill execution only. It does not own application semantics, Data Plane semantics, Root Authority, the Independent Verifier, release approval, signing/key custody, or React state.

## Drill contract

`rollback-drill.sh <candidate-image> <rollback-image> <evidence-dir>` performs a bounded same-endpoint blue/rollback drill:

1. Require distinct candidate and rollback OCI image IDs.
2. Start the candidate on the staging endpoint.
3. Verify `/health/ready`, `/health/live`, and Docker HEALTHCHECK.
4. Remove the candidate.
5. Start the previous baseline artifact on the same endpoint.
6. Re-verify readiness, liveness, and container health.
7. Emit immutable evidence containing both image IDs and timestamps.

The drill is fail-closed. A candidate that does not become healthy, a rollback artifact that does not recover the endpoint, or identical image identities causes failure.

This gate proves local production-like staging rollback mechanics in GitHub Actions. It does not by itself prove cloud/Kubernetes deployment, live customer traffic switching, backup/restore, signed provenance, or Production GA.
