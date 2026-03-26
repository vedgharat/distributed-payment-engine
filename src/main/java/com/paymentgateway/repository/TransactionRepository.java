package com.paymentgateway.repository;

import com.paymentgateway.domain.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByCorrelationId(UUID correlationId);
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}