// CreateWalletRequest.java
package com.paymentgateway.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateWalletRequest {

    @NotBlank(message = "Owner name is required")
    @Size(max = 255)
    private String ownerName;

    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance cannot be negative")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal initialBalance;

    @NotBlank
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;
}