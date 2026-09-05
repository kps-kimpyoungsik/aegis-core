#!/usr/bin/env bash
set -euo pipefail

: "${AEGIS_REDIS_HOST:=127.0.0.1}"
: "${AEGIS_REDIS_PORT:=56379}"
RC=(redis-cli -h "$AEGIS_REDIS_HOST" -p "$AEGIS_REDIS_PORT")

"${RC[@]}" FLUSHDB >/dev/null

# Cache TTL behavior.
"${RC[@]}" SET "aegis:cache:test" "value" EX 10 >/dev/null
[[ "$("${RC[@]}" GET "aegis:cache:test")" == "value" ]]
ttl="$("${RC[@]}" TTL "aegis:cache:test")"
(( ttl > 0 && ttl <= 10 ))

# Monotonic fencing token.
token1="$("${RC[@]}" INCR "aegis:lease:fence:resource-1")"
token2="$("${RC[@]}" INCR "aegis:lease:fence:resource-1")"
(( token2 > token1 ))

# Lease acquire / reject second owner / release safely.
[[ "$("${RC[@]}" SET "aegis:lease:resource-1" "owner-a:$token2" NX PX 30000)" == "OK" ]]
second="$("${RC[@]}" SET "aegis:lease:resource-1" "owner-b:$((token2+1))" NX PX 30000 || true)"
[[ -z "$second" ]]

current="$("${RC[@]}" GET "aegis:lease:resource-1")"
[[ "$current" == "owner-a:$token2" ]]

deleted="$("${RC[@]}" EVAL \
  'if redis.call("get",KEYS[1]) == ARGV[1] then return redis.call("del",KEYS[1]) else return 0 end' \
  1 "aegis:lease:resource-1" "owner-a:$token2")"
[[ "$deleted" == "1" ]]

# Stale owner cannot release a lease it does not own.
"${RC[@]}" SET "aegis:lease:resource-1" "owner-new:$((token2+1))" PX 30000 >/dev/null
stale_deleted="$("${RC[@]}" EVAL \
  'if redis.call("get",KEYS[1]) == ARGV[1] then return redis.call("del",KEYS[1]) else return 0 end' \
  1 "aegis:lease:resource-1" "owner-a:$token2")"
[[ "$stale_deleted" == "0" ]]

echo "REDIS_LIVE_TEST=PASS"
