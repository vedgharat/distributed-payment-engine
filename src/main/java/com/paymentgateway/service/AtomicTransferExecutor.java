package com.paymentgateway.service;

import com.paymentgateway.domain.entity.Transaction;
import com.paymentgateway.domain.entity.Wallet;
import com.paymentgateway.domain.enums.TransactionStatus;
import com.paymentgateway.domain.enums.TransactionType;
import com.paymentgateway.dto.request.TransferRequest;
import com.paymentgateway.dto.response.TransferResponse;
import com.paymentgateway.exception.WalletNotFoundException;
import com.paymentgateway.repository.TransactionRepository;
import com.paymentgateway.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Isolated Spring bean whose sole purpose is to own the @Transactional boundary.
 *
 * WHY THIS EXISTS: Spring's @Transactional works through a runtime proxy. When a
 * method calls another @Transactional method on the SAME bean (self-invocation),
 * the call goes directly to 'this' — bypassing the proxy — and no transaction is
 * started. By moving the transactional work into a separate bean, every call goes
 * through the Spring proxy and the transaction is correctly opened.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtomicTransferExecutor {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransferResponse execute(TransferRequest request, String idempotencyKey) {
        UUID correlationId = UUID.randomUUID();

        log.info("Opening DB transaction [correlationId={}]: {} {} from {} to {}",
                correlationId, request.getAmount(), request.getCurrency(),
                request.getSenderWalletId(), request.getReceiverWalletId());

        // Lock wallets in consistent UUID order to prevent deadlocks
        UUID firstId  = min(request.getSenderWalletId(), request.getReceiverWalletId());
        UUID secondId = max(request.getSenderWalletId(), request.getReceiverWalletId());

        Wallet firstLocked  = findAndLock(firstId);
        Wallet secondLocked = findAndLock(secondId);

        Wallet sender   = firstId.equals(request.getSenderWalletId())   ? firstLocked : secondLocked;
        Wallet receiver = firstId.equals(request.getReceiverWalletId()) ? firstLocked : secondLocked;

        // Currency guard
        if (!sender.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new IllegalArgumentException(
                    String.format("Currency mismatch: requested %s, sender wallet holds %s",
                            request.getCurrency(), sender.getCurrency())
            );
        }

        BigDecimal amount = request.getAmount();

        // Wallet.debit() throws InsufficientFundsException if balance < amount
        sender.debit(amount);
        receiver.credit(amount);

        walletRepository.save(sender);
        walletRepository.save(receiver);

        // Immutable double-entry ledger records
        transactionRepository.saveAll(java.util.List.of(
                Transaction.builder()
                        .correlationId(correlationId)
                        .walletId(sender.getId())
                        .amount(amount)
                        .transactionType(TransactionType.DEBIT)
                        .status(TransactionStatus.COMPLETED)
                        .description("Transfer to wallet " + receiver.getId())
                        .idempotencyKey(idempotencyKey)
                        .build(),
                Transaction.builder()
                        .correlationId(correlationId)
                        .walletId(receiver.getId())
                        .amount(amount)
                        .transactionType(TransactionType.CREDIT)
                        .status(TransactionStatus.COMPLETED)
                        .description("Transfer from wallet " + sender.getId())
                        .idempotencyKey(idempotencyKey)
                        .build()
        ));

        log.info("DB transaction committed [correlationId={}]. Sender: {}, Receiver: {}",
                correlationId, sender.getBalance(), receiver.getBalance());

        return TransferResponse.builder()
                .correlationId(correlationId)
                .senderWalletId(sender.getId())
                .receiverWalletId(receiver.getId())
                .amount(amount)
                .currency(request.getCurrency().toUpperCase())
                .status("COMPLETED")
                .senderBalanceAfter(sender.getBalance())
                .receiverBalanceAfter(receiver.getBalance())
                .processedAt(OffsetDateTime.now())
                .build();
    }

    private Wallet findAndLock(UUID id) {
        return walletRepository.findByIdWithPessimisticLock(id)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + id));
    }

    private UUID min(UUID a, UUID b) { return a.compareTo(b) <= 0 ? a : b; }
    private UUID max(UUID a, UUID b) { return a.compareTo(b) > 0  ? a : b; }
}