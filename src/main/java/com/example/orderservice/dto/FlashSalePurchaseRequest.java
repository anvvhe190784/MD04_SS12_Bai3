package com.example.orderservice.dto;

public record FlashSalePurchaseRequest(
        Long customerId,
        String customerEmail,
        Long productId,
        Integer quantity) {
}
