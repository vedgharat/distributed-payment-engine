package com.paymentgateway.service;

import com.paymentgateway.domain.entity.Transaction;
import com.paymentgateway.domain.entity.Wallet;
import com.paymentgateway.dto.request.CreateWalletRequest;
import com.paymentgateway.dto.request.TransferRequest;
import com.paymentgateway.dto.response.TransactionResponse;
import com.paymentgateway.dto.response.TransferResponse;
import com.paymentgateway.dto.response.WalletResponse;
import com.paymentgateway.exception.WalletNotFoundException;
import com.paymentgateway.kafka.event.PaymentCompletedEvent;
import com.paymentgateway.kafka.producer.PaymentEventProducer;
import com.paymentgateway.repository.TransactionRepository;
import com.paymentgateway.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private static final String LOCK_KEY_PREFIX = "wallet:lock:";

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final AtomicTransferExecutor atomicTransferExecutor;
    private final RedissonClient redissonClient;
    private final PaymentEventProducer paymentEventProducer;

    @Value("${app.redis.lock.wait-time-seconds:3}")
    private long lockWaitTimeSeconds;

    @Value("${app.redis.lock.lease-time-seconds:10}")
    private long lockLeaseTimeSeconds;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        Wallet wallet = Wallet.builder()
                .ownerName(request.getOwnerName())
                .balance(request.getInitialBalance())
                .currency(request.getCurrency().toUpperCase())
                .build();
        Wallet saved = walletRepository.save(wallet);
        log.info("Created wallet {} for '{}'", saved.getId(), saved.getOwnerName());
        return toWalletResponse(saved);
    }

    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        if (request.getSenderWalletId().equals(request.getReceiverWalletId())) {
            throw new IllegalArgumentException("Sender and receiver cannot be the same wallet.");
        }

        String lockKey = LOCK_KEY_PREFIX + request.getSenderWalletId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean lockAcquired = false;

        try {
            lockAcquired = lock.tryLock(lockWaitTimeSeconds, lockLeaseTimeSeconds, TimeUnit.SECONDS);
            if (!lockAcquired) {
                throw new IllegalStateException(
                        "Wallet is currently locked by another transaction. Please retry."
                );
            }

            TransferResponse response = atomicTransferExecutor.execute(request, idempotencyKey);
            publishEvent(response, request);
            return response;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring wallet lock.", e);
        } finally {
            if (lockAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public WalletResponse getWallet(UUID id) {
        return walletRepository.findById(id)
                .map(this::toWalletResponse)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + id));
    }

    public List<TransactionResponse> getTransactionHistory(UUID walletId) {
        walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));

        return transactionRepository
                .findByWalletIdOrderByCreatedAtDesc(walletId)
                .stream()
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());
    }

    private void publishEvent(TransferResponse response, TransferRequest request) {
        try {
            paymentEventProducer.publishPaymentCompleted(
                    PaymentCompletedEvent.builder()
                            .correlationId(response.getCorrelationId())
                            .senderWalletId(request.getSenderWalletId())
                            .receiverWalletId(request.getReceiverWalletId())
                            .amount(request.getAmount())
                            .currency(request.getCurrency())
                            .processedAt(response.getProcessedAt())
                            .build()
            );
        } catch (Exception e) {
            log.error("Kafka publish failed for correlationId {}. Money moved. Consider Outbox Pattern.",
                    response.getCorrelationId(), e);
        }
    }

    private WalletResponse toWalletResponse(Wallet w) {
        return WalletResponse.builder()
                .id(w.getId())
                .ownerName(w.getOwnerName())
                .balance(w.getBalance())
                .currency(w.getCurrency())
                .build();
    }

    private TransactionResponse toTransactionResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .correlationId(t.getCorrelationId())
                .walletId(t.getWalletId())
                .amount(t.getAmount())
                .transactionType(t.getTransactionType())
                .status(t.getStatus())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }
}