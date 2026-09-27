package com.example.orderservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.exception.OutOfStockException;
import com.example.orderservice.product.model.Product;
import com.example.orderservice.product.repository.ProductRepository;
import com.example.orderservice.service.FlashSaleOrderService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"order-events"})
class RedissonFlashSaleConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(RedissonFlashSaleConcurrencyTest.class);

    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired
    private FlashSaleOrderService flashSaleOrderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedissonClient redissonClient;

    private static final Long FLASH_SALE_PRODUCT_ID = 4L;

    @BeforeEach
    void setUp() {
        productRepository.setStock(FLASH_SALE_PRODUCT_ID, 1);
        RLock lock = redissonClient.getLock("lock:product:" + FLASH_SALE_PRODUCT_ID);
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Test
    @DisplayName("Verify Anti-Overselling: 100 concurrent requests competing for 1 stock item -> Exactly 1 succeeds, 99 fail with 'Sản phẩm đã hết hàng'")
    void test100ConcurrentUsersCompetingForSingleStockItem() throws InterruptedException {
        int numberOfConcurrentUsers = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentUsers);
        CountDownLatch readyLatch = new CountDownLatch(numberOfConcurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfConcurrentUsers);

        AtomicInteger successOrders = new AtomicInteger(0);
        AtomicInteger outOfStockRejections = new AtomicInteger(0);
        AtomicInteger unexpectedErrors = new AtomicInteger(0);

        log.info(">>> STARTING FLASH SALE STRESS TEST: 100 threads competing for product {} (Stock = 1)", FLASH_SALE_PRODUCT_ID);

        for (int i = 1; i <= numberOfConcurrentUsers; i++) {
            final long customerId = 1000L + i;
            final String customerEmail = "customer_" + i + "@flashsale.com";

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    OrderResponse response = flashSaleOrderService.purchaseFlashSaleProduct(
                        customerId,
                        customerEmail,
                        FLASH_SALE_PRODUCT_ID,
                        1
                    );

                    if (response != null && response.id() != null) {
                        successOrders.incrementAndGet();
                        log.info(">>> SUCCESS: Customer {} won the Flash Sale item! Order #{}", customerId, response.id());
                    }
                } catch (OutOfStockException e) {
                    if ("Sản phẩm đã hết hàng".equals(e.getMessage())) {
                        outOfStockRejections.incrementAndGet();
                    } else {
                        unexpectedErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Unexpected error in concurrent worker: ", e);
                    unexpectedErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean allReady = readyLatch.await(10, TimeUnit.SECONDS);
        assertThat(allReady).isTrue();

        startLatch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        log.info(">>> FLASH SALE COMPLETED! Results: Success = {}, OutOfStock = {}, Errors = {}",
            successOrders.get(), outOfStockRejections.get(), unexpectedErrors.get());

        assertThat(completed).isTrue();
        assertThat(successOrders.get()).isEqualTo(1);
        assertThat(outOfStockRejections.get()).isEqualTo(99);
        assertThat(unexpectedErrors.get()).isEqualTo(0);

        Product finalProduct = productRepository.findById(FLASH_SALE_PRODUCT_ID).orElseThrow();
        assertThat(finalProduct.getStock()).isEqualTo(0);

        RLock lock = redissonClient.getLock("lock:product:" + FLASH_SALE_PRODUCT_ID);
        assertThat(lock.isLocked()).isFalse();
    }
}
