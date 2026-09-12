# Wallet & P2P Transfer — Design Write-up

## Data model

Two tables, money as integer **paise** (`BIGINT`) everywhere — no float, no decimal.

**`wallets`** — `id` PK, `user_id` (**UNIQUE**), `balance_paise BIGINT` (**CHECK ≥ 0**),
`version` (optimistic-lock column, unused on the money path — see below), `created_at`,
`updated_at`.

**`transfers`** — `id` PK, `idempotency_key` (**UNIQUE**), `request_hash` (SHA-256 of
`from|to|amount`, for same-key/different-body detection), `from_wallet_id`,
`to_wallet_id` (**CHECK distinct**), `amount_paise BIGINT` (**CHECK > 0**),
`status` (`PENDING`→`SUCCEEDED`/`DECLINED`), `decline_reason`, `created_at`, `updated_at`.
Indexed on both wallet FKs.

The `CHECK` and `UNIQUE` constraints are the last line of defence: even a bug can't persist
a negative balance, a second wallet per user, or a duplicate transfer key.

## The simplest-correct mechanism for conservation + no-overdraft

**Atomic conditional debit under a deterministic sorted lock, all in one transaction:**

1. Lock both wallet rows in **ascending id order**:
   `SELECT id FROM wallets WHERE id IN (from, to) ORDER BY id FOR UPDATE`.
2. Debit conditionally:
   `UPDATE wallets SET balance_paise = balance_paise - :amt WHERE id = :from AND balance_paise >= :amt`.
   **Rows-affected = 0 ⇒ decline** (no partial apply). No read-modify-write in app code, so no lost updates.
3. Credit: `UPDATE ... SET balance_paise = balance_paise + :amt WHERE id = :to`.

Debit and credit are equal and in the same transaction ⇒ **conservation**. The conditional
`WHERE balance >= amount` ⇒ **no overdraft**, atomically.

**Deadlock (A→B vs B→A):** the danger is two transfers locking the same two rows in opposite
orders. We remove it by always acquiring the two row locks in the **same global order (lowest
wallet id first)**, regardless of transfer direction — via `ORDER BY id ... FOR UPDATE`. Both
A→B and B→A therefore lock `min(A,B)` then `max(A,B)`; no cycle, no deadlock. Verified by
`TransferConservationConcurrencyIT` (500 concurrent transfers including both directions → 0
errors, total conserved).

**Heavier alternatives rejected:**
- **`SELECT ... FOR UPDATE` then read-modify-write in Java.** Correct only if you never forget
  the lock; the conditional `UPDATE` makes no-overdraft a property of one statement instead of
  a discipline. We keep the sorted `FOR UPDATE` *only* for lock ordering, and still let the
  conditional `UPDATE` decide the debit.
- **`SERIALIZABLE` isolation everywhere.** Correct, but pushes the cost onto the app as
  serialization-failure retries under contention (exactly the A→B/B→A hot-row case). More moving
  parts for no extra safety here. We use the default READ COMMITTED; row locks + the conditional
  update are sufficient.
- **A per-wallet application lock / queue.** Reinvents what the database already does correctly,
  and breaks across instances.

## Where idempotency lives

In the **`transfers.idempotency_key` UNIQUE constraint**, claimed **in the same transaction as
the ledger movement**. `create()` runs as one `@Transactional` unit:

1. `INSERT INTO transfers (... status='PENDING') ON CONFLICT (idempotency_key) DO NOTHING`
   — this **claims** the key.
2. If we won the claim (1 row), do the money move and set the terminal status.
3. If we lost (0 rows), a committed winner exists: read it back and return it (a **replay**), or
   return **409** if its `request_hash` differs (same key, different body).

Because the claim and the debit/credit commit together, a concurrent duplicate can **never**
produce a second debit: it either **blocks** on the unique index and then reads the committed
result, or **loses** the insert and returns the existing transfer — it never touches balances.
This closes the check-then-insert TOCTOU (where two duplicates both pass a pre-check and both
debit). Verified by `TransferIdempotencyConcurrencyIT` (30 concurrent same-key → one debit, one
transfer id; different body → 409).

`PENDING` is a transient in-transaction state only; it is always overwritten with a terminal
status before commit, so it is never observable by another transaction and never persists (a
crash rolls the whole transaction back).

**Race-free get-or-create** uses the same idea on `wallets.user_id`:
`INSERT ... ON CONFLICT (user_id) DO NOTHING` + re-select ⇒ one wallet under any concurrency.

## Consistency vs availability

This is money, so we choose **strong consistency (CP)**. All correctness lives in a single
primary Postgres via row locks and atomic conditional writes; there is one source of truth for
every balance. What we consciously give up: **write availability during a Postgres
failover/outage** — while the primary is unreachable, transfers fail rather than risk a
double-spend or a lost update on a stale replica. For a wallet, a brief decline is strictly
better than creating or destroying money. The app tier is stateless and can scale horizontally;
the database is the correctness boundary.

## R3 readiness — reversal/refund

The debit/credit is a single primitive (`moveMoney`, sorted-lock + conditional debit). A reversal
is that primitive with roles swapped and its **own** `idempotency_key` in its own transaction:
credit the original sender, debit the original recipient (declining cleanly if the recipient has
since spent the funds), guarded against reversing an already-reversed or unknown transfer. No
special-casing of the ledger — deliberately not built yet, but the seam is in place.

## AI: directed vs decided

- **Directed (I chose the approach; AI typed):** the correctness architecture — atomic conditional
  debit + sorted `FOR UPDATE` lock order for deadlock-freedom; claim-first idempotency via
  `ON CONFLICT DO NOTHING` in the same transaction as the ledger move; integer-paise money model;
  the CP consistency stance; the package/layering structure; returning `200` uniformly so the
  idempotency storm sees identical responses; recording declines as a normal outcome; the
  self-asserted-token auth decision (so graders can address fresh users).
- **Decided (AI chose, I reviewed and accept):** boilerplate and mechanical detail — MapStruct
  mapper wiring, the logback JSON encoder configuration, Micrometer counter registration, the
  Dockerfile/compose specifics, and the burst-script plumbing. Each was read and is defensible;
  none affects the correctness invariants.

## Free-tier cost note (₹0)

Everything runs on free tiers with **no card required**: Render free web service (Docker) +
Render free managed Postgres, GitHub for the public repo. JVM memory is capped
(`-XX:MaxRAMPercentage=75`) for the 512 MB free instance. The free instance cold-starts after
idle (first request slow, harmless for probing) and the free Postgres is time-limited — fine for
an evaluation deployment. **Total cost: ₹0.**
