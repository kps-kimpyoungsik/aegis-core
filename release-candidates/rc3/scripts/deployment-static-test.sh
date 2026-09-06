#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
public="$ROOT/deployment/kubernetes/aegis-server-public.yaml"
network="$ROOT/deployment/kubernetes/aegis-networkpolicy.yaml"
grep -q 'automountServiceAccountToken: false' "$public"
grep -q 'allowPrivilegeEscalation: false' "$public"
grep -q 'drop: \["ALL"\]' "$public"
grep -q 'readOnlyRootFilesystem: true' "$public"
grep -q 'secretKeyRef:' "$public"
if grep -nE '(password|api-key-sha256):[[:space:]]+[^[:space:]]+' "$public" | grep -v 'key:'; then
  echo 'secret literal detected'; exit 1
fi
grep -q 'policyTypes:' "$network"
grep -q 'Ingress' "$network"
grep -q 'Egress' "$network"
echo 'Deployment static security PASS'
