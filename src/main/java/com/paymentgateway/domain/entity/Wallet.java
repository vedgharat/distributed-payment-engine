package com.paymentgateway.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    // NUMERIC(19,4) — critical for financial precision. NEVER use float or double.
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // @Version enables JPA Optimistic Locking as a secondary safety net.
    // Our primary defense is Pessimistic (SELECT FOR UPDATE), but this
    // catches any edge cases where two threads somehow bypass the lock.
    @Version
    @Column(name = "version")
    private Long version;

    public void debit(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new com.paymentgateway.exception.InsufficientFundsException(
                    String.format("Wallet %s has insufficient funds. Balance: %s, Required: %s",
                            this.id, this.balance, amount)
            );
        }
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}