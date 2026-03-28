package com.paymentgateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  record ErrorResponse(String error, String message, int status,
                       OffsetDateTime timestamp, List<String> details) {}

  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
    log.warn("Insufficient funds: {}", ex.getMessage());
    return buildError(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.getMessage(), null);
  }

  @ExceptionHandler(WalletNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleWalletNotFound(WalletNotFoundException ex) {
    return buildError(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", ex.getMessage(), null);
  }

  @ExceptionHandler(DuplicateTransactionException.class)
  public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateTransactionException ex) {
    return buildError(HttpStatus.CONFLICT, "DUPLICATE_REQUEST", ex.getMessage(), null);
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleLockFailed(IllegalStateException ex) {
    return buildError(HttpStatus.SERVICE_UNAVAILABLE, "LOCK_UNAVAILABLE", ex.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.toList());
    return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", details);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
    log.error("Unexpected error", ex);
    return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred.", null);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    log.warn("Bad request: {}", ex.getMessage());
    return buildError(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), null);
  }

  private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String error,
                                                   String message, List<String> details) {
    ErrorResponse body = new ErrorResponse(error, message, status.value(),
            OffsetDateTime.now(), details);
    return ResponseEntity.status(status).body(body);
  }
}