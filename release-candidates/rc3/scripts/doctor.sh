#!/usr/bin/env bash
set -u
fail=0
check(){ if command -v "$1" >/dev/null 2>&1; then echo "PASS $1: $($1 --version 2>&1 | head -n1)"; else echo "MISSING $1"; fail=1; fi; }
check java
check javac
check node
check npm
if command -v psql >/dev/null 2>&1; then echo "PASS psql: $(psql --version)"; else echo "BLOCKED_DEPENDENCY psql/postgresql-client"; fi
if command -v postgres >/dev/null 2>&1; then echo "PASS postgres: $(postgres --version)"; else echo "BLOCKED_DEPENDENCY postgres-server"; fi
if command -v docker >/dev/null 2>&1 || command -v podman >/dev/null 2>&1; then echo "PASS container-runtime"; else echo "BLOCKED_DEPENDENCY container-runtime"; fi
exit "$fail"
