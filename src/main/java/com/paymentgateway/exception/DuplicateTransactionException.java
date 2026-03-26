// DuplicateTransactionException.java
package com.paymentgateway.exception;

public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String message) {
        super(message);
    }
}