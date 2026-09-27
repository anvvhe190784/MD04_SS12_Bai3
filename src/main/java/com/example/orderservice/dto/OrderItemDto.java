package com.example.orderservice.dto;

import java.math.BigDecimal;

public record OrderItemDto(
    Long productId,
    String productName,
    int quantity,
    BigDecimal price
) {}
