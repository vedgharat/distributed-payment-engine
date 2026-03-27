package com.paymentgateway.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.exception.DuplicateTransactionException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Objects;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private static final String IDEMPOTENCY_HEADER   = "Idempotency-Key";
    private static final String REDIS_KEY_PREFIX      = "idempotency:";
    private static final String PROCESSING_SENTINEL   = "__PROCESSING__";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.idempotency.ttl-hours:24}")
    private long defaultTtlHours;

    @Around("@annotation(idempotent)")
    public Object intercept(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String idempotencyKey = extractIdempotencyKey();
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;

        RBucket<String> bucket = redissonClient.getBucket(redisKey);
        String cachedValue = bucket.get();

        if (cachedValue != null) {
            if (PROCESSING_SENTINEL.equals(cachedValue)) {
                throw new DuplicateTransactionException(
                        "Request with idempotency key [" + idempotencyKey + "] is already being processed."
                );
            }

            // Cached value is a raw JSON string of the response body.
            // Deserialise it into a generic Object (will be LinkedHashMap) and
            // return it wrapped in a 200 OK — the client gets the same body as
            // the original call without touching the database.
            log.info("Cache HIT for idempotency key: {}. Returning cached response.", idempotencyKey);
            Object cachedBody = objectMapper.readValue(cachedValue, Object.class);
            return ResponseEntity.ok(cachedBody);
        }

        // Mark as in-progress (short 5-minute sentinel TTL)
        bucket.set(PROCESSING_SENTINEL, Duration.ofMinutes(5));

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            bucket.delete(); // allow client to retry on failure
            throw e;
        }

        // Cache ONLY the response body as plain JSON, not the ResponseEntity wrapper
        long ttlHours = (idempotent.ttlHours() > 0) ? idempotent.ttlHours() : defaultTtlHours;
        Object body = (result instanceof ResponseEntity<?> re) ? re.getBody() : result;
        bucket.set(objectMapper.writeValueAsString(body), Duration.ofHours(ttlHours));

        log.info("Cached response for idempotency key: {} (TTL: {}h)", idempotencyKey, ttlHours);
        return result;
    }

    private String extractIdempotencyKey() {
        HttpServletRequest request = ((ServletRequestAttributes)
                Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Required header '" + IDEMPOTENCY_HEADER + "' is missing or empty."
            );
        }
        return key.trim();
    }
}