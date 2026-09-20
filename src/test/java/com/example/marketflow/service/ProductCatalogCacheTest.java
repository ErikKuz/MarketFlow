package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.Seller.Service.SellerProductService;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.products.UpdateProductRequest;

@SpringJUnitConfig(ProductCatalogCacheTest.TestConfig.class)
class ProductCatalogCacheTest {

    @Autowired ProductService productService;
    @Autowired SellerProductService sellerProductService;
    @Autowired ProductRepository productRepository;
    @Autowired AuthService authService;
    @Autowired CacheManager cacheManager;

    private ProductEntity product;

    @BeforeEach
    void prepare() {
        org.mockito.Mockito.reset(productRepository, authService);
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        product = new ProductEntity(
                7L, "Keyboard", "Mechanical keyboard",
                new BigDecimal("100.00"), 5, "keyboard.jpg"
        );
        product.setId(20L);
        when(productRepository.findAllByActiveTrueAndQuantityGreaterThan(0))
                .thenReturn(List.of(product));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));
    }

    @Test
    void cachesCatalogReadsAndClearsBothCachesAfterProductChange() {
        productService.getAvailableProducts();
        productService.getAvailableProducts();
        productService.getProductById(20L);
        productService.getProductById(20L);

        verify(productRepository, times(1)).findAllByActiveTrueAndQuantityGreaterThan(0);
        verify(productRepository, times(1)).findById(20L);

        when(authService.isSeller(7L)).thenReturn(true);
        when(productRepository.findForSellerUpdate(20L, 7L)).thenReturn(Optional.of(product));
        sellerProductService.updateProduct(
                20L,
                7L,
                new UpdateProductRequest("Updated keyboard", null, null, null)
        );

        productService.getAvailableProducts();
        productService.getProductById(20L);

        verify(productRepository, times(2)).findAllByActiveTrueAndQuantityGreaterThan(0);
        verify(productRepository, times(2)).findById(20L);
    }

    @Test
    void catalogResultSupportsDefaultRedisCacheSerialization() {
        var catalogue = productService.getAvailableProducts();

        assertDoesNotThrow(() -> {
            try (var output = new ObjectOutputStream(new ByteArrayOutputStream())) {
                output.writeObject(catalogue);
            }
        });
    }

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("catalogProducts", "catalogProduct");
        }

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        AuthService authService() {
            return mock(AuthService.class);
        }

        @Bean
        ProductService productService(ProductRepository repository) {
            return new ProductService(repository);
        }

        @Bean
        SellerProductService sellerProductService(
                ProductRepository repository,
                AuthService authService
        ) {
            return new SellerProductService(repository, authService);
        }
    }
}
