#!/usr/bin/env bash
set -euo pipefail

CANDIDATE_IMAGE="${1:?candidate image required}"
ROLLBACK_IMAGE="${2:?rollback image required}"
EVIDENCE_DIR="${3:?evidence directory required}"
PORT="${AEGIS_STAGING_PORT:-18080}"
RUN_SUFFIX="${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
CANDIDATE_NAME="aegis-r14-candidate-${RUN_SUFFIX}"
ROLLBACK_NAME="aegis-r14-rollback-${RUN_SUFFIX}"

mkdir -p "$EVIDENCE_DIR"

cleanup() {
  docker rm -f "$CANDIDATE_NAME" >/dev/null 2>&1 || true
  docker rm -f "$ROLLBACK_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_http() {
  local url="$1"
  local expected_status="$2"
  local attempts="${3:-45}"
  for attempt in $(seq 1 "$attempts"); do
    if node -e "fetch(process.argv[1]).then(async r=>{const b=await r.json();if(r.status!==200||b.status!==process.argv[2])process.exit(1)}).catch(()=>process.exit(1))" "$url" "$expected_status"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_container_healthy() {
  local name="$1"
  for attempt in $(seq 1 45); do
    local status
    status="$(docker inspect "$name" --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}')"
    if [[ "$status" == "healthy" ]]; then
      return 0
    fi
    if [[ "$status" == "unhealthy" ]]; then
      return 1
    fi
    sleep 1
  done
  return 1
}

CANDIDATE_IMAGE_ID="$(docker image inspect "$CANDIDATE_IMAGE" --format '{{.Id}}')"
ROLLBACK_IMAGE_ID="$(docker image inspect "$ROLLBACK_IMAGE" --format '{{.Id}}')"
if [[ "$CANDIDATE_IMAGE_ID" == "$ROLLBACK_IMAGE_ID" ]]; then
  echo "candidate and rollback image IDs must differ" >&2
  exit 1
fi

CANDIDATE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker run --detach --name "$CANDIDATE_NAME" -p "${PORT}:8080" "$CANDIDATE_IMAGE" >/tmp/aegis-r14-candidate-container-id.txt
wait_http "http://127.0.0.1:${PORT}/health/ready" "READY"
wait_http "http://127.0.0.1:${PORT}/health/live" "HEALTHY"
wait_container_healthy "$CANDIDATE_NAME"
CANDIDATE_HEALTH="$(docker inspect "$CANDIDATE_NAME" --format '{{.State.Health.Status}}')"
CANDIDATE_STOPPED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker rm -f "$CANDIDATE_NAME" >/dev/null

ROLLBACK_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker run --detach --name "$ROLLBACK_NAME" -p "${PORT}:8080" "$ROLLBACK_IMAGE" >/tmp/aegis-r14-rollback-container-id.txt
wait_http "http://127.0.0.1:${PORT}/health/ready" "READY"
wait_http "http://127.0.0.1:${PORT}/health/live" "HEALTHY"
wait_container_healthy "$ROLLBACK_NAME"
ROLLBACK_HEALTH="$(docker inspect "$ROLLBACK_NAME" --format '{{.State.Health.Status}}')"
ROLLBACK_VERIFIED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

cat > "$EVIDENCE_DIR/rollback-drill.json" <<EOF
{
  "gate": "R1_4_STAGING_ROLLBACK_DRILL",
  "status": "PASS",
  "port": ${PORT},
  "candidate_image": "${CANDIDATE_IMAGE}",
  "candidate_image_id": "${CANDIDATE_IMAGE_ID}",
  "candidate_health": "${CANDIDATE_HEALTH}",
  "candidate_started_at": "${CANDIDATE_STARTED_AT}",
  "candidate_stopped_at": "${CANDIDATE_STOPPED_AT}",
  "rollback_image": "${ROLLBACK_IMAGE}",
  "rollback_image_id": "${ROLLBACK_IMAGE_ID}",
  "rollback_health": "${ROLLBACK_HEALTH}",
  "rollback_started_at": "${ROLLBACK_STARTED_AT}",
  "rollback_verified_at": "${ROLLBACK_VERIFIED_AT}"
}
EOF

printf 'R1_4_STAGING_ROLLBACK_DRILL=PASS\n' > "$EVIDENCE_DIR/gate-status.txt"
printf '%s\n' "$CANDIDATE_IMAGE_ID" > "$EVIDENCE_DIR/candidate-image-id.txt"
printf '%s\n' "$ROLLBACK_IMAGE_ID" > "$EVIDENCE_DIR/rollback-image-id.txt"
cat "$EVIDENCE_DIR/rollback-drill.json"
