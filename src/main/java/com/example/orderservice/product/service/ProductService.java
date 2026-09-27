package com.example.orderservice.product.service;

import com.example.orderservice.exception.ResourceNotFoundException;
import com.example.orderservice.product.dto.CreateProductRequest;
import com.example.orderservice.product.dto.ProductResponse;
import com.example.orderservice.product.dto.UpdateProductRequest;
import com.example.orderservice.product.model.Product;
import com.example.orderservice.product.repository.ProductRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        log.info("ProductService: getProductById({}) cache miss - fetching from database", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductResponse.from(product);
    }

    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        log.info("ProductService: updateProduct({}) - evicting cache and updating database", id);
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (request.name() != null) {
            existing.setName(request.name());
        }
        if (request.description() != null) {
            existing.setDescription(request.description());
        }
        if (request.price() != null) {
            existing.setPrice(request.price());
        }
        if (request.imageUrl() != null) {
            existing.setImageUrl(request.imageUrl());
        }

        Product saved = productRepository.save(existing);
        return ProductResponse.from(saved);
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("ProductService: createProduct({})", request.name());
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .build();
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
