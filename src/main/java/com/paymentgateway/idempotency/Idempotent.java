package com.paymentgateway.idempotency;

import java.lang.annotation.*;

/**
 * Mark a controller method as idempotent. The AOP aspect will:
 *  1. Read the "Idempotency-Key" HTTP header.
 *  2. Check Redis for a prior response under that key.
 *  3. If found, return the cached response immediately (no business logic runs).
 *  4. If not found, proceed, then cache the response before returning.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    // Allows overriding the TTL per method; defaults to app config value
    long ttlHours() default -1;
}