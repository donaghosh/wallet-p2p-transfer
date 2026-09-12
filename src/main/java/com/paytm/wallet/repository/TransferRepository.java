package com.paytm.wallet.repository;

import com.paytm.wallet.entity.Transfer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    /**
     * Claims the idempotency key by inserting a PENDING transfer, in the SAME transaction
     * that will move the money. Returns 1 if this caller won the claim, 0 if the key already
     * exists (a concurrent duplicate blocks here until the winner commits, then reads 0 and
     * returns the committed result). Because the claim and the ledger movement share one
     * transaction, a duplicate can never produce a second debit — the exactly-once guard.
     */
    @Modifying
    @Query(value = """
            INSERT INTO transfers (idempotency_key, request_hash, from_wallet_id, to_wallet_id,
                                   amount_paise, status, created_at, updated_at)
            VALUES (:key, :requestHash, :fromWalletId, :toWalletId, :amount, 'PENDING', now(), now())
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertClaim(@Param("key") String key,
                    @Param("requestHash") String requestHash,
                    @Param("fromWalletId") long fromWalletId,
                    @Param("toWalletId") long toWalletId,
                    @Param("amount") long amount);
}
