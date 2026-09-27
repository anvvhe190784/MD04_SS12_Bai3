package com.example.orderservice.dto;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long id,
    Long customerId,
    String customerEmail,
    List<OrderItemDto> items,
    BigDecimal totalAmount,
    OrderStatus status,
    Instant createdAt,
    String message
) {
    public static OrderResponse from(Order order, String message) {
        List<OrderItemDto> itemDtos = (order.getItems() == null) ? List.of() : order.getItems().stream()
            .map(i -> new OrderItemDto(i.getProductId(), i.getProductName(), i.getQuantity(), i.getPrice()))
            .toList();
        return new OrderResponse(
            order.getId(),
            order.getCustomerId(),
            order.getCustomerEmail(),
            itemDtos,
            order.getTotalAmount(),
            order.getStatus(),
            order.getCreatedAt(),
            message
        );
    }
}
