package com.paymentgateway.kafka.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private UUID correlationId;
    private UUID senderWalletId;
    private UUID receiverWalletId;
    private BigDecimal amount;
    private String currency;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private OffsetDateTime processedAt;

    private String eventVersion = "1.0";
    private String eventType = "PAYMENT_COMPLETED";
}