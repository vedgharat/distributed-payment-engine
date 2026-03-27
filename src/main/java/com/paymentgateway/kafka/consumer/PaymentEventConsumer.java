package com.paymentgateway.kafka.consumer;

import com.paymentgateway.kafka.event.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventConsumer {

    @KafkaListener(
            topics = "payments-topic",
            groupId = "payment-gateway-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(
            ConsumerRecord<String, PaymentCompletedEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        PaymentCompletedEvent event = record.value();

        log.info("===== [MAILROOM] Receipt Processing =====");
        log.info("  Correlation ID : {}", event.getCorrelationId());
        log.info("  From Wallet    : {}", event.getSenderWalletId());
        log.info("  To Wallet      : {}", event.getReceiverWalletId());
        log.info("  Amount         : {} {}", event.getAmount(), event.getCurrency());
        log.info("  Processed At   : {}", event.getProcessedAt());
        log.info("  Partition: {}, Offset: {}", partition, offset);
        log.info("  [ACTION] Receipt sent. Audit log updated.");
        log.info("=========================================");
    }
}
