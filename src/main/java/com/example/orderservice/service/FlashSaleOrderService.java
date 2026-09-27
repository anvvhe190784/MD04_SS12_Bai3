package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderItemRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.exception.OutOfStockException;
import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.product.model.Product;
import com.example.orderservice.product.repository.ProductRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FlashSaleOrderService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleOrderService.class);

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;
    private final OrderService orderService;

    public FlashSaleOrderService(
        RedissonClient redissonClient,
        ProductRepository productRepository,
        OrderService orderService
    ) {
        this.redissonClient = redissonClient;
        this.productRepository = productRepository;
        this.orderService = orderService;
    }

    public OrderResponse purchaseFlashSaleProduct(Long customerId, String customerEmail, Long productId, int quantity) {
        String lockKey = "lock:product:" + productId;
        RLock lock = redissonClient.getLock(lockKey);

        log.info("[FLASH SALE] Customer {} ({}) attempting to acquire lock '{}'", customerId, customerEmail, lockKey);

        boolean isLocked = false;
        try {
            isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("[FLASH SALE] Customer {} failed to acquire lock '{}' (System Busy)", customerId, lockKey);
                throw new OutOfStockException("Sản phẩm đã hết hàng");
            }

            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại với id: " + productId));

            Integer currentStock = product.getStock();
            log.info("[FLASH SALE] Acquired lock '{}'. Current stock for product '{}' (id={}) is {}",
                lockKey, product.getName(), productId, currentStock);

            if (currentStock == null || currentStock < quantity) {
                log.warn("[FLASH SALE] Out of stock for product id={} (Requested: {}, Available: {})",
                    productId, quantity, currentStock);
                throw new OutOfStockException("Sản phẩm đã hết hàng");
            }

            productRepository.deductStock(productId, quantity);
            log.info("[FLASH SALE] Stock deducted successfully for product id={}. Remaining stock: {}",
                productId, product.getStock());

            CreateOrderRequest orderRequest = new CreateOrderRequest(
                customerId,
                customerEmail,
                List.of(new CreateOrderItemRequest(productId, product.getName(), quantity, product.getPrice()))
            );

            return orderService.createOrder(orderRequest);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Flash sale transaction interrupted", e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[FLASH SALE] Released lock '{}' successfully", lockKey);
            }
        }
    }
}
