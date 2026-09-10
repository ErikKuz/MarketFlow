package com.example.marketflow.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.marketflow.cart.CartItemEntity;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CartItemEntity i where i.buyerId = :buyerId and i.selected = true order by i.productId")
    List<CartItemEntity> findSelectedForCheckout(@Param("buyerId") Long buyerId);

    List<CartItemEntity> findAllByBuyerId(Long buyerId);
    Optional<CartItemEntity> findByIdAndBuyerId(Long id, Long buyerId);
    List<CartItemEntity> findAllByBuyerIdAndSelectedTrue(Long buyerId);
    Optional<CartItemEntity> findByBuyerIdAndProductId(Long buyerId, Long productId);

    @Modifying
    @Query("""
            DELETE FROM CartItemEntity item
            WHERE item.buyerId = :buyerId
            AND item.selected = true
            """)
    int deleteSelectedByBuyerId(
            @Param("buyerId") Long buyerId
    );
}
