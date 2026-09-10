package com.example.marketflow.Repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.marketflow.products.ProductEntity;

import jakarta.persistence.EntityManager;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldReturnOnlyActiveProductsWithPositiveStock() {
        ProductEntity available = product("Keyboard", 10, true);
        ProductEntity inactive = product("Mouse", 10, false);
        ProductEntity outOfStock = product("Monitor", 0, true);

        productRepository.saveAllAndFlush(List.of(available, inactive, outOfStock));

        List<ProductEntity> result =
                productRepository.findAllByActiveTrueAndQuantityGreaterThan(0);

        assertEquals(1, result.size());
        assertEquals("Keyboard", result.get(0).getName());
    }

    @Test
    void shouldDecreaseStockWhenEnoughProductsExist() {
        ProductEntity saved = productRepository.saveAndFlush(
                product("Keyboard", 10, true)
        );

        int updatedRows = productRepository.decreaseStock(saved.getId(), 3);
        entityManager.flush();
        entityManager.clear();

        ProductEntity updated = productRepository.findById(saved.getId()).orElseThrow();
        assertEquals(1, updatedRows);
        assertEquals(7, updated.getQuantity());
    }

    @Test
    void shouldNotDecreaseStockWhenQuantityIsInsufficient() {
        ProductEntity saved = productRepository.saveAndFlush(
                product("Keyboard", 2, true)
        );

        int updatedRows = productRepository.decreaseStock(saved.getId(), 3);
        entityManager.flush();
        entityManager.clear();

        ProductEntity unchanged = productRepository.findById(saved.getId()).orElseThrow();
        assertEquals(0, updatedRows);
        assertEquals(2, unchanged.getQuantity());
    }


    private ProductEntity product(String name, int quantity, boolean active) {
        ProductEntity product = new ProductEntity(
                5L,
                name,
                "Description",
                new BigDecimal("1000.00"),
                quantity,
                name.toLowerCase() + ".jpg"
        );
        product.setActive(active);
        return product;
    }
}
