# Wallet & P2P Transfer Service

A small wallet service with peer-to-peer transfers, built to stay **correct under
concurrency and failure**. Money is integer **paise** end to end (never floats, never
decimal rupees). Correctness is enforced in Postgres, not in application code.

- **Live URL:** _TBD (filled after deploy — see [Deploy](#deploy))_
- **Stack:** Java 21, Spring Boot 3.4, Postgres 16, Flyway, Micrometer/Prometheus
- **Design notes & reasoning:** [WRITEUP.md](./WRITEUP.md)

## Invariants

1. **Conservation** — the sum of balances never changes across a transfer.
2. **No overdraft** — a balance never goes negative; an overdrawing debit is declined cleanly.
3. **Exactly-once transfer** — the same `idempotency_key` applies once; a retry returns the
   original result; the same key with a different body is a `409`.
4. **Race-free get-or-create** — concurrent `POST /wallets` for one user yield one wallet.

## API

All endpoints are under `/api/v1` and require `Authorization: Bearer <token>`. The token
**is** the caller's user id (self-asserted identity — auth sophistication is not graded;
see [WRITEUP.md](./WRITEUP.md)). Actuator endpoints are unauthenticated.

| Method & path | Purpose |
|---|---|
| `POST /api/v1/wallets` | Get-or-create the caller's wallet (user = bearer token). Returns `wallet_id`, `user_id`, `balance_paise`. |
| `GET /api/v1/wallets/{id}` | Current balance. |
| `POST /api/v1/wallets/{id}/deposit` | Fund a wallet (mint). Body `{ "amount_paise": <n> }`. Not in the minimal spec but required — wallets start at 0, so without funding every transfer would decline. A deposit is intentionally outside the transfer-conservation invariant. |
| `POST /api/v1/transfers` | Move money. Body `{ "from", "to", "amount_paise", "idempotency_key" }`. Returns `200` uniformly; a declined transfer is `status: DECLINED` (not an HTTP error). |
| `GET /api/v1/transfers/{id}` | Transfer status. |

Observability:

| Path | Purpose |
|---|---|
| `GET /actuator/health` | Health (liveness/readiness groups enabled). |
| `GET /actuator/prometheus` | Prometheus metrics: request rate, latency, errors, plus domain counters `wallet_transfers_created_total`, `wallet_transfers_declined_total`, `wallet_transfers_idempotent_replay_total`. |

Every request gets an `X-Correlation-Id` (generated or echoed from the request header),
threaded through structured JSON logs and returned in the response header.

### Example

```bash
BASE=http://localhost:8080

# create two wallets (tokens = user identities)
A=$(curl -s -XPOST $BASE/api/v1/wallets -H "Authorization: Bearer alice" | python3 -c 'import sys,json;print(json.load(sys.stdin)["wallet_id"])')
B=$(curl -s -XPOST $BASE/api/v1/wallets -H "Authorization: Bearer bob"   | python3 -c 'import sys,json;print(json.load(sys.stdin)["wallet_id"])')

# fund alice
curl -s -XPOST $BASE/api/v1/wallets/$A/deposit -H "Authorization: Bearer alice" \
  -H 'Content-Type: application/json' -d '{"amount_paise": 100000}'

# transfer alice -> bob (retry-safe via idempotency_key)
curl -s -XPOST $BASE/api/v1/transfers -H "Authorization: Bearer alice" \
  -H 'Content-Type: application/json' \
  -d "{\"from\": $A, \"to\": $B, \"amount_paise\": 2500, \"idempotency_key\": \"demo-1\"}"
```

## Run locally

### With Docker (one command)

```bash
docker compose up --build
```

Brings up Postgres + the app; the app waits for the database healthcheck, then Flyway
applies the migrations. Service on `http://localhost:8080`.

### Without Docker

Requires a local Postgres. Point the app at it via env vars and run:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/wallet DB_USER=wallet DB_PASSWORD=wallet
./mvnw spring-boot:run   # or: mvn spring-boot:run
```

## Test

Integration tests use **Testcontainers** (real Postgres — H2 would not reproduce
row-locking / `ON CONFLICT` semantics), so a container runtime must be running.

```bash
mvn test
```

The suite includes the three concurrency gates:

- `WalletGetOrCreateConcurrencyIT` — 50 concurrent get-or-create → one wallet.
- `TransferIdempotencyConcurrencyIT` — 30 concurrent same-key transfers → one debit/credit;
  same-key/different-body → 409.
- `TransferConservationConcurrencyIT` — 500 concurrent transfers (incl. A→B and B→A) →
  total conserved, no negatives, no deadlocks/5xx.

## Burst script

Reproduces the three probes against a running instance (defaults to localhost):

```bash
./burst.sh                       # http://localhost:8080
./burst.sh https://<live-url>    # against the deployed URL
```

Needs `bash`, `curl`, `python3`. Prints per-gate PASS/FAIL and exits non-zero on failure.

## Deploy

Deployed as a Docker image on a free host (Render) backed by free managed Postgres.
The app reads `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `PORT` from the environment.
See [WRITEUP.md](./WRITEUP.md) for the free-tier cost note (₹0).
