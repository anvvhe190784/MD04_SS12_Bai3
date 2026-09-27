package com.example.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.orderservice.consumer.EmailServiceConsumer;
import com.example.orderservice.consumer.InventoryServiceConsumer;
import com.example.orderservice.dto.CreateOrderItemRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.service.OrderService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"order-events"})
@DirtiesContext
class OrderAsyncKafkaIntegrationTest {

    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private EmailServiceConsumer emailServiceConsumer;

    @Autowired
    private InventoryServiceConsumer inventoryServiceConsumer;

    @BeforeEach
    void setUp() {
        emailServiceConsumer.clear();
        inventoryServiceConsumer.clear();
    }

    @Test
    @DisplayName("Verify asynchronous Order placing and dual-consumer group dispatch (Email + Inventory)")
    void testOrderPlacementAndAsyncKafkaEventDispatch() {
        CreateOrderRequest request = new CreateOrderRequest(
            888L,
            "customer@example.com",
            List.of(
                new CreateOrderItemRequest(101L, "Mechanical Keyboard", 2, new BigDecimal("120.00")),
                new CreateOrderItemRequest(102L, "Wireless Mouse", 1, new BigDecimal("50.00"))
            )
        );

        // 1. Order Service returns immediately (non-blocking)
        long startTime = System.currentTimeMillis();
        OrderResponse response = orderService.createOrder(request);
        long elapsed = System.currentTimeMillis() - startTime;

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("290.00"));
        assertThat(elapsed).isLessThan(200); // Proves client doesn't wait for email or inventory deduction!

        Long orderId = response.id();

        // 2. Verify Email-Service (Group: email-service-group) receives event asynchronously
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(emailServiceConsumer.getProcessedCount()).isGreaterThanOrEqualTo(1);
            OrderCreatedEvent emailEvent = emailServiceConsumer.getReceivedEvents().stream()
                .filter(e -> e.orderId().equals(orderId))
                .findFirst()
                .orElse(null);
            assertThat(emailEvent).isNotNull();
            assertThat(emailEvent.customerEmail()).isEqualTo("customer@example.com");
            assertThat(emailEvent.totalAmount()).isEqualByComparingTo(new BigDecimal("290.00"));
        });

        // 3. Verify Inventory-Service (Group: inventory-service-group) receives event independently
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(inventoryServiceConsumer.getProcessedCount()).isGreaterThanOrEqualTo(1);
            OrderCreatedEvent inventoryEvent = inventoryServiceConsumer.getReceivedEvents().stream()
                .filter(e -> e.orderId().equals(orderId))
                .findFirst()
                .orElse(null);
            assertThat(inventoryEvent).isNotNull();
            assertThat(inventoryEvent.items()).hasSize(2);
            assertThat(inventoryEvent.items().get(0).productName()).isEqualTo("Mechanical Keyboard");
            assertThat(inventoryEvent.items().get(0).quantity()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Verify multiple orders trigger independent consumer processing on Kafka topic")
    void testMultipleOrdersDispatchedIndependently() {
        for (int i = 1; i <= 3; i++) {
            CreateOrderRequest req = new CreateOrderRequest(
                (long) (100 + i),
                "user" + i + "@example.com",
                List.of(new CreateOrderItemRequest((long) (200 + i), "Item " + i, i, new BigDecimal("25.00")))
            );
            orderService.createOrder(req);
        }

        // Both consumers should independently process all 3 orders
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(emailServiceConsumer.getProcessedCount()).isEqualTo(3);
            assertThat(inventoryServiceConsumer.getProcessedCount()).isEqualTo(3);
        });
    }
}
