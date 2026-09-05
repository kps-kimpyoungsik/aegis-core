# AEGIS-CLI RC-1 Physical Baseline

Release-convergence repository skeleton grounded in the AEGIS canonical implementation baseline.

## Verified locally in this artifact
- Java 21 source compilation with `javac -Xlint:all -Werror`
- deterministic architecture checks
- minimal contract/unit smoke tests
- executable server and CLI packaging as JARs
- release manifest generation

## Not verified in the current environment
- npm dependency installation / React production build
- OCI image build (Docker/Podman absent)
- PostgreSQL integration
- Kubernetes deployment

## Commands
```bash
./scripts/doctor.sh
./scripts/lint.sh
./scripts/test.sh
./scripts/build.sh
./scripts/package.sh
```

Server: `java -jar dist/aegis-server.jar`
CLI: `java -jar dist/aegis-cli.jar health`

## RC-2 delta

RC-2 adds a deterministic Runtime Kernel for Task/Execution transitions and fencing, plus a PostgreSQL adapter boundary and V001 canonical runtime migration. PostgreSQL is optional for local boot; set `AEGIS_REQUIRE_POSTGRES=true` for production-style fail-closed readiness. In an environment without PostgreSQL/JDBC driver, readiness becomes HTTP 503 rather than silently accepting canonical mutations.
