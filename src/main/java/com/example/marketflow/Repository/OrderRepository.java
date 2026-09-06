package com.example.marketflow.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.marketflow.Order.OrderEntity;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    org.springframework.data.domain.Page<OrderEntity> findAllByBuyerIdOrderByCreatedAtDescIdDesc(
            Long buyerId, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findLocked(@Param("id") Long id);

    @Query("select o.id from OrderEntity o where o.status = com.example.marketflow.Order.OrderStatus.CREATED and o.paymentExpiresAt <= :now order by o.id")
    java.util.List<Long> findExpiredIds(@Param("now") java.time.Instant now, org.springframework.data.domain.Pageable pageable);

    @Query("select o.id from OrderEntity o where o.status = com.example.marketflow.Order.OrderStatus.COMPLETED and o.fundsReleased = false and o.returnDeadline <= :now order by o.id")
    java.util.List<Long> findSettlementIds(@Param("now") java.time.Instant now, org.springframework.data.domain.Pageable pageable);
    Optional<OrderEntity> findByIdAndBuyerId(
            Long orderId,
            Long buyerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT order
            FROM OrderEntity order
            WHERE order.id = :orderId
            AND order.buyerId = :buyerId
            """)
    Optional<OrderEntity> findForPayment(
            @Param("orderId") Long orderId,
            @Param("buyerId") Long buyerId
    );
}
