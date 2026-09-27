# MD04_SS12_Bai3: Anti-Overselling with Redisson Distributed Lock, Kafka & Redis Cache

This repository contains the complete implementation for **Exercise 3 (Bài tập 3)** of Module 04 - Session 12, **inheriting all features from Exercise 1 and Exercise 2**.

---

## 1. Objectives & Overview

- **Inherited from Exercise 1 (Redis Cache-aside):**
  - Product Service with **Cache-aside Pattern** using Redis Cache.
  - 30-minute TTL for cached products.
  - Automatic cache eviction (`@CacheEvict`) upon updating product data.
- **Inherited from Exercise 2 (Kafka Async Order Processing):**
  - **Order-Service (Producer):** Saves orders to DB with status `PENDING` and asynchronously publishes `OrderCreatedEvent` to Kafka topic `order-events` without blocking the client.
  - **Email-Service (Consumer):** Listens to `order-events` (`email-service-group`) to send confirmation emails.
  - **Inventory-Service (Consumer):** Listens to `order-events` (`inventory-service-group`) to deduct stock asynchronously.
- **Exercise 3 Core Implementation (Anti-Overselling with Redisson Lock):**
  - **Flash Sale Concurrency Protection:** When multiple concurrent requests compete for scarce items (e.g., 100 requests for 1 stock item), prevents race conditions and overselling.
  - **Redisson Distributed Lock:** Acquires distributed lock with key `lock:product:{id}` using `RLock.tryLock(waitTime, leaseTime, TimeUnit)`.
  - **Safe Execution & Unlock:** Performs inventory stock checks and deduction only after acquiring the lock, guaranteeing lock release in a `finally` block (`if (lock.isHeldByCurrentThread()) lock.unlock();`).
  - **Strict Outcome:** Exactly matches available stock to successful orders; surplus buyers receive a `"Sản phẩm đã hết hàng"` rejection (HTTP 409 Conflict).

---

## 2. System Architecture

```
                    +--------------------+
                    |   Client Request   |
                    +---------+----------+
                              |
                 POST /api/orders/flash-sale
                              |
                              v
                +----------------------------+
                |   FlashSaleOrderService    |
                +--------------+-------------+
                               |
               1. Acquire Lock | Key: "lock:product:{id}"
                               v
                    +---------------------+
                    | Redis Distributed   |
                    |   Lock (Redisson)   |
                    +----------+----------+
                               |
                   Lock Acquired!
                               v
                    +---------------------+
                    | Check & Deduct Stock|
                    |   (In Repository)   |
                    +----------+----------+
                               |
                    Stock >= Qty ?
                   /              \
             YES  /                \  NO
                 v                  v
     +--------------------+   +-----------------------+
     |   Order Service    |   | OutOfStockException   |
     |   (Save PENDING)   |   | "Sản phẩm đã hết hàng"|
     +---------+----------+   +-----------------------+
               |                          |
               | Publishes Event          |
               v                          |
      ====================                |
      Kafka Topic "events"                |
      ====================                |
         /              \                 |
        v                v                v
   +----------+    +-----------+    +-----------+
   |  Email   |    | Inventory |    |  finally  |
   | Consumer |    |  Consumer |    |  unlock!  |
   +----------+    +-----------+    +-----------+
```

---

## 3. Technology Stack

- **Java:** 21 LTS
- **Spring Boot:** 4.1.1
- **Distributed Lock:** Redisson 3.40.2 (`RedissonClient`, `RLock`)
- **Cache & Key-Value:** Redis 7 (Spring Data Redis, Lettuce, Cache-Aside)
- **Messaging:** Apache Kafka (KRaft mode, Spring Kafka, `@EmbeddedKafka`)
- **Testing:** JUnit 5, AssertJ, Awaitility, Mockito
- **Build Tool:** Gradle

---

## 4. Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Starting Infrastructure

```bash
docker compose up -d
```
Starts Redis on port `6379` and Kafka KRaft on port `9092`.

### Running the Application

```bash
./gradlew bootRun
```
The application starts on port `8083`.

---

## 5. API Endpoints

### Flash Sale Order (Bai 3)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/orders/flash-sale` | Purchases flash-sale product protected by Redisson distributed lock |

#### Sample Request (`POST /api/orders/flash-sale`):
```json
{
  "customerId": 1001,
  "customerEmail": "user@example.com",
  "productId": 4,
  "quantity": 1
}
```

#### Success Response (`201 Created`):
```json
{
  "id": 1,
  "customerId": 1001,
  "customerEmail": "user@example.com",
  "status": "PENDING",
  "totalAmount": 89990000,
  "createdAt": "2026-09-27T15:30:00Z",
  "message": "Order placed successfully. Background processing started asynchronously via Kafka."
}
```

#### Out-of-Stock Response (`409 Conflict`):
```json
{
  "type": "about:blank",
  "title": "Product Out Of Stock",
  "status": 409,
  "detail": "Sản phẩm đã hết hàng"
}
```

### Regular Orders & Product APIs (Inherited from Bai 1 & 2)

- `GET /api/products`: Retrieve all products
- `GET /api/products/{id}`: Cached product details (30m TTL, ~200ms DB miss, <10ms Redis hit)
- `PUT /api/products/{id}`: Update product & evict cache (`@CacheEvict`)
- `POST /api/orders`: Regular order placement with async Kafka dispatch
- `GET /api/orders/{id}`: Get order details

---

## 6. Verification & Concurrency Stress Test

To verify anti-overselling under heavy concurrency, run:
```bash
./gradlew test
```

### Concurrency Stress Test Details (`RedissonFlashSaleConcurrencyTest`):
- **Scenario:** 100 concurrent threads simultaneously race to buy a limited Flash Sale item with **stock = 1**.
- **Results:**
  - Exactly **1 request succeeds** (`successCount == 1`).
  - Exactly **99 requests are rejected** with `"Sản phẩm đã hết hàng"` (`outOfStockCount == 99`).
  - Final remaining stock is strictly **0** (no negative stock, no overselling).
  - Redisson lock `lock:product:4` is cleanly released (`isLocked() == false`).
