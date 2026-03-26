// WalletNotFoundException.java
package com.paymentgateway.exception;

public class WalletNotFoundException extends RuntimeException {
    public WalletNotFoundException(String message) {
        super(message);
    }
}