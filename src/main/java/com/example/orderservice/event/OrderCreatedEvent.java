package com.example.orderservice.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderCreatedEvent(
    Long orderId,
    Long customerId,
    String customerEmail,
    List<OrderItemEvent> items,
    BigDecimal totalAmount,
    Instant createdAt
) implements Serializable {}
