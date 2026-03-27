// WalletResponse.java
package com.paymentgateway.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {
    private UUID id;
    private String ownerName;
    private BigDecimal balance;
    private String currency;
}