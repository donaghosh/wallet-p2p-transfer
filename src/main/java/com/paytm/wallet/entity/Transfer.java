package com.paytm.wallet.entity;

import com.paytm.wallet.constants.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A single money movement. Written exactly once, in the same transaction as the
 * debit/credit. {@code idempotencyKey} carries a UNIQUE constraint so a concurrent
 * duplicate loses the insert race and the caller reads back the original result.
 *
 * <p>{@code requestHash} is a hash of the semantic request (from/to/amount) used to
 * detect a same-key/different-body replay and answer it with 409 rather than a
 * second debit. Amounts are integer paise ({@code BIGINT}).
 */
@Entity
@Table(name = "transfers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "from_wallet_id", nullable = false)
    private Long fromWalletId;

    @Column(name = "to_wallet_id", nullable = false)
    private Long toWalletId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransferStatus status;

    @Column(name = "decline_reason", length = 64)
    private String declineReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transfer other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
