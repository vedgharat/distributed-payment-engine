package com.paymentgateway.kafka.producer;

import com.paymentgateway.kafka.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        String messageKey = event.getCorrelationId().toString();

        CompletableFuture<SendResult<String, PaymentCompletedEvent>> future =
                kafkaTemplate.send("payments-topic", messageKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish PaymentCompletedEvent [correlationId={}]: {}",
                        event.getCorrelationId(), ex.getMessage(), ex);
            } else {
                log.info("Published PaymentCompletedEvent [correlationId={}] to partition-{}",
                        event.getCorrelationId(),
                        result.getRecordMetadata().partition());
            }
        });
    }
}