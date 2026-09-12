package com.paytm.wallet.repository;

import com.paytm.wallet.entity.Wallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(String userId);
}
