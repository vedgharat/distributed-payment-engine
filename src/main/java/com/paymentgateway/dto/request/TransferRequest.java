// TransferRequest.java
package com.paymentgateway.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {

    @NotNull(message = "Sender wallet ID is required")
    private UUID senderWalletId;

    @NotNull(message = "Receiver wallet ID is required")
    private UUID receiverWalletId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Transfer amount must be greater than zero")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;
}