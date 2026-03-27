// TransferResponse.java
package com.paymentgateway.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransferResponse {
    private UUID correlationId;
    private UUID senderWalletId;
    private UUID receiverWalletId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private BigDecimal senderBalanceAfter;
    private BigDecimal receiverBalanceAfter;
    private OffsetDateTime processedAt;
}