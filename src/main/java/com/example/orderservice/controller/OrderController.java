package com.example.orderservice.controller;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.service.OrderService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final com.example.orderservice.service.FlashSaleOrderService flashSaleOrderService;

    public OrderController(
            OrderService orderService,
            com.example.orderservice.service.FlashSaleOrderService flashSaleOrderService) {
        this.orderService = orderService;
        this.flashSaleOrderService = flashSaleOrderService;
    }

    @PostMapping("/flash-sale")
    public ResponseEntity<OrderResponse> purchaseFlashSale(
            @RequestBody com.example.orderservice.dto.FlashSalePurchaseRequest request) {
        OrderResponse response = flashSaleOrderService.purchaseFlashSaleProduct(
                request.customerId(),
                request.customerEmail(),
                request.productId(),
                request.quantity() != null ? request.quantity() : 1);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.created(URI.create("/api/orders/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}
