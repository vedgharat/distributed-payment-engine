package com.paymentgateway.controller;

import com.paymentgateway.dto.request.CreateWalletRequest;
import com.paymentgateway.dto.request.TransferRequest;
import com.paymentgateway.dto.response.TransferResponse;
import com.paymentgateway.dto.response.WalletResponse;
import com.paymentgateway.idempotency.Idempotent;
import com.paymentgateway.service.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private final TransferService transferService;

    // POST /api/v1/wallets
    // No idempotency here — wallet creation is typically idempotent by owner_name
    // in real systems, but we keep it simple for this implementation.
    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {
        WalletResponse response = transferService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /api/v1/transfers
    // @Idempotent — the AOP aspect intercepts this BEFORE transferService.transfer() runs.
    // If the key was seen before, the cached response is returned directly.
    @Idempotent
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            HttpServletRequest httpRequest) {

        log.info("Transfer request received [idempotencyKey={}]: {} {} from {} to {}",
                idempotencyKey, request.getAmount(), request.getCurrency(),
                request.getSenderWalletId(), request.getReceiverWalletId());

        TransferResponse response = transferService.transfer(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}