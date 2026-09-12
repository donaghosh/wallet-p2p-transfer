package com.paytm.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
 * A user's wallet. Balance is stored as integer paise ({@code BIGINT}) — never a
 * float or decimal rupee value — per the exercise money-representation invariant.
 *
 * <p>Balance mutations do NOT go through JPA dirty-checking; they are applied via an
 * atomic conditional {@code UPDATE ... WHERE balance_paise >= amount} in
 * {@code WalletRepository}, which is the concurrency-control mechanism for
 * conservation + no-overdraft. {@code @Version} is retained for any future
 * JPA-managed update path.
 */
@Entity
@Table(name = "wallets")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 128)
    private String userId;

    @Column(name = "balance_paise", nullable = false)
    private long balancePaise;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Wallet(String userId) {
        this.userId = userId;
        this.balancePaise = 0L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Wallet other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
