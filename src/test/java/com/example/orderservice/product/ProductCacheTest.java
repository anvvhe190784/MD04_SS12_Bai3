package com.example.orderservice.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.orderservice.product.dto.ProductResponse;
import com.example.orderservice.product.dto.UpdateProductRequest;
import com.example.orderservice.product.repository.ProductRepository;
import com.example.orderservice.product.service.ProductService;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "order-events" })
@DirtiesContext
class ProductCacheTest {

    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisCacheConfiguration redisCacheConfiguration;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache("products");
        if (cache != null) {
            cache.clear();
        }
        clearInvocations(productRepository);
    }

    @Test
    @DisplayName("Verify Redis Cache configuration TTL is 30 minutes (Inherited from Bai 1)")
    void testRedisCacheTtlConfiguration() {
        Duration ttl = redisCacheConfiguration.getTtlFunction().getTimeToLive("products", "1");
        assertThat(ttl).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("Verify Cache-Aside pattern: 1st call touches DB (~200ms), 2nd call hits Redis (<50ms)")
    void testCacheAsidePattern() {
        Long productId = 1L;

        // 1st access: Cache Miss -> calls DB
        long start1 = System.currentTimeMillis();
        ProductResponse response1 = productService.getProductById(productId);
        long elapsed1 = System.currentTimeMillis() - start1;

        assertThat(response1).isNotNull();
        assertThat(response1.id()).isEqualTo(productId);
        assertThat(elapsed1).isGreaterThanOrEqualTo(190);
        verify(productRepository, times(1)).findById(productId);

        // Verify item is now in Redis cache
        Cache cache = cacheManager.getCache("products");
        assertThat(cache).isNotNull();
        assertThat(cache.get(productId)).isNotNull();

        // 2nd access: Cache Hit -> reads from Redis without querying DB
        long start2 = System.currentTimeMillis();
        ProductResponse response2 = productService.getProductById(productId);
        long elapsed2 = System.currentTimeMillis() - start2;

        assertThat(response2).isNotNull();
        assertThat(response2.id()).isEqualTo(productId);
        assertThat(elapsed2).isLessThan(50);
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("Verify @CacheEvict: updating product clears Redis cache, next read misses cache and fetches fresh DB data")
    void testCacheEvictOnUpdate() {
        Long productId = 2L;

        // Populate cache
        ProductResponse initial = productService.getProductById(productId);
        assertThat(initial).isNotNull();
        Cache cache = cacheManager.getCache("products");
        assertThat(cache).isNotNull();
        assertThat(cache.get(productId)).isNotNull();

        // Update product -> triggers @CacheEvict
        UpdateProductRequest updateReq = new UpdateProductRequest(
                "MacBook Pro M4 Max (Inherited & Updated)",
                "36GB Unified RAM, 1TB SSD",
                new BigDecimal("69990000"),
                "https://example.com/images/macbook-m4-max.jpg");
        ProductResponse updated = productService.updateProduct(productId, updateReq);
        assertThat(updated.name()).isEqualTo("MacBook Pro M4 Max (Inherited & Updated)");

        // Verify cache was evicted
        assertThat(cache.get(productId)).isNull();

        // Read again -> must fetch from DB again
        clearInvocations(productRepository);
        long start = System.currentTimeMillis();
        ProductResponse freshResponse = productService.getProductById(productId);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(freshResponse.name()).isEqualTo("MacBook Pro M4 Max (Inherited & Updated)");
        assertThat(elapsed).isGreaterThanOrEqualTo(190);
        verify(productRepository, times(1)).findById(productId);
    }
}
