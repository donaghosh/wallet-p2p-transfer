package com.paytm.wallet.repository;

import com.paytm.wallet.entity.Transfer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
}
