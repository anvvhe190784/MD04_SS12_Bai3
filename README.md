# MD04_SS12_Bai2: Asynchronous Order Processing with Kafka & Redis Cache

This repository contains the complete implementation for **Exercise 2 (Bài tập 2)** of Module 04 - Session 12, **inheriting all features from Exercise 1 (Bài tập 1)**.

---

## 1. Objectives & Overview

- **Inherited from Exercise 1:**
  - Product Service with **Cache-aside Pattern** using Redis Cache.
  - 30-minute TTL for cached products.
  - Automatic cache eviction (`@CacheEvict`) upon updating product data.
  - Initial simulated database query latency of ~200ms; subsequent cached reads complete in < 10ms.
- **Exercise 2 Core Implementations:**
  - **Asynchronous Order Processing** using Apache Kafka.
  - **Order-Service (Producer):** Receives client orders, resolves product metadata from Redis-cached Product Service, persists orders into database with status `PENDING`, and asynchronously publishes `OrderCreatedEvent` to the Kafka topic `order-events` without blocking the client.
  - **Email-Service (Consumer):** Listens to `order-events` with Consumer Group `email-service-group` to simulate dispatching confirmation emails.
  - **Inventory-Service (Consumer):** Listens to `order-events` with Consumer Group `inventory-service-group` to simulate inventory stock deduction independently and concurrently.

---

## 2. System Architecture

```
                    +--------------------+
                    |   Client Request   |
                    +---------+----------+
                              |
                     POST /api/orders
                              |
                              v
                    +--------------------+
                    |   Order Service    |
                    |     (Producer)     |
                    +---+------------+---+
                        |            |
         Queries Product|            | Saves Order (PENDING)
         via Cache-Aside|            |
                        v            v
            +---------------+   +------------+
            |  Redis Cache  |   |  Order DB  |
            |   (30m TTL)   |   +------------+
            +---------------+
                        |
                        | Publishes OrderCreatedEvent (Async non-blocking)
                        v
         =======================================
               Kafka Topic: "order-events"
         =======================================
                   /                 \
                  /                   \
        Consumer Group:           Consumer Group:
     "email-service-group"    "inventory-service-group"
                /                       \
               v                         v
     +-------------------+     +-------------------+
     |   Email Service   |     | Inventory Service |
     |  Sends Order Mail |     | Deducts Stock     |
     +-------------------+     +-------------------+
```

---

## 3. Technology Stack

- **Java:** 21 LTS
- **Spring Boot:** 4.1.1
- **Apache Kafka:** KRaft Mode (Producer & Consumer with `@KafkaListener`, Spring Kafka, `@EmbeddedKafka` for integration testing)
- **Redis:** Redis 7 (Spring Data Redis, Lettuce, Cache-Aside, JSON serialization)
- **Testing:** JUnit 5, AssertJ, Awaitility, Mockito
- **Build Tool:** Gradle

---

## 4. Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Starting Infrastructure (Kafka & Redis)

Start Redis and Kafka KRaft container:
```bash
docker compose up -d
```

### Running the Application

```bash
./gradlew bootRun
```
The application will start on port `8082`.

---

## 5. API Endpoints

### Product Management (Inherited from Bai 1)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/products` | Retrieve all products |
| `GET` | `/api/products/{id}` | Retrieve product by ID (Cache-aside: 1st call ~200ms DB, subsequent <10ms Redis) |
| `POST` | `/api/products` | Create a new product |
| `PUT` | `/api/products/{id}` | Update product & invalidate cache (`@CacheEvict`) |

### Order Processing (Bai 2)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/orders` | Place a new order; returns immediately (`PENDING`), dispatches Kafka event |
| `GET` | `/api/orders/{id}` | Retrieve order status by ID |
| `GET` | `/api/orders` | List all orders |

#### Sample Request (`POST /api/orders`):
```json
{
  "customerId": 1001,
  "customerEmail": "customer@example.com",
  "items": [
    {
      "productId": 1,
      "productName": "iPhone 16 Pro Max",
      "quantity": 1,
      "price": 34990000
    },
    {
      "productId": 3,
      "productName": "AirPods Pro 2",
      "quantity": 2,
      "price": 5990000
    }
  ]
}
```

#### Sample Response:
```json
{
  "id": 1,
  "customerId": 1001,
  "customerEmail": "customer@example.com",
  "status": "PENDING",
  "totalAmount": 46970000,
  "createdAt": "2026-09-27T15:10:00Z",
  "message": "Order placed successfully. Background processing started asynchronously via Kafka."
}
```

---

## 6. Running Automated Tests

Run integration and unit tests:
```bash
./gradlew test
```
All tests verify:
1. Cache-aside behavior, 30m TTL, and cache eviction.
2. Immediate response from `OrderService` (non-blocking).
3. Concurrent processing of `OrderCreatedEvent` by both `EmailService` and `InventoryService`.
