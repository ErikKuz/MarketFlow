package com.example.marketflow.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.marketflow.products.ProductEntity;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProductEntity>{
    @Query("select p from ProductEntity p where p.active = true and p.hidden = false and p.quantity > :quantity")
    List<ProductEntity> findAllByActiveTrueAndQuantityGreaterThan(Integer quantity);
    List<ProductEntity> findAllBySellerId(Long sellerId);
    Optional<ProductEntity> findByIdAndSellerId(Long productId,Long sellerId);
    @Modifying
    @Query(value = """
            UPDATE products
            SET quantity = quantity - :requestedQuantity
            WHERE id = :productId
            AND active = TRUE
            AND hidden = FALSE
            AND quantity >= :requestedQuantity
            """, nativeQuery = true)
    int decreaseStock(
            @Param("productId") Long productId,
            @Param("requestedQuantity") Integer requestedQuantity
    );

    @Modifying
    @Query(value = """
            UPDATE products
            SET quantity = quantity + :quantity
            WHERE id = :productId
            """, nativeQuery = true)
    int increaseStock(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity
    );
}
