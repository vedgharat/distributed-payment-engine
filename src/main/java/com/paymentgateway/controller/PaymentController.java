package com.paymentgateway.controller;

import com.paymentgateway.dto.request.CreateWalletRequest;
import com.paymentgateway.dto.request.TransferRequest;
import com.paymentgateway.dto.response.TransferResponse;
import com.paymentgateway.dto.response.WalletResponse;
import com.paymentgateway.idempotency.Idempotent;
import com.paymentgateway.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.paymentgateway.dto.response.TransactionResponse;
import java.util.List;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransferService transferService;

    // POST /api/v1/wallets
    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {

        log.info("Create wallet request for owner: {}", request.getOwnerName());
        WalletResponse response = transferService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/v1/wallets
    @GetMapping("/wallets")
    public ResponseEntity<List<WalletResponse>> getAllWallets() {

        log.info("List all wallets request");
        List<WalletResponse> wallets = transferService.getAllWallets();
        return ResponseEntity.ok(wallets);
    }

    // GET /api/v1/wallets/{id}
    @GetMapping("/wallets/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {

        log.info("Get wallet request for id: {}", id);
        WalletResponse response = transferService.getWallet(id);
        return ResponseEntity.ok(response);
    }

    // POST /api/v1/transfers
    @Idempotent
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey) {

        log.info("Transfer request [idempotencyKey={}]: {} {} from {} to {}",
                idempotencyKey,
                request.getAmount(),
                request.getCurrency(),
                request.getSenderWalletId(),
                request.getReceiverWalletId());

        TransferResponse response = transferService.transfer(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/wallets/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(
            @PathVariable UUID id) {

        log.info("Transaction history request for wallet: {}", id);
        List<TransactionResponse> transactions = transferService.getTransactionHistory(id);
        return ResponseEntity.ok(transactions);
    }
}