package com.example.orderservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long id;
    private Long customerId;
    private String customerEmail;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private Instant createdAt;
}
