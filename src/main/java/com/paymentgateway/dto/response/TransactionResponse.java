package com.paymentgateway.dto.response;

import com.paymentgateway.domain.enums.TransactionStatus;
import com.paymentgateway.domain.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private UUID correlationId;
    private UUID walletId;
    private BigDecimal amount;
    private TransactionType transactionType;
    private TransactionStatus status;
    private String description;
    private OffsetDateTime createdAt;
}