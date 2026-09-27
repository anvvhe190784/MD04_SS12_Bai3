package com.example.orderservice.producer;

import com.example.orderservice.event.OrderCreatedEvent;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public OrderEventProducer(
        KafkaTemplate<String, Object> kafkaTemplate,
        @Value("${app.kafka.topics.order-events:order-events}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        String key = String.valueOf(event.orderId());
        log.info("[PRODUCER] Publishing OrderCreatedEvent to topic '{}' with key '{}': Order ID #{}", topic, key, event.orderId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[PRODUCER SUCCESS] Sent event for Order ID #{} to partition {} at offset {}",
                    event.orderId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                log.error("[PRODUCER ERROR] Failed to send event for Order ID #{}: {}", event.orderId(), ex.getMessage());
            }
        });
    }
}
