package com.example.orderservice.event;

import java.io.Serializable;
import java.math.BigDecimal;

public record OrderItemEvent(
    Long productId,
    String productName,
    int quantity,
    BigDecimal price
) implements Serializable {}
