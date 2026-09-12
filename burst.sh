#!/usr/bin/env bash
#
# One-command burst script: reproduces the three graded invariants against a running
# wallet service. Uses only bash + curl + python3 (for JSON parsing/assertions).
#
#   ./burst.sh [BASE_URL]
#
# BASE_URL defaults to http://localhost:8080 (or set BASE_URL env var).
#
# Exit code 0 = all gates passed, non-zero = a gate failed.

set -uo pipefail

BASE="${1:-${BASE_URL:-http://localhost:8080}}"
GC_CONCURRENCY=50      # Gate 1: concurrent get-or-create
IDEM_CONCURRENCY=30    # Gate 2: idempotent storm
CONS_WALLETS=5         # Gate 3: wallets
CONS_TRANSFERS=400     # Gate 3: concurrent transfers
INITIAL=1000000        # paise seeded per wallet

export BASE
PASS=0
FAIL=0

uid() { uuidgen 2>/dev/null || echo "${RANDOM}${RANDOM}${RANDOM}"; }
jget() { python3 -c "import sys,json;print(json.load(sys.stdin).get('$1',''))"; }

# post_wallet TOKEN -> prints wallet_id
post_wallet() {
  curl -s -X POST "$BASE/api/v1/wallets" -H "Authorization: Bearer $1" | jget wallet_id
}
# deposit ID AMOUNT (as any authenticated caller)
deposit() {
  curl -s -X POST "$BASE/api/v1/wallets/$1/deposit" \
    -H "Authorization: Bearer seed" -H "Content-Type: application/json" \
    -d "{\"amount_paise\": $2}" >/dev/null
}
# balance ID -> prints balance_paise
balance() {
  curl -s "$BASE/api/v1/wallets/$1" -H "Authorization: Bearer seed" | jget balance_paise
}

banner() { echo; echo "=== $1 ==="; }
ok()   { echo "PASS: $1"; PASS=$((PASS+1)); }
bad()  { echo "FAIL: $1"; FAIL=$((FAIL+1)); }

# ---------------------------------------------------------------------------
banner "Gate 1: race-free get-or-create ($GC_CONCURRENCY concurrent, one fresh user)"
GC_USER="gc-$(uid)"
export GC_USER
gc_worker() { curl -s -X POST "$BASE/api/v1/wallets" -H "Authorization: Bearer $GC_USER" | \
  python3 -c "import sys,json;print(json.load(sys.stdin)['wallet_id'])"; }
export -f gc_worker
GC_OUT="$(mktemp)"
seq 1 "$GC_CONCURRENCY" | xargs -P"$GC_CONCURRENCY" -I{} bash -c 'gc_worker' >"$GC_OUT"
DISTINCT=$(sort -u "$GC_OUT" | grep -c . || true)
RESPONSES=$(grep -c . "$GC_OUT" || true)
echo "responses=$RESPONSES distinct_wallet_ids=$DISTINCT"
if [ "$DISTINCT" = "1" ] && [ "$RESPONSES" = "$GC_CONCURRENCY" ]; then
  ok "exactly one wallet for a brand-new user under $GC_CONCURRENCY concurrent calls"
else
  bad "expected 1 distinct wallet id from $GC_CONCURRENCY responses, got distinct=$DISTINCT responses=$RESPONSES"
fi
rm -f "$GC_OUT"

# ---------------------------------------------------------------------------
banner "Gate 2: idempotent exactly-once transfer ($IDEM_CONCURRENCY concurrent, same key)"
FROM_ID=$(post_wallet "idem-from-$(uid)"); deposit "$FROM_ID" "$INITIAL"
TO_ID=$(post_wallet "idem-to-$(uid)");    deposit "$TO_ID" "$INITIAL"
KEY="idem-$(uid)"
AMOUNT=250
export FROM_ID TO_ID KEY AMOUNT
idem_worker() {
  curl -s -X POST "$BASE/api/v1/transfers" -H "Authorization: Bearer idem" \
    -H "Content-Type: application/json" \
    -d "{\"from\": $FROM_ID, \"to\": $TO_ID, \"amount_paise\": $AMOUNT, \"idempotency_key\": \"$KEY\"}" | \
    python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('transfer_id',''))"
}
export -f idem_worker
IDEM_OUT="$(mktemp)"
seq 1 "$IDEM_CONCURRENCY" | xargs -P"$IDEM_CONCURRENCY" -I{} bash -c 'idem_worker' >"$IDEM_OUT"
IDEM_DISTINCT=$(sort -u "$IDEM_OUT" | grep -c . || true)
FROM_BAL=$(balance "$FROM_ID"); TO_BAL=$(balance "$TO_ID")
echo "distinct_transfer_ids=$IDEM_DISTINCT from_balance=$FROM_BAL to_balance=$TO_BAL"
if [ "$IDEM_DISTINCT" = "1" ] && [ "$FROM_BAL" = "$((INITIAL-AMOUNT))" ] && [ "$TO_BAL" = "$((INITIAL+AMOUNT))" ]; then
  ok "one debit/credit and identical transfer id across $IDEM_CONCURRENCY concurrent duplicates"
else
  bad "expected 1 transfer id and a single $AMOUNT move; got distinct=$IDEM_DISTINCT from=$FROM_BAL to=$TO_BAL"
fi
rm -f "$IDEM_OUT"

# same key + different body -> 409
CONFLICT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/transfers" \
  -H "Authorization: Bearer idem" -H "Content-Type: application/json" \
  -d "{\"from\": $FROM_ID, \"to\": $TO_ID, \"amount_paise\": $((AMOUNT+1)), \"idempotency_key\": \"$KEY\"}")
echo "same-key-different-body http_code=$CONFLICT_CODE"
if [ "$CONFLICT_CODE" = "409" ]; then
  ok "same key with a different body returns 409"
else
  bad "expected 409 for same-key/different-body, got $CONFLICT_CODE"
fi

# ---------------------------------------------------------------------------
banner "Gate 3: conservation + no-overdraft ($CONS_TRANSFERS concurrent among $CONS_WALLETS wallets)"
IDS=()
for i in $(seq 1 "$CONS_WALLETS"); do
  ID=$(post_wallet "cons-$i-$(uid)"); deposit "$ID" "$INITIAL"; IDS+=("$ID")
done
TOTAL_BEFORE=0
for ID in "${IDS[@]}"; do TOTAL_BEFORE=$((TOTAL_BEFORE + $(balance "$ID"))); done
echo "wallets=${IDS[*]} total_before=$TOTAL_BEFORE"

ID_CSV=$(IFS=,; echo "${IDS[*]}")
export ID_CSV
cons_worker() {
  python3 - "$BASE" "$ID_CSV" <<'PY'
import sys, json, random, urllib.request
base, csv = sys.argv[1], sys.argv[2]
ids = [int(x) for x in csv.split(",")]
a, b = random.sample(ids, 2)
amount = random.randint(1, 30000)
body = json.dumps({"from": a, "to": b, "amount_paise": amount,
                   "idempotency_key": "cons-" + str(random.random())}).encode()
req = urllib.request.Request(base + "/api/v1/transfers", data=body, method="POST",
                             headers={"Authorization": "Bearer cons", "Content-Type": "application/json"})
try:
    with urllib.request.urlopen(req) as r:
        print(r.status)
except urllib.error.HTTPError as e:
    print(e.code)
except Exception:
    print("ERR")
PY
}
export -f cons_worker
CONS_OUT="$(mktemp)"
seq 1 "$CONS_TRANSFERS" | xargs -P32 -I{} bash -c 'cons_worker' >"$CONS_OUT"
NON_2XX=$(grep -vc '^2' "$CONS_OUT" || true)
SVR_ERR=$(grep -c 'ERR\|^5' "$CONS_OUT" || true)
TOTAL_AFTER=0
NEG=0
for ID in "${IDS[@]}"; do
  BAL=$(balance "$ID")
  TOTAL_AFTER=$((TOTAL_AFTER + BAL))
  if [ "$BAL" -lt 0 ]; then NEG=$((NEG+1)); fi
done
echo "total_after=$TOTAL_AFTER non_2xx=$NON_2XX server_errors=$SVR_ERR negative_balances=$NEG"
if [ "$TOTAL_BEFORE" = "$TOTAL_AFTER" ] && [ "$NEG" = "0" ] && [ "$SVR_ERR" = "0" ]; then
  ok "total conserved ($TOTAL_AFTER), no negative balances, no 5xx/deadlocks"
else
  bad "conservation/overdraft/errors: before=$TOTAL_BEFORE after=$TOTAL_AFTER neg=$NEG 5xx=$SVR_ERR"
fi
rm -f "$CONS_OUT"

# ---------------------------------------------------------------------------
banner "Summary"
echo "passed=$PASS failed=$FAIL"
[ "$FAIL" = "0" ] && { echo "ALL GATES PASSED"; exit 0; } || { echo "SOME GATES FAILED"; exit 1; }
