package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.event.OrderItemEvent;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.producer.OrderEventProducer;
import com.example.orderservice.product.dto.ProductResponse;
import com.example.orderservice.product.service.ProductService;
import com.example.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

        private static final Logger log = LoggerFactory.getLogger(OrderService.class);

        private final OrderRepository orderRepository;
        private final ProductService productService;
        private final OrderEventProducer orderEventProducer;

        public OrderService(
                        OrderRepository orderRepository,
                        ProductService productService,
                        OrderEventProducer orderEventProducer) {
                this.orderRepository = orderRepository;
                this.productService = productService;
                this.orderEventProducer = orderEventProducer;
        }

        public OrderResponse createOrder(CreateOrderRequest request) {
                log.info("[ORDER SERVICE] Processing new order request for customer '{}' ({})",
                                request.customerId(), request.customerEmail());

                List<OrderItem> items = (request.items() == null) ? List.of()
                                : request.items().stream()
                                                .map(i -> {
                                                        String name = i.productName();
                                                        BigDecimal price = i.price();
                                                        if (i.productId() != null && (name == null || price == null)) {
                                                                ProductResponse product = productService
                                                                                .getProductById(i.productId());
                                                                if (name == null) {
                                                                        name = product.name();
                                                                }
                                                                if (price == null) {
                                                                        price = product.price();
                                                                }
                                                        }
                                                        return OrderItem.builder()
                                                                        .productId(i.productId())
                                                                        .productName(name != null ? name
                                                                                        : "Unknown Product")
                                                                        .quantity(i.quantity())
                                                                        .price(price != null ? price : BigDecimal.ZERO)
                                                                        .build();
                                                })
                                                .toList();

                BigDecimal totalAmount = items.stream()
                                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                Order order = Order.builder()
                                .customerId(request.customerId())
                                .customerEmail(request.customerEmail())
                                .items(items)
                                .totalAmount(totalAmount)
                                .status(OrderStatus.PENDING)
                                .createdAt(Instant.now())
                                .build();

                Order saved = orderRepository.save(order);
                log.info("[ORDER SERVICE] Saved Order #{} to database with status {}", saved.getId(),
                                saved.getStatus());

                List<OrderItemEvent> itemEvents = items.stream()
                                .map(i -> new OrderItemEvent(i.getProductId(), i.getProductName(), i.getQuantity(),
                                                i.getPrice()))
                                .toList();

                OrderCreatedEvent event = new OrderCreatedEvent(
                                saved.getId(),
                                saved.getCustomerId(),
                                saved.getCustomerEmail(),
                                itemEvents,
                                saved.getTotalAmount(),
                                saved.getCreatedAt());

                // Send event to Kafka asynchronously
                orderEventProducer.publishOrderCreatedEvent(event);

                return OrderResponse.from(saved,
                                "Order placed successfully. Background processing started asynchronously via Kafka.");
        }

        public OrderResponse getOrderById(Long id) {
                Order order = orderRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
                return OrderResponse.from(order, "Order retrieved successfully.");
        }

        public List<OrderResponse> getAllOrders() {
                return orderRepository.findAll().stream()
                                .map(o -> OrderResponse.from(o, null))
                                .toList();
        }
}
