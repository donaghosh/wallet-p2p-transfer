package com.paytm.wallet.repository;

import com.paytm.wallet.entity.Wallet;
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
}
