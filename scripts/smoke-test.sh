#!/usr/bin/env bash
# Calisan bir ortama (ornegin docker compose) gercek HTTP istekleriyle uctan uca duman testi.
# Gerekenler: bash, curl, jq. Ortam degiskenleri: ADMIN_EMAIL, ADMIN_PASSWORD (zorunlu), BASE_URL (varsayilan localhost:8080).
# Sifreler ve token'lar ekrana yazdirilmaz.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL must be set}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD must be set}"

# Windows'taki jq satir sonuna  ekleyebilir; karsilastirmalar bozulmasin (Linux'ta etkisiz).
jqr() { jq "$@" | tr -d ''; }

pass() { echo "  OK  $*"; }
fail() { echo "  FAIL $*" >&2; exit 1; }

# call METOT YOL BEKLENEN_KOD [TOKEN] [GOVDE]  -> cevap govdesi $BODY'de
call() {
  local method="$1" path="$2" expected="$3" token="${4:-}" body="${5:-}"
  local args=(-s -o /tmp/smoke-body -w '%{http_code}' -X "$method" "$BASE_URL$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  local code
  code=$(curl "${args[@]}")
  BODY=$(cat /tmp/smoke-body)
  [ "$code" = "$expected" ] || fail "$method $path -> $code (beklenen $expected): $BODY"
  pass "$method $path -> $code"
}

login() { # login EMAIL SIFRE -> token
  curl -s -X POST "$BASE_URL/api/auth/token" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg e "$1" --arg p "$2" '{email:$e,password:$p}')" | jqr -er '.accessToken'
}

echo "== Saglik kontrolu bekleniyor: $BASE_URL"
for i in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/health" || true)" = "200" ]; then break; fi
  [ "$i" = 60 ] && fail "uygulama 120 saniyede ayaga kalkmadi"
  sleep 2
done
call GET /api/health 200

echo "== Kimlik"
ADMIN_TOKEN=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD") || fail "admin girisi basarisiz"
pass "admin token alindi"
RUN=$(date +%s)
PW="smoke-$RUN-password"
call POST /api/users 201 "" "$(jq -n --arg e "ayse-$RUN@example.com" --arg p "$PW" '{email:$e,fullName:"Ayse",password:$p}')"
AYSE_ID=$(jqr -r '.id' <<<"$BODY")
call POST /api/users 201 "" "$(jq -n --arg e "mehmet-$RUN@example.com" --arg p "$PW" '{email:$e,fullName:"Mehmet",password:$p}')"
MEHMET_ID=$(jqr -r '.id' <<<"$BODY")
AYSE=$(login "ayse-$RUN@example.com" "$PW")
MEHMET=$(login "mehmet-$RUN@example.com" "$PW")
call GET /api/users/me 200 "$AYSE"
[ "$(jqr -r '.id' <<<"$BODY")" = "$AYSE_ID" ] || fail "/me yanlis kullanici"

echo "== Etkinlik (yalnizca ADMIN)"
EVENT='{"name":"Smoke Atolye","startsAt":"2027-01-15T19:00:00Z","capacity":1}'
call POST /api/events 403 "$AYSE" "$EVENT"
call POST /api/events 201 "$ADMIN_TOKEN" "$EVENT"
EVENT_ID=$(jqr -r '.id' <<<"$BODY")

echo "== Rezervasyon, bekleme listesi, yetki, iptal ve terfi"
call POST "/api/events/$EVENT_ID/reservations" 401
call POST "/api/events/$EVENT_ID/reservations" 201 "$AYSE"
AYSE_RES=$(jqr -r '.id' <<<"$BODY")
call POST "/api/events/$EVENT_ID/reservations" 202 "$MEHMET"
[ "$(jqr -r '.waitlistPosition' <<<"$BODY")" = "1" ] || fail "Mehmet sirada 1. olmali"
call POST "/api/reservations/$AYSE_RES/cancel" 403 "$MEHMET"
call POST "/api/reservations/$AYSE_RES/cancel" 200 "$AYSE"
[ "$(jqr -r '.promoted[0].userId' <<<"$BODY")" = "$MEHMET_ID" ] || fail "Mehmet terfi etmeliydi: $BODY"
call GET "/api/events/$EVENT_ID/users/$MEHMET_ID/reservation" 200 "$MEHMET"
[ "$(jqr -r '.status' <<<"$BODY")" = "CONFIRMED" ] || fail "Mehmet CONFIRMED olmali"
call POST "/api/reservations/$AYSE_RES/cancel" 200 "$AYSE"
[ "$(jqr -r '.alreadyCancelled' <<<"$BODY")" = "true" ] || fail "tekrar iptal idempotent olmali"
call GET "/api/users/me/reservations?size=5" 200 "$MEHMET"
[ "$(jqr -r '.totalElements' <<<"$BODY")" = "1" ] || fail "Mehmet'in 1 kaydi olmali"

echo "== Dokumantasyon"
call GET /v3/api-docs 200
call GET /swagger-ui/index.html 200

echo "SMOKE TEST: BASARILI"
