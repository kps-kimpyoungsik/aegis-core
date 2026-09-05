# Session Artifact Archive Index

This directory records all session artifacts known to the convergence task by immutable SHA-256.

## Storage policy

- Git-editable source, design documents, manifests, tests, deployment definitions, and the latest RC implementation are imported directly into this repository.
- Historical ZIP/TAR/JAR artifacts are indexed by SHA-256 in `SHA256SUMS`.
- The current GitHub connector write surface accepts UTF-8 file content but does not accept a local binary file reference for repository upload. Therefore binary archives are **not claimed as physically uploaded** merely because their digest is indexed.
- A binary becomes a repository/release asset only after an actual binary-capable upload path verifies the exact digest.

## Canonical rule

Archive presence never grants canonical ownership. Promotion follows `REUSE -> ADAPT -> COMPOSE -> HANDOFF/RELATION -> CREATE`, evidence comparison, and regression gates.
