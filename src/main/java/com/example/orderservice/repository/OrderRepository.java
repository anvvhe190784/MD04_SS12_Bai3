package com.example.orderservice.repository;

import com.example.orderservice.model.Order;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final Map<Long, Order> database = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1000);

    public Order save(Order order) {
        if (order.getId() == null) {
            order.setId(idGenerator.incrementAndGet());
        }
        database.put(order.getId(), order);
        return order;
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(database.get(id));
    }

    public List<Order> findAll() {
        return new ArrayList<>(database.values());
    }
}
