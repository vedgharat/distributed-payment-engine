package com.paymentgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentgateway.domain.entity.OutboxEvent;
import com.paymentgateway.kafka.event.PaymentCompletedEvent;
import com.paymentgateway.kafka.producer.PaymentEventProducer;
import com.paymentgateway.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayJob {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 5;

    // Runs every 5 seconds. Polls the outbox table for PENDING events
    // and publishes them to Kafka. Marks them PUBLISHED on success.
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();

        if (pendingEvents.isEmpty()) {
            return; // nothing to do
        }

        log.debug("OutboxRelayJob: found {} pending event(s) to publish", pendingEvents.size());

        for (OutboxEvent outboxEvent : pendingEvents) {
            try {
                // Deserialize the stored JSON back into the event object
                PaymentCompletedEvent event = objectMapper.readValue(
                        outboxEvent.getPayload(),
                        PaymentCompletedEvent.class
                );

                // Publish to Kafka
                paymentEventProducer.publishPaymentCompleted(event);

                // Mark as PUBLISHED in the same transaction
                outboxEvent.setStatus("PUBLISHED");
                outboxEvent.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(outboxEvent);

                log.info("OutboxRelayJob: published event [correlationId={}]",
                        outboxEvent.getCorrelationId());

            } catch (Exception e) {
                // Kafka is down or serialization failed — increment retry count.
                // After MAX_RETRIES the event is marked FAILED for manual inspection.
                outboxEvent.setRetryCount(outboxEvent.getRetryCount() + 1);

                if (outboxEvent.getRetryCount() >= MAX_RETRIES) {
                    outboxEvent.setStatus("FAILED");
                    log.error("OutboxRelayJob: event [correlationId={}] permanently failed " +
                                    "after {} retries. Manual intervention required.",
                            outboxEvent.getCorrelationId(), MAX_RETRIES);
                } else {
                    log.warn("OutboxRelayJob: failed to publish event [correlationId={}], " +
                                    "retry {}/{}. Error: {}",
                            outboxEvent.getCorrelationId(),
                            outboxEvent.getRetryCount(), MAX_RETRIES,
                            e.getMessage());
                }

                outboxEventRepository.save(outboxEvent);
            }
        }
    }
}