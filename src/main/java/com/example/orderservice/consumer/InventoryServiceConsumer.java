package com.example.orderservice.consumer;

import com.example.orderservice.event.OrderCreatedEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class InventoryServiceConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceConsumer.class);

    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final List<OrderCreatedEvent> receivedEvents = new CopyOnWriteArrayList<>();

    @KafkaListener(
        topics = "${app.kafka.topics.order-events:order-events}",
        groupId = "inventory-service-group"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("==================================================================");
        log.info("[INVENTORY CONSUMER] [Group: inventory-service-group] Received OrderCreatedEvent");
        log.info("[INVENTORY CONSUMER] Order ID   : #{}", event.orderId());
        log.info("[INVENTORY CONSUMER] >> Deducting stock items for Order #{}...", event.orderId());
        if (event.items() != null) {
            for (var item : event.items()) {
                log.info("[INVENTORY CONSUMER] -> Deducted {} units of Product ID: {} ({})",
                    item.quantity(), item.productId(), item.productName());
            }
        }
        log.info("[INVENTORY CONSUMER] Stock deduction completed for Order #{}", event.orderId());
        log.info("==================================================================");

        receivedEvents.add(event);
        processedCount.incrementAndGet();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public List<OrderCreatedEvent> getReceivedEvents() {
        return receivedEvents;
    }

    public void clear() {
        receivedEvents.clear();
        processedCount.set(0);
    }
}
