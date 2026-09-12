package com.paytm.wallet.repository;

import com.paytm.wallet.entity.Wallet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(String userId);

    /**
     * Race-free get-or-create half #1: insert a zero-balance wallet only if the user has
     * none. Concurrent callers for the same fresh user serialize on the unique index;
     * exactly one insert wins, the rest are no-ops. The caller then re-selects by user_id
     * to return the single surviving row. This is the {@code INSERT ... ON CONFLICT DO
     * NOTHING} + re-select mechanism, so no two wallets can exist for one user.
     */
    @Modifying
    @Query(value = """
            INSERT INTO wallets (user_id, balance_paise, version, created_at, updated_at)
            VALUES (:userId, 0, 0, now(), now())
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId);

    /**
     * Locks both wallet rows FOR UPDATE in ascending id order. Acquiring the two locks in a
     * deterministic (sorted) order in every transfer is what makes A&rarr;B and B&rarr;A
     * safe to run concurrently without deadlocking. Returns the ids that actually exist, so
     * the caller can reject a transfer that names a non-existent wallet.
     */
    @Query(value = """
            SELECT id FROM wallets
            WHERE id IN (:firstId, :secondId)
            ORDER BY id
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> lockWalletsInOrder(@Param("firstId") long firstId, @Param("secondId") long secondId);

    /**
     * Atomic conditional debit: subtract only if the balance can cover it. Returns 1 when
     * applied, 0 when it would overdraw (&rarr; decline). This single statement is the
     * no-overdraft mechanism — there is no read-modify-write in application code.
     */
    @Modifying
    @Query(value = """
            UPDATE wallets
            SET balance_paise = balance_paise - :amount, version = version + 1, updated_at = now()
            WHERE id = :walletId AND balance_paise >= :amount
            """, nativeQuery = true)
    int debit(@Param("walletId") long walletId, @Param("amount") long amount);

    @Modifying
    @Query(value = """
            UPDATE wallets
            SET balance_paise = balance_paise + :amount, version = version + 1, updated_at = now()
            WHERE id = :walletId
            """, nativeQuery = true)
    int credit(@Param("walletId") long walletId, @Param("amount") long amount);
}
