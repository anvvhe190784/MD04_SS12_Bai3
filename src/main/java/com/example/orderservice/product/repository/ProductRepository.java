package com.example.orderservice.product.repository;

import com.example.orderservice.product.model.Product;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(ProductRepository.class);
    private final Map<Long, Product> database = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(3);

    @PostConstruct
    public void initData() {
        saveInternal(new Product(1L, "iPhone 16 Pro Max", "Titan sa mac, 256GB", new BigDecimal("34990000"),
                "https://example.com/images/iphone16.jpg"));
        saveInternal(new Product(2L, "MacBook Pro M4", "Chip M4 Pro, 24GB RAM, 512GB SSD", new BigDecimal("49990000"),
                "https://example.com/images/macbook-m4.jpg"));
        saveInternal(new Product(3L, "AirPods Pro 2", "Chong on chu dong, USB-C", new BigDecimal("5990000"),
                "https://example.com/images/airpods-pro.jpg"));
        log.info("ProductRepository: Initialized {} sample products into database", database.size());
    }

    public Optional<Product> findById(Long id) {
        log.info("--> [PRODUCT DATABASE ACCESS] Querying database for Product ID: {}", id);
        simulateDatabaseLatency();
        Product product = database.get(id);
        return Optional.ofNullable(product);
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(idGenerator.incrementAndGet());
        }
        log.info("--> [PRODUCT DATABASE ACCESS] Saving Product ID: {} to database", product.getId());
        simulateDatabaseLatency();
        saveInternal(product);
        return product;
    }

    public List<Product> findAll() {
        return new ArrayList<>(database.values());
    }

    private void saveInternal(Product product) {
        database.put(product.getId(), product);
    }

    private void simulateDatabaseLatency() {
        try {
            // Simulate SQL DB network round-trip & query execution (~200ms)
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
