package com.example.marketflow.marketplace;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SellerOrderRepository extends JpaRepository<SellerOrderEntity, Long> {
    @Query("select s.orderId from SellerOrderEntity s where s.id = :id")
    Optional<Long> findOrderId(@Param("id") Long id);
    List<SellerOrderEntity> findAllByOrderIdOrderBySellerId(Long orderId);
    Page<SellerOrderEntity> findAllBySellerIdOrderByCreatedAtDescIdDesc(Long sellerId, Pageable pageable);
}
