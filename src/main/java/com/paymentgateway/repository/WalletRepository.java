package com.paymentgateway.repository;

import com.paymentgateway.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    // This is the CORE of Phase 3. The @Lock annotation translates to:
    //   SELECT * FROM wallets WHERE id = ? FOR UPDATE
    // PostgreSQL will block any other transaction that tries to lock the same row.
    // Combined with the Redis distributed lock, this creates two layers of safety.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithPessimisticLock(@Param("id") UUID id);
}